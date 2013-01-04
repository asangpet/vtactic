package ak.vtactic.model;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunMarker {
	private long _id;
	
	private Double init;
	private Double rampup;
	private Double steady;
	private Double finish;
	
	private String runRequest;
	
	private final Set<String> paths = new HashSet<>();
	private final Set<String> hosts = new HashSet<>();
	
	public static RunMarker fromRequest(String request) {
		Matcher matcher = Pattern.compile(".*/marker/([0-9]+)/.*").matcher(request);
		if (matcher.matches()) {
			RunMarker marker = new RunMarker().withId(Long.valueOf(matcher.group(1))).withRunRequest(request);
			return marker;
		} else {
			return null;
		}
	}
	
	public RunMarker withId(long id) {
		this._id = id;
		return this;
	}
	
	public RunMarker withInit(Double init) {
		this.init = init;
		return this;
	}
	
	public RunMarker withRampup(Double rampup) {
		this.rampup = rampup;
		return this;
	}
	
	public RunMarker withSteady(Double steady) {
		this.steady = steady;
		return this;
	}

	public RunMarker withFinish(Double finish) {
		this.finish = finish;
		return this;
	}
	
	public RunMarker withRunRequest(String request) {
		runRequest = request;
		return this;
	}
	
	public String getRunRequest() {
		return runRequest;
	}
	
	public long getId() {
		return _id;
	}
	
	public Double getFinish() {
		return finish;
	}
	
	public Double getInit() {
		return init;
	}
	
	public Set<String> getPaths() {
		return paths;
	}
	
	public Set<String> getHosts() {
		return hosts;
	}
	
	public Double getRampup() {
		return rampup;
	}
	
	public Double getSteady() {
		return steady;
	}
	
	public String getLinks() {
		StringBuilder builder = new StringBuilder("");
		for (String path : paths) {
			builder.append(path);
			buildPath(builder, init, rampup, path, "init");
			buildPath(builder, rampup, steady, path, "rampup");
			buildPath(builder, steady, finish, path, "steady");
			builder.append("<br/>");
		}
		for (String host : hosts) {
			builder.append(host);
			buildUtil(builder, init, rampup, host, "init");
			buildUtil(builder, rampup, steady, host, "rampup");
			buildUtil(builder, steady, finish, host, "steady");
			builder.append("<br/>");
		}
		return builder.toString();
	}
	
	private StringBuilder buildUtil(StringBuilder builder, Double start, Double end, String host, String label) {
		builder.append(" - <a href='/util?start=")
		.append(start);
		if (end != null) {
			builder.append("&end=")
			.append(end);
		}
		builder.append("&host=")
		.append(host)
		.append("'>")
		.append(label)
		.append("</a>");
		return builder;
	}
	
	private StringBuilder buildPath(StringBuilder builder, Double start, Double end, String path, String label) {
		builder.append(" - <a href='/result?startTime=")
		.append(start);
		if (end != null) {
			builder.append("&endTime=")
			.append(end);
		}
		builder.append("&request=")
		.append(path)
		.append("'>")
		.append(label)
		.append("</a>");
		return builder;
	}
}