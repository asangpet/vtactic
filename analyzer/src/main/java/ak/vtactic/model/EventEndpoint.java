package ak.vtactic.model;

public class EventEndpoint {
	SocketInfo address;
	String request;
	Double time;
	
	public EventEndpoint address(SocketInfo socket) {
		this.address = socket;
		return this;
	}
	
	public EventEndpoint request(String request) {
		this.request = request;
		return this;
	}
	
	public EventEndpoint time(Double time) {
		this.time = time;
		return this;
	}
	
	public Double getTime() {
		return time;
	}
	
	public SocketInfo getAddress() {
		return address;
	}
	
	public String getRequest() {
		return request;
	}
	
	public static EventEndpoint fromEventClient(EventInfo event) {
		return new EventEndpoint().address(event.getClient()).request(event.getRequest()).time(event.getTimestamp());
	}
	
	public static EventEndpoint fromEventServer(EventInfo event) {
		return new EventEndpoint().address(event.getServer()).request(event.getRequest()).time(event.getTimestamp());
	}
	
	@Override
	public int hashCode() {
		return 37*address.getAddress().hashCode()+17*address.getPort();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof EventEndpoint)) {
			return false;
		}
		EventEndpoint compare = (EventEndpoint)obj;
		return address.equals(compare.address);
	}
}
