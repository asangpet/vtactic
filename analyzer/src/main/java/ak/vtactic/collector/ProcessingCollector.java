package ak.vtactic.collector;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import ak.vtactic.math.DiscreteProbDensity;
import ak.vtactic.model.NodeEventInfo;

public class ProcessingCollector {
	Map<String, Queue<Double>> nodeResponse = new HashMap<>();
	Map<String, DiscreteProbDensity> processings = new HashMap<>();

	String base;
	
	public ProcessingCollector(String remoteBase) {
		base = remoteBase;
		processings.put(base, new DiscreteProbDensity());
	}
	
	public void collect(NodeEventInfo event) {
		if (event.isReply()) {
			String remoteNode = event.getRemote().getAddress();
			if (!remoteNode.equals(base)) {
				Queue<Double> processQueue = nodeResponse.get(remoteNode);
				if (processQueue == null) {
					processQueue = new LinkedList<Double>();
					nodeResponse.put(remoteNode, processQueue);
				}
				processQueue.add(event.getResponseTime());
			} else {
				double existingProc = 0;
				for (Map.Entry<String, Queue<Double>> entry : nodeResponse.entrySet()) {
					Double value = entry.getValue().poll();
					if (value != null) {
						existingProc += value;
					}
				}
				if (event.getResponseTime() > existingProc) {
					processings.get(base).add(event.getResponseTime()-existingProc);
				}
			}
		}
	}
	
	public Map<String, DiscreteProbDensity> getNodeProcessing() {
		return processings;
	}
}
