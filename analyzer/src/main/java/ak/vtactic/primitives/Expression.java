package ak.vtactic.primitives;

import java.util.Map;

import ak.vtactic.math.DiscreteProbDensity;

public interface Expression {
	StringBuilder print(StringBuilder builder);
	StringBuilder print();
	DiscreteProbDensity eval(Map<String, DiscreteProbDensity> bind);
	boolean contain(String operand);

}
