package ak.vtactic.analyzer;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;

import ak.vtactic.model.Direction;
import ak.vtactic.model.EventCounter;
import ak.vtactic.model.EventInfo;
import ak.vtactic.model.ResponseCounter;
import ak.vtactic.model.ResponseInfo;
import ak.vtactic.service.DataService;

public class AnalyzerSimpleVert {
	private static final Logger log = LoggerFactory.getLogger(AnalyzerSimpleVert.class);
	
	Vertx vertx;
	EventBus eventBus;
	URI id;
	ObjectMapper mapper;
	DataService dataService;
	int httpPort;
	
	boolean saveInternal = false;
	String startTime = "1355620946302"; //separate-comp
	//String startTime = "1355296757695"; //--dist
	//String startTime = "1355603001518"; //--multi-comp
	
	long time() {
		return System.currentTimeMillis();
	}
	
	String serialize(Object object) {
		try {
			return mapper.writeValueAsString(object);
		} catch (JsonGenerationException | JsonMappingException e) {
			log.error("Cannot serialize object {}",object);
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public AnalyzerSimpleVert(final URI id, final int port, final int httpPort) throws Exception {
    	//vertx = Vertx.newVertx(port,Utils.getBindAddress());
    	vertx = Vertx.newVertx();
    	dataService = new DataService();
    	this.httpPort = httpPort;
    	
    	this.id = id;
    	eventBus = vertx.eventBus();
    	mapper = new ObjectMapper();
    	mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    	HttpServer server = vertx.createHttpServer();
    	final RouteMatcher routeMatcher = new RouteMatcher();
    	
    	final int limit = 100000;
    	routeMatcher.all("/multidistpdf",new Handler<HttpServerRequest>() {    		
    		
			@Override
			public void handle(final HttpServerRequest req) {
				//String startTime = "1355561151577"; //--multi-dist
				String startTime =   "1355562000000"; //--multi-dist
				Iterator<EventInfo> iter = 
						dataService.events()
							.find("{timestamp:{$gt:"+startTime+"}}")
							.sort("{timestamp:1}")
							.as(EventInfo.class).iterator();
				EventCounter multiCounter = new EventCounter("/multi");
				int processed = 0;
				while (iter.hasNext() && processed < limit) {
					EventInfo event = iter.next();
					multiCounter.countEvent(event);
					processed++;
				}
				
				req.response.setChunked(true);
				multiCounter.printDistance(req, "multidist");
				//multiCounter.printResponse(req, "multidist");
				//use response time data
				Iterator<ResponseInfo> respIter = 
						dataService.response()
							.find("{timestamp:{$gt:"+startTime+"}}")
							.sort("{timestamp:1}")
							.as(ResponseInfo.class).iterator();
				ResponseCounter responseCounter = new ResponseCounter();
				processed = 0;
				while (respIter.hasNext() && processed < limit) {
					ResponseInfo resp = respIter.next();
					responseCounter.count(resp);
					processed++;
				}
				responseCounter.printResponse(req, "multidist");

				req.response.end();
			}
    	});

    	routeMatcher.all("/multicomppdf",new Handler<HttpServerRequest>() {    		
    		
			@Override
			public void handle(final HttpServerRequest req) {
				String startTime = "1355604000000"; //--multi-comp
				String endTime =   "1355620000000";
				Iterator<EventInfo> iter = 
						dataService.events()
							.find("{timestamp:{$gt:"+startTime+", $lt:"+endTime+"}}")
							.sort("{timestamp:1}")
							.as(EventInfo.class).iterator();
				EventCounter multiCounter = new EventCounter("/multicomp");
				int processed = 0;
				while (iter.hasNext() && processed < limit) {
					EventInfo event = iter.next();
					multiCounter.countEvent(event);
					processed++;
				}
				
				req.response.setChunked(true);
				multiCounter.printDistance(req, "multicomp");
				
				//multiCounter.printResponse(req, "multicomp");
				//use response time data
				Iterator<ResponseInfo> respIter = 
						dataService.response()
							.find("{timestamp:{$gt:"+startTime+"}}")
							.sort("{timestamp:1}")
							.as(ResponseInfo.class).iterator();
				ResponseCounter responseCounter = new ResponseCounter();
				processed = 0;
				while (respIter.hasNext() && processed < limit) {
					ResponseInfo resp = respIter.next();
					responseCounter.count(resp);
					processed++;
				}
				responseCounter.printResponse(req, "multicomp");

				req.response.end();
			}
    	});
    	
    	routeMatcher.all("/sepcomppdf",new Handler<HttpServerRequest>() {    		
			@Override
			public void handle(final HttpServerRequest req) {
				// 2 VM by 2 hosts separate component
				String startTime = "1355620946302"; //--separate-comp
				Iterator<EventInfo> iter = 
						dataService.events()
							.find("{timestamp:{$gt:"+startTime+"}}")
							.sort("{timestamp:1}")
							.as(EventInfo.class).iterator();
				EventCounter multiCounter = new EventCounter("/multicomp");
				int processed = 0;
				while (iter.hasNext() && processed < limit) {
					EventInfo event = iter.next();
					multiCounter.countEvent(event);
					processed++;
				}
				
				req.response.setChunked(true);
				multiCounter.printDistance(req, "sep_comp");
				
				//multiCounter.printResponse(req, "sep_comp");
				// use response time data
				Iterator<ResponseInfo> respIter = 
						dataService.response()
							.find("{timestamp:{$gt:"+startTime+"}}")
							.sort("{timestamp:1}")
							.as(ResponseInfo.class).iterator();
				ResponseCounter responseCounter = new ResponseCounter();
				processed = 0;
				while (respIter.hasNext() && processed < limit) {
					ResponseInfo resp = respIter.next();
					responseCounter.count(resp);
					processed++;
				}
				responseCounter.printResponse(req, "sep_comp");

				req.response.end();
			}
    	});

    	routeMatcher.all("/dist-comp-pdf",new Handler<HttpServerRequest>() {    		
    		
			@Override
			public void handle(final HttpServerRequest req) {
				Iterator<EventInfo> iter = 
						dataService.events()
							.find("{timestamp:{$gt:"+startTime+"}}")
							.sort("{timestamp:1}")
							.as(EventInfo.class).iterator();
				EventCounter compCounter = new EventCounter("/comp");
				EventCounter distCounter = new EventCounter("/dist");
				while (iter.hasNext()) {
					EventInfo event = iter.next();
					compCounter.countEvent(event);
					distCounter.countEvent(event);
				}
				
				req.response.setChunked(true);
				compCounter.printDistance(req, "comp");
				compCounter.printResponse(req, "comp");
				
				distCounter.printDistance(req, "dist");
				distCounter.printResponse(req, "dist");
				
				req.response.end();
			}
    	});
    	
    	routeMatcher.all("/split",new Handler<HttpServerRequest>() {			
			@Override
			public void handle(final HttpServerRequest req) {
				req.response.setChunked(true);
				dataService.events().remove("{timestamp:{$gt:"+startTime+"}}");

				Iterator<ResponseInfo> iter = 
						dataService.response()
							.find("{timestamp:{$gt:"+startTime+"}}")
							.sort("{timestamp:1}")
							.as(ResponseInfo.class).iterator();
				int count = 0;
				while (iter.hasNext()) {
					ResponseInfo response = iter.next();
					EventInfo send = new EventInfo().setTimestamp(Math.round(response.getRequestTime()))
							.setRequest(response.getRequest())
							.setType(Direction.OUT);
					dataService.events().save(send);
					req.response.write(serialize(send));
					EventInfo recv = new EventInfo().setTimestamp(Math.round(response.getRequestTime() + response.getResponseTime()))
							.setRequest(response.getRequest())
							.setType(Direction.IN);
					dataService.events().save(recv);
					req.response.write(serialize(recv));

					++count;
					/*
					if (count == 1000) {
						break;
					}*/
				}
				req.response.write("Convert "+count+" items");
				req.response.end();
			}
		});

    	routeMatcher.noMatch(new Handler<HttpServerRequest>() {			
			@Override
			public void handle(HttpServerRequest req) {
				req.response.setChunked(true);
				req.response.write("Runner "+id+"\n");		
				req.response.end();
			}
		});
    	
    	log.info("Listening on {}",httpPort);
    	server.requestHandler(routeMatcher).listen(httpPort);
	}
	
	public String getAddress() {
		return id.toString();
	}

	public static void main(String[] args) throws Exception {
		new AnalyzerSimpleVert(new URI("analyzer:0"), 25100, 8100);
		while (true) {
			Thread.sleep(10000);
		}
	}
}
