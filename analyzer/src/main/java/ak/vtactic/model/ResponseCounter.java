package ak.vtactic.model;

import java.util.HashMap;
import java.util.Map;

import org.vertx.java.core.http.HttpServerRequest;

import ak.vtactic.math.DiscreteProbDensity;

public class ResponseCounter {
	Map<String, DiscreteProbDensity> responsePdfs = new HashMap<String, DiscreteProbDensity>();
	
	private void addPoint(String requestType, Map<String, DiscreteProbDensity> pdfMap, double value) {
		DiscreteProbDensity pdf = pdfMap.get(requestType);
		if (pdf == null) {
			// Resolution configuration
			pdf = new DiscreteProbDensity();
			pdfMap.put(requestType, pdf);
		}
		pdf.add(value);
	}
	
	public void count(ResponseInfo response) {
		addPoint(response.getRequest(), responsePdfs, response.getResponseTime());
	}
	
	public Map<String, DiscreteProbDensity> getResponsePdfs() {
		return responsePdfs;
	}

	public void printResponse(HttpServerRequest req, String varname) {
		req.response.write("figure; hold on;\n");
		String[] colors = new String[] { "b", "r", "g", "m", "k" };
		int i = 0;
		for (Map.Entry<String, DiscreteProbDensity> entry : getResponsePdfs().entrySet()) {
			String name = varname+"_resp_"+entry.getKey().substring(entry.getKey().lastIndexOf("/")+1);
			req.response.write(name);
			req.response.write("=");
			req.response.write(entry.getValue().printBuffer().toString());
			req.response.write(";\n");
			req.response.write("plot("+name+",'"+colors[i]+"');");
			i++; if (i == colors.length) { i = 0; }
		}
		req.response.write("hold off;\n\n");
	}
}

