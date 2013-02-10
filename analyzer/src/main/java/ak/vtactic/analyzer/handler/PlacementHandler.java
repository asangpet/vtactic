package ak.vtactic.analyzer.handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
import ak.vtactic.engine.PlacementEngine;
import ak.vtactic.engine.PlacementResult;
import ak.vtactic.math.DiscreteProbDensity;
import ak.vtactic.primitives.ComponentNode;
import ak.vtactic.primitives.ModelApplication;

@Component
public class PlacementHandler {
	private static final Logger log = LoggerFactory.getLogger(PlacementHandler.class);
	
	@Autowired
	AnalyzerVert analyzerVert;
	
	@Autowired DependencyTool dependencyTool;
	@Autowired ModelTool modelTool;
	
	@Autowired
	BeanFactory factory;
	
	class EngineHandler implements Handler<HttpServerRequest> {
		double startTime;
		double stopTime;
		
		public EngineHandler(double start, double stop) {
			startTime = start;
			stopTime = stop;			
		}
		
		@Override
		public void handle(HttpServerRequest req) {
			req.response.setChunked(true);
			
			ModelApplication app = factory.getBean(ModelApplication.class)
					.build("10.4.20.1", 80, startTime, stopTime);
			
			PlacementEngine engine = new PlacementEngine(app, 3);
			Map<String, DiscreteProbDensity> result = engine.evaluate();
			
			PlacementResult placementResult = engine.place(engine.usePercentile(90));
			result.put("Best", placementResult.getResult().get(app.getEntryNode().getHost()));
			
			/*
			Map<String, DiscreteProbDensity> r2 = engine.randomAssign().impact();
			for (String key:r2.keySet()) {
				result.put("alt_"+key, r2.get(key));
			}
			*/
			
			ModelApplication baseline2 = factory.getBean(ModelApplication.class)
					.build("10.4.20.1", 80, 1.358122651383813E12, 1.358125413177958E12);

			ModelApplication best = factory.getBean(ModelApplication.class)
					.build("10.4.20.1", 80, 1.358625956855718E12, 1.358718372775114E12);
			
			result.put("Resp1", baseline2.getNode("10.4.20.1").getMeasuredResponse());
			
			result.put("BestResp1", best.getNode("10.4.20.1").getMeasuredResponse());
			//result.put("Resp2", baseline2.getNode("10.4.20.2").getMeasuredResponse());
			//result.put("Resp7", baseline2.getNode("10.4.20.7").getMeasuredResponse());
			
			PrettyPrinter.printJSON(req, result.entrySet());
			req.response.end();
			
			log.info("Placement: {}",placementResult.getPlacement().toString());
		}		
	}
	
	class GreedyEngineHandler implements Handler<HttpServerRequest> {
		double startTime;
		double stopTime;
		
		public GreedyEngineHandler(double start, double stop) {
			startTime = start;
			stopTime = stop;			
		}
		
		@Override
		public void handle(HttpServerRequest req) {
			req.response.setChunked(true);
			
			ModelApplication app = factory.getBean(ModelApplication.class)
					.build("10.4.20.1", 80, startTime, stopTime);
			
			PlacementEngine engine = new PlacementEngine(app, 3);
			Map<String, DiscreteProbDensity> result = engine.evaluate();
			
			PlacementResult placementResult = engine.placeGreedy(engine.usePercentile(90));
			result.put("Best", placementResult.getResult().get(app.getEntryNode().getHost()));
			
			ModelApplication baseline2 = factory.getBean(ModelApplication.class)
					.build("10.4.20.1", 80, 1.358122651383813E12, 1.358125413177958E12);

			ModelApplication best = factory.getBean(ModelApplication.class)
					.build("10.4.20.1", 80, 1.358625956855718E12, 1.358718372775114E12);
			
			result.put("Resp1", baseline2.getNode("10.4.20.1").getMeasuredResponse());
			
			result.put("BestResp1", best.getNode("10.4.20.1").getMeasuredResponse());
			
			PrettyPrinter.printJSON(req, result.entrySet());
			req.response.end();
			
			log.info("Placement: {}",placementResult.getPlacement().toString());
		}		
	}
	
	class ProcessModelHandler implements Handler<HttpServerRequest> {
		double startTime;
		double stopTime;
		
