package ak.vtactic.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.springframework.stereotype.Service;

import ak.vtactic.model.Direction;
import ak.vtactic.model.EventInfo;
import ak.vtactic.model.NodeEventInfo;
import ak.vtactic.model.ResponseInfo;
import ak.vtactic.model.SocketInfo;

import com.mongodb.Mongo;

@Service
public class DataService {
	Mongo mongo;
	Jongo jongo;
	MongoCollection responseCollection;
	MongoCollection markerCollection;
	MongoCollection eventCollection;
	MongoCollection utilizationCollection;
	
	static final String modelCollection = "model";
	static final String coarrivalCollection = "coarrival";
	
	public DataService() throws Exception {
		/*
		mongo = new Mongo("127.0.0.1", 27017);
		jongo = new Jongo(mongo.getDB("collector_b"));
		*/
		//mongo = new Mongo("10.0.20.1", 27017);
		mongo = new Mongo("127.0.0.1", 27017);
		jongo = new Jongo(mongo.getDB("collector"));
		responseCollection = jongo.getCollection("responseTime");
		//eventCollection = jongo.getCollection("events");
		eventCollection = jongo.getCollection("capture");
		markerCollection = jongo.getCollection("markers");
		
		utilizationCollection = new Jongo(mongo.getDB("collectd")).getCollection("libvirt");
		//getModelCollection().ensureIndex("{name:1}");
	}
	
	public MongoCollection markers() {
		return markerCollection;
	}
	
	public MongoCollection response() {
		return responseCollection;
	}
	
	public MongoCollection events() {
		return eventCollection;
	}
	
	public MongoCollection utilizations() {
		return utilizationCollection;
	}
	
	public MongoCollection events(String collection) {
		MongoCollection jongoCollection = jongo.getCollection(collection);
		jongoCollection.ensureIndex("{timestamp:1}");
		return jongoCollection;
	}
	
	public MongoCollection getCollection(String collectionName) {
		return jongo.getCollection(collectionName);
	}
	
	public MongoCollection getModelCollection() {
		return jongo.getCollection(modelCollection);
	}
	
	public MongoCollection getCoarrivalCollection() {
		return jongo.getCollection(coarrivalCollection);
	}
	
	public Iterable<ResponseInfo> getResponses(String collectionName) {
		final MongoCollection collection = jongo.getCollection(collectionName);
		return collection.find().as(ResponseInfo.class);
	}
	
	public Iterable<ResponseInfo> getResponsesByTime(String collectionName) {
		final MongoCollection collection = jongo.getCollection(collectionName);
		return collection.find("{}").sort("{timestamp:1}").as(ResponseInfo.class);
	}
	
	public Iterable<NodeEventInfo> getMatchedNodeEvents(final String node, final int basePort, final double from, final double to) {
		return new Iterable<NodeEventInfo>() {
			// Use to identify existing request pair
			Map<String, NodeEventInfo> existingEvents = new HashMap<String, NodeEventInfo>();
			
			String getMapKey(NodeEventInfo event) {
				return new StringBuilder().append(event.getLocal().getAddress())
						.append("|")
						.append(event.getLocal().getPort())
						.append("|")
						.append(event.getRemote().getAddress())
						.append("|")
						.append(event.getRemote().getPort())
						.toString();
			}
			
			@Override
			public Iterator<NodeEventInfo> iterator() {
				final Iterator<EventInfo> iter = events()
						.find("{$and:[" +
								"{'server.address':#}," +
								"{timestamp:{$lt:#}}," +
								"{timestamp:{$gt:#}}]}", node, to, from)
						.sort("{timestamp:1}")
						.as(EventInfo.class).iterator();
				return new Iterator<NodeEventInfo>() {
					@Override
					public boolean hasNext() {
						return iter.hasNext();
					}
					
					@Override
					public NodeEventInfo next() {
						EventInfo sourceEvent = iter.next();
						NodeEventInfo nodeEvent = new NodeEventInfo()
							.setContent(sourceEvent.getRequest())
							.setTimestamp(sourceEvent.getTimestamp())
							.setHost(sourceEvent.getTracker())
							.setRemote(sourceEvent.getClient())
							.setLocal(sourceEvent.getServer())
							.setDirection(sourceEvent.getType());
						
						String key = getMapKey(nodeEvent);
						if ((nodeEvent.getDirection() == Direction.IN && nodeEvent.getLocal().getPort() != basePort) // return value from dependencies
							|| (nodeEvent.getDirection() == Direction.OUT && nodeEvent.getLocal().getPort() == basePort)) { // reply for client request
							if (existingEvents.containsKey(key)) {
								NodeEventInfo prev = existingEvents.remove(key); 
								nodeEvent.setPair(prev);
								prev.setPair(nodeEvent);
							}
						} else {
							existingEvents.put(key, nodeEvent);
						}
						return nodeEvent;
					}
					
					@Override
					public void remove() {
						next();
						return;
					}
				};
			}
		};
	}
	
	public Iterable<NodeEventInfo> getNodeEvents(final String node, final double from, final double to) {
		return new Iterable<NodeEventInfo>() {
			
			@Override
			public Iterator<NodeEventInfo> iterator() {
				final Iterator<EventInfo> iter = events()
						.find("{$and:[" +
								"{'server.address':#}," +
								"{timestamp:{$lt:#}}," +
								"{timestamp:{$gt:#}}]}", node, to, from)
						.sort("{timestamp:1}")
						.as(EventInfo.class).iterator();
				return new Iterator<NodeEventInfo>() {
					@Override
					public boolean hasNext() {
						return iter.hasNext();
					}
					
					@Override
					public NodeEventInfo next() {
						EventInfo sourceEvent = iter.next();
						NodeEventInfo nodeEvent = new NodeEventInfo()
							.setContent(sourceEvent.getRequest())
							.setTimestamp(sourceEvent.getTimestamp())
							.setHost(sourceEvent.getTracker())
							.setRemote(sourceEvent.getClient())
							.setLocal(sourceEvent.getServer())
							.setDirection(sourceEvent.getType());
						return nodeEvent;
					}
					
					@Override
					public void remove() {
						next();
						return;
					}
				};
			}
		};
	}
	
	public void queryOne() {
		MongoCollection responses = jongo.getCollection("response");
		ResponseInfo resp = new ResponseInfo();
		SocketInfo serverSocket = new SocketInfo();
		serverSocket.setAddress("1.1.1.1");
		serverSocket.setPort(80);
		resp.setServer(serverSocket);
		
		SocketInfo clientSocket = new SocketInfo();
		clientSocket.setAddress("2.2.2.2");
		clientSocket.setPort(90);
		resp.setClient(clientSocket);
		
		resp.setRequest("HelloWorld");
		resp.setResponse("Goodbye");
		responses.save(resp);
	}
}
