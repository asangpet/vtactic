package ak.vtactic.model;

public class SocketInfo implements Comparable<SocketInfo> {
	private String address;
	private int port;
	
	public SocketInfo setAddress(String address) {
		this.address = address;
		return this;
	}
	
	public SocketInfo setPort(int port) {
		this.port = port;
		return this;
	}
	
	public String getAddress() {
		return address;
	}
	
	public int getPort() {
		return port;
	}
	
	@Override
	public String toString() {
		return "address:"+address+",port:"+port;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj != null && obj instanceof SocketInfo) {
			SocketInfo otherSocket = (SocketInfo)obj;
			return address.equals(otherSocket.address) && port == otherSocket.port;
		} else {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		return address.hashCode()*37+port*13;
	}

	@Override
	public int compareTo(SocketInfo o) {
		int addrCompare = address.compareTo(o.address);
		if (addrCompare != 0) {
			return addrCompare;
		} else {
			return port-o.port;
		}
	}
}