		public ProcessModelHandler(double start, double stop) {
			startTime = start;
			stopTime = stop;			
		}
		
		private String translate(String host) {
			switch (host) {
			case "10.4.20.1":return "A";
			case "10.4.20.2":return "B";
			case "10.4.20.3":return "C";
			case "10.4.20.4":return "D";
			case "10.4.20.5":return "E";
			case "10.4.20.6":return "F";
			case "10.4.20.7":return "G";
			default:
				return host;
			}
		}
		
		@Override
		public void handle(HttpServerRequest req) {
			req.response.setChunked(true);
			ModelApplication app = factory.getBean(ModelApplication.class)
					.build("10.4.20.1", 80, startTime, stopTime);
			
			ModelApplication baseline1 = factory.getBean(ModelApplication.class)
					.build("10.4.20.1", 80, 1.35812541317837E12, 1358135491396.193);
			
			ModelApplication baseline2 = factory.getBean(ModelApplication.class)
					.build("10.4.20.1", 80, 1.358122651383813E12, 1.358125413177958E12);
			
			Map<String, DiscreteProbDensity> result = new TreeMap<String, DiscreteProbDensity>();
			Map<String, DiscreteProbDensity> bind = new HashMap<String, DiscreteProbDensity>();
			Set<ComponentNode> contender = new HashSet<ComponentNode>();
			
			/*
			for (Map.Entry<String, ComponentNode> node : app.getNodes().entrySet()) {
				result.put("Processing "+translate(node.getKey()), node.getValue().getProcessingTime());
				result.put("Inter "+translate(node.getKey()), node.getValue().getInterarrival());
				if (!translate(node.getKey()).equals("A")) {
					result.put("Lag "+translate(node.getKey()), node.getValue().getLag());
				}
			}
			 */
			
			/*			
			contender.add(app.getNode("10.4.20.3"));
			
			bind.put("10.4.20.4", baseline1.getNode("10.4.20.4").getMeasuredResponse());
			bind.put("10.4.20.5", baseline1.getNode("10.4.20.5").getMeasuredResponse());
						
			DiscreteProbDensity bsim = app.getContendedProcessingSim(app.getNode("10.4.20.2"), contender, baseline1.getEntryNode().getInterarrival());
			DiscreteProbDensity bguess = bsim.tconv(app.getNode("10.4.20.2").subsystem(bind));
			result.put("Contended B1", bguess);
			log.info("B1:{} {}",bguess.percentile(90), bguess.percentile(95));
			*/
			
			/****Test placement, ABC + D|E + F|G ********************************/
			ComponentNode nodeA = app.getNode("10.4.20.1");
			ComponentNode nodeB = app.getNode("10.4.20.2");
			ComponentNode nodeC = app.getNode("10.4.20.3");
			ComponentNode nodeD = app.getNode("10.4.20.4");
			ComponentNode nodeE = app.getNode("10.4.20.5");
			ComponentNode nodeF = app.getNode("10.4.20.6");
			ComponentNode nodeG = app.getNode("10.4.20.7");
			
			bind.put("10.4.20.4", nodeD.getMeasuredResponse());
			bind.put("10.4.20.5", nodeE.getMeasuredResponse());
			contender.clear();
			contender.add(nodeA);
			contender.add(nodeC);
			DiscreteProbDensity bsim2 = app.getContendedProcessingSim(nodeB, contender, baseline2.getEntryNode().getInterarrival());
			DiscreteProbDensity bguess2 = bsim2.tconv(nodeB.subsystem(bind));
			result.put("Contended B2", bguess2);
			log.info("B2:{} {}",bguess2.percentile(90), bguess2.percentile(95));
			
			contender.clear();
			contender.add(nodeA);
			contender.add(nodeB);
			bind.put("10.4.20.6", nodeF.getMeasuredResponse());
			bind.put("10.4.20.7", nodeG.getMeasuredResponse());
			DiscreteProbDensity csim2 = app.getContendedProcessingSim(nodeC, contender, baseline2.getEntryNode().getInterarrival());
			DiscreteProbDensity cguess2 = csim2.tconv(nodeC.subsystem(bind));
			result.put("Contended C2", cguess2);
			log.info("C2:{} {}",cguess2.percentile(90), cguess2.percentile(95));
			
			contender.clear();
			contender.add(nodeB);
			contender.add(nodeC);
			bind.put("10.4.20.2", bguess2);
			bind.put("10.4.20.3", cguess2);
			DiscreteProbDensity asim2 = app.getContendedProcessingSim(nodeA, contender, baseline2.getEntryNode().getInterarrival());
			result.put("Contended A2 proc", asim2);
			DiscreteProbDensity aguess2 = (nodeA.subsystem(bind)).tconv(asim2);
			result.put("Contended A2", aguess2);
			log.info("A2:{} {}",aguess2.percentile(90), aguess2.percentile(95));
			
			
			//baseline comparisons
			/*
			Map<String, DiscreteProbDensity> baseline = dependencyTool.collectResponse("10.4.20.2", 80, 1.35812541317837E12, 1358135491396.193);
			DiscreteProbDensity actualB1 = baseline.get("10.4.20.1");
			result.put("Actual contendB1",actualB1);
			log.info("Measure B1:{} {}",actualB1.percentile(90), actualB1.percentile(95));
			*/

			//baseline = dependencyTool.collectResponse("10.4.20.2", 80, 1.358122651383813E12, 1.358125413177958E12); 
			DiscreteProbDensity actualB2 = baseline2.getNode("10.4.20.2").getMeasuredResponse();
			result.put("Actual contendB2",actualB2);
			log.info("Measure B2:{} {}",actualB2.percentile(90), actualB2.percentile(95));
			
			DiscreteProbDensity actualC2 = baseline2.getNode("10.4.20.3").getMeasuredResponse();
			result.put("Actual contendC2",actualC2);
			log.info("Measure C2:{} {}",actualC2.percentile(90), actualC2.percentile(95));
			
			DiscreteProbDensity actualA2 = baseline2.getEntryNode().getMeasuredResponse();
			result.put("Actual contendA2",actualA2);
			log.info("Measure A2:{} {}",actualA2.percentile(90), actualA2.percentile(95));
			
			/*
			result.put("Actual contendC1",baseline.get("10.4.20.1"));
			baseline = dependencyTool.collectResponse("10.4.20.3", 80, 1.358122651383813E12, 1.358125413177958E12); 
			result.put("Actual contendC2",baseline.get("10.4.20.1"));
			*/
			
			/*
			
			
			Map<String, DiscreteProbDensity> lag = dependencyTool.collectLag("10.4.20.1", 80, startTime, stopTime);
			result.put("lag B", lag.get("10.4.20.2"));
			result.put("lag C", lag.get("10.4.20.3"));
			
			ComponentNode main = factory.getBean(ComponentNode.class).host("10.4.20.1").port(80);
			main.findModel(startTime, stopTime);
			result.put("A", main.getMeasuredResponse());
			result.put("proc A", main.getProcessingTime());
			result.put("A interarrival", main.getInterarrival());
			
			ComponentNode subB = factory.getBean(ComponentNode.class).host("10.4.20.2").port(80);
			Map<String, DiscreteProbDensity> respB = subB.findModel(startTime, stopTime);
			result.put("B", subB.getMeasuredResponse());
			result.put("Estimate B", subB.estimate(respB));
			result.put("Subsys   B", subB.subsystem(respB));
			result.put("B interarrival", subB.getInterarrival());
			
			DiscreteProbDensity bproc = subB.getProcessingTime();
			result.put("Process B", bproc);
			//result.put("Contend B0", ModelTool.findContendedProcessingIndependent(1000, 0, bs).tconv(sub.subsystem(respB)));
			
			ComponentNode subC = factory.getBean(ComponentNode.class).host("10.4.20.3").port(80);
			Map<String, DiscreteProbDensity> respC = subC.findModel(startTime, stopTime);
			result.put("C", subC.getMeasuredResponse());
			result.put("C interarrival", subC.getInterarrival());
			
			DiscreteProbDensity contendMixB =
					ModelTool.contendedProcessingSim(lag.get("10.4.20.2"), lag.get("10.4.20.3"), subB.getProcessingTime(), subC.getProcessingTime(),
					main.getInterarrival());
			//result.put("GProc B", contendMixB);
			result.put("Guess B", contendMixB.tconv(subB.subsystem(respB)));
			
			DiscreteProbDensity contendMixC =
					ModelTool.contendedProcessingSim(lag.get("10.4.20.3"), lag.get("10.4.20.2"), subC.getProcessingTime(), subB.getProcessingTime(),
					main.getInterarrival());
			result.put("Guess C", contendMixC.tconv(subC.subsystem(respC)));
			

			DiscreteProbDensity contendMixB2 =
					ModelTool.contendedProcessingSim(lag.get("10.4.20.2"), main.getProcessingTime(), contendMixB, main.getProcessingTime(),
					main.getInterarrival());
			result.put("Guess ABC", contendMixB2.tconv(subB.subsystem(respB)));
			
			DiscreteProbDensity contendMixB3 =
					ModelTool.alternateProcessingSim(main, lag, subB.getProcessingTime(), subC.getProcessingTime(), main.getInterarrival()); 
			result.put("Guess B3", contendMixB2.tconv(subB.subsystem(respB)));
			*/
			//result.put("Contend B1", ModelTool.findContendedProcessingIndependent(1000, 1, bs));
			//result.put("Contend B2", ModelTool.findContendedProcessingIndependent(1000, 2, bs));
			//result.put("Contend B3", ModelTool.findContendedProcessingIndependent(1000, 3, bs));
			//result.put("Process Bx2", bs.tconv(bs));
			//result.put("Process Bx3", bs.tconv(bs).tconv(bs));
			
			/*
			result.put("D", resp.get("10.4.20.4"));
			result.put("E", resp.get("10.4.20.5"));
			
			ComponentNode subC = factory.getBean(ComponentNode.class).host("10.4.20.3").port(80);
			resp = subC.findModel(startTime, stopTime);
			result.put("C", subC.getMeasuredResponse());
			result.put("Estimate C", subC.estimate(resp));
			result.put("Process  C", subC.getProcessingTime());
			
			result.put("F", resp.get("10.4.20.6"));
			result.put("G", resp.get("10.4.20.7"));
			
			ComponentNode actual = factory.getBean(ComponentNode.class).host("10.4.20.1").port(80);
			resp = actual.findModel(startTime, stopTime);
			result.put("A", actual.getMeasuredResponse());
			result.put("Estimate A", actual.estimate(resp));
			result.put("Process  A", actual.getProcessingTime());
			*/
			
			PrettyPrinter.printJSON(req, result.entrySet());
			/*
			Expression path = app.pickPath();
			log.info("Pick random path:"+path.print(new StringBuilder()).toString());
			for (String node : app.getNodes().keySet()) {
				log.info("Containing {}:{}",node, path.contain(node));	
			}
			*/
			
			req.response.end();
		}
	}
	
