package ak.vtactic.analyzer;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ak.vcon.model.NodeEventInfo;
import ak.vcon.service.DataService;
import ak.vtactic.math.DiscreteProbDensity;

@Component
public class DependencyTool {
	@Autowired
	DataService dataService;
	
	public Map<String, DiscreteProbDensity> collectResponse(String node, int basePort, double start, double stop) {
		Iterable<NodeEventInfo> events = dataService.getNodeEvents(node, basePort, start, stop);
		ResponseCollector collector = new ResponseCollector();
		int count = 0;
		for (NodeEventInfo event : events) {
			if (event.isReply()) {
				count++;
			}
			if (count > 500 && count <9500) {
				collector.collect(event);
			}
		}
		
		// normalized results
		Map<String, DiscreteProbDensity> normalized = new TreeMap<>();
		for (Map.Entry<String, DiscreteProbDensity> density : collector.getNodeResponse().entrySet()) {
			normalized.put(density.getKey(), density.getValue().normalize());
		}
		return normalized;
	}
}