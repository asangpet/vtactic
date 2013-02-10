package ak.vtactic.math.flow.cppport;

public class node {
	arc first;		// first outcoming arc

	arc parent;	// node's parent
	node next;		// pointer to the next active node
							//   (or to itself if it is the last node in the list)
	int			TS;			// timestamp showing when DIST was computed
	int			DIST;		// distance to the terminal
	boolean is_sink = true;	// flag showing whether the node is in the source or in the sink tree (if parent!=NULL)
	boolean is_marked = true;	// set by mark_node()
	boolean is_in_changed_list = true; // set by maxflow if 

	int tr_cap;		// if tr_cap > 0 then tr_cap is residual capacity of the arc SOURCE->node
							// otherwise         -tr_cap is residual capacity of the arc node->SINK 		
}
