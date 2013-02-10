package ak.vtactic.analyzer;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.http.HttpServerRequest;

import ak.vtactic.math.DiscreteProbDensity;

public class PrettyPrinter {
	private static final Logger log = LoggerFactory.getLogger(PrettyPrinter.class);
	static final String[] colors = new String[] { "b", "r", "g", "m", "k", "c" };

	private static String sanitize(String var) {
		var = var.replaceAll("[\\:\\.\\$]", "_");
		StringBuilder sb = new StringBuilder();
		for (String s : var.split(" ")) {
			if (s.isEmpty()) continue;
			sb.append(s);
			sb.append("_");
		}
		sb.deleteCharAt(sb.length()-1);
		return sb.toString();
	}
	
	private static String nameToLegend(String name) {
		return name.replace("_", ".");
	}
	
	/**
	 * Print MATLAB response
	 * @param req
	 * @param varprefix
	 * @param entries
	 */
	public static void printResponse(HttpServerRequest req, String varprefix, Collection<Map.Entry<String, DiscreteProbDensity>> entries) {
		req.response.write("figure; hold on;\n");
		int i = 0;
		StringBuilder legends = new StringBuilder();
		for (Map.Entry<String, DiscreteProbDensity> entry : entries) {
			log.debug("Pretty printing component {}", entry.getKey());
			
			String name = sanitize(varprefix+"_"+entry.getKey().substring(entry.getKey().lastIndexOf("/")+1));
			if (legends.length() > 0) {
				legends.append(",");
			}
			legends.append("'").append(nameToLegend(name)).append("'");
			req.response.write(name);
			req.response.write("=");
			req.response.write(entry.getValue().printBuffer().toString());
			req.response.write(";\n");
			req.response.write("plot("+name+",'"+colors[i]+"');");
			i++; if (i == colors.length) { i = 0; }
		}
		legends.insert(0, "legend(").append(");");
		req.response.write(legends.toString());
		req.response.write("hold off;\n\n");

		// plot cdf;
		req.response.write("figure; hold on;\n");
		i = 0;
		for (Map.Entry<String, DiscreteProbDensity> entry : entries) {
			String name = sanitize(varprefix+"_"+entry.getKey().substring(entry.getKey().lastIndexOf("/")+1));
			req.response.write("plot(cumsum("+name+"),'"+colors[i]+"');");
			i++; if (i == colors.length) { i = 0; }
		}
		req.response.write(legends.toString());
		req.response.write("hold off;\n\n");

	}

	public static void printJSON(HttpServerRequest req, Collection<Map.Entry<String, DiscreteProbDensity>> entries) {
		req.response.write("{");
		int count = 0;
		for (Map.Entry<String, DiscreteProbDensity> entry : entries) {
			log.debug("Pretty printing JSON component {}", entry.getKey());
			String name = entry.getKey();
			req.response.write(String.format("\"%s\":",name));			
			req.response.write(entry.getValue().printBuffer().toString());
			count++;
			if (count < entries.size()) {
				req.response.write(",");
			}
		}
		req.response.write("}");
		log.debug("Response generation completed");
	}
	
	public static void printJSONSeries(HttpServerRequest req, Collection<Map.Entry<String, DiscreteProbDensity>> entries) {
		req.response.write("{");
		int count = 0;
		for (Map.Entry<String, DiscreteProbDensity> entry : entries) {
			log.debug("Pretty printing JSON component {}", entry.getKey());
			String name = entry.getKey();
			req.response.write(String.format("\"%s\":",name));			
			req.response.write(entry.getValue().printSeriesBuffer().toString());
			count++;
			if (count < entries.size()) {
				req.response.write(",");
			}
		}
		req.response.write("}");
		log.debug("Response generation completed");
	}	
}
