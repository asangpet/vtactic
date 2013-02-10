package ak.vtactic.primitives;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import ak.vtactic.math.DiscreteProbDensity;

public class Distributed implements Expression {
	private final TreeSet<ExecutionPath> terms;
	private double independentWeight = 0.0;

	@Override
	public DiscreteProbDensity eval(Map<String, DiscreteProbDensity> bind) {
		if (terms.isEmpty()) {
			return null;
		}
		double[] prob = new double[terms.size()];
		DiscreteProbDensity[] pdfs = new DiscreteProbDensity[terms.size()];
		
		Iterator<ExecutionPath> iter = terms.iterator();
		int idx = 0;
		while (iter.hasNext()) {
			ExecutionPath term = iter.next();
			pdfs[idx] = term.expression.eval(bind);
			prob[idx] = term.prob;
			idx++;
		}
		return DiscreteProbDensity.distribute(prob, pdfs);
	}
	
	public Distributed() {
		terms = new TreeSet<ExecutionPath>();
	}
	
	public void addTerm(Expression term, double prob) {
		terms.add(new ExecutionPath(term, prob));
	}
	
	public StringBuilder prettyPrint(StringBuilder builder) {
		boolean first = true;
		Iterator<ExecutionPath> iter = terms.iterator();
		while (iter.hasNext()) {
			ExecutionPath term = iter.next();
			if (first) {
				first = false;
			} else {
				builder.append(" + ");
			}
			builder.append(term.prob)
				.append(term.expression.print(new StringBuilder()));
			
			if (terms.isEmpty()) {
				break;
			}
		}
		return builder;
	}
	
	@Override
	public String toString() {
		return prettyPrint(new StringBuilder()).toString();
	}
	
	@Override
	public StringBuilder print(StringBuilder builder) {
		Iterator<ExecutionPath> iter = terms.iterator();
		while (iter.hasNext()) {
			ExecutionPath term = iter.next();
			builder.append("\n").append(term.prob).append(",")
				.append(term.expression.print(new StringBuilder()));
			
			if (terms.isEmpty()) {
				break;
			}
		}
		return builder;
	}
	
	@Override
	public StringBuilder print() {
		return print(new StringBuilder());
	}
	
	public Expression random() {
		double rand = Math.random();
		double sum = 0;
		for (ExecutionPath term : terms) {
			sum += term.prob;
			if (sum >= rand) {
				return term.expression;
			}
		}
		return terms.last().expression;
	}
	
	@Override
	public boolean contain(String operand) {
		for (ExecutionPath term : terms) {
			if (term.expression.contain(operand)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isEmpty() {
		return terms.isEmpty();
	}
	
	public void setIndependentWeight(double independentWeight) {
		this.independentWeight = independentWeight;
	}
	
	public double getIndependentWeight() {
		return independentWeight;
	}
	
}