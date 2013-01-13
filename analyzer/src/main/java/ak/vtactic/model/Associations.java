package ak.vtactic.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ak.vtactic.primitives.Expression;

/**
 * Associations maintains the relationship between an incoming request
 * identified by pairSocket/pairRequest and dependency calls occur between the request and its response.
 *
 */
public class Associations implements Comparable<Associations> {
	double requestTime;
	double replyTime;
	
	SocketInfo pairSocket;
	String pairRequest;
	Expression expression;
	
	// dependent queries (calls to dependent components)
	List<NodeEventInfo> queries = new LinkedList<NodeEventInfo>();
	
	// identified the set of dependencies for the request
	Set<String> targets = new HashSet<String>();
	
	Map<SocketInfo, NodeEventInfo> replies = new HashMap<>();
	
	// How likely that this association is correct
	double weight = 1.0;
	
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
			// For dependency calls, the remote port are the same across different requests
			// The local port, however, is different since A:ephermeral port calls B:serviceport
			// and can be used to distinguished requests.
			// 
			if (query.getLocal().equals(reply.getLocal())) {
				query.setPair(reply);
				return true;
			}
		}
		return false;
	}
	
	public Associations weight(double weight) {
		this.weight = weight;
		return this;
	}
	
	public double weight() {
		return weight;
	}
	
	public Associations replyTime(double time) {
		replyTime = time;
		return this;
	}
	
	public double getReplyTime() {
		return replyTime;
	}
	
	public Associations setExpression(Expression expression) {
		this.expression = expression;
		return this;
	}
	
	public Expression getExpression() {
		return expression;
	}
}