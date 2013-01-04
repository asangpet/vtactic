package ak.vtactic.analyzer;

import java.util.Collection;
import java.util.Map;

import org.vertx.java.core.http.HttpServerRequest;

import ak.vtactic.math.DiscreteProbDensity;

public class PrettyPrinter {
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
	
	public static void printResponse(HttpServerRequest req, String varprefix, Collection<Map.Entry<String, DiscreteProbDensity>> entries) {
		req.response.write("figure; hold on;\n");
		int i = 0;
		StringBuilder legends = new StringBuilder();
		for (Map.Entry<String, DiscreteProbDensity> entry : entries) {
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

}
