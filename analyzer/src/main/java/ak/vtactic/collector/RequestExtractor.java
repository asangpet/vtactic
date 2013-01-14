package ak.vtactic.collector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ak.vtactic.model.Associations;
import ak.vtactic.model.Direction;
import ak.vtactic.model.NodeEventInfo;
import ak.vtactic.model.SocketInfo;
import ak.vtactic.primitives.Composite;
import ak.vtactic.primitives.Concurrent;
import ak.vtactic.primitives.Distributed;
import ak.vtactic.primitives.Expression;
import ak.vtactic.primitives.Operand;
import ak.vtactic.util.GenericTreeBidiMap;
import ak.vtactic.util.OrderedBiMap;
import ak.vtactic.util.Pair;

public class RequestExtractor {
	private static final Logger logger = LoggerFactory.getLogger(RequestExtractor.class);
	
	OrderedBiMap<Associations, SocketInfo> priorities = new GenericTreeBidiMap<>();
	Map<String, Integer> termCounts = new HashMap<>();
	Map<String, Expression> termExpressions = new HashMap<>();
	int sum = 0;
	
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
	//LinkedList<Associations> prevTolls = new LinkedList<Associations>();
	public void emit(Associations toll) {
		requestCount++;
		/*
		prevTolls.add(toll);
		if (prevTolls.size() > 10) {
			prevTolls.removeFirst();
		}
		*/
		
		if (toll.queries().isEmpty()) {
			idCount++;
			if ((idCount & 0x3FF) == 0x200) {
				logger.warn("Independent request {} {} {} ",
						new Object[] { toll.getRequestTime(), toll.getReplyTime()-toll.getRequestTime(),idCount });
				
				/*
				Iterator<Associations> prevToll = prevTolls.iterator();
				while (prevToll.hasNext()) {
					Associations prev = prevToll.next();
					logger.info("Previous toll {} {} {}", new Object[] { String.format("%.3f",prev.getRequestTime()), String.format("%.3f",prev.getReplyTime()), prev.getExpression()});
					prevToll.remove();
				}*/
			}
			return;
		}
		Expression expression = deriveExpression(toll.queries());
		toll.setExpression(expression);
		StringBuilder exp = new StringBuilder();
		expression.print(exp);
		String term = exp.toString();
		if (!termCounts.containsKey(term)) {
			termCounts.put(term, 1);
			termExpressions.put(term, expression);
		} else {
			//long count = termCounts.get(term)+1;
			termCounts.put(term, termCounts.get(term)+toll.weight());
		}
		sum += toll.weight();
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
	
	int orphanCount = 0;
	int reassignCount = 0;
	int requestCount = 0;
	public void collect(NodeEventInfo event) {
		if (event.getLocal().getPort() == basePort) {
			// This is client request
			if (event.getDirection() == Direction.IN) {
				// incoming: create association
				Associations toll = new Associations(event);
				priorities.put(toll, event.getRemote());
			} else {								
				// outgoing, remove association
				Associations toll = priorities.removeValue(event.getRemote());
				if (toll != null) {
					// Sanity check, move pairs with response outside boundary to next term
					Collection<NodeEventInfo> futureEvents = toll.prune(event.getTimestamp());
					if (futureEvents.size() > 0) {
						reassignCount += futureEvents.size();
						if ((reassignCount & 0x3FF) == 0x200) {
							logger.info("Reassigning {} events", reassignCount);
						}
					}
					if (!priorities.isEmpty()) {
						Associations next = priorities.firstKey();
						for (NodeEventInfo future : futureEvents) {
							if (next.getRequestTime() < future.getTimestamp()) {
								next.addQuery(future.getRemote(), future);
							} else {
								logger.warn("Dropped event {}, can't find open query", future);
							}
						}
					} else if (!futureEvents.isEmpty()) {
						logger.warn("Dropped {} events", futureEvents.size());
					}
					toll.replyTime(event.getTimestamp());
					emit(toll);
				} else {
					// cannot find matching pair
					logger.warn("Cannot find matching request for reply {}",event);
				}
			}
		} else {
			// This is dependency calls, we should associate the call with the request based on lag time
			if (event.getDirection() == Direction.OUT) {
				SocketInfo target = event.getRemote();
				
				if (priorities.size() == 0) {
					// no open query, ignore event
					logger.warn("No open association, cannot assign event {}",event);					
				} else if (priorities.size() == 1) {
					// Confidently associate if there is no concurrency
					priorities.firstKey().addQuery(target, event);
				} else {
					// concurrent requests, need to intelligently pick the associations
					assignEarliestRequestWithoutTargetStrategy(target, event);
				}
			} else {
				// This is returned dependency call, associate it with existing queries
				for (Associations toll : priorities.keySet()) {
					if (toll.associate(event)) {
						return;
					}
				}
				orphanCount++;
				logger.warn("Orphaned event, cannot match association {} {}",event, orphanCount);
			}
		}
	}
	
	int maxConcurrent = 0;
	private void assignEarliestRequestWithoutTargetStrategy(SocketInfo target,
			NodeEventInfo event) {
		Associations lastToll = null;
		if (priorities.size() > maxConcurrent) {
			maxConcurrent = priorities.size();
		}
		for (Associations toll : priorities.keySet()) {
			lastToll = toll;
			if (toll.exist(target)) {
				// request already associated, use another parent
				continue;
			}
			toll.addQuery(target, event);
			return;
		}
		// assign to latest node, if cannot find a match
		lastToll.addQuery(target, event);
	}
	
	public int getMaxConcurrent() {
		return maxConcurrent;
	}
	
	/*
	private void assignRequestWtihRandomStrategy(SocketInfo target, NodeEventInfo event) {
		int index = (int)Math.floor(Math.random()*priorities.size());
		double damper = 1;
		double weight = 1.0/(damper*priorities.size());
		MapIter<Associations, SocketInfo> iter = priorities.mapIterator();
		int itIdx = -1;
		Associations toll = null;
		while (iter.hasNext() && itIdx < index) {
			itIdx++;
			toll = iter.next();
		}
		toll.addQuery(target, event);
		toll.weight(toll.weight()*weight);
	}
	*/

	public Expression getExpression() {
		Distributed expression = new Distributed();
		for (Map.Entry<String, Integer> term : termCounts.entrySet()) {
			logger.info("{} - {}", term.getKey(), term.getValue());
			expression.addTerm(termExpressions.get(term.getKey()), 1.0*term.getValue().doubleValue()/sum);
		}
		return expression;
	}
	
	public int getRequestCount() {
		return requestCount;
	}
}