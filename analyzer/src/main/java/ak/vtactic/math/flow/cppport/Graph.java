package ak.vtactic.math.flow.cppport;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class Graph {
	int node_num;
	
	static final int INFINITE_D = Integer.MAX_VALUE;
	
	static final arc TERMINAL = new arc();
	static final arc ORPHAN = new arc(); 
	
	List<arc> orphans;
	
	List<node> nodes;
	List<arc> arcs;
	
	int node_last, node_max;
	int arc_last, arc_max;
	
	int flow;
	int maxflow_iteration;
	
	List<node> _changed_list;
	List<node> changed_list;
	
	node[] queue_first = new node[2];
	node[] queue_last = new node[2];
	
	nodeptr orphan_first, orphan_last;
	
	class nodeptr {
		node ptr;
		nodeptr next;
	}
	
	int TIME;
	
	enum termtype {
		SOURCE, SINK;
	}

	// Constructor.
	// The first argument gives an estimate of the maximum number of nodes that
	// can be added
	// to the graph, and the second argument is an estimate of the maximum
	// number of edges.
	// The last (optional) argument is the pointer to the function which will be
	// called
	// if an error occurs; an error message is passed to this function.
	// If this argument is omitted, exit(1) will be called.
	//
	// IMPORTANT: It is possible to add more nodes to the graph than
	// node_num_max
	// (and node_num_max can be zero). However, if the count is exceeded, then
	// the internal memory is reallocated (increased by 50%) which is expensive.
	// Also, temporarily the amount of allocated memory would be more than twice
	// than needed.
	// Similarly for edges.
	// If you wish to avoid this overhead, you can download version 2.2, where
	// nodes and edges are stored in blocks.
	Graph(int node_num_max, int edge_num_max) {
		if (node_num_max < 16) node_num_max = 16;
		if (edge_num_max < 16) edge_num_max = 16;

		nodes = new ArrayList<node>(node_num_max);
		arcs = new ArrayList<arc>(2*edge_num_max);

		node_last = 0;
		node_max = node_num_max;
		arc_last = 0;
		arc_max = 2*edge_num_max;

		maxflow_iteration = 0;
		flow = 0;
	}

	// Adds node(s) to the graph. By default, one node is added (num=1); then
	// first call returns 0, second call returns 1, and so on.
	// If num>1, then several nodes are added, and node_id of the first one is
	// returned.
	// IMPORTANT: see note about the constructor
	int add_node() {
		return add_node(1);
	}

	int add_node(int num) {
		for (int i = 0; i < num; i++) {
			nodes.add(new node());
		}
		int i = node_num;
		node_num += num;
		node_last += num;
		return i;
	}

	// Adds a bidirectional edge between 'i' and 'j' with the weights 'cap' and
	// 'rev_cap'.
	// IMPORTANT: see note about the constructor
	void add_edge(int _i, int _j, int cap, int rev_cap) {
		assert(_i >= 0 && _i < node_num);
		assert(_j >= 0 && _j < node_num);
		assert(_i != _j);
		assert(cap >= 0);
		assert(rev_cap >= 0);

		//if (arc_last == arc_max) reallocate_arcs();

		arc a = new arc();
		arc_last++; arcs.add(a);
		arc a_rev = new arc();
		arc_last ++; arcs.add(a_rev);

		node i = nodes.get(_i);
		node j = nodes.get(_j);

		a.sister = a_rev;
		a_rev.sister = a;
		a.next = i.first;
		i.first = a;
		a_rev.next = j.first;
		j.first = a_rev;
		a.head = j;
		a_rev.head = i;
		a.r_cap = cap;
		a_rev.r_cap = rev_cap;		
	}

	// Adds new edges 'SOURCE->i' and 'i->SINK' with corresponding weights.
	// Can be called multiple times for each node.
	// Weights can be negative.
	// NOTE: the number of such edges is not counted in edge_num_max.
	// No internal memory is allocated by this call.
	void add_tweights(int i, int cap_source, int cap_sink) {
		assert(i >= 0 && i < node_num);

		int delta = nodes.get(i).tr_cap;
		if (delta > 0) cap_source += delta;
		else           cap_sink   -= delta;
		flow += (cap_source < cap_sink) ? cap_source : cap_sink;
		nodes.get(i).tr_cap = cap_source - cap_sink;		
	}

	// Computes the maxflow. Can be called several times.
	// FOR DESCRIPTION OF reuse_trees, SEE mark_node().
	// FOR DESCRIPTION OF changed_list, SEE remove_from_changed_list().
	int maxflow() {
		return maxflow(false, null);
	}

	int maxflow(boolean reuse_trees) {
		return maxflow(false);
	}

	int maxflow(boolean reuse_trees, List<node> changed_list) {
		node i, j, current_node = null;
		arc a;
		
		nodeptr np, np_next;

		/*
		if (!nodeptr_block)
		{
			nodeptr_block = new DBlock<nodeptr>(NODEPTR_BLOCK_SIZE, error_function);
		}
		*/

		//changed_list = _changed_list;
		if (maxflow_iteration == 0 && reuse_trees) {
			throw new IllegalStateException("reuse_trees cannot be used in the first call to maxflow()!");
		}
		
		if (changed_list != null && !reuse_trees) {
			throw new IllegalStateException("changed_list cannot be used without reuse_trees!");
		}

		if (reuse_trees) maxflow_reuse_trees_init();
		else             maxflow_init();

		// main loop
		while ( true )
		{
			// test_consistency(current_node);
			i = current_node;
			if (i!=null)
			{
				i.next = null; /* remove active flag */
				if (i.parent != null) {
					i = null;
				}
			}
			if (i == null)
			{
				i = next_active();
				if (i == null) break;
			}

			/* growth */
			if (!i.is_sink)
			{
				/* grow source tree */
				for (a=i.first; a != null; a=a.next) {
					if (a.r_cap != 0)
					{
						j = a.head;
						if (j.parent == null)
						{
							j.is_sink = false;
							j.parent = a.sister;
							j.TS = i.TS;
							j.DIST = i.DIST + 1;
							set_active(j);
							add_to_changed_list(j);
						}
						else if (j.is_sink) break;
						else if (j.TS <= i.TS &&
						         j.DIST > i.DIST)
						{
							/* heuristic - trying to make the distance from j to the source shorter */
							j.parent = a.sister;
							j.TS = i.TS;
							j.DIST = i.DIST + 1;
						}
					}
				}
			}
			else
			{
				/* grow sink tree */
				for (a=i.first; a != null; a=a.next) {
					if (a.sister.r_cap != 0)
					{
						j = a.head;
						if (j.parent != null)
						{
							j.is_sink = true;
							j.parent = a.sister;
							j.TS = i.TS;
							j.DIST = i.DIST + 1;
							set_active(j);
							add_to_changed_list(j);
						}
						else if (!j.is_sink) { a = a.sister; break; }
						else if (j.TS <= i.TS &&
						         j.DIST > i.DIST)
						{
							/* heuristic - trying to make the distance from j to the sink shorter */
							j.parent = a.sister;
							j.TS = i.TS;
							j.DIST = i.DIST + 1;
						}
					}
				}
			}

			TIME ++;

			if (a != null)
			{
				i.next = i; /* set active flag */
				current_node = i;

				/* augmentation */
				augment(a);
				/* augmentation end */

				/* adoption */
				np = orphan_first;
				while ((np!=null))
				{
					np_next = np.next;
					np.next = null;

					np = orphan_first;
					while ((np != null))
					{
						orphan_first = np.next;
						i = np.ptr;
						//nodeptr_block -> Delete(np);
						if (orphan_first != null) orphan_last = null;
						if (i.is_sink) process_sink_orphan(i);
						else           process_source_orphan(i);
						np = orphan_first;
					}

					orphan_first = np_next;
					np = orphan_first;
				}
				/* adoption end */
			}
			else current_node = null;
		}
		// test_consistency();

		if (!reuse_trees || (maxflow_iteration % 64) == 0)
		{
			//delete nodeptr_block; 
			//nodeptr_block = NULL; 
		}

		maxflow_iteration ++;
		return flow;
	}

	// After the maxflow is computed, this function returns to which
	// segment the node 'i' belongs (Graph<captype,tcaptype,flowtype>::SOURCE or
	// Graph<captype,tcaptype,flowtype>::SINK).
	//
	// Occasionally there may be several minimum cuts. If a node can be assigned
	// to both the source and the sink, then default_segm is returned.
	termtype what_segment(int i) {
		return what_segment(i, termtype.SOURCE);
	}

	termtype what_segment(int i, termtype default_segm) {
		if (nodes.get(i).parent != null)
		{
			return (nodes.get(i).is_sink) ? termtype.SINK : termtype.SOURCE;
		}
		else
		{
			return default_segm;
		}
	}

	// //////////////////////////
	// 1. Reallocating graph. //
	// //////////////////////////

	// Removes all nodes and edges.
	// After that functions add_node() and add_edge() must be called again.
	//
	// Advantage compared to deleting Graph and allocating it again:
	// no calls to delete/new (which could be quite slow).
	//
	// If the graph structure stays the same, then an alternative
	// is to go through all nodes/edges and set new residual capacities
	// (see functions below).
	/*
	void reset() {
		node_last = 0;
		arc_last = 0;
		node_num = 0;

		if (nodeptr_block) 
		{ 
			delete nodeptr_block; 
			nodeptr_block = NULL; 
		}

		maxflow_iteration = 0;
		flow = 0;
	}*/
	
	////////////////////////////////////////////////////////////////////////////////
	// 2. Functions for getting pointers to arcs and for reading graph structure. //
	//    NOTE: adding new arcs may invalidate these pointers (if reallocation    //
	//    happens). So it's best not to add arcs while reading graph structure.   //
	////////////////////////////////////////////////////////////////////////////////

	// The following two functions return arcs in the same order that they
	// were added to the graph. NOTE: for each call add_edge(i,j,cap,cap_rev)
	// the first arc returned will be i->j, and the second j->i.
	// If there are no more arcs, then the function can still be called, but
	// the returned arc_id is undetermined.
	arc get_first_arc() {
		return arcs.get(0);
	}
	arc get_next_arc(ListIterator<arc> a) {
		return a.next();
	}
	
	// other functions for reading graph structure
	int get_node_num() { return nodes.size(); }
	int get_arc_num() { return arcs.size(); }
	void get_arc_ends(ListIterator<arc> iter, int i, int j) {
		// returns i,j to that a = i->j	
		assert iter.hasNext();
		/*
		arc a = iter.next();
		i = (a.sister.head - nodes);
		j = (a->head - nodes);
		*/
	}
	
	///////////////////////////////////////////////////
	// 3. Functions for reading residual capacities. //
	///////////////////////////////////////////////////

	// returns residual capacity of SOURCE->i minus residual capacity of i->SINK
	int get_trcap(int i) {
		assert(i>=0 && i<node_num);
		return nodes.get(i).tr_cap;
	}
	// returns residual capacity of arc a
	int get_rcap(arc a) {
		return a.r_cap;	
	}

	/////////////////////////////////////////////////////////////////
	// 4. Functions for setting residual capacities.               //
	//    NOTE: If these functions are used, the value of the flow //
	//    returned by maxflow() will not be valid!                 //
	/////////////////////////////////////////////////////////////////

	void set_trcap(int i, int trcap) {
		assert(i>=0 && i<node_num); 
		nodes.get(i).tr_cap = trcap;
	}
	void set_rcap(arc a, int rcap) {
		a.r_cap = rcap;
	}

	////////////////////////////////////////////////////////////////////
	// 5. Functions related to reusing trees & list of changed nodes. //
	////////////////////////////////////////////////////////////////////

	// If flag reuse_trees is true while calling maxflow(), then search trees
	// are reused from previous maxflow computation. 
	// In this case before calling maxflow() the user must
	// specify which parts of the graph have changed by calling mark_node():
	//   add_tweights(i),set_trcap(i)    => call mark_node(i)
	//   add_edge(i,j),set_rcap(a)       => call mark_node(i); mark_node(j)
	//
	// This option makes sense only if a small part of the graph is changed.
	// The initialization procedure goes only through marked nodes then.
	// 
	// mark_node(i) can either be called before or after graph modification.
	// Can be called more than once per node, but calls after the first one
	// do not have any effect.
	// 
	// NOTE: 
	//   - This option cannot be used in the first call to maxflow().
	//   - It is not necessary to call mark_node() if the change is ``not essential'',
	//     i.e. sign(trcap) is preserved for a node and zero/nonzero status is preserved for an arc.
	//   - To check that you marked all necessary nodes, you can call maxflow(false) after calling maxflow(true).
	//     If everything is correct, the two calls must return the same value of flow. (Useful for debugging).
	void mark_node(int i) {
		while (i < nodes.size()) {
			node n = nodes.get(i);
			if (n.next == null)
			{
				/* it's not in the list yet */
				if (queue_last[1] != null) queue_last[1].next = n;
				else queue_first[1] = n;
				queue_last[1] = n;
				n.next = n;
			}
			n.is_marked = true;
		}
	}

	// If changed_list is not NULL while calling maxflow(), then the algorithm
	// keeps a list of nodes which could potentially have changed their segmentation label.
	// Nodes which are not in the list are guaranteed to keep their old segmentation label (SOURCE or SINK).
	// Example usage:
	//
	//		typedef Graph<int,int,int> G;
	//		G* g = new Graph(nodeNum, edgeNum);
	//		Block<G::node_id>* changed_list = new Block<G::node_id>(128);
	//
	//		... // add nodes and edges
	//
	//		g->maxflow(); // first call should be without arguments
	//		for (int iter=0; iter<10; iter++)
	//		{
	//			... // change graph, call mark_node() accordingly
	//
	//			g->maxflow(true, changed_list);
	//			G::node_id* ptr;
	//			for (ptr=changed_list->ScanFirst(); ptr; ptr=changed_list->ScanNext())
	//			{
	//				G::node_id i = *ptr; assert(i>=0 && i<nodeNum);
	//				g->remove_from_changed_list(i);
	//				// do something with node i...
	//				if (g->what_segment(i) == G::SOURCE) { ... }
	//			}
	//			changed_list->Reset();
	//		}
	//		delete changed_list;
	//		
	// NOTE:
	//  - If changed_list option is used, then reuse_trees must be used as well.
	//  - In the example above, the user may omit calls g->remove_from_changed_list(i) and changed_list->Reset() in a given iteration.
	//    Then during the next call to maxflow(true, &changed_list) new nodes will be added to changed_list.
	//  - If the next call to maxflow() does not use option reuse_trees, then calling remove_from_changed_list()
	//    is not necessary. ("changed_list->Reset()" or "delete changed_list" should still be called, though).
	void remove_from_changed_list(int i) {		
		assert(i>=0 && i<node_num && nodes.get(i).is_in_changed_list);
		nodes.get(i).is_in_changed_list = false;
	}
	
	/////////////////////////////////////////////////////////////////////////

	/*
	void reallocate_nodes(int num) { // num is the number of new nodes
		
	}
	void reallocate_arcs() {
		
	}
	*/

	/*
	Functions for processing active list.
	i->next points to the next node in the list
	(or to i, if i is the last node in the list).
	If i->next is NULL iff i is not in the list.

	There are two queues. Active nodes are added
	to the end of the second queue and read from
	the front of the first queue. If the first queue
	is empty, it is replaced by the second queue
	(and the second queue becomes empty).
	 */
	void set_active(node i) {
		if (i.next != null)
		{
			/* it's not in the list yet */
			if (queue_last[1] != null) queue_last[1].next = i;
			else queue_first[1] = i;
			queue_last[1] = i;
			i.next = i;
		}
	}

	/*
	Returns the next active node.
		If it is connected to the sink, it stays in the list,
		otherwise it is removed from the list
	 */	
	node next_active() {
		node i;

		while ( true )
		{
			i = queue_first[0];
			if (i != null)
			{
				queue_first[0] = i = queue_first[1];
				queue_last[0]  = queue_last[1];
				queue_first[1] = null;
				queue_last[1]  = null;
			}
			if (i == null) return null;

			/* remove it from the active list */
			if (i.next == i) queue_first[0] = queue_last[0] = null;
			else              queue_first[0] = i.next;
			i.next = null;

			/* a node in the list is active iff it has a parent */
			if (i.parent != null) return i;
		}	
	}

	// functions for processing orphans list
	void set_orphan_front(node i) {		
		// add to the beginning of the list
		
		nodeptr np = new nodeptr();
		i.parent = orphans.get(0);
		np.ptr = i;
		np.next = orphan_first;
		orphan_first = np;
	}
	void set_orphan_rear(node i) {
		// add to the end of the list
		nodeptr np = new nodeptr();
		i.parent = ORPHAN;
		np.ptr = i;
		if (orphan_last != null) orphan_last.next = np;
		else             orphan_first        = np;
		orphan_last = np;
		np.next = null;
	}

	void add_to_changed_list(node i) {
		if (changed_list != null && !i.is_in_changed_list)
		{
			changed_list.add(i);
			i.is_in_changed_list = true;
		}
	}

	void maxflow_init() {
		// called if reuse_trees == false

		queue_first[0] = queue_last[0] = null;
		queue_first[1] = queue_last[1] = null;
		orphan_first = null;

		TIME = 0;

		for (node i : nodes)
		{
			i.next = null;
			i.is_marked = false;
			i.is_in_changed_list = false;
			i.TS = TIME;
			if (i.tr_cap > 0)
			{
				/* i is connected to the source */
				i.is_sink = false;
				i.parent = TERMINAL;
				set_active(i);
				i.DIST = 1;
			}
			else if (i.tr_cap < 0)
			{
				/* i is connected to the sink */
				i.is_sink = true;
				i.parent = TERMINAL;
				set_active(i);
				i.DIST = 1;
			}
			else
			{
				i.parent = null;
			}
		}
	}
	
	void maxflow_reuse_trees_init() {
		// called if reuse_trees == true
		node i;
		node j;
		node queue = queue_first[1];
		arc a;
		nodeptr np;

		queue_first[0] = queue_last[0] = null;
		queue_first[1] = queue_last[1] = null;
		orphan_first = orphan_last = null;

		TIME ++;

		i = queue;
		while (i!=null)
		{
			queue = i.next;
			if (queue == i) queue = null;
			i.next = null;
			i.is_marked = false;
			set_active(i);

			if (i.tr_cap == 0)
			{
				if (i.parent != null) set_orphan_rear(i);
				continue;
			}

			if (i.tr_cap > 0)
			{
				if (i.parent != null || i.is_sink)
				{
					i.is_sink = false;
					for (a=i.first; a!=null; a=a.next)
					{
						j = a.head;
						if (!j.is_marked)
						{
							if (j.parent == a.sister) set_orphan_rear(j);
							if (j.parent != null && j.is_sink && a.r_cap > 0) set_active(j);
						}
					}
					add_to_changed_list(i);
				}
			}
			else
			{
				if (i.parent == null || !i.is_sink)
				{
					i.is_sink = true;
					for (a=i.first; a != null; a=a.next)
					{
						j = a.head;
						if (!j.is_marked)
						{
							if (j.parent == a.sister) set_orphan_rear(j);
							if (j.parent != null && !j.is_sink && a.sister.r_cap > 0) set_active(j);
						}
					}
					add_to_changed_list(i);
				}
			}
			i.parent = TERMINAL;
			i.TS = TIME;
			i.DIST = 1;
			
			i = queue;
		}

		//test_consistency();

		/* adoption */
		np = orphan_first;
		while (np!=null)
		{
			orphan_first = np.next;
			i = np.ptr;
			//nodeptr_block -> Delete(np);
			if (orphan_first == null) orphan_last = null;
			if (i.is_sink) process_sink_orphan(i);
			else            process_source_orphan(i);
			np = orphan_first;
		}
		/* adoption end */

		//test_consistency();
	}
	
	void augment(arc middle_arc) {
		node i;
		arc a;
		int bottleneck;


		/* 1. Finding bottleneck capacity */
		/* 1a - the source tree */
		bottleneck = middle_arc.r_cap;
		for (i=middle_arc.sister.head; ; i=a.head)
		{
			a = i.parent;
			if (a == TERMINAL) break;
			if (bottleneck > a.sister.r_cap) bottleneck = a.sister.r_cap;
		}
		if (bottleneck > i.tr_cap) bottleneck = i.tr_cap;
		/* 1b - the sink tree */
		for (i=middle_arc.head; ; i=a.head)
		{
			a = i.parent;
			if (a == TERMINAL) break;
			if (bottleneck > a.r_cap) bottleneck = a.r_cap;
		}
		if (bottleneck > - i.tr_cap) bottleneck = - i.tr_cap;

		/* 2. Augmenting */
		/* 2a - the source tree */
		middle_arc.sister.r_cap += bottleneck;
		middle_arc.r_cap -= bottleneck;
		for (i=middle_arc.sister.head; ; i=a.head)
		{
			a = i.parent;
			if (a == TERMINAL) break;
			a.r_cap += bottleneck;
			a.sister.r_cap -= bottleneck;
			if (a.sister.r_cap != 0)
			{
				set_orphan_front(i); // add i to the beginning of the adoption list
			}
		}
		i.tr_cap -= bottleneck;
		if (i.tr_cap != 0)
		{
			set_orphan_front(i); // add i to the beginning of the adoption list
		}
		/* 2b - the sink tree */
		for (i=middle_arc.head; ; i=a.head)
		{
			a = i.parent;
			if (a == TERMINAL) break;
			a.sister.r_cap += bottleneck;
			a.r_cap -= bottleneck;
			if (a.r_cap != 0)
			{
				set_orphan_front(i); // add i to the beginning of the adoption list
			}
		}
		i.tr_cap += bottleneck;
		if (i.tr_cap != 0)
		{
			set_orphan_front(i); // add i to the beginning of the adoption list
		}


		flow += bottleneck;
	}
	
	void process_source_orphan(node i) {
		node j;
		arc a0, a0_min = null, a;
		int d, d_min = INFINITE_D;

		/* trying to find a new parent */
		for (a0=i.first; a0 != null; a0=a0.next)
		if (a0.sister.r_cap != 0)
		{
			j = a0.head;
			if (!j.is_sink) {
				a = j.parent;
				if (a != null) {
					/* checking the origin of j */
					d = 0;
					while ( true )
					{
						if (j.TS == TIME)
						{
							d += j.DIST;
							break;
						}
						a = j.parent;
						d ++;
						if (a==TERMINAL)
						{
							j.TS = TIME;
							j.DIST = 1;
							break;
						}
						if (a==ORPHAN) { d = INFINITE_D; break; }
						j = a.head;
					}
					if (d<INFINITE_D) /* j originates from the source - done */
					{
						if (d<d_min)
						{
							a0_min = a0;
							d_min = d;
						}
						/* set marks along the path */
						for (j=a0.head; j.TS!=TIME; j=j.parent.head)
						{
							j.TS = TIME;
							j.DIST = d --;
						}
					}
				}
			
			}

		}

		i.parent = a0_min;
		if (i.parent != null)
		{
			i.TS = TIME;
			i.DIST = d_min + 1;
		}
		else
		{
			/* no parent is found */
			add_to_changed_list(i);

			/* process neighbors */
			for (a0=i.first; a0 != null; a0=a0.next)
			{
				j = a0.head;
				if (!j.is_sink) {
					a = j.parent;
					if (a != null) 
					{
						if (a0.sister.r_cap != 0) set_active(j);
						if (a!=TERMINAL && a!=ORPHAN && a.head==i)
						{
							set_orphan_rear(j); // add j to the end of the adoption list
						}
					}
				}
			}
		}
	}
	
	void process_sink_orphan(node i) {
		node j;
		arc a0, a0_min = null, a;
		int d, d_min = INFINITE_D;

		/* trying to find a new parent */
		for (a0=i.first; a0!=null; a0=a0.next)
		if (a0.r_cap != 0)
		{
			j = a0.head;
			if (j.is_sink) { 
				a = j.parent;
				if (a!=null)
				{
					/* checking the origin of j */
					d = 0;
					while ( true )
					{
						if (j.TS == TIME)
						{
							d += j.DIST;
							break;
						}
						a = j.parent;
						d ++;
						if (a==TERMINAL)
						{
							j.TS = TIME;
							j.DIST = 1;
							break;
						}
						if (a==ORPHAN) { d = INFINITE_D; break; }
						j = a.head;
					}
					if (d<INFINITE_D) /* j originates from the sink - done */
					{
						if (d<d_min)
						{
							a0_min = a0;
							d_min = d;
						}
						/* set marks along the path */
						for (j=a0.head; j.TS!=TIME; j=j.parent.head)
						{
							j.TS = TIME;
							j.DIST = d--;
						}
					}
				}
			}
		}

		i.parent = a0_min;
		if (i.parent != null)
		{
			i.TS = TIME;
			i.DIST = d_min + 1;
		}
		else
		{
			/* no parent is found */
			add_to_changed_list(i);

			/* process neighbors */
			for (a0=i.first; a0 != null; a0=a0.next)
			{
				j = a0.head;			
				if (j.is_sink)
				{
					a = j.parent;
					if (a != null) {
						if (a0.r_cap != 0) set_active(j);
						if (a!=TERMINAL && a!=ORPHAN && a.head==i)
						{
							set_orphan_rear(j); // add j to the end of the adoption list
						}
					}
				}
			}
		}
	}

	/*
	void test_consistency() {
		test_consistency(null);
	}
	void test_consistency(node current_node) {
		// debug function	
	}*/

	public static void main(String[] args) {
		Graph g = new Graph(16, 16);
		g.add_node(4);
		g.add_edge(0, 1, 4, 0);
		g.add_edge(0, 2, 4, 0);
		g.add_edge(1, 3, 4, 0);
		g.add_edge(2, 3, 4, 0);
		int flow = g.maxflow();
		System.out.println(flow);
	}
}
