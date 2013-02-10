package ak.vtactic.primitives;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ak.vtactic.math.DiscreteProbDensity;

public class Operand implements Expression {
	Logger log = LoggerFactory.getLogger(Operand.class);
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
	
	@Override
	public StringBuilder print() {
		return print(new StringBuilder());
	}
	
	private String translate(String operand) {
		return Translator.translate(operand);
	}
	
	@Override
	public DiscreteProbDensity eval(
			Map<String, DiscreteProbDensity> bind) {
		if (bind.get(operand) == null) {
			log.error("Null operand found {}, need to supply response binding",operand);
		}
		return bind.get(operand);
	}
	
	@Override
	public String toString() {
		return translate(operand);
	}
	
	@Override
	public boolean contain(String operand) {
		return this.operand.equals(operand) || translate(this.operand).equals(operand);
	}
}
