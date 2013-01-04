package ak.vcon.model;


public class NodeEventInfo {
	private double timestamp;
	private String content;
	private String host;
	private SocketInfo local;
	private SocketInfo remote;
	Direction direction;
	private NodeEventInfo pair;
	
	public NodeEventInfo() {
	}
	
	public NodeEventInfo setTimestamp(double timestamp) {
		this.timestamp = timestamp;
		return this;
	}
	
	public NodeEventInfo setDirection(Direction direction) {
		this.direction = direction;
		return this;
	}
	
	public NodeEventInfo setContent(String content) {
		this.content = content;
		return this;
	}
	
	public NodeEventInfo setRemote(SocketInfo remote) {
		this.remote = remote;
		return this;
	}
	
	public NodeEventInfo setLocal(SocketInfo local) {
		this.local = local;
		return this;
	}
	
	public NodeEventInfo setPair(NodeEventInfo pair) {
		this.pair = pair;
		return this;
	}
	
	public NodeEventInfo setHost(String host) {
		this.host = host;
		return this;
	}
	
	public NodeEventInfo getPair() {
		return pair;
	}
	
	public SocketInfo getRemote() {
		return remote;
	}
	
	public SocketInfo getLocal() {
		return local;
	}
	
	public double getTimestamp() {
		return timestamp;
	}
	
	public Direction getDirection() {
		return direction;
	}
	
	public String getContent() {
		return content;
	}
	
	public String getHost() {
		return host;
	}
	
	public Double getResponseTime() {
		if (pair == null) {
			return null;
		}
		return timestamp - pair.timestamp;
	}
	
	public boolean isReply() {
		return pair != null && pair.timestamp < timestamp;
	}
	
	public String getRequest() {
		if (pair == null) {
			return content;
		} else {
			return pair.content;
		}
	}
}