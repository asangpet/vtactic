package ak.vtactic.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class RequestToll implements Comparable<RequestToll> {
	double requestTime;
	
	SocketInfo pairSocket;
	String pairRequest;
	
	Map<String, NodeEventInfo> queries = new HashMap<String, NodeEventInfo>();
	Map<String, NodeEventInfo> replies = new HashMap<String, NodeEventInfo>();
	
	public RequestToll(NodeEventInfo ev) {
		this.requestTime = ev.getTimestamp();
		this.pairRequest = ev.getContent();
		this.pairSocket = ev.getRemote();
	}
	
	RequestToll(double time) {
		this.requestTime = time;
	}
	
	RequestToll(EventInfo event) {
		this.pairSocket = event.getClient();
		this.pairRequest = event.getRequest();
	}
	
	@Override
	public int compareTo(RequestToll o) {
		if (requestTime < o.requestTime) {
			return -1;
		} else if (requestTime > o.requestTime) {
			return 1;
		}
		int result = pairSocket.getAddress().compareTo(o.pairSocket.getAddress());
		if (result == 0) {
			return pairSocket.getPort() - o.pairSocket.getPort();
		} else {
			return result;
		}
	}
	
	public boolean verifyResponsePair(EventInfo event) {
		return event.getClient().equals(pairSocket);
	}
	
	public double getRequestTime() {
		return requestTime;
	}
	
	public boolean exist(String target) {
		return queries.containsKey(target);
	}
	
	public boolean replyExist(String target) {
		return replies.containsKey(target);
	}
	
	@Deprecated
	public void addTarget(String target) {
		queries.put(target, new NodeEventInfo());
	}
	
	public void addQuery(String target, NodeEventInfo event) {
		queries.put(target, event);
	}
	
	public void addReply(String target, NodeEventInfo event) {
		replies.put(target, event);
	}
	
	public NodeEventInfo getReplyFor(String target) {
		return replies.get(target);
	}
	
	public Collection<NodeEventInfo> queries() {
		return queries.values();
	}
}