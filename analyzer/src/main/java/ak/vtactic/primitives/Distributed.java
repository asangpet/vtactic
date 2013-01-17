package ak.vtactic.primitives;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import ak.vtactic.math.DiscreteProbDensity;
import ak.vtactic.util.Pair;

public class Distributed implements Expression {
	private final TreeSet<Pair<Expression,Double>> terms;

	@Override
	public DiscreteProbDensity eval(Map<String, DiscreteProbDensity> bind) {
		if (terms.isEmpty()) {
			return null;
		}
		double[] prob = new double[terms.size()];
		DiscreteProbDensity[] pdfs = new DiscreteProbDensity[terms.size()];
		
		Iterator<Pair<Expression,Double>> iter = terms.iterator();
		int idx = 0;
		while (iter.hasNext()) {
			Pair<Expression,Double> term = iter.next();
			pdfs[idx] = term.first.eval(bind);
			prob[idx] = term.second;
			idx++;
		}
		return DiscreteProbDensity.distribute(prob, pdfs);
	}
	
	class TermComparator implements Comparator<Pair<Expression,Double>> {
		@Override
		public int compare(Pair<Expression, Double> o1,
				Pair<Expression, Double> o2) {
			if (o1.second > o2.second) {
				return -1;
			} else if (o1.second < o2.second) {
				return 1;
			} else {					
				String s1 = o1.first.print(new StringBuilder()).toString();
				String s2 = o2.first.print(new StringBuilder()).toString();
				return s1.compareTo(s2);
			}
		}
	}
	
	public Distributed() {
		terms = new TreeSet<Pair<Expression,Double>>(new TermComparator());
	}
	
	public void addTerm(Expression term, double prob) {
		terms.add(new Pair<Expression, Double>(term, prob));
	}
	
	public StringBuilder prettyPrint(StringBuilder builder) {
		boolean first = true;
		Iterator<Pair<Expression,Double>> iter = terms.iterator();
		while (iter.hasNext()) {
			Pair<Expression,Double> term = iter.next();
			if (first) {
				first = false;
			} else {
				builder.append(" + ");
			}
			builder.append(term.second)
				.append(term.first.print(new StringBuilder()));
			
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
		Iterator<Pair<Expression,Double>> iter = terms.iterator();
		while (iter.hasNext()) {
			Pair<Expression,Double> term = iter.next();
			builder.append("\n").append(term.second).append(",")
				.append(term.first.print(new StringBuilder()));
			
			if (terms.isEmpty()) {
				break;
			}
		}
		return builder;
	}
	
	public Expression random() {
		double rand = Math.random();
		double sum = 0;
		for (Pair<Expression, Double> term : terms) {
			sum += term.second;
			if (sum >= rand) {
				return term.first;
			}
		}
		return terms.last().first;
	}
	
	@Override
	public boolean contain(String operand) {
		for (Pair<Expression, Double> term : terms) {
			if (term.first.contain(operand)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isEmpty() {
		return terms.isEmpty();
	}
}