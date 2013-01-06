package ak.vtactic.collector;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import ak.vtactic.math.DiscreteProbDensity;
import ak.vtactic.model.Direction;
import ak.vtactic.model.NodeEventInfo;
import ak.vtactic.model.RequestToll;
import ak.vtactic.model.SocketInfo;

public class LagCollector {
	Map<String, DiscreteProbDensity> nodeLag = new HashMap<>();
	Map<SocketInfo, RequestToll> associations = new HashMap<>();
	TreeMap<RequestToll, SocketInfo> priorities = new TreeMap<>();
	
	final int basePort;
	
	public LagCollector(int basePort) {
		this.basePort = basePort;
	}
	
	public void collect(NodeEventInfo event) {
		if (event.getLocal().getPort() == basePort) {
			// This is client request
			if (event.getDirection() == Direction.IN) {
				// incoming: create association
				RequestToll toll = new RequestToll(event);
				associations.put(event.getRemote(), toll);
				priorities.put(toll, event.getRemote());
			} else {
				// outgoing, remove association
				RequestToll toll = associations.remove(event.getRemote());
				if (toll != null) {
					priorities.remove(toll);
				}
			}
		} else {
			// This is dependency calls, we should associate the call with the request to find lag time
			if (!event.isReply()) {
				String target = event.getRemote().getAddress();
				for (RequestToll toll : priorities.keySet()) {
					if (toll.exist(target)) {
						// request already associated, ignore
						continue;
					}
					toll.addTarget(target);
					DiscreteProbDensity pdf = nodeLag.get(target);
					if (pdf == null) {
						pdf = new DiscreteProbDensity();
						nodeLag.put(target, pdf);
					}
					pdf.add(event.getTimestamp() - toll.getRequestTime());
				}
			}
		}
	}
	
	public Map<String, DiscreteProbDensity> getNodeLag() {
		return nodeLag;
	}
}