package ak.vtactic.math.flow.cppport;

public class arc {
	node head;		// node the arc points to
	arc next;		// next arc with the same originating node
	arc sister;	// reverse arc

	int r_cap;		// residual capacity
}