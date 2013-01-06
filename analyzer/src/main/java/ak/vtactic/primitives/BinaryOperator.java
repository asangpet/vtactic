package ak.vtactic.primitives;

public abstract class BinaryOperator implements Expression {
	Expression left;
	Expression right;
	
	public BinaryOperator setLeft(Expression left) {
		this.left = left;
		return this;
	}
	
	public BinaryOperator setRight(Expression right) {
		this.right = right;
		return this;
	}
}
