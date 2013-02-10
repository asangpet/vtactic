package ak.vtactic.analyzer.handler;

import java.util.Date;
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
import ak.vtactic.math.DiscreteProbDensity;
import ak.vtactic.model.UtilizationInfo;
import ak.vtactic.primitives.ModelApplication;
import ak.vtactic.service.DataService;

@Component
public class MultiCoreHandler {
	private static final Logger log = LoggerFactory.getLogger(MultiCoreHandler.class);
	
	@Autowired
	AnalyzerVert analyzerVert;
	
	@Autowired DependencyTool dependencyTool;
	@Autowired ModelTool modelTool;
	
	@Autowired
	BeanFactory factory;
	
	@Autowired
	DataService dataService;
	
	class TestMCHandler implements Handler<HttpServerRequest> {
		double startTime;
		double stopTime;
		
		public TestMCHandler(double start, double stop) {
			startTime = start;
			stopTime = stop;			
		}
		
		@Override
		public void handle(HttpServerRequest req) {
			req.response.setChunked(true);
			ModelApplication app = factory.getBean(ModelApplication.class)
					.build("10.4.20.1", 80, startTime, stopTime);

			ModelApplication app2 = factory.getBean(ModelApplication.class)
					.build("10.4.20.6", 80, startTime, stopTime);
			
			Map<String, DiscreteProbDensity> result = new TreeMap<String,DiscreteProbDensity>();
			result.put("a1", app.getEntryNode().getMeasuredResponse());
			
			Map<String, DiscreteProbDensity> bind = new TreeMap<String,DiscreteProbDensity>();
			bind.put("10.4.20.2", app.getNode("10.4.20.2").getMeasuredResponse());
			bind.put("10.4.20.3", app.getNode("10.4.20.3").getMeasuredResponse());
			bind.put("10.4.20.4", app.getNode("10.4.20.4").getMeasuredResponse());
			bind.put("10.4.20.5", app.getNode("10.4.20.5").getMeasuredResponse());
			result.put("estimate", app.getEntryNode().estimate(bind));
			
			result.put("b1", app.getNode("10.4.20.2").getMeasuredResponse());
			result.put("c1", app.getNode("10.4.20.3").getMeasuredResponse());
			result.put("d1", app.getNode("10.4.20.4").getMeasuredResponse());
			result.put("e1", app.getNode("10.4.20.5").getMeasuredResponse());

			result.put("a2", app2.getEntryNode().getMeasuredResponse());
			result.put("b2", app2.getNode("10.4.20.7").getMeasuredResponse());
			result.put("c2", app2.getNode("10.4.20.8").getMeasuredResponse());
			result.put("d2", app2.getNode("10.4.20.9").getMeasuredResponse());
			result.put("e2", app2.getNode("10.4.20.10").getMeasuredResponse());
			
			//PrettyPrinter.printJSONSeries(req, result.entrySet());
			PrettyPrinter.printJSON(req, result.entrySet());
			req.response.end();
		}
	}
	
	class UtilMCHandler implements Handler<HttpServerRequest> {
		double startTime;
		double stopTime;
		
		public UtilMCHandler(double start, double stop) {
			startTime = start;
			stopTime = stop;			
		}
		
		private Date convert(double value) {
			try {
				return new Date(Math.round(value));
			} catch (Exception e) {
				return null;
			}
		}
		
		@Override
		public void handle(HttpServerRequest req) {
			Date start = convert(startTime);
			Date end = convert(stopTime);
			
			String host = "fx2.arcanine.lan";
			Iterable<UtilizationInfo> utils = dataService.getUtilCpuCollection()
					.find("{time:{$gt:#}, host:#, type:#, type_instance:#, plugin_instance:#}", 
							start, host, "cpu", "idle", "1").sort("{time:1}")
					.as(UtilizationInfo.class);
			
			req.response.setChunked(true);
			req.response.write("util=[");
			
			boolean first = true;
			for (UtilizationInfo util : utils) {
				if (first) {
					first = false;
				} else {
					req.response.write(",");
				}
				req.response.write(""+(100.0-util.values[0]));
			}
			req.response.end("];");
		}
	}
	
	public void bind(RouteMatcher routeMatcher) {
		routeMatcher.all("/analyze/mcdiststack", 
				new TestMCHandler(1.360284229804942E12, 1.360304229804942E12));
		
		routeMatcher.all("/util/mcdiststack", 
				new UtilMCHandler(1.360284229804942E12, 1.360304229804942E12));		
	}
}
