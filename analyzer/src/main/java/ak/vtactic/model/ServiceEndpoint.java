package ak.vtactic.model;

import ak.vcon.model.EventInfo;
import ak.vcon.model.SocketInfo;

public class ServiceEndpoint {
	String address;
	String request;
	
	public ServiceEndpoint address(SocketInfo socket) {
		this.address = socket.getAddress();
		return this;
	}
	
	public ServiceEndpoint request(String request) {
		this.request = request;
		return this;
	}
	
	public String getAddress() {
		return address;
	}
	
	public String getRequest() {
		return request;
	}
	
	public static ServiceEndpoint fromClientEventInfo(EventInfo info) {
		return new ServiceEndpoint().request(info.getRequest()).address(info.getClient());
	}
	
	public static ServiceEndpoint fromServerEventInfo(EventInfo info) {
		return new ServiceEndpoint().request(info.getRequest()).address(info.getServer());
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ServiceEndpoint)) {
			return false;
		}
		ServiceEndpoint compare = (ServiceEndpoint)obj;
		return address.equals(compare.address) && request.equals(compare.request);
	}
	
	@Override
	public int hashCode() {
		return 37*address.hashCode() + request.hashCode();
	}
	
	@Override
	public String toString() {
		return address.toString()+"/"+request.toString();
	}
}
