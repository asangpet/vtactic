package ak.vtactic.analyzer;

import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ak.vtactic.collector.LagCollector;
import ak.vtactic.collector.RequestExtractor;
import ak.vtactic.collector.ResponseCollector;
import ak.vtactic.math.DiscreteProbDensity;
import ak.vtactic.model.NodeEventInfo;
import ak.vtactic.service.DataService;

@Component
public class DependencyTool {
	private static final Logger logger = LoggerFactory.getLogger(DependencyTool.class);
	
	@Autowired
	DataService dataService;
	
	public Map<String, DiscreteProbDensity> collectResponse(String node, int basePort, double start, double stop) {
		Iterable<NodeEventInfo> events = dataService.getMatchedNodeEvents(node, basePort, start, stop);
		ResponseCollector collector = new ResponseCollector();
		int count = 0;
		for (NodeEventInfo event : events) {			
			if (event.isReply()) {
				count++;
			}
			//if (count > 500 && count <9500) {			
				collector.collect(event, basePort);
			//}
		}
		
		// normalized results
		Map<String, DiscreteProbDensity> normalized = new TreeMap<>();
		for (Map.Entry<String, DiscreteProbDensity> density : collector.getNodeResponse().entrySet()) {
			normalized.put(density.getKey(), density.getValue().normalize());
		}
		return normalized;
	}

	public Map<String, DiscreteProbDensity> collectLag(String node, int basePort, double start, double stop) {
		Iterable<NodeEventInfo> events = dataService.getMatchedNodeEvents(node, basePort, start, stop);
		LagCollector collector = new LagCollector(basePort);
		int count = 0;
		for (NodeEventInfo event : events) {
			if (event.isReply()) {
				count++;
			}
			//if (count > 500 && count <9500) {
				collector.collect(event);
			//}
		}
		
		// normalized results
		Map<String, DiscreteProbDensity> normalized = new TreeMap<>();
		for (Map.Entry<String, DiscreteProbDensity> density : collector.getNodeLag().entrySet()) {
			normalized.put(density.getKey(), density.getValue().normalize());
		}
		return normalized;
	}

	public RequestExtractor extract(String node, int basePort, double start, double stop) {
		Iterable<NodeEventInfo> events = dataService.getNodeEvents(node, start, stop);
		RequestExtractor collector = new RequestExtractor(basePort);
		int count = 0;
		for (NodeEventInfo event : events) {
			collector.collect(event);
			count++;
		}
		logger.info("Processed {} events",count);
		
		return collector;
	}

}