package ak.vtactic.primitives;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import ak.vtactic.analyzer.DependencyTool;
import ak.vtactic.engine.HostAssignment;
import ak.vtactic.math.DiscreteProbDensity;

import com.google.common.base.Function;
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;

@Component
@Scope("prototype")
public class ModelApplication {
	private static final Logger log = LoggerFactory.getLogger(ModelApplication.class);
	
	BiMap<String, ComponentNode> nodes;
	Map<String, DiscreteProbDensity> lags;
	
	ComponentNode entryNode;
	
	@Autowired DependencyTool dependencyTool;
	@Autowired BeanFactory factory;
	
	public ModelApplication build(String entryAddress, int entryPort, double startTime, double stopTime) {
		nodes = HashBiMap.create();
		lags = new HashMap<String, DiscreteProbDensity>();
		
		entryNode = innerBuild(entryAddress, entryPort, startTime, stopTime, null);
		for (Map.Entry<String, DiscreteProbDensity> lag : lags.entrySet()) {
			nodes.get(lag.getKey()).lag = lag.getValue();
		}
		DiscreteProbDensity delta = DiscreteProbDensity.deltaPdf(0);
		entryNode.lag = delta;
		if (!lags.containsKey(entryNode.getHost())) {
			lags.put(entryNode.getHost(), delta);
		}
		
		// dump output
		FluentIterable<String> expressionIterables = FluentIterable.from(nodes.entrySet())
				.transform(new Function<Map.Entry<String,ComponentNode>, String>() {
					@Override
					public String apply(Map.Entry<String, ComponentNode> entry) {
						return entry.getKey() + " = "+entry.getValue().getExpression().print(new StringBuilder()).toString();
					}
				});
		Path path = FileSystems.getDefault().getPath(".", "app-"+entryAddress+".map");
		try {
			Files.write(path, expressionIterables, Charset.defaultCharset(), StandardOpenOption.CREATE);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return this;
	}
	
	private ComponentNode innerBuild(String addr, int port, double startTime, double stopTime, DiscreteProbDensity parentLag) {
		if (nodes.containsKey(addr)) {
			return nodes.get(addr);
		}
		
		log.info("Building model for {}:{}", addr, port);
		
		ComponentNode node = factory.getBean(ComponentNode.class).host(addr).port(port);
		node.findModel(startTime, stopTime);
		node.interarrival = node.getInterarrival().normalize();
		addNode(addr, node);
		
		Map<String, DiscreteProbDensity> thislags = dependencyTool.collectLag(addr, port, startTime, stopTime);
		for (String dep : node.getDependencies().keySet()) {
			if (lags.get(dep) == null) {
				if (parentLag == null) {
					lags.put(dep, thislags.get(dep));
				} else {
					lags.put(dep, thislags.get(dep).tconv(parentLag));
				}
			}			
		}
		
		for (Map.Entry<String, Integer> dep : node.getDependencies().entrySet()) {
			innerBuild(dep.getKey(), dep.getValue(), startTime, stopTime, lags.get(dep.getKey())); 
		}
		
		return node;		
	}
	
	public void setEntryNode(ComponentNode entryNode) {
		this.entryNode = entryNode;
	}
	
	public ComponentNode getEntryNode() {
		return entryNode;
	}
	
	public BiMap<String, ComponentNode> getNodes() {
		return nodes;
	}
	
	public void addNode(String name, ComponentNode node) {
		nodes.put(name, node);
	}
	
	public ComponentNode getNode(String name) {
		return nodes.get(name);
	}
	
	public Expression pickPath() {
		return new Composite().setLeft(new Operand(entryNode.getHost())).setRight(expand(entryNode.getExpression()));
	}
	
	private Expression expand(Expression path) {
		if (path instanceof Distributed) {
			if (!((Distributed)path).isEmpty()) {
				return expand(((Distributed)path).random());				
			}
		} else if (path instanceof Operand) {
			String snode = ((Operand) path).getOperand();
			ComponentNode node = nodes.get(snode);
			if (!((Distributed)node.getExpression()).isEmpty()) {
				Expression nodeExp = expand(node.getExpression());
				return new Composite().setLeft(path).setRight(nodeExp);
			}
		} else if (path instanceof BinaryOperator) {
			try {				
				return ((BinaryOperator)path.getClass().newInstance()).setLeft(expand(((BinaryOperator) path).getLeft()))
						.setRight(expand(((BinaryOperator) path).getRight()));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return path;
	}
	
	private Set<String> getNodesFromTerm(Set<String> nodes, Expression path) {
		if (path instanceof Operand) {
			nodes.add(((Operand) path).getOperand());
		} else if (path instanceof BinaryOperator) {
			getNodesFromTerm(nodes, ((BinaryOperator) path).getLeft());
			getNodesFromTerm(nodes, ((BinaryOperator) path).getRight());
		}
		return nodes;
	}
	
	public DiscreteProbDensity getContendedProcessingSim(ComponentNode node, Set<ComponentNode> contenders,
			DiscreteProbDensity interarrivalPdf) {
		return getContendedProcessingSim(node, contenders, interarrivalPdf, node.getProcessingTime());
	}	
	
	final int maxIterations = 100000;
	boolean scaleStack = true;
	//int maxPb = 0; // debugging only, used to monitor processing time
	/**
	 * This function calculate the contended processing time of the requests
	 * Given the interarrival time, the processing time of the node.
	 * 
	 * It calculate the contended processing time based on the set of contenders
	 * (using lag time to adjust the co-arrival of the node)
	 * The lag time is used to estimate the co-arrival to indicate the contention.
	 * 
	 * The dependency is used from the function calling this one to properly combine the subsystem response time
	 * with this contended processing time
	 * 
	 * @param node
	 * @param contenders
	 * @param interarrivalPdf
	 * @param processingTime
	 * @return
	 */
	public DiscreteProbDensity getContendedProcessingSim(ComponentNode node, Set<ComponentNode> contenders,
			DiscreteProbDensity interarrivalPdf, DiscreteProbDensity processingTime) {
		DiscreteProbDensity xPdf = new DiscreteProbDensity();
		
		DiscreteProbDensity PbPdf = processingTime;
		DiscreteProbDensity TbPdf = node.getLag();
		
		// max number of sampling points
		int tb,tc,pb,pc,arrival;
		
		// stats
		int selfArrival = 0;
		int maxStack = 0;
		int limit = xPdf.getPdf().length;
		
		Set<String> testNode = new HashSet<String>();
		for (int i = 0; i < maxIterations; i++) {
			Expression term = pickPath();
			if (!term.contain(node.getHost())) {
				continue;
			}

			int stack = 0;
			// this term
			// find internal contention
			pb = PbPdf.random();
			tb = TbPdf.random();
			testNode.clear();
			testNode = getNodesFromTerm(testNode, term);			
			for (ComponentNode cnode : contenders){
				if (testNode.contains(cnode.getHost())) {
					if (!cnode.equals(entryNode)) {
						tc = cnode.getLag().random();
					} else {
						// entry node, no lag
						tc = 0;
					}
					pc = cnode.getProcessingTime().random();
					// add contention
					pb = adjust(pb,pc,tb,tc,limit,stack++);
				}
			}
			
			// find contention cause by inter-arrival
			arrival = 0;
			do {				
				// next term
				term = pickPath();			
				// get next request arrival time,
				// if it falls with in this request processing period,
				// then consider how much processing time should be scaled
				int nextArrival = interarrivalPdf.random();
				if (nextArrival > tb+pb || arrival > Integer.MAX_VALUE - nextArrival) {
					break;
				} else {
					arrival += nextArrival;
				}
				testNode.clear();
				testNode = getNodesFromTerm(testNode, term);
				if (testNode.contains(node.getHost())) {
					tc = arrival + TbPdf.random();
					pc = PbPdf.random();
					// add self contention
					pb = adjust(pb,pc,tb,tc,limit,stack++);
				}
				for (ComponentNode cnode : contenders){
					if (testNode.contains(cnode.getHost())) {
						if (!cnode.equals(entryNode)) {
							tc = arrival + cnode.getLag().random();
						} else {
							// entry node, no lag
							tc = arrival;
						}
						pc = cnode.getProcessingTime().random();
						// add contention
						pb = adjust(pb,pc,tb,tc,limit,stack++);
					}
				}
				selfArrival++;
			} while (arrival < pb);
		
			/*
			if (stack > maxStack || pb > maxPb) {				
				log.info("Adjusted pb {} stack {} self-arrival {}", new Object[] { pb, stack, selfArrival });
				if (stack > maxStack) maxStack = stack;
				if (pb > maxPb) maxPb = pb;
			}*/
			
			xPdf.add(pb);
		}
		log.info("Total count {} {} {}",
				new Object[] { xPdf.count(),selfArrival,maxStack });
		
		// trim ends
		//xPdf.getPdf()[xPdf.getPdf().length-1] = 0;
		return xPdf.normalize();
	}	

	private int adjust(int pb, int pc, int tb, int tc, int limit, int stack) {
		// contention scale on competing event
		if (scaleStack) {
			if (stack > 1) pc = pc * stack;
		}
		
		int d = tc-tb;
		if (d >= 0) {
			if (d >= pb) {
				return pb;
			} else {
				if (d >= pb-pc) {
					if (pb - d < limit - pc) {
						return pb+pc-d;
					} else {
						return limit;
					}
				} else {
					if (pb < limit - pc) {
						return pb+pc;
					} else {
						return limit;
					}
				}
			}
		} else {
			if (d < -pc) {
				return pb;
			} else {
				if (d >= pb-pc) {
					if (pb < limit - pc) {
						return (pb+pc);
					} else {
						return limit;
					}
				} else {
					if (pb < limit - pc - d) {
						return (d+pb+pc);
					} else {
						return limit;
					}
				}
			}
		}				
	}

	public void deepEval(ComponentNode node, Map<String, DiscreteProbDensity> bind, Map<String, DiscreteProbDensity> procMod, HostAssignment hosts) {
		Set<String> deps = new HashSet<>(node.getDependencies().keySet());
		Set<ComponentNode> contenders = new HashSet<ComponentNode>(hosts.getContenders(node));
		
		DiscreteProbDensity processing;
		if (deps.isEmpty() && !bind.containsKey(node.getHost())) {
			if (procMod.containsKey(node.getHost())) {
				// contained modded processing
				processing = getContendedProcessingSim(node,contenders,getEntryNode().getInterarrival(), procMod.get(node.getHost()));
			} else {
				processing = getContendedProcessingSim(node,contenders,getEntryNode().getInterarrival());
			}
			bind.put(node.getHost(), processing);
		} else {			
			Iterator<String> iter = deps.iterator();
			while (iter.hasNext()) {
				String addr = iter.next();
				if (!bind.containsKey(addr)) {
					ComponentNode depNode = getNode(addr);
					deepEval(depNode, bind, procMod, hosts);
				}
			}
			
			// We now have all bindings for dependencies, find estimate for current node
			
			if (procMod.containsKey(node.getHost())) {
				// contained modded processing
				processing = getContendedProcessingSim(node,contenders,getEntryNode().getInterarrival(), procMod.get(node.getHost()));
			} else {
				processing = getContendedProcessingSim(node,contenders,getEntryNode().getInterarrival());
			}
			
			bind.put(node.getHost(), node.estimate(bind, processing));			
		}
	}
	
	
}