	public void bind(RouteMatcher routeMatcher) {
		routeMatcher.all("/analyze/greedyengine", 
				new GreedyEngineHandler(1.358120526491732E12, 1.358122651383419E12));
		
		routeMatcher.all("/analyze/engine", 
				new EngineHandler(1.358120526491732E12, 1.358122651383419E12));
		
		// round-robin request from A, placement a-bc/df/eg (a+bc on same hosts (diff cpu). df eg = nopin)
		// 3M cycle processing time on A
		routeMatcher.all("/analyze/placement-rr-pin-a-bc-proc-a-3M", 
				new ProcessModelHandler(1.35812541317837E12, 1.358625956849702E12));
		
		// round-robin request from A, placement abc/df/eg (abc pin on same cpus. df eg = nopin)
		// 3M cycle processing time on A
		routeMatcher.all("/analyze/placement-rr-pin-shared-proc-a-3M", 
				new ProcessModelHandler(1.358122651383813E12, 1.358125413177958E12));
			
		// round-robin request from A, placement abc/df/eg (pin on separate cpus)
		// 3M cycle processing time on A
		routeMatcher.all("/analyze/placement-rr-pin-separate-proc-a-3M", 
				new ProcessModelHandler(1.358120526491732E12, 1.358122651383419E12));
		
		// round-robin request from A, placement abc/df/eg (pin on separate cpus)
		// no processing time on A
		routeMatcher.all("/analyze/placement-rr-pin-separate-no-proc-a",
				new ProcessModelHandler(1.358110211222640E12, 1.358120526491354E12));
		
		routeMatcher.all("/analyze/placement-rr-shared-no-proc-a", new Handler<HttpServerRequest>() {
			// round-robin request from A, placement abc/df/eg (pin all 1 cpu)
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
