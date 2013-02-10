package ak.vtactic.engine;

import java.util.Map;

import org.vertx.java.core.json.JsonObject;

import ak.vtactic.math.DiscreteProbDensity;

public class PlacementResult {
	final JsonObject placement;
	final Map<String, DiscreteProbDensity> result;
	
	public PlacementResult(JsonObject p, Map<String, DiscreteProbDensity> r) {
		placement = p;
		result = r;
	}
	
	public JsonObject getPlacement() {
		return placement;
	}
	public Map<String, DiscreteProbDensity> getResult() {
		return result;
	}
}
