package ak.vtactic.collector;

import java.util.HashMap;
import java.util.Map;

import ak.vtactic.math.DiscreteProbDensity;
import ak.vtactic.model.NodeEventInfo;

public class ResponseCollector {
	Map<String, DiscreteProbDensity> nodeResponse = new HashMap<>();
	DiscreteProbDensity clientResponses = new DiscreteProbDensity();
	public static final String RESPONSE_KEY = "response";
	
	public ResponseCollector() {
		nodeResponse.put(RESPONSE_KEY, clientResponses);
	}
	
	public void collect(NodeEventInfo event, int basePort) {
		if (event.isReply()) {
			String remoteNode = event.getRemote().getAddress();
			DiscreteProbDensity pdf = nodeResponse.get(remoteNode);
			if (pdf == null) {
				pdf = new DiscreteProbDensity();
				nodeResponse.put(remoteNode, pdf);
			}
			pdf.add(event.getResponseTime());
			
			// add upstream request
			if (event.getLocal().getPort() == basePort) {
				clientResponses.add(event.getResponseTime());
			}
		}
	}
	
	public Map<String, DiscreteProbDensity> getNodeResponse() {
		return nodeResponse;
	}
	
	public DiscreteProbDensity getClientResponse() {
		return clientResponses;
	}
}
