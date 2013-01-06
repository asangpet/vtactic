package ak.vtactic.collector;

import java.util.HashMap;
import java.util.Map;

import ak.vtactic.math.DiscreteProbDensity;
import ak.vtactic.model.NodeEventInfo;

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
