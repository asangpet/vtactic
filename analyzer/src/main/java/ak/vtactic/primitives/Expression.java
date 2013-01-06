package ak.vtactic.primitives;

import java.util.Map;

import ak.vtactic.math.DiscreteProbDensity;

public interface Expression {
	StringBuilder print(StringBuilder builder);
	DiscreteProbDensity eval(Map<String, DiscreteProbDensity> bind);
}
