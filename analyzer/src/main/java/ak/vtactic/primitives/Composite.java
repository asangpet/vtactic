package ak.vtactic.primitives;

import java.util.Map;

import ak.vtactic.math.DiscreteProbDensity;

public class Composite extends BinaryOperator {
	@Override
	public StringBuilder print(StringBuilder builder) {
		builder.append("(").append(left.print(new StringBuilder())).append("*")
			.append(right.print(new StringBuilder())).append(")");
		return builder;
	}
	
	@Override
	public DiscreteProbDensity eval(
			Map<String, DiscreteProbDensity> bind) {
		DiscreteProbDensity leftPdf = left.eval(bind);
		DiscreteProbDensity rightPdf = right.eval(bind);
		return leftPdf.tconv(rightPdf);
	}
}
