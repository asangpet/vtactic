package ak.vtactic.model;

import java.util.HashSet;
import java.util.Set;

import ak.vcon.model.EventInfo;
import ak.vcon.model.SocketInfo;

public class RequestToll {
	double requestTime;
	
	SocketInfo pairSocket;
	String pairRequest;
	
	Set<String> targets = new HashSet<String>();
	
	RequestToll(double time) {
		this.requestTime = time;
	}
	
	RequestToll(EventInfo event) {
		this.pairSocket = event.getClient();
		this.pairRequest = event.getRequest();
	}
	
	public boolean verifyResponsePair(EventInfo event) {
		return event.getClient().equals(pairSocket);
	}
	
	public double getRequestTime() {
		return requestTime;
	}
	
	public boolean exist(String target) {
		return targets.contains(target);
	}
	
	public void addTarget(String target) {
		targets.add(target);
	}
}

