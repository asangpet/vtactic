package ak.vtactic.primitives;

import java.util.Map;

import ak.vtactic.math.DiscreteProbDensity;

public class Operand implements Expression {
	String operand;
	
	public Operand(String operand) {
		this.operand = operand;
	}
	
	public String getOperand() {
		return operand;
	}
	
	@Override
	public StringBuilder print(StringBuilder builder) {
		return builder.append(translate(operand));
	}
	
	private String translate(String operand) {
		switch (operand) {
		case "10.4.20.2":return "B";
		case "10.4.20.3":return "C";
		case "10.4.20.4":return "D";
		case "10.4.20.5":return "E";
		case "10.4.20.6":return "F";
		case "10.4.20.7":return "G";
		}
		return operand;
	}
	
	@Override
	public DiscreteProbDensity eval(
			Map<String, DiscreteProbDensity> bind) {
		return bind.get(operand);
	}
	
	@Override
	public String toString() {
		return translate(operand);
	}
}
