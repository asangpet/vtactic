package ak.vtactic.collector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ak.vtactic.model.Associations;
import ak.vtactic.model.Direction;
import ak.vtactic.model.NodeEventInfo;
import ak.vtactic.model.Pair;
import ak.vtactic.model.SocketInfo;
import ak.vtactic.primitives.Composite;
import ak.vtactic.primitives.Concurrent;
import ak.vtactic.primitives.Distributed;
import ak.vtactic.primitives.Expression;
import ak.vtactic.primitives.Operand;

public class RequestExtractor {
	private static final Logger logger = LoggerFactory.getLogger(RequestExtractor.class);
	
	Map<SocketInfo, Associations> associations = new HashMap<>();
	TreeMap<Associations, SocketInfo> priorities = new TreeMap<>();
	Map<String, Long> termCounts = new HashMap<>();
	Map<String, Expression> termExpressions = new HashMap<>();
	long sum = 0;
	
	final int basePort;
	
	public RequestExtractor(int basePort) {
		this.basePort = basePort;
	}

	public Pair<List<NodeEventInfo>, List<NodeEventInfo>> split(NodeEventInfo splitter, Iterable<NodeEventInfo> events) {
		double replyTime = splitter.getPair().getTimestamp();
		Pair<List<NodeEventInfo>, List<NodeEventInfo>> pair = new Pair<List<NodeEventInfo>, List<NodeEventInfo>>(new ArrayList<NodeEventInfo>(), new ArrayList<NodeEventInfo>());
		for (NodeEventInfo event : events) {
			if (event.getRemote().getAddress().equals(splitter.getRemote().getAddress())) {
				// ignore splitter
				continue;
			}
			if (event.getTimestamp() > replyTime) {
				pair.first.add(event);
			} else {
				pair.second.add(event);
			}
		}
		return pair;
	}
	
	public NodeEventInfo getFirstQuery(Collection<NodeEventInfo> queries) {
		Iterator<NodeEventInfo> iter = queries.iterator();
		if (!iter.hasNext()) {
			return null;
		}
		NodeEventInfo minQuery = iter.next();
		while (iter.hasNext()) {
			NodeEventInfo info = iter.next();
			// optional, grab the non-null pair
			if (info.getTimestamp() < minQuery.getTimestamp()) {
				minQuery = info;
			}
		}
		return minQuery;
	}
	
	int idCount = 0;
	public void emit(Associations toll) {
		if (toll.queries().isEmpty()) {
			idCount++;
			if (idCount % 1000 == 0) {
				logger.warn("Independent request {} {}",toll.getRequestTime(),idCount);
			}
			return;
		}
		Expression expression = deriveExpression(toll.queries());
		StringBuilder exp = new StringBuilder();
		expression.print(exp);
		String term = exp.toString();
		if (!termCounts.containsKey(term)) {
			termCounts.put(term, Long.valueOf(1));
			termExpressions.put(term, expression);
		} else {
			long count = termCounts.get(term)+1;
			termCounts.put(term, count);
		}
		sum++;
	}
	
	private Expression deriveExpression(Collection<NodeEventInfo> events) {
		NodeEventInfo first = getFirstQuery(events);
		if (first == null) {
			return null;
		}
		Operand thisOp = new Operand(first.getRemote().getAddress());
		if (events.size() == 1) {
			return thisOp;
		}
		Pair<List<NodeEventInfo>, List<NodeEventInfo>> result = split(first, events);
		Expression composite = deriveExpression(result.first);
		Expression concurrents = deriveExpression(result.second);
		Expression exp = null;
		if (concurrents != null) {
			exp = new Concurrent().setLeft(thisOp).setRight(concurrents);
		} else {
			exp = thisOp;
		}
		if (composite != null) {
			exp = new Composite().setLeft(exp).setRight(composite);
		} 
		return exp;
	}
	
	public void collect(NodeEventInfo event) {
		if (event.getLocal().getPort() == basePort) {
			// This is client request
			if (event.getDirection() == Direction.IN) {
				// incoming: create association
				Associations toll = new Associations(event);
				associations.put(event.getRemote(), toll);
				priorities.put(toll, event.getRemote());
			} else {
				// outgoing, remove association
				Associations toll = associations.remove(event.getRemote());
				if (toll != null) {
					// Remove from map
					priorities.remove(toll);

					// Sanity check, move pairs with response outside boundary to next term
					Collection<NodeEventInfo> futureEvents = toll.prune(event.getTimestamp());
					if (!priorities.isEmpty()) {
						Associations next = priorities.firstKey();
						for (NodeEventInfo future : futureEvents) {
							if (next.getRequestTime() < future.getTimestamp()) {
								next.addQuery(future.getRemote(), future);
							} else {
								logger.warn("Dropped event {}", future);
							}
						}
					} else if (!futureEvents.isEmpty()) {
						logger.warn("Dropped {} events", futureEvents.size());
					}
					
					emit(toll);
				}
			}
		} else {
			// This is dependency calls, we should associate the call with the request to find lag time
			if (event.getDirection() == Direction.OUT) {
				SocketInfo target = event.getRemote();
				//logger.info("Prior info {}",priorities.size());
				//ArrayList<Associations> reverse = new ArrayList<Associations>();
				//reverse.addAll(priorities.keySet());
				
				for (Associations toll : priorities.keySet()) {
				//for (int i=reverse.size()-1;i>=0;i--) {
					//Associations toll = reverse.get(i);
					if (toll.exist(target)) {
						// request already associated, use another parent
						continue;
					}
					toll.addQuery(target, event);
					return;
				}
				logger.warn("Orphaned event {}",event);
			} else {
				// This is returned dependency call, associate it with existing queries
				for (Associations toll : priorities.keySet()) {
					if (toll.associate(event)) {
						return;
					}
				}
				logger.warn("Orphaned event, cannot match association {}",event);
			}
		}
	}
	
	public Expression getExpression() {
		Distributed expression = new Distributed();
		for (Map.Entry<String, Long> term : termCounts.entrySet()) {
			logger.info("{} - {}", term.getKey(), term.getValue());
			expression.addTerm(termExpressions.get(term.getKey()), 1.0*term.getValue().doubleValue()/sum);
		}
		return expression;
	}
}