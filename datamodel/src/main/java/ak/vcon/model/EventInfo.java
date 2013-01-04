package ak.vcon.model;

import org.bson.types.ObjectId;
import org.codehaus.jackson.annotate.JsonProperty;

public class EventInfo {
	@JsonProperty("_id")
	private ObjectId id;
	
	private double timestamp;
	private String request;
	private String tracker;
	private SocketInfo server, client;
	Direction type;
	
	public EventInfo() {
	}
	
	public EventInfo setId(ObjectId id) {
		this.id = id;
		return this;
	}
	
	public EventInfo setTimestamp(double timestamp) {
		this.timestamp = timestamp;
		return this;
	}
	
	public EventInfo setType(Direction type) {
		this.type = type;
		return this;
	}
	
	public EventInfo setRequest(String request) {
		this.request = request;
		return this;
	}
	
	public EventInfo setTracker(String tracker) {
		this.tracker = tracker;
		return this;
	}
	
	public void setServer(SocketInfo server) {
		this.server = server;
	}
	
	public void setClient(SocketInfo client) {
		this.client = client;
	}
	
	public SocketInfo getServer() {
		return server;
	}
	
	public SocketInfo getClient() {
		return client;
	}
	
	public double getTimestamp() {
		return timestamp;
	}
	
	public Direction getType() {
		return type;
	}
	
	public String getRequest() {
		return request;
	}
	
	public String getTracker() {
		return tracker;
	}
}
