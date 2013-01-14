package ak.vtactic.analyzer.handler;

import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;

import ak.vtactic.analyzer.AnalyzerVert;
import ak.vtactic.analyzer.DependencyTool;
import ak.vtactic.analyzer.ModelTool;
import ak.vtactic.analyzer.PrettyPrinter;
import ak.vtactic.collector.ResponseCollector;
import ak.vtactic.math.DiscreteProbDensity;
import ak.vtactic.primitives.ComponentNode;

@Component
public class PlacementHandler {
	private static final Logger log = LoggerFactory.getLogger(PlacementHandler.class);
	
	@Autowired
	AnalyzerVert analyzerVert;
	
	@Autowired DependencyTool dependencyTool;
	@Autowired ModelTool modelTool;
	
	@Autowired
	BeanFactory factory;
	
	public void bind(RouteMatcher routeMatcher) {
		routeMatcher.all("/analyze/placement-rr-pin-separate-proc-a-3M", new Handler<HttpServerRequest>() {
			// round-robin request from A, placement abc/df/eg (pin on separate cpus)
			// no processing time on A
			
			double startTime = 1.358120526491732E12;
			double stopTime =  Double.MAX_VALUE;
			
			@Override
			public void handle(HttpServerRequest req) {
				req.response.setChunked(true);
				
				Map<String, DiscreteProbDensity> result = new TreeMap<String, DiscreteProbDensity>();
				
				ComponentNode actual = factory.getBean(ComponentNode.class).host("10.4.20.1").port(80);
				Map<String, DiscreteProbDensity> resp = actual.findModel(startTime, stopTime);
				result.put("Measured A", actual.getMeasuredResponse());
				result.put("ActualEst A", actual.estimate(resp));
				result.put("ActualProc A", actual.getProcessingTime());
				
				result.put("B", resp.get("10.4.20.2"));
				result.put("C", resp.get("10.4.20.3"));
				
				PrettyPrinter.printJSON(req, result.entrySet());
				req.response.end();
			}
		});		
		
		routeMatcher.all("/analyze/placement-rr-pin-separate-no-proc-a", new Handler<HttpServerRequest>() {
			// round-robin request from A, placement abc/df/eg (pin on separate cpus)
			// no processing time on A
			
			double startTime = 1.358110211222640E12;
			double stopTime =  1.358120526491354E12;
			
			@Override
			public void handle(HttpServerRequest req) {
				req.response.setChunked(true);
				
				Map<String, DiscreteProbDensity> result = new TreeMap<String, DiscreteProbDensity>();
				
				ComponentNode actual = factory.getBean(ComponentNode.class).host("10.4.20.1").port(80);
				Map<String, DiscreteProbDensity> resp = actual.findModel(startTime, stopTime);
				result.put("Measured A", actual.getMeasuredResponse());
				result.put("ActualEst A", actual.estimate(resp));
				result.put("ActualProc A", actual.getProcessingTime());
				
				result.put("B", resp.get("10.4.20.2"));
				result.put("C", resp.get("10.4.20.3"));
				
				PrettyPrinter.printJSON(req, result.entrySet());
				req.response.end();
			}
		});
		
		routeMatcher.all("/analyze/placement-rr", new Handler<HttpServerRequest>() {
			// round-robin request from A, placement abc/df/eg (pin 1 cpu)
			// no processing time on A
			
			double startTime = 1.358081420772367E12;
			double stopTime =  1.358105895597000E12;
			
			@Override
			public void handle(HttpServerRequest req) {
				req.response.setChunked(true);
				
				Map<String, DiscreteProbDensity> result = new TreeMap<String, DiscreteProbDensity>();
				
				ComponentNode actual = factory.getBean(ComponentNode.class).host("10.4.20.1").port(80);
				Map<String, DiscreteProbDensity> resp = actual.findModel(startTime, stopTime);
				
				result.put("Measured A", actual.getMeasuredResponse());
				result.put("ActualEst A", actual.estimate(resp));
				result.put("ActualProc A", actual.getProcessingTime());
				
				result.put("B", resp.get("10.4.20.2"));
				result.put("C", resp.get("10.4.20.3"));
				
				PrettyPrinter.printJSON(req, result.entrySet());
				req.response.end();
			}
		});
		
		routeMatcher.all("/analyze/placement-roundrobin", new Handler<HttpServerRequest>() {
    		public DiscreteProbDensity extrapolate(DiscreteProbDensity processingTime) {
				double startTime = 1357853539188.614;
				double stopTime = 1357869061784.727;
				stopTime = startTime + (stopTime-startTime)/10;
				Map<String, DiscreteProbDensity> compA = dependencyTool.collectResponse("10.4.20.1", 80,  startTime, stopTime);
				
				DiscreteProbDensity aPdf = compA.get(ResponseCollector.RESPONSE_KEY);
				double lambda = 1.0/1000;
				DiscreteProbDensity dPdf = DiscreteProbDensity.expPdf(lambda);
				return modelTool.findContendedProcessingTime(1, processingTime.mode(), 1000, aPdf, dPdf);
    		}
    		
    		private ComponentNode findModel() {
				double startTime = 1357853539188.614;
				double stopTime = 1357869061784.727;
				stopTime = startTime + (stopTime-startTime)/10;
				
				ComponentNode node = factory.getBean(ComponentNode.class);
				node.host("10.4.20.1").port(80).findModel(startTime, stopTime);
    			return node;
    		}
    		
			@Override
			public void handle(HttpServerRequest req) {
				// double startTime = 1.358039470279157E12; // non-stack placement (dedicate server)
				double startTime = 1358042991565.90; // stack placement (ABC/DF/EG)
				double stopTime = startTime + 1552259.6113;
				req.response.setChunked(true);
				
				Map<String, DiscreteProbDensity> result = new TreeMap<String, DiscreteProbDensity>();
				
				ComponentNode model = findModel();
				ComponentNode actual = factory.getBean(ComponentNode.class).host("10.4.20.1").port(80);
				Map<String, DiscreteProbDensity> resp = actual.findModel(startTime, stopTime);
				
				DiscreteProbDensity extraProc = extrapolate(model.getProcessingTime());
				
				result.put("Measured A", actual.getMeasuredResponse());
				result.put("NonContended Estimate A", model.estimate(resp));
				result.put("Contended Estimate A", model.estimate(resp, extraProc));
				result.put("ActualEst A", actual.estimate(resp));
				
				result.put("ModelProc A", model.getProcessingTime());				
				result.put("ExtraProc A", extraProc);
				result.put("ActualProc A", actual.getProcessingTime());
				
				result.put("B", resp.get("10.4.20.2"));
				result.put("C", resp.get("10.4.20.3"));
				
				PrettyPrinter.printJSON(req, result.entrySet());
				req.response.end();
			}
		});
		
		routeMatcher.all("/analyze/placementlag",new Handler<HttpServerRequest>() {    		
			@Override
			public void handle(final HttpServerRequest req) {
				double startTime = 1357853539188.614;
				double stopTime  = 1357859939188.614;
				//double stopTime =1357869061784.727; // actual end time
				req.response.setChunked(true);
				Map<String, DiscreteProbDensity> density = dependencyTool.collectLag("10.4.20.3", 80, startTime, stopTime);
				
				PrettyPrinter.printJSON(req, density.entrySet());
				req.response.end();
			}
		});
		
    	routeMatcher.all("/analyze/placement",new Handler<HttpServerRequest>() {
			@Override
			public void handle(final HttpServerRequest req) {
				double startTime = 1357853539188.614;
				double stopTime = 1357869061784.727;
				//double splitTime = (startTime + stopTime)/2;
				req.response.setChunked(true);
				Map<String, DiscreteProbDensity> result = new TreeMap<String, DiscreteProbDensity>();
				ComponentNode[] nodes = new ComponentNode[3];
				
				Map<String, DiscreteProbDensity> resp;
				
				nodes[0] = factory.getBean(ComponentNode.class);
				resp = nodes[0].host("10.4.20.2").port(80).findModel(startTime, stopTime);
				result.put("Estimate B", nodes[0].estimate(resp));
				result.put("Measured B", nodes[0].getMeasuredResponse());
				//result.put("Measured B", resp.get("10.4.20.1"));
				
				nodes[1] = factory.getBean(ComponentNode.class);				
				resp = nodes[1].host("10.4.20.3").port(80).findModel(startTime, stopTime);
				result.put("Estimate C", nodes[1].estimate(resp));
				result.put("Measured C", nodes[1].getMeasuredResponse());
				//result.put("Measured C", resp.get("10.4.20.1"));				
				
				nodes[2] = factory.getBean(ComponentNode.class);
				resp = nodes[2].host("10.4.20.1").port(80).findModel(startTime, stopTime);
				resp.put("10.4.20.2", result.get("Estimate B"));
				resp.put("10.4.20.3", result.get("Estimate C"));				
				result.put("Measured A", nodes[2].getMeasuredResponse());
				result.put("Estimate A", nodes[2].estimate(resp));
				result.put("Processing A", nodes[2].getProcessingTime());
				
				PrettyPrinter.printResponse(req, "r", result.entrySet());
				req.response.end();
			}
    	});    	

    	routeMatcher.all("/analyze/stackplacement",new Handler<HttpServerRequest>() {    		
			@Override
			public void handle(final HttpServerRequest req) {
				double startTime = 1.357872788771123E12;
				double stopTime = 1357882986682.395;
				
				req.response.setChunked(true);
				Map<String, DiscreteProbDensity> result = new TreeMap<String, DiscreteProbDensity>();				
				ComponentNode[] nodes = new ComponentNode[3];
				
				Map<String, DiscreteProbDensity> resp;
				nodes[0] = factory.getBean(ComponentNode.class);
				resp = nodes[0].host("10.4.20.2").port(80).findModel(startTime, stopTime);
				result.put("Measured B", nodes[0].getMeasuredResponse());
				result.put("Estimate B", nodes[0].estimate(resp));
				
				nodes[1] = factory.getBean(ComponentNode.class);				
				resp = nodes[1].host("10.4.20.3").port(80).findModel(startTime, stopTime);
				result.put("Measured C", nodes[1].getMeasuredResponse());
				result.put("Estimate C", nodes[1].estimate(resp));
				
				nodes[2] = factory.getBean(ComponentNode.class);
				resp = nodes[2].host("10.4.20.1").port(80).findModel(startTime, stopTime);
				log.info("Extracted expression:{}",nodes[2].getExpression());
				result.put("Processing A", nodes[2].getProcessingTime());
				result.put("Measured A", nodes[2].getMeasuredResponse());
				resp.put("10.4.20.2", result.get("Estimate B"));
				resp.put("10.4.20.3", result.get("Estimate C"));
				result.put("Estimate A", nodes[2].estimate(resp));
				//result.put("B", resp.get("10.4.20.2"));
				//result.put("C", resp.get("10.4.20.3"));
				
				PrettyPrinter.printResponse(req, "r", result.entrySet());
				req.response.end();
			}
    	});
    	
    	routeMatcher.all("/analyze/stackplacement2",new Handler<HttpServerRequest>() {    		
			@Override
			public void handle(final HttpServerRequest req) {
				double startTime = 1.357906878559387E12;
				double stopTime =  1357916855422.249;
				
				req.response.setChunked(true);
				Map<String, DiscreteProbDensity> result = new TreeMap<String, DiscreteProbDensity>();				
				ComponentNode[] nodes = new ComponentNode[3];
				
				Map<String, DiscreteProbDensity> resp;
				nodes[0] = factory.getBean(ComponentNode.class);
				resp = nodes[0].host("10.4.20.2").port(80).findModel(startTime, stopTime);
				result.put("Measured B", nodes[0].getMeasuredResponse());
				result.put("Estimate B", nodes[0].estimate(resp));
				
				nodes[1] = factory.getBean(ComponentNode.class);				
				resp = nodes[1].host("10.4.20.3").port(80).findModel(startTime, stopTime);
				result.put("Measured C", nodes[1].getMeasuredResponse());
				result.put("Estimate C", nodes[1].estimate(resp));
				
				nodes[2] = factory.getBean(ComponentNode.class);
				resp = nodes[2].host("10.4.20.1").port(80).findModel(startTime, stopTime);
				log.info("Extracted expression:{}",nodes[2].getExpression());
				result.put("Processing A", nodes[2].getProcessingTime());
				result.put("Measured A", nodes[2].getMeasuredResponse());
				//resp.put("10.4.20.2", result.get("Estimate B"));
				//resp.put("10.4.20.3", result.get("Estimate C"));
				result.put("Estimate A", nodes[2].estimate(resp));
				//result.put("B", resp.get("10.4.20.2"));
				//result.put("C", resp.get("10.4.20.3"));
				
				PrettyPrinter.printResponse(req, "r", result.entrySet());
				req.response.end();
			}
    	});
		
	}
}
