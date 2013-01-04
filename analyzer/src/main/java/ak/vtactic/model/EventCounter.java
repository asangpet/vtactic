package ak.vtactic.model;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.http.HttpServerRequest;

import ak.vcon.model.Direction;
import ak.vcon.model.EventInfo;
import ak.vtactic.math.DiscreteProbDensity;

public class EventCounter {
	static class Resolution {
		public int slots = 100;
		public double min = 0;
		public double max = 100;
		
		public Resolution(int slots, double min, double max) {
			this.slots = slots;
			this.min = min;
			this.max = max;
		}
	}
	//static final Resolution resolution = new Resolution(3000, 0, 3000); //multicomp/dist
	static final Resolution resolution = new Resolution(300, 0, 30000);
	static final int primaryQueueLimit = 10000;
	
	Logger log = LoggerFactory.getLogger(EventCounter.class);
	
	String requestTarget;
	Map<String, DiscreteProbDensity> distancePdfs = new HashMap<String, DiscreteProbDensity>();
	Map<String, DiscreteProbDensity> responsePdfs = new HashMap<String, DiscreteProbDensity>();
	Map<String, Queue<Double>> lastRequests = new HashMap<String, Queue<Double>>();
	Map<String, Long> discards = new HashMap<String, Long>();
	Map<String, Long> counters = new HashMap<String, Long>();
	LinkedList<RequestToll> primaries = new LinkedList<RequestToll>();
	Queue<EventInfo> queue;
	long expiration = 0;
	
	public EventCounter(String target) {
		requestTarget = target;
		queue = new PriorityQueue<EventInfo>(10, new Comparator<EventInfo>() {
				@Override
				public int compare(EventInfo o1, EventInfo o2) {
					if (o1.getTimestamp() < o2.getTimestamp()) {
						return -1;
					} else if (o1.getTimestamp() > o2.getTimestamp()) {
						return 1;
					} else {
						if (target(o1).toString().equals(requestTarget)) {
							return -1;
						} else {
							return target(o1).compareTo(target(o2));
						}
					}
				}
			});

	}
	
	public EventCounter setExpiration(long expiration) {
		this.expiration = expiration;
		return this;
	}
	
	private void addPoint(String requestType, Map<String, DiscreteProbDensity> pdfMap, double value) {
		DiscreteProbDensity pdf = pdfMap.get(requestType);
		if (pdf == null) {
			// Resolution configuration
			pdf = new DiscreteProbDensity();
			pdfMap.put(requestType, pdf);
		}
		pdf.add(value);
	}
	
	private void addQueryPoint(String requestType, double time) {
		Queue<Double> requests = lastRequests.get(requestType);
		if (requests == null) {
			requests = new LinkedList<Double>();
		}
		// always reset outgoing request to remove queuing effect
		requests.clear();
		
		requests.offer(time);
		lastRequests.put(requestType, requests);
	}
	
	private void discards(String type) {
		Long counter = discards.get(type);
		if (counter == null) {
			counter = 1L;
			discards.put(type, counter);
		} else {
			counter = counter + 1;
			discards.put(type, counter+1);
		}
		log.warn("Discarded {} {}", type, counter);
	}
	
	private void count(String type) {
		Long counter = counters.get(type);
		if (counter == null) {
			counter = 1L;
			counters.put(type, counter);
		} else {
			counter = counter + 1;
			counters.put(type, counter+1);
		}
		//log.warn("Count {} {} {}", new Object[] { type, counter, discards.get(type) });
	}
	
	public void countEvent(EventInfo event) {
		queue.offer(event);
		EventInfo existing = queue.peek();
		while (existing.getTimestamp() < event.getTimestamp()) {
			countEventInternal(queue.poll());
			existing = queue.peek();
			if (existing == null) {
				break;
			}
		}
	}
	
	private boolean monitorTarget(EventInfo event) {
		// normally, we could use the fixed request type
		// requestTarget.equals(target(event))
		if (event.getClient().getAddress().equals("10.0.20.1")) {
			if (event.getRequest().contains("Update") && event.getRequest().contains("ycsb")) {
				return true;
			}
		}
		return false;
	}
	
	private String target(EventInfo event) {
		// for multicomp/dist
		//return event.getRequest();
		// for mongo
		return "R_"+ (event.getClient().getAddress().replaceAll("\\.", "_")) + "_"+event.getRequest();
	}
	
	private double time(EventInfo event) {
		return event.getTimestamp();
		// for multicomp/dist
		//return event.getRequest();
		// for mongo
	}
	
	private void countEventInternal(EventInfo event) {
		if (event.getType() == Direction.OUT) {
			if (monitorTarget(event)) {
				// primary OUT = response
				
				primaries.poll();
				Queue<Double> requests = lastRequests.get(target(event));
				if (requests != null) {
					Double out = requests.poll();
					if (out != null) {
						addPoint(target(event), responsePdfs, time(event) - out);
					}
				}				
			} else {
				// other OUT = request to dependents
				addQueryPoint(target(event), time(event));
				// collect distance between dependency and primary event
				Iterator<RequestToll> requestIterator = primaries.iterator();
				boolean matched = false;
				while (requestIterator.hasNext()) {
					RequestToll primary = requestIterator.next();
					if (primary.exist(target(event))) {
						continue;
					}
					primary.addTarget(target(event));
					addPoint(target(event), distancePdfs, time(event) - primary.getRequestTime());
					matched = true;
					break;
				}
				count(target(event));
				if (!matched) {
					discards(target(event));
				}
			}
		} else {
			if ((monitorTarget(event))) {
				// primary IN = request operation
				addQueryPoint(target(event), time(event));
				primaries.offer(new RequestToll(event));
				if (primaries.size() > primaryQueueLimit) {
					primaries.remove();
				}
				/*
				if (expiration > 0 && primaries.peek().getRequestTime() < event.getTimestamp() - expiration) {
					// auto expiration
					primaries.poll();
				}
				*/
				//log.info("Queue {}", primaries.size());
			} else {
				// For incoming (response)
				// match with existing requests
				Queue<Double> requests = lastRequests.get(target(event));
				if (requests != null) {
					Double out = requests.poll();
					if (out != null) {
						addPoint(target(event), responsePdfs, time(event) - out);
					}
				}
			}
		}
	}
	
	public Map<String, DiscreteProbDensity> getDistancePdfs() {
		return distancePdfs;
	}
	public Map<String, DiscreteProbDensity> getResponsePdfs() {
		return responsePdfs;
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
		for (Map.Entry<String, DiscreteProbDensity> entry : getDistancePdfs().entrySet()) {
			String name = sanitize(varname+"_"+entry.getKey().substring(entry.getKey().lastIndexOf("/")+1));
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
	
	public void printResponse(HttpServerRequest req, String varname) {
		req.response.write("figure; hold on;\n");
		int i = 0;
		StringBuilder legends = new StringBuilder();
		for (Map.Entry<String, DiscreteProbDensity> entry : getResponsePdfs().entrySet()) {
			String name = sanitize(varname+"_resp_"+entry.getKey().substring(entry.getKey().lastIndexOf("/")+1));
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

