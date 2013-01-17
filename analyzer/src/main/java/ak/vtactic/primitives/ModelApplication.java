package ak.vtactic.primitives;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import ak.vtactic.analyzer.DependencyTool;
import ak.vtactic.math.DiscreteProbDensity;

@Component
@Scope("prototype")
public class ModelApplication {
	private static final Logger log = LoggerFactory.getLogger(ModelApplication.class);
	
	Map<String, ComponentNode> nodes;
	Map<String, DiscreteProbDensity> lags;
	
	ComponentNode entryNode;
	
	@Autowired DependencyTool dependencyTool;
	@Autowired BeanFactory factory;
	
	public ModelApplication build(String entryAddress, int entryPort, double startTime, double stopTime) {
		nodes = new HashMap<String, ComponentNode>();
		lags = new HashMap<String, DiscreteProbDensity>();
		
		entryNode = innerBuild(entryAddress, entryPort, startTime, stopTime, null);
		for (Map.Entry<String, DiscreteProbDensity> lag : lags.entrySet()) {
			nodes.get(lag.getKey()).lag = lag.getValue();
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
	
	public Map<String, ComponentNode> getNodes() {
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
	
	int stack = 0;
	public DiscreteProbDensity getContendedProcessingSim(ComponentNode node, Set<ComponentNode> contenders,
			DiscreteProbDensity interarrivalPdf) {
		DiscreteProbDensity xPdf = new DiscreteProbDensity();
		
		DiscreteProbDensity PbPdf = node.getProcessingTime();
		DiscreteProbDensity TbPdf = node.getLag();
		
		// max number of sampling points
		int max = 100000;
		int tb,tc,pb,pc,arrival;
		
		// stats
		int selfArrival = 0;
		int maxStack = 0;
		
		Set<String> testNode = new HashSet<String>();
		for (int i = 0; i < max; i++) {
			Expression term = pickPath();
			if (!term.contain(node.getHost())) {
				continue;
			}

			stack = 0;
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
					pb = adjust(pb,pc,tb,tc);
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
				arrival += interarrivalPdf.random();
				if (arrival > pb) {
					break;
				}
				testNode.clear();
				testNode = getNodesFromTerm(testNode, term);
				if (testNode.contains(node.getHost())) {
					tc = arrival + TbPdf.random();
					pc = PbPdf.random();
					// add self contention
					pb = adjust(pb,pc,tb,tc);
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
						pb = adjust(pb,pc,tb,tc);
					}
				}
				selfArrival++;
			} while (arrival < pb);
			
			if (stack > maxStack) {
				log.info("Adjusted pb {} stack {}", pb, stack);
				maxStack = stack;
			}
			
			xPdf.add(pb);
		}
		log.info("Total count {} {} {}",
				new Object[] { xPdf.count(),selfArrival,maxStack });
		
		// trim ends
		xPdf.getPdf()[xPdf.getPdf().length-1] = 0;
		return xPdf.normalize();
	}	

	private int adjust(int pb, int pc, int tb, int tc) {
		// contention scale
		int epsilon = stack * pc;
		
		int d = tc-tb;
		if (d >= 0) {
			if (d >= pb) {
				return pb;
			} else {
				stack++;
				if (d >= pb-pc) {
					return pb+pc-d + epsilon;
				} else {
					return pb+pc + epsilon;
				}
			}
		} else {
			if (d < -pc) {
				return pb;
			} else {
				stack++;
				if (d >= pb-pc) {
					return (pb+pc) + epsilon;
				} else {
					return (d+pb+pc) + epsilon;
				}
			}
		}				
	}

}
