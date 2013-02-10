package ak.vtactic.analyzer.handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.RelationshipType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import ak.vtactic.analyzer.AnalyzerVert;
import ak.vtactic.analyzer.DependencyTool;
import ak.vtactic.analyzer.ModelTool;
import ak.vtactic.analyzer.PrettyPrinter;
import ak.vtactic.math.DiscreteProbDensity;
import ak.vtactic.primitives.ComponentNode;
import ak.vtactic.primitives.Distributed;
import ak.vtactic.primitives.ModelApplication;
import ak.vtactic.primitives.Translator;

@Component
public class DistributedHandler {
	private static final Logger log = LoggerFactory.getLogger(PlacementHandler.class);
	
	@Autowired
	GraphDatabaseService graphDb;
	
	@Autowired
	AnalyzerVert analyzerVert;
	
	@Autowired DependencyTool dependencyTool;
	@Autowired ModelTool modelTool;
	
	@Autowired
	BeanFactory factory;
	
	private static enum RelTypes implements RelationshipType {
		KNOWS
	}
	
	class EngineHandler implements Handler<HttpServerRequest> {
		double startTime;
		double stopTime;
		
		public EngineHandler(double start, double stop) {
			startTime = start;
			stopTime = stop;			
		}
		
		class DbTranslator extends Translator {
			@Override
			public String doTranslate(String operand) {
				switch (operand) {
				case "10.4.20.1":return "A1";
				case "10.4.20.2":return "A2";
				case "10.4.20.3":return "A3";
				case "10.4.20.4":return "B1";
				case "10.4.20.5":return "B2";
				case "10.4.20.6":return "B3";
				case "10.4.20.7":return "C1";
				case "10.4.20.8":return "C2";
				case "10.4.20.9":return "C3";
				case "10.4.20.10":return "R";
				}
				return operand;		
			}			
		}
		
		@Override
		public void handle(HttpServerRequest req) {
			req.response.setChunked(true);
			
			Translator trans = new DbTranslator();
			Translator.setTranslator(trans);
			
			ModelApplication app = factory.getBean(ModelApplication.class)
					.build("10.4.20.10", 80, startTime, stopTime);
			
			Map<String, DiscreteProbDensity> result = new TreeMap<String, DiscreteProbDensity>();
			Map<String, DiscreteProbDensity> measured = new HashMap<String, DiscreteProbDensity>();
			for (ComponentNode node : app.getNodes().values()) {
				result.put(trans.doTranslate(node.getHost()), node.getMeasuredResponse());
				measured.put(node.getHost(), node.getMeasuredResponse());
			}
			for (ComponentNode node : app.getNodes().values()) {
				String name = trans.doTranslate(node.getHost());
				result.put("Est_"+name, node.estimate(measured));
				if (name.startsWith("A")) {
					DiscreteProbDensity sub = node.subsystem(measured);
					if (sub != null) {
						result.put("Sub_"+name, sub);
					}
					if (name.startsWith("A1")) {
						result.put("Test_"+name, node.estimate(measured, node.subsystem(measured).tconv(node.getProcessingTime())));
					}
					result.put("Proc_"+name, node.getProcessingTime());
					result.put("Upper_"+name, node.getMeasuredResponse().getUpperDistribution(
							((Distributed)node.getExpression()).getIndependentWeight()));
				} else {
					//result.remove(name);
				}
			}
			
			result.put("R", app.getNode("10.4.20.10").getMeasuredResponse());
			result.put("Est_R", app.getNode("10.4.20.10").estimate(measured));
			
			
			/*
			PlacementEngine engine = new PlacementEngine(app, 3);
			Map<String, DiscreteProbDensity> result = engine.evaluate();
			
			PlacementResult placementResult = engine.place(engine.usePercentile(90));
			result.put("Best", placementResult.getResult().get(app.getEntryNode().getHost()));
			
			ModelApplication baseline2 = factory.getBean(ModelApplication.class)
					.build("10.4.20.1", 80, 1.358122651383813E12, 1.358125413177958E12);

			ModelApplication best = factory.getBean(ModelApplication.class)
					.build("10.4.20.1", 80, 1.358625956855718E12, Double.MAX_VALUE);
			result.put("Resp1", baseline2.getNode("10.4.20.1").getMeasuredResponse());
			
			result.put("BestResp1", best.getNode("10.4.20.1").getMeasuredResponse());
			*/
			
			PrettyPrinter.printJSON(req, result.entrySet());
			req.response.end();
		}
	}
	
	class GraphHandler implements Handler<HttpServerRequest> {
		double startTime;
		double stopTime;
		
		public GraphHandler(double start, double stop) {
			startTime = start;
			stopTime = stop;			
		}
		
		class Link {
			String source;
			String target;
			public Link(String source, String target) {
				this.source = source;
				this.target= target;
			}
		}
		
		@Override
		public void handle(HttpServerRequest req) {
			req.response.setChunked(true);
			ModelApplication app = factory.getBean(ModelApplication.class)
					.build("10.4.20.10", 80, startTime, stopTime);

			Set<String> visited = new HashSet<String>();
			Set<Link> links = new HashSet<Link>();
			Queue<String> nextNode = new LinkedList<String>();
			nextNode.add(app.getEntryNode().getHost());

			while (!nextNode.isEmpty()) {
				ComponentNode node = app.getNode(nextNode.poll());
				for (String dep : node.getDependencies().keySet()) {
					links.add(new Link(node.getHost(), dep));
					if (!visited.contains(dep)) {
						nextNode.add(dep);
					}
				}
				visited.add(node.getHost());
			}
			
			JsonObject json = new JsonObject();
			JsonArray nodeArray = new JsonArray();
			Map<String, Integer> nodeOrders = new HashMap<>();
			int idx = 0;
			for (String node : app.getNodes().keySet()) {
				JsonObject obj = new JsonObject();
				obj.putString("name", node);
				obj.putString("color", "black");
				nodeOrders.put(node, idx++);
				nodeArray.add(obj);
			}
			JsonArray linkArray = new JsonArray();
			for (Link link : links) {
				JsonObject obj = new JsonObject();
				obj.putNumber("source", nodeOrders.get(link.source));
				obj.putNumber("target", nodeOrders.get(link.target));
				obj.putNumber("value", 1);
				linkArray.add(obj);
			}
			json.putArray("nodes", nodeArray);
			json.putArray("links", linkArray);
			
			req.response.write(json.toString());
			req.response.end();
			/*
			Transaction tx = graphDb.beginTx();
			try {
				Node first = graphDb.createNode();
				Node second = graphDb.createNode();
				first.createRelationshipTo(second, RelTypes.KNOWS);
				tx.success();
			} finally {
				tx.finish();
			}
			*/
		}
	}
	
	public void bind(RouteMatcher routeMatcher) {
		routeMatcher.all("/analyze/graph", new GraphHandler(1.358791919500622E12, Double.MAX_VALUE));
		routeMatcher.all("/analyze/distdb", 
				new EngineHandler(1.358791919500622E12, Double.MAX_VALUE));

	}
}
