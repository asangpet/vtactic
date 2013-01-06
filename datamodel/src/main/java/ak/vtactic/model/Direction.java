package ak.vtactic.model;

public enum Direction {
	IN, OUT;
	
	public Direction reverse() {
		if (this == IN) {
			return OUT;
		} else {
			return IN;
		}
	}
}
