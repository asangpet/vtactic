package ak.vtactic.analyzer;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;

import ak.vtactic.model.ResponseInfo;
import ak.vtactic.model.RunMarker;
import ak.vtactic.service.DataService;

@Component
public class AnalyzerTool {
	Logger log = LoggerFactory.getLogger(AnalyzerTool.class);
	
	@Autowired
	DataService dataService;
	
	public void registerRoute(RouteMatcher routeMatcher) {
    	routeMatcher.all("/marker", new MarkerHandler());
    	routeMatcher.all("/generateMarker", new MarkerGeneratorHandler());
	}
	
	public class MarkerGeneratorHandler implements Handler<HttpServerRequest> {
		private void writeRequestMarker(final RunMarker updateMarker, Double startTime, Double endTime) {
			RunMarker marker = dataService.markers().findOne("{_id:#}", updateMarker.getId()).as(RunMarker.class);
			if (marker != null) {
				if (updateMarker.getRunRequest() != null) {
					//merge marker
					if (updateMarker.getRunRequest().contains("/rampup/")) {
						// update previous marker
						RunMarker prevMarker = dataService.markers().findOne("{_id:#}", updateMarker.getId() - 1).as(RunMarker.class);
						if (prevMarker != null && prevMarker.getFinish() == null) {
							prevMarker.withFinish(startTime-1);
							dataService.markers().save(prevMarker);
						}
						marker.withInit(startTime).withRampup(endTime);
					} else if (updateMarker.getRunRequest().contains("/start/")) {
						marker.withRampup(startTime).withSteady(endTime);
					} else if (updateMarker.getRunRequest().contains("/steady/")) {
						marker.withSteady(startTime).withFinish(endTime);
					} else if (updateMarker.getRunRequest().contains("/finish/")) {
						marker.withFinish(startTime);
					}
				}
			} else {
				marker = updateMarker.withInit(startTime);
			}
			marker.withRunRequest(updateMarker.getRunRequest());
			for (String path : getDistinctPath(startTime, endTime)) {
				marker.getPaths().add(path);
			}
			for (String host : getDistinctHost(startTime, endTime)) {
				marker.getHosts().add(host);
			}

			dataService.markers().remove("{_id:#}", marker.getId());
			dataService.markers().save(marker);
		}

		@Override
		public void handle(HttpServerRequest req) {
			req.response.setChunked(true);
			req.response.write("<html><body>");
			double lastTime = -1;
			int count = 0;
			
			RunMarker marker = new RunMarker();
			
			for (ResponseInfo result : findMarkers()) {
				/* links for previous run set */
				if (lastTime > 0) {
					double startTime = lastTime;
					double endTime = result.getRequestTime();
					
					if (marker != null) {
						writeRequestMarker(marker, startTime, endTime);
						count++;
					}
				}
				lastTime = result.getRequestTime();
				
				/* next run set */
				marker = RunMarker.fromRequest(result.getRequest());
			}
			if (lastTime > 0 && marker != null) {    					
				writeRequestMarker(marker, lastTime, null);
				count++;
			}
			req.response.write("Generated markers : "+count);
			req.response.write("</html></body>");
			req.response.end();
		}
	}

	public class MarkerHandler implements Handler<HttpServerRequest> {
		@Override
		public void handle(HttpServerRequest req) {
			req.response.setChunked(true);
			req.response.write("<html><body>");
			Iterable<RunMarker> iters = dataService.markers().find().sort("{init:-1}").as(RunMarker.class);
			for (RunMarker marker : iters) {
				log.info("Outputting {}", marker.getRunRequest());
				req.response.write(""+marker.getRunRequest()+"<br/>");
				String links = marker.getLinks();
				if (!links.isEmpty()) {
					req.response.write(""+marker.getLinks());
				}
				req.response.write("<hr/>");
			}
			req.response.write("</body></html>");
			req.response.end();
		}
	}
	
	public Iterable<ResponseInfo> findMarkers() {
		return dataService.response()
				.find("{request:{$regex:#}}", "/marker.*").as(ResponseInfo.class);
	}

	public Iterable<ResponseInfo> getResults(Double startTime, Double endTime, String request) {
		Iterable<ResponseInfo> source = null;
		if (endTime == null && request == null) {
			source = dataService.response()
				.find("{requestTime:{$gt:#}}", startTime)
				.as(ResponseInfo.class);
		} else if (endTime == null) {
			source = dataService.response()
					.find("{requestTime:{$gt:#}, request:{$regex:#}}", startTime, request+".*")
					.as(ResponseInfo.class);			
		} else if (request == null) {
			source = dataService.response()
					.find("{$and:[{requestTime:{$gt:#}}, {requestTime:{$lt:#}}]}", startTime, endTime)
					.as(ResponseInfo.class);			
		} else {
			source = dataService.response()
					.find("{$and:[{requestTime:{$gt:#}}, {requestTime:{$lt:#}}], request:{$regex:#}}", startTime, endTime, request+".*")
					.as(ResponseInfo.class);			
		}

		return new MarkerFilterIterable<ResponseInfo>(source);
	}
	
	public Iterable<String> getDistinctPath(final double startTime, final Double endTime) {
		if (endTime != null) {
			return dataService.response().distinct("request")
			.query("{$and:[{requestTime:{$gt:#}},{requestTime:{$lt:#}}]}", startTime, endTime)
			.as(String.class);
		} else {
			return dataService.response().distinct("request")
			.query("{requestTime:{$gt:#}}", startTime)
			.as(String.class);
		}
	}
		
	public Iterable<String> getDistinctHost(final double startTime, final Double endTime) {
		if (endTime != null) {
			return dataService.utilizations().distinct("host")
			.query("{$and:[{time:{$gt:#}},{time:{$lt:#}}]}", new Date(Math.round(startTime)), new Date(Math.round(endTime)))
			.as(String.class);
		} else {
			return dataService.utilizations().distinct("host")
			.query("{requestTime:{$gt:#}}", new Date(Math.round(startTime)))
			.as(String.class);
		}
	}
}