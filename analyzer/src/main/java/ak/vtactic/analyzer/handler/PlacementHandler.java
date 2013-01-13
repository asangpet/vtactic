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
import ak.vtactic.analyzer.PrettyPrinter;
import ak.vtactic.math.DiscreteProbDensity;
import ak.vtactic.primitives.ComponentNode;

@Component
public class PlacementHandler {
	private static final Logger log = LoggerFactory.getLogger(PlacementHandler.class);
	
	@Autowired
	AnalyzerVert analyzerVert;
	
	@Autowired
	DependencyTool dependencyTool;
	
	@Autowired
	BeanFactory factory;
	
	public void bind(RouteMatcher routeMatcher) {
		routeMatcher.all("/analyze/placementlag",new Handler<HttpServerRequest>() {    		
			@Override
			public void handle(final HttpServerRequest req) {
				double startTime = 1357853539188.614;
				double stopTime  = 1357859939188.614;
				//double stopTime =1357869061784.727; // actual end time
				req.response.setChunked(true);
				Map<String, DiscreteProbDensity> density = dependencyTool.collectLag("10.4.20.3", 80, startTime, stopTime);
				
				PrettyPrinter.printResponse(req, "lag", density.entrySet());
				req.response.end();
			}
		});
		
    	routeMatcher.all("/analyze/placement",new Handler<HttpServerRequest>() {    		
			@Override
			public void handle(final HttpServerRequest req) {
				double startTime = 1357853539188.614;
				//double stopTime  = 1357859939188.614;
				double stopTime =1357869061784.727; // actual end time
				req.response.setChunked(true);
				Map<String, DiscreteProbDensity> result = new TreeMap<String, DiscreteProbDensity>();
				
				ComponentNode[] nodes = new ComponentNode[3];
				
				Map<String, DiscreteProbDensity> resp;
				
				nodes[0] = factory.getBean(ComponentNode.class);
				resp = nodes[0].host("10.4.20.2").port(80).findModel("10.4.20.1", startTime, stopTime);
				result.put("Measured B", nodes[0].getMeasuredResponse());
				result.put("Estimate B", nodes[0].estimate(resp));
				
				nodes[1] = factory.getBean(ComponentNode.class);				
				resp = nodes[1].host("10.4.20.3").port(80).findModel("10.4.20.1", startTime, stopTime);
				result.put("Measured C", nodes[1].getMeasuredResponse());
				result.put("Estimate C", nodes[1].estimate(resp));
				
				//Map<String, DiscreteProbDensity> lag = dependencyTool.collectLag("10.4.20.1", 80, startTime, stopTime);
				//PrettyPrinter.printResponse(req, "lag", lag.entrySet());
				nodes[2] = factory.getBean(ComponentNode.class);
				resp = nodes[2].host("10.4.20.1").port(80).findModel("10.1.1.9", startTime, stopTime);
				resp.put("10.4.20.2", result.get("Estimate B"));
				resp.put("10.4.20.3", result.get("Estimate C"));				
				//result.put("Measured A", nodes[2].getMeasuredResponse());
				//result.put("Estimate A", nodes[2].estimate(resp));
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
				resp = nodes[0].host("10.4.20.2").port(80).findModel("10.4.20.1", startTime, stopTime);
				result.put("Measured B", nodes[0].getMeasuredResponse());
				result.put("Estimate B", nodes[0].estimate(resp));
				
				nodes[1] = factory.getBean(ComponentNode.class);				
				resp = nodes[1].host("10.4.20.3").port(80).findModel("10.4.20.1", startTime, stopTime);
				result.put("Measured C", nodes[1].getMeasuredResponse());
				result.put("Estimate C", nodes[1].estimate(resp));
				
				nodes[2] = factory.getBean(ComponentNode.class);
				resp = nodes[2].host("10.4.20.1").port(80).findModel("10.1.1.9", startTime, stopTime);
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
				resp = nodes[0].host("10.4.20.2").port(80).findModel("10.4.20.1", startTime, stopTime);
				result.put("Measured B", nodes[0].getMeasuredResponse());
				result.put("Estimate B", nodes[0].estimate(resp));
				
				nodes[1] = factory.getBean(ComponentNode.class);				
				resp = nodes[1].host("10.4.20.3").port(80).findModel("10.4.20.1", startTime, stopTime);
				result.put("Measured C", nodes[1].getMeasuredResponse());
				result.put("Estimate C", nodes[1].estimate(resp));
				
				nodes[2] = factory.getBean(ComponentNode.class);
				resp = nodes[2].host("10.4.20.1").port(80).findModel("10.1.1.9", startTime, stopTime);
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
