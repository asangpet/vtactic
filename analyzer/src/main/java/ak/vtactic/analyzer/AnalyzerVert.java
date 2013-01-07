package ak.vtactic.analyzer;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;

import ak.vtactic.collector.RequestExtractor;
import ak.vtactic.config.AnalyzerConfig;
import ak.vtactic.math.DiscreteProbDensity;
import ak.vtactic.math.MathService;
import ak.vtactic.model.Direction;
import ak.vtactic.model.EventCounter;
import ak.vtactic.model.EventInfo;
import ak.vtactic.model.MappedCounter;
import ak.vtactic.model.ResponseCounter;
import ak.vtactic.model.ResponseInfo;
import ak.vtactic.model.SocketInfo;
import ak.vtactic.model.UtilizationInfo;
import ak.vtactic.primitives.Expression;
import ak.vtactic.service.DataService;

@Component
public class AnalyzerVert {
	private static final Logger log = LoggerFactory.getLogger(AnalyzerVert.class);
	
	Vertx vertx;
	EventBus eventBus;
	URI id;
	
	@Autowired
	ObjectMapper mapper;
	
	@Autowired
	DataService dataService;
	
	@Autowired
	AnalyzerTool analyzerTool;
	
	@Autowired
	DependencyTool dependencyTool;
	
	@Autowired
	MathService mathService;

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
	
