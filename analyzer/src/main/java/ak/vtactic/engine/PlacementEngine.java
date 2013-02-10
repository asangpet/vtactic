package ak.vtactic.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import ak.vtactic.math.DiscreteProbDensity;
import ak.vtactic.primitives.ComponentNode;
import ak.vtactic.primitives.ModelApplication;
import ak.vtactic.util.Pair;

import com.google.common.base.Function;

public class PlacementEngine {
	private static final Logger log = LoggerFactory.getLogger(PlacementEngine.class);
	
	ModelApplication app;
	
	HostAssignment hosts;
	
	public PlacementEngine(ModelApplication app, int nHost) {
		this.app = app;
		hosts = new HostAssignment();
		if (nHost <= 0) {
			nHost = 1;
		}
		for (int i = 0; i< nHost; i++) {
			Host host = new Host("host_"+i);
			hosts.add(host);
		}
		randomAssign();
	}
	
	public PlacementEngine randomDistributedAssign() {
		Set<ComponentNode> nodes = app.getNodes().values();
		Iterator<Host> hostIter = hosts.randomIterator();		
		for (ComponentNode node : nodes) {
			if (!hostIter.hasNext()) {
				hostIter = hosts.randomIterator();
			}
			Host host = hostIter.next();
			hosts.assign(host, node);
		}
		return this;
	}
	
	public PlacementEngine randomAssign() {
		List<Host> host = hosts.getHostList();
		Set<ComponentNode> nodes = app.getNodes().values();
		for (ComponentNode node : nodes) {
			int idx = (int)Math.round(Math.floor(Math.random()*host.size()));
			hosts.assign(host.get(idx), node);
		}
		return this;
	}
	
	Map<ComponentNode, Double> impacts = new HashMap<ComponentNode, Double>();
	
	private Pair<Host, ComponentNode> nextNode(Collection<ComponentNode> nodes, Collection<Host> hostList) {
		double effective = 0;
		ComponentNode effectiveNode = nodes.iterator().next();
		// Recalculate expected co-arrivals
		for (ComponentNode node : nodes) {
			Set<ComponentNode> contenders = hosts.getContenders(node);
			double co = node.findExpectedCoarrivalProb(contenders, app.getEntryNode().getInterarrival());
			double effectiveImpact = co * impacts.get(node); 
			if (effectiveImpact > effective) {
				effective = effectiveImpact;
				effectiveNode = node;
			}
		}
		
		List<ComponentNode> comps = new ArrayList<>();
		double minCo = Double.MAX_VALUE;
		Host minHost = null;
		
		for (Host host : hostList) {
			comps.clear();
			comps.addAll(host.getComponents());
			comps.remove(effectiveNode);
			
			double co = effectiveNode.findExpectedCoarrivalProb(comps, app.getEntryNode().getInterarrival());
			if (co < minCo) {
				minCo = co;
				minHost = host;
			}
		}		
		log.info("New assignment move {} to {}", effectiveNode.getHost(), minHost.getName());
		return new Pair<Host, ComponentNode>(minHost, effectiveNode);
	}
	
	public boolean greedyAssign() {
		List<Host> hostList = hosts.getHostList();
		Set<ComponentNode> nodes = new HashSet<ComponentNode>();
		nodes.addAll(app.getNodes().values());				
		
		// find impact if needed
		if (impacts.isEmpty()) {
			ComponentNode entry = app.getEntryNode();
			for (ComponentNode node : nodes) {
				Map<String, DiscreteProbDensity> result = new HashMap<String, DiscreteProbDensity>();
				Map<String, DiscreteProbDensity> procMod = new HashMap<String, DiscreteProbDensity>();
				double avg = node.getProcessingTime().average();
				DiscreteProbDensity shift = node.getProcessingTime().shiftByValue(avg*0.5);
				procMod.put(node.getHost(), shift);
				// force shift
				app.deepEval(entry, result, procMod, new HostAssignment());
				DiscreteProbDensity pdf = result.get(entry.getHost());
				double impact = (pdf.average() - avg) / avg; 
				impacts.put(node, impact);
			}
		}
		
		Pair<Host, ComponentNode> newAssignment = nextNode(nodes, hostList);
		while (newAssignment.first.getComponents().contains(newAssignment.second)) {
			nodes.remove(newAssignment.second);
			if (nodes.isEmpty()) {
				log.warn("Cannot find improvement, stop!!!!");
				break;
			}
			newAssignment = nextNode(nodes, hostList);
		}
		if (!newAssignment.first.getComponents().contains(newAssignment.second)) {
			hosts.assign(newAssignment.first, newAssignment.second);
		} else {
			log.warn("Out of candidates");
			return false;
		}
		return true;
	}
	
