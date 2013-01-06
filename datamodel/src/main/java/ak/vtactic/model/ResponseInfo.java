package ak.vtactic.model;

import java.util.Comparator;

import org.bson.types.ObjectId;
import org.codehaus.jackson.annotate.JsonProperty;

public class ResponseInfo {
	@JsonProperty("_id")
	private ObjectId id;
	
	private long timestamp;
	private SocketInfo server, client;
	private double requestTime, responseTime;
	private String request,response;
	private String protocol;
	
	public String getProtocol() {
		return protocol;
	}		
	public ResponseInfo setProtocol(String protocol) {
		this.protocol = protocol;
		return this;
	}
	
	public SocketInfo getServer() {
		return server;
	}
	public ResponseInfo setServer(SocketInfo server) {
		this.server = server;
		return this;
	}
	
	public String getRequest() {
		return request;
	}
	public ResponseInfo setRequest(String request) {
		this.request = request;
		return this;
	}
	
	public String getResponse() {
		return response;
	}
	public ResponseInfo setResponse(String response) {
		this.response = response;
		return this;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	public ResponseInfo setTimestamp(long timestamp) {
		this.timestamp = timestamp;
		return this;
	}
	
	public SocketInfo getClient() {
		return client;
	}
	public ResponseInfo setClient(SocketInfo client) {
		this.client = client;
		return this;
	}
	
	public double getResponseTime() {
		return responseTime;
	}
	public double getRequestTime() {
		return requestTime;
	}
	public ResponseInfo setRequestTime(double requestTime) {
		this.requestTime = requestTime;
		return this;
	}
	public ResponseInfo setResponseTime(double responseTime) {
		this.responseTime = responseTime;
		return this;
	}
	
	boolean contains(double time) {
		return requestTime <= time && requestTime+responseTime >= time;
	}
	public boolean isOverlap(ResponseInfo r) {
		return r.contains(requestTime) || r.contains(requestTime+responseTime) || contains(r.requestTime);
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("id:").append(id);
		builder.append(" timestamp:").append(timestamp);
		builder.append(" server:").append(server);
		builder.append(" client:").append(client);
		builder.append(" proto:").append(protocol);
		builder.append(" time - req:").append(requestTime);
		builder.append(" res:").append(responseTime);
		builder.append(" req:").append(request);
		builder.append(" req:").append(response);
		return builder.toString();
	}

	public static Comparator<ResponseInfo> getDeadlineComparator() {
		return new Comparator<ResponseInfo>() {
			@Override
			public int compare(ResponseInfo o1, ResponseInfo o2) {
				double diff = (o1.requestTime+o1.responseTime - (o2.requestTime+o2.responseTime));
				if (diff < 0) return -1;
				else if (diff > 0) return 1;
				else return 0;
			}
		};
	}
	
	public static Comparator<ResponseInfo> getRequestTimeComparator() {
		return new Comparator<ResponseInfo>() {
			@Override
			public int compare(ResponseInfo o1, ResponseInfo o2) {
				double diff = (o1.requestTime - o2.requestTime);
				if (diff < 0) return -1;
				else if (diff > 0) return 1;
				else return 0;
			}
		};
	}
}
