package ak.vtactic.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.vertx.java.core.http.HttpServerRequest;

import ak.vtactic.math.DiscreteProbDensity;

public class MappedCounter {
	ServiceEndpoint focus;

	Map<ServiceEndpoint, DiscreteProbDensity> distances = new HashMap<>();
	Set<ServiceEndpoint> clients = new HashSet<>();
	Map<EventEndpoint, Set<ServiceEndpoint>> trackedEvents = new HashMap<>(); 

	public MappedCounter(String request, SocketInfo address) {
		focus = new ServiceEndpoint().request(request).address(address);
	}
	
	public DiscreteProbDensity getEndpointPdf(ServiceEndpoint serviceEndpoint) {
		DiscreteProbDensity pdf = distances.get(serviceEndpoint);
		if (pdf == null) {
			pdf = new DiscreteProbDensity();
			distances.put(serviceEndpoint, pdf);
		}
		return pdf;
	}
	
	public void count(EventInfo event) {
		final ServiceEndpoint serverEndpoint = ServiceEndpoint.fromServerEventInfo(event);
		final ServiceEndpoint clientEndpoint = ServiceEndpoint.fromClientEventInfo(event);		
		final EventEndpoint eventClient = EventEndpoint.fromEventClient(event);
		
		if (focus.equals(serverEndpoint)) {
			// This is client event
			if (event.getType() == Direction.IN) {
				// incoming client request, create a mapped end point
				trackedEvents.remove(eventClient);
				trackedEvents.put(eventClient, new HashSet<ServiceEndpoint>());
				clients.add(clientEndpoint);
			}
		}

		if (event.getType() == Direction.OUT 
				&& serverEndpoint.getAddress().equals(focus.getAddress())
				&& !clients.contains(clientEndpoint)) {
			// outgoing request				
			if (trackedEvents.containsKey(eventClient)) {
				// outgoing request to client (reply), remove associated tracking
				trackedEvents.remove(eventClient);
			} else {
				for (Map.Entry<EventEndpoint, Set<ServiceEndpoint>> entry : trackedEvents.entrySet()) {
					Set<ServiceEndpoint> services = entry.getValue();
					if (services.contains(clientEndpoint)) {
						// do nothing, duplicate calls
					} else {
						services.add(clientEndpoint);
						// record data
						getEndpointPdf(clientEndpoint).add(event.getTimestamp() - entry.getKey().getTime());
					}
				}
			}
		}
	}
	
	private String sanitize(String var) {
		var = var.replaceAll("[\\:\\.\\$]", "_");
		StringBuilder sb = new StringBuilder();
		for (String s : var.split(" ")) {
			if (s.isEmpty()) continue;
			sb.append(s);
			sb.append("_");
		}
		sb.deleteCharAt(sb.length()-1);
		return sb.toString();
	}

	
	static final String[] colors = new String[] { "b", "r", "g", "m", "k", "c" };
	public void printDistance(HttpServerRequest req, String varname) {
		req.response.write("figure; hold on;\n");
		int i = 0;
		StringBuilder legends = new StringBuilder();
		for (Map.Entry<ServiceEndpoint, DiscreteProbDensity> entry : distances.entrySet()) {
			String key = entry.getKey().getAddress() + "_" + entry.getKey().getRequest();
			String name = sanitize(varname+"_"+key.substring(key.lastIndexOf("/")+1));
			if (legends.length() > 0) {
				legends.append(",");
			}
			legends.append("'").append(name).append("'");
			req.response.write(name);
			req.response.write("=");
			req.response.write(entry.getValue().printBuffer().toString());
			req.response.write(";\n");
			req.response.write("plot("+name+",'"+colors[i]+"');");
			i++; if (i == colors.length) { i = 0; }
		}
		legends.insert(0, "legend(").append(");");
		req.response.write(legends.toString());
		req.response.write("hold off;\n\n");
	}
	
}
