package ak.vtactic.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Associations implements Comparable<Associations> {
	double requestTime;
	
	SocketInfo pairSocket;
	String pairRequest;
	
	List<NodeEventInfo> queries = new LinkedList<NodeEventInfo>();
	Set<String> targets = new HashSet<String>();
	
	Map<SocketInfo, NodeEventInfo> replies = new HashMap<>();
	
	public Associations(NodeEventInfo ev) {
		this.requestTime = ev.getTimestamp();
		this.pairRequest = ev.getContent();
		this.pairSocket = ev.getRemote();
	}
	
	public String getPairRequest() {
		return pairRequest;
	}
	
	public SocketInfo getPairSocket() {
		return pairSocket;
	}

	@Override
	public int compareTo(Associations o) {
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
	
	public void addQuery(SocketInfo target, NodeEventInfo event) {
		queries.add(event);
		targets.add(target.getAddress());
	}
	
	public boolean exist(SocketInfo target) {
		return targets.contains(target.getAddress());
	}
	
	public void addReply(NodeEventInfo event) {
		replies.put(event.getRemote(), event);
	}
	
	public NodeEventInfo getReplyFor(SocketInfo target) {
		return replies.get(target);
	}
	
	public Collection<NodeEventInfo> queries() {
		return queries;
	}
	
	/**
	 * @return Collection of events whose reply exceed the given time
	 */
	public Collection<NodeEventInfo> prune(double limit) {
		List<NodeEventInfo> futureEvents = new LinkedList<NodeEventInfo>();
		Iterator<NodeEventInfo> iter = queries.iterator();
		while (iter.hasNext()) {
			NodeEventInfo event = iter.next();
			NodeEventInfo reply = event.getPair();
			if (reply == null || reply.getTimestamp() > limit) {
				futureEvents.add(event);
				iter.remove();
			}
		}
		return futureEvents;
	}
	
	public boolean associate(NodeEventInfo reply) {
		for (NodeEventInfo query : queries) {
			if (query.getTimestamp() > reply.getTimestamp()) {
				return false;
			}
			if (query.getPair() != null) {
				continue;
			}
			if (query.getRemote().equals(reply.getRemote())) {
				query.setPair(reply);
				return true;
			}
		}
		return false;
	}
}