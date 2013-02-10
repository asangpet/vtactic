package ak.vtactic.primitives;

public class ExecutionPath implements Comparable<ExecutionPath> {
	public Expression expression;
	public double prob;

	public ExecutionPath(Expression expression, double prob) {
		this.expression = expression;
		this.prob = prob;
	}

	@Override
	public int compareTo(ExecutionPath o2) {
		if (prob > o2.prob) {
			return -1;
		} else if (prob < o2.prob) {
			return 1;
		} else {
			String s1 = expression.print(new StringBuilder()).toString();
			String s2 = expression.print(new StringBuilder()).toString();
			return s1.compareTo(s2);
		}
	}

}
