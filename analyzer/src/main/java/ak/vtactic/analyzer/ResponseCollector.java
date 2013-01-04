package ak.vtactic.analyzer;

import java.util.HashMap;
import java.util.Map;

import ak.vcon.model.NodeEventInfo;
import ak.vtactic.math.DiscreteProbDensity;

public class ResponseCollector {
	Map<String, DiscreteProbDensity> nodeResponse = new HashMap<>();
	
	public void collect(NodeEventInfo event) {
		if (event.isReply()) {
			String remoteNode = event.getRemote().getAddress();
			DiscreteProbDensity pdf = nodeResponse.get(remoteNode);
			if (pdf == null) {
				pdf = new DiscreteProbDensity();
				nodeResponse.put(remoteNode, pdf);
			}
			pdf.add(event.getResponseTime());
		}
	}
	
	public Map<String, DiscreteProbDensity> getNodeResponse() {
		return nodeResponse;
	}
}