	public void assign(String hostName, String nodeName) {
		ComponentNode node = app.getNode(nodeName);
		Host host = hosts.getHostByName(hostName);
		hosts.assign(host, node);
	}
	
	public JsonObject getPlacement() {
		JsonObject json = new JsonObject();
		for (Host host : hosts) {
			List<Object> compList = new LinkedList<Object>();
			for (ComponentNode comp : host.getComponents()) {
				compList.add(comp.getHost());
			}
			JsonArray components = new JsonArray(compList);
			json.putArray(host.getName(), components);
		}
		return json;
	}
	
	static class BestOrTest {
		DiscreteProbDensity best;
		DiscreteProbDensity test;
		
		public BestOrTest(DiscreteProbDensity b, DiscreteProbDensity t) {
			best = b;
			test = t;
		}
	}
	
	public PlacementResult place(Function<BestOrTest, Boolean> evaluator) {
		PlacementResult bestResult = new PlacementResult(getPlacement(), evaluate());
		String entry = app.getEntryNode().getHost();
		int bestIter = 0;
		
		for (int iter = 0; iter < 100; iter++) {
			Map<String, DiscreteProbDensity> evalResult = randomAssign().evaluate();
			log.info("Tested placement:{} {} {}",new Object[] { getPlacement(), iter, bestIter});
			boolean betterResult = evaluator.apply(new BestOrTest(bestResult.result.get(entry), evalResult.get(entry)));
			if (betterResult) {
				bestResult = new PlacementResult(getPlacement(), evalResult);
				bestIter = iter;
			}
		}
		
		return bestResult;
	}
	
	public PlacementResult placeGreedy(Function<BestOrTest, Boolean> evaluator) {
		PlacementResult bestResult = new PlacementResult(getPlacement(), evaluate());
		String entry = app.getEntryNode().getHost();
		int bestIter = 0;
		
		for (int iter = 0; iter < 100; iter++) {
			boolean newResult = greedyAssign();
			Map<String, DiscreteProbDensity> evalResult = this.evaluate();
			log.info("Tested placement:{} {} {}",new Object[] { getPlacement(), iter, bestIter});
			boolean betterResult = evaluator.apply(new BestOrTest(bestResult.result.get(entry), evalResult.get(entry)));
			if (betterResult) {
				bestResult = new PlacementResult(getPlacement(), evalResult);
				bestIter = iter;
			}
			if (!newResult) {
				break;
			}
		}
		
		return bestResult;
	}
	
	public PercentileEvaluator usePercentile(double percent) {
		return new PercentileEvaluator(percent);
	}
	
	static class PercentileEvaluator implements Function<BestOrTest, Boolean> {
		double percentile = 90;
		
		PercentileEvaluator(double percent) {
			percentile = percent;
		}
		
		@Override
		public Boolean apply(BestOrTest input) {
			double best = input.best.percentile(percentile);
			double test = input.test.percentile(percentile);
			
			log.info("New placement:{} {}",best,test);
			// is it better?
			return test < best;
		}
	}
	
	public Map<String, DiscreteProbDensity> evaluate() {
		// grab all dependencies
		ComponentNode entry = app.getEntryNode();
		Map<String, DiscreteProbDensity> result = new HashMap<String, DiscreteProbDensity>();
		app.deepEval(entry, result, new HashMap<String, DiscreteProbDensity>(), hosts);
		
		return result;
	}
	
	
}
