package ak.vtactic.analyzer.handler;

import java.util.Date;
import java.util.Map;

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
import ak.vtactic.service.DataService;

@Component
public class SLAHandler {
	private static final Logger log = LoggerFactory.getLogger(SLAHandler.class);
	
	@Autowired
	AnalyzerVert analyzerVert;
	
	@Autowired DependencyTool dependencyTool;
	@Autowired ModelTool modelTool;
	
	@Autowired
	BeanFactory factory;
	
	@Autowired
	DataService dataService;
	
	class TestSLAHandler implements Handler<HttpServerRequest> {
		double startTime;
		double stopTime;
		
		public TestSLAHandler(double start, double stop) {
			startTime = start;
			stopTime = stop;			
		}
		
		@Override
		public void handle(HttpServerRequest req) {
			req.response.setChunked(true);
			
			Map<String, DiscreteProbDensity> result = dependencyTool.collectRawResponse("10.4.20.1", 80, startTime, stopTime).getNodeResponse();
			
			PrettyPrinter.printJSONSeries(req, result.entrySet());
			req.response.end();
		}
	}
	
	class UtilSLAHandler implements Handler<HttpServerRequest> {
		double startTime;
		double stopTime;
		
		public UtilSLAHandler(double start, double stop) {
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
			/*
			Iterable<UtilizationInfo> utils = dataService.getUtilCpuCollection()
					.find("{$and:[{time:{$lt:#}},{time:{$gt:#}}], host:#, type:#, type_instance:#, plugin_instance:#}", 
							end, start, host, "cpu", "idle", "1").sort("{time:1}")
					.as(UtilizationInfo.class);
			*/
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
		routeMatcher.all("/analyze/sla", 
				new TestSLAHandler(1.360204425182E12, 1360214373136.755));
		routeMatcher.all("/analyze/slavm1", 
				new TestSLAHandler(1.360243941343512E12, 1.360253941343512E12));
		
		routeMatcher.all("/util/sla", 
				new UtilSLAHandler(1.360204425182E12, 1360214373136.755));				
		routeMatcher.all("/util/slavm1", 
				new UtilSLAHandler(1.360243941343512E12, 1.360253941343512E12));				
	}
}