	int limit = 200000;
	public void init(final URI id, final int port, final int httpPort) throws Exception {
    	//vertx = Vertx.newVertx(port,Utils.getBindAddress());
    	vertx = Vertx.newVertx();
    	this.httpPort = httpPort;
    	
    	this.id = id;
    	eventBus = vertx.eventBus();

    	HttpServer server = vertx.createHttpServer();
    	final RouteMatcher routeMatcher = new RouteMatcher();
    	
    	analyzerTool.registerRoute(routeMatcher);
    	
    	routeMatcher.all("/util", new Handler<HttpServerRequest>() {
    		private Date convert(String value) {
    			try {
    				return new Date(Math.round(Double.valueOf(value)));
    			} catch (Exception e) {
    				return null;
    			}
    		}
    		
    		@Override
    		public void handle(HttpServerRequest req) {
    			Date start = convert(req.params().get("start"));
    			Date end = convert(req.params().get("end"));
    			if (end == null) {
    				end = new Date();
    			}
    			String host = (req.params().get("host"));
    			Iterable<UtilizationInfo> utils = dataService.utilizations()
    					.find("{$and:[{time:{$lt:#}},{time:{$gt:#}}], host:#, type:#}", end, start, host, "virt_vcpu")
    					.as(UtilizationInfo.class);
    			//Iterable<UtilizationInfo> utils = dataService.utilizations().find().sort("{time:1}").as(/UtilizationInfo);
    			
    			req.response.setChunked(true);
    			req.response.write("util=[");
    			
    			long lastTime = -1;
    			boolean first = true;
    			for (UtilizationInfo util : utils) {
    				if (lastTime > 0) {
        				double cpuPercent = util.values[0] / 1E7;
        				
        				//req.response.write(""+util.time+" "+util.values[0]+" "+cpuPercent+"\n");
        				if (first) {
        					req.response.write(""+cpuPercent);
        					first = false;
        				} else {
        					req.response.write(","+cpuPercent);
        				}
    					
    				}
    				
    				lastTime = util.time.getTime();
    			}
    			req.response.end("];");
    		}
    	});
    	
    	routeMatcher.all("/result", new Handler<HttpServerRequest>() {
    		private Double convert(String value) {
    			try {
    				return Double.valueOf(value);
    			} catch (Exception e) {
    				return null;
    			}
    		}
    		
    		// e.g /result?startTime=xxx&endTime=xxx&request=xxx
    		@Override
    		public void handle(HttpServerRequest req) {
    			long time = System.currentTimeMillis();
    			req.response.setChunked(true);
    			Double start = convert(req.params().get("startTime"));
    			Double end = convert(req.params().get("endTime"));
    			String request = req.params().get("request");
    			
    			Iterable<ResponseInfo> results = analyzerTool.getResults(start, end, request);
    			int count = 0;
    			boolean first = true;
    			req.response.write("resp=[");
    			for (ResponseInfo info : results) {
    				if (!first) {
    					req.response.write(",");
    				} else {
    					first = false;
    				}
    				req.response.write(String.valueOf(info.getResponseTime()));
    				count++;
    			}
    			log.info("Queried result {} in {} ms",count, System.currentTimeMillis() - time);
    			req.response.write("];");
    			req.response.write("\ncount = "+count+";\n");
    			req.response.write("hist(resp,1000);");
    			req.response.end();
    		}
    	});

    	routeMatcher.all("/mongopdf",new Handler<HttpServerRequest>() {    		
			@Override
			public void handle(final HttpServerRequest req) {
				String server = "10.4.3.1";
				Iterator<EventInfo> iter = 
						dataService.events("ycsbrun")
							//.find("{$or:[{'server.address':#}, {'client.address':#}]}", server, server)
							.find("{'server.address':#}", server)
							.sort("{timestamp:1}")
							.as(EventInfo.class).iterator();
				//EventCounter multiCounter = new EventCounter("MONGO Request : Update document  ycsb.usertable");
				//MappedCounter multiCounter = new MappedCounter("MONGO Request : Query  ycsb.usertable", new SocketInfo().setAddress(server));
				MappedCounter multiCounter = new MappedCounter("MONGO Request : Update document  ycsb.usertable", new SocketInfo().setAddress(server));
				int processed = 0;
				limit = 100000;
				while (iter.hasNext() && processed < limit) {
					EventInfo event = iter.next();
					multiCounter.count(event);
					processed++;
					if (processed % 5000 == 0) {
						log.info("Processed {} events", processed);
					}
				}
				
				req.response.setChunked(true);
				multiCounter.printDistance(req, "mg");
				//multiCounter.printResponse(req, "mg");

				req.response.end();
			}
    	});
    	
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
    	
    	routeMatcher.all("/analyze",new Handler<HttpServerRequest>() {    		
			@Override
			public void handle(final HttpServerRequest req) {
				req.response.setChunked(true);
				req.response.write("<html><body><table>");
				Iterable<EventInfo> markers = dataService.events().find("{request:{$regex:'start'}}").sort("{timestamp:-1}").as(EventInfo.class);
				for (EventInfo marker : markers) {
					req.response.write("<tr><td>"+marker.getTimestamp()+"</td><td>"+marker.getRequest()+"</td>");
					EventInfo reply = dataService.events().findOne("{timestamp:{$gt:#}}",marker.getTimestamp()).as(EventInfo.class);
					req.response.write("<td>"+reply.getRequest()+"</td><td>"+reply.getTimestamp()+"</td></tr>");
				}
				req.response.write("</table></html></body>");
				req.response.end();
			}
    	});
    	
    	routeMatcher.all("/analyze/composite",new Handler<HttpServerRequest>() {    		
			@Override
			public void handle(final HttpServerRequest req) {
				req.response.setChunked(true);
				Map<String, DiscreteProbDensity> normalized = dependencyTool.collectResponse("10.4.20.1", 80, 1357246910069.706,  1357253184884.561);
				DiscreteProbDensity d1 = normalized.get("10.4.20.2");
				DiscreteProbDensity d2 = normalized.get("10.4.20.3");
				DiscreteProbDensity est = mathService.filter(d1,d2).normalize();
				normalized.put("Estimate", est);
				PrettyPrinter.printResponse(req, "resp", normalized.entrySet());
				req.response.end();
			}
    	});

    	routeMatcher.all("/analyze/concurrent",new Handler<HttpServerRequest>() {    		
			@Override
			public void handle(final HttpServerRequest req) {
				req.response.setChunked(true);
				Map<String, DiscreteProbDensity> normalized = dependencyTool.collectResponse("10.4.20.1", 80, 1357253184885.276, 1357260042449.704);
				DiscreteProbDensity d1 = normalized.get("10.4.20.2");
				DiscreteProbDensity d2 = normalized.get("10.4.20.3");
				DiscreteProbDensity est = d1.maxPdf(d2);
				normalized.put("Estimate", est);
				PrettyPrinter.printResponse(req, "resp", normalized.entrySet());
				req.response.end();
			}
    	});
    	
    	routeMatcher.all("/analyze/distributed",new Handler<HttpServerRequest>() {    		
			@Override
			public void handle(final HttpServerRequest req) {
				req.response.setChunked(true);
				Map<String, DiscreteProbDensity> normalized = dependencyTool.collectResponse("10.4.20.1", 80, 1357267063269.393, 1357272156540.792);
				DiscreteProbDensity d1 = normalized.get("10.4.20.2");
				DiscreteProbDensity d2 = normalized.get("10.4.20.3");
				double total = d1.getRawCount() + d2.getRawCount();
				DiscreteProbDensity est = DiscreteProbDensity.distribute(new double[] { d1.getRawCount()/total, d2.getRawCount()/total},  new DiscreteProbDensity[] {d1,d2});
				normalized.put("Estimate", est);
				PrettyPrinter.printResponse(req, "resp", normalized.entrySet());
				req.response.end();
			}
    	});
    	
    	routeMatcher.all("/analyze/dependencies",new Handler<HttpServerRequest>() {    		
			@Override
			public void handle(final HttpServerRequest req) {
				req.response.setChunked(true);
				RequestExtractor extractor = dependencyTool.extract("10.4.20.1", 80, 1357360213416.915, 1357416207817.871);
				StringBuilder sb = new StringBuilder();
				Expression expression = extractor.getExpression();
				expression.print(sb);
				req.response.write(sb.toString());
				req.response.write("\n\n");
				Map<String, DiscreteProbDensity> normalized = dependencyTool.collectResponse("10.4.20.1", 80, 1357360213416.915, 1357416207817.871);
				normalized.put("Estimate",expression.eval(normalized));

				/*
				DiscreteProbDensity d1 = normalized.get("10.4.20.2");
				DiscreteProbDensity d2 = normalized.get("10.4.20.3");
				double total = d1.getRawCount() + d2.getRawCount();
				DiscreteProbDensity est = DiscreteProbDensity.distribute(new double[] { d1.getRawCount()/total, d2.getRawCount()/total},  new DiscreteProbDensity[] {d1,d2});
				normalized.put("Estimate", est);
				*/
				PrettyPrinter.printResponse(req, "r", normalized.entrySet());
				req.response.end();
			}
    	});

    	routeMatcher.all("/analyze/processing/pair",new Handler<HttpServerRequest>() {    		
			@Override
			public void handle(final HttpServerRequest req) {
				req.response.setChunked(true);
				Map<String, DiscreteProbDensity> compA = dependencyTool.collectResponse("10.4.20.1", 80,  1357416207818.402, 1357430913946.345);
				Map<String, DiscreteProbDensity> compB = dependencyTool.collectResponse("10.4.20.2", 80,  1357416207818.402, 1357430913946.345);
				for (Map.Entry<String, DiscreteProbDensity> entry : compB.entrySet()) {
					compA.put("b_"+entry.getKey(), entry.getValue());
				}
				PrettyPrinter.printResponse(req, "r", compA.entrySet());
				req.response.end();
			}
    	});
    	
    	routeMatcher.all("/analyze/processing/single",new Handler<HttpServerRequest>() {
    		
    		private DiscreteProbDensity expPdf(double lambda) {
    			DiscreteProbDensity result = new DiscreteProbDensity();
    			for (int i = 0; i < result.getPdf().length; i++) {
    				result.getPdf()[i] = lambda*Math.exp(-lambda*i);
    			}
    			return result;
    		}
    		
    		private double poisson(double k, double lambda) {
    			double result = Math.exp(-lambda);
    			for (int i=1;i<=k;i++) {
    				result = result * lambda / i;
    			}
    			return result;
    		}
    		
    		/**
    		 * Find distribution of processing time, based on the non-contended processing time (aPdf)
    		 * and the probability of having co-arrival request at a point in time t (dPdf) - approximate by inter-arrival
    		 * 
    		 * The co-arrival probability is calculated based on the mean interarrival (period)
    		 * ideal processing time (processing) and rate of arrival within the period
    		 * 
    		 * @param rate
    		 * @param processing
    		 * @param period
    		 * @param aPdf
    		 * @param dPdf
    		 * @return
    		 */
    		DiscreteProbDensity findNdistP(double rate, double processing, double period, DiscreteProbDensity aPdf, DiscreteProbDensity dPdf) {
    			double lambda = 2*rate*processing / period;
				int degree = 5;
				DiscreteProbDensity[] nConv = new DiscreteProbDensity[degree];
				double[] coeff = new double[degree];
				coeff[0] = poisson(0,lambda);
				nConv[0] = aPdf;
				double sum = coeff[0];						
				for (int i=1;i<degree;i++) {
					nConv[i] = DiscreteProbDensity.coConv(i+1,dPdf,aPdf).normalize();
					coeff[i] = poisson(i,lambda);
					if (i<degree-1) {
						sum+=coeff[i];
					}
				}
				coeff[degree-1] = 1-sum;
				DiscreteProbDensity ndistP = DiscreteProbDensity.distribute(coeff, nConv);
				return ndistP;
    		}
    		
			@Override
			public void handle(final HttpServerRequest req) {
				req.response.setChunked(true);
				Map<String, DiscreteProbDensity> compA = dependencyTool.collectResponse("10.4.20.1", 80,  1357431192259.329, 2357430913946.345);
				
				DiscreteProbDensity aPdf = compA.get("10.1.1.9");
				double lambda = 1.0/500;
				DiscreteProbDensity dPdf = expPdf(lambda);
				compA.put("ndist", findNdistP(1.0, 100.0, 500.0, aPdf, dPdf));
				
				PrettyPrinter.printResponse(req, "r", compA.entrySet());
				
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
    	
    	server.requestHandler(routeMatcher).listen(httpPort);
    	log.info("Listening on {}",httpPort);
	}
	
	public String getAddress() {
		return id.toString();
	}
	
	public void run() {
		while (true) {
			try {
				Thread.sleep(100000);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		BasicConfigurator.configure();
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AnalyzerConfig.class);
		context.start();
		AnalyzerVert vert = context.getBean(AnalyzerVert.class);
		vert.init(new URI("analyzer:0"), 25100, 8100);
		vert.run();
		context.close();
	}
}
