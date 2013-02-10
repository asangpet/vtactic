package ak.vtactic.math.flow.cppport;

import java.util.LinkedList;

/**
 * 
 * @author bastien Jacquet
 * 
 *         Implements a graph to cut Very complicated to program and debug It
 *         worked for me, but it may still have bugs See the paper for more
 *         details Yuri Boykov, Vladimir Kolmogorov: An Experimental Comparison
 *         of Min-Cut/Max-Flow Algorithms for Energy Minimization in Vision.
 *         IEEE Trans. Pattern Anal. Mach. Intell. 26(9): 1124-1137 (2004)
 */
public class AltMaxFlow {
	class Node {
		int index;
		int prevEdgeIndex; /* Edge to previous node of the path */
		double maxCapToHere = 0;
		// V2
		int dist;

	}

	class Edge {
		int initial_vertex; /* initial vertex of this edge */
		int terminal_vertex; /* terminal vertex of this edge */
		double capacity; /* capacity */
		double flow;
		int invEdgeIndex;
	}

	boolean debug = true;
	/** We assume that an edge with eps capacity is saturated */
	private final double eps = 1e-3;
	private final int nbNode, nbEdges;
	// private int sourceNode=0,sinkNode=1; /* start node, terminate node */
	private final int w, h;
	private final Node node[];
	private final Edge edge[];
	/**
	 * startingEdge[nodeIndex] is an array of edges indexes that starts from
	 * node nodeIndex
	 */
	private final int[][] startingEdge;

	/** Retrieve the node index for the (x,y) pixel. Inlined */
	private int indice(int x, int y) {
		return x * h + y + 2;
	}

	/**
	 * Retrieve the starting edge index in the <b>startingEdge</b> array to the
	 * (x,y) pixel from source or sink. Inlined
	 */
	private int indicePart(int x, int y) {
		return x * h + y;
	}

	/**
	 * Construct an width x height Graph, with C-8 connectivity It creates all
	 * the edges, with 0 capacity
	 * 
	 * @param width
	 * @param height
	 */
	AltMaxFlow(int width, int height) {
		w = width;
		h = height;
		// neighborhood=8;
		// int[][] voisins=new
		// int[][]{{+1,0},{+1,-1},{+1,+1},{0,+1},{0,-1},{-1,+1},{-1,0},{-1,-1}};
		int[][] voisinsEdgeACreer = new int[][] { { +1, 0 }, { +1, -1 },
				{ 0, -1 }, { -1, -1 } };
		this.nbNode = w * h + 2;
		this.nbEdges = w * h * 4 + (h - 2) * (w - 2) * 8 + 2 * 5 * (h + w - 4)
				+ 4 * 3;
		node = new Node[this.nbNode];
		edge = new Edge[this.nbEdges];
		startingEdge = new int[this.nbNode][];
		int[] curNbVoisins = new int[this.nbNode];
		int vx, vy, x, y, v, curEdge = 0, i1, i2;
		// Create nodes
		node[0] = new Node();
		node[0].index = 0;
		node[1] = new Node();
		node[1].index = 1;
		for (x = 0; x < w; x++) {
			for (y = 0; y < h; y++) {
				i1 = x * h + y + 2;
				node[i1] = new Node();
				node[i1].index = i1;
			}
		}
		// Create array of starting Edges
		startingEdge[0] = new int[this.nbNode - 2];
		startingEdge[1] = new int[this.nbNode - 2];
		if ((w == 1) || (h == 1)) { // Cas h==1 or w==1
			for (x = 0; x < w; x++) {
				for (y = 0; y < h; y++) {
					if (x * (x + 1 - w) == 0 && y * (y + 1 - h) == 0) {// Coins
						startingEdge[(x * h + y + 2)] = new int[1 + 1];
					} else if (x * (x + 1 - w) == 0 || y * (y + 1 - h) == 0) {// Edges
						startingEdge[(x * h + y + 2)] = new int[2 + 1];
					}
				}
			}
		} else {
			for (x = 0; x < w; x++) {
				for (y = 0; y < h; y++) {
					if (x * (x + 1 - w) == 0 && y * (y + 1 - h) == 0) {// Coins
						startingEdge[(x * h + y + 2)] = new int[3 + 2];
					} else if (x * (x + 1 - w) == 0 || y * (y + 1 - h) == 0) {// Edges
						startingEdge[(x * h + y + 2)] = new int[5 + 2];
					} else {// Milieu
						startingEdge[(x * h + y + 2)] = new int[8 + 2];
					}
				}
			}
		}
		// T-links : Sink Edges
		for (x = 0; x < w; x++) {
			for (y = 0; y < h; y++) {
				i1 = x * h + y + 2;
				i2 = 1;
				edge[curEdge] = new Edge();
				edge[curEdge].initial_vertex = i1;
				edge[curEdge].terminal_vertex = i2;
				edge[curEdge].invEdgeIndex = curEdge + 1;
				startingEdge[i1][curNbVoisins[i1]++] = curEdge;
				curEdge++;

				edge[curEdge] = new Edge();
				edge[curEdge].initial_vertex = i2;
				edge[curEdge].terminal_vertex = i1;
				edge[curEdge].invEdgeIndex = curEdge - 1;
				startingEdge[i2][(x * h + y)] = curEdge;
				curEdge++;
			}
		}
		// N-Links
		for (x = 0; x < w; x++) {
			for (y = 0; y < h; y++) {
				i1 = x * h + y + 2;
				for (v = 0; v < voisinsEdgeACreer.length; v++) {
					vx = x + voisinsEdgeACreer[v][0];
					vy = y + voisinsEdgeACreer[v][1];
					if (vx < 0 || vx >= w || vy < 0 || vy >= h)
						continue;
					i2 = vx * h + vy + 2;
					edge[curEdge] = new Edge();
					edge[curEdge].initial_vertex = i1;
					edge[curEdge].terminal_vertex = i2;
					edge[curEdge].invEdgeIndex = curEdge + 1;
					startingEdge[i1][curNbVoisins[i1]++] = curEdge;
					curEdge++;

					edge[curEdge] = new Edge();
					edge[curEdge].initial_vertex = i2;
					edge[curEdge].terminal_vertex = i1;
					edge[curEdge].invEdgeIndex = curEdge - 1;
					startingEdge[i2][curNbVoisins[i2]++] = curEdge;
					curEdge++;
				}
			}
		}
		// T-links : Source Edges
		for (x = 0; x < w; x++) {
			for (y = 0; y < h; y++) {
				i1 = 0;
				i2 = x * h + y + 2;
				edge[curEdge] = new Edge();
				edge[curEdge].initial_vertex = i1;
				edge[curEdge].terminal_vertex = i2;
				edge[curEdge].invEdgeIndex = curEdge + 1;
				startingEdge[i1][(x * h + y)] = curEdge;
				curEdge++;

				edge[curEdge] = new Edge();
				edge[curEdge].initial_vertex = i2;
				edge[curEdge].terminal_vertex = i1;
				edge[curEdge].invEdgeIndex = curEdge - 1;
				startingEdge[i2][curNbVoisins[i2]++] = curEdge;
				// TODO:Cannot return to source
				curEdge++;
			}
		}
		if (curEdge != nbEdges)
			System.out.println("Pb creation edges");
	}

	/**
	 * retrieve the edge between (x1,y1) and (x2,y2)
	 * 
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 * @return the matching edge if exists, null otherwise
	 */
	private Edge getEdge(int x1, int y1, int x2, int y2) {
		int i1 = x1 * h + y1 + 2, i2 = x2 * h + y2 + 2;
		for (int v = 0; v < startingEdge[i1].length; v++) {
			if (edge[startingEdge[i1][v]].terminal_vertex == i2)
				return edge[startingEdge[i1][v]];
		}
		return null;
	}

	/**
	 * set the edge between (x1,y1) and (x2,y2) to the capacity w
	 * 
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 * @param w
	 *            capacity
	 */
	public void setInternWeight(int x1, int y1, int x2, int y2, double w) {
		int i1 = x1 * h + y1 + 2, i2 = x2 * h + y2 + 2;
		for (int v = 0; v < startingEdge[i1].length; v++) {
			if (edge[startingEdge[i1][v]].terminal_vertex == i2) {
				edge[startingEdge[i1][v]].capacity = w;
				edge[edge[startingEdge[i1][v]].invEdgeIndex].capacity = w;
			}
		}
	}

	/**
	 * set the edge between Source and (x1,y1) to the capacity w
	 * 
	 * @param x1
	 * @param y1
	 * @param w
	 *            capacity
	 */
	public void setSourceWeight(int x1, int y1, double w) {
		int i1 = 0, i2 = x1 * h + y1;
		edge[startingEdge[i1][i2]].capacity = w;
		edge[edge[startingEdge[i1][i2]].invEdgeIndex].capacity = w;
	}

	/**
	 * set the edge between Sink and (x1,y1) to the capacity w
	 * 
	 * @param x1
	 * @param y1
	 * @param w
	 *            capacity
	 */
	public void setSinkWeight(int x1, int y1, double w) {
		int i1 = 1, i2 = x1 * h + y1;
		edge[startingEdge[i1][i2]].capacity = w;
		edge[edge[startingEdge[i1][i2]].invEdgeIndex].capacity = w;
	}

	/**
	 * set the edge between Source and (x1,y1) to the capacity wSource set the
	 * edge between Sink and (x1,y1) to the capacity wSink
	 * 
	 * @param x1
	 * @param y1
	 * @param wSource
	 *            capacity to the source
	 * @param wSink
	 *            capacity to the sink
	 */
	public void setTWeight(int x1, int y1, double wSource, double wSink) {
		int i2 = x1 * h + y1;
		edge[startingEdge[0][i2]].capacity = wSource;
		edge[edge[startingEdge[0][i2]].invEdgeIndex].capacity = -wSource;
		edge[startingEdge[1][i2]].capacity = -wSink;
		edge[edge[startingEdge[1][i2]].invEdgeIndex].capacity = wSink;
	}

	/**
	 * return the part of the graph where (x1,y1) is
	 * 
	 * @param x1
	 * @param y1
	 * @return 0 if connected to Source, 1 if connected to Sink 2 if isolated
	 */
	public int linkedTo(int x1, int y1) {
		int i2 = x1 * h + y1;
		if (edge[startingEdge[0][i2]].capacity - edge[startingEdge[0][i2]].flow > eps)
			return 0;
		else if (edge[edge[startingEdge[1][i2]].invEdgeIndex].capacity
				- edge[edge[startingEdge[1][i2]].invEdgeIndex].flow > eps) {
			return 1;
		} else {
			return 2;
		}
	}

	/**
	 * Calculate current Flow
	 * 
	 * @return current Flow
	 */
	public double getFlow() {
		double f = 0;
		int x, y;
		for (x = 0; x < w; x++) {
			for (y = 0; y < h; y++) {
				f += edge[edge[startingEdge[1][(x * h + y)]].invEdgeIndex].flow;
			}
		}
		return f;
	}

	/**
	 * Reset the flow in the graph
	 */
	private void resetFlow() {
		for (int i = 0; i < edge.length; i++)
			edge[i].flow = 0;
	}

	/**
	 * Based on Boykov & Kolmogorov Implementation By Bastien Jacquet see
	 * "An experimental Comparison of Min-Cut/Max-Flow Algorithms for Energy Minimization in Vision"
	 * Boykov & Kolmogorov [2004]
	 */
	LinkedList<Integer> orphan;
	boolean[] isInS;
	LinkedList<Integer> active;
	boolean[] isInA;

	public void doCut() {
		resetFlow();
		isInS = new boolean[nbNode];
		isInS[0] = true;
		active = new LinkedList<Integer>();
		active.add(0);
		isInA = new boolean[nbNode];
		isInA[0] = true;
		orphan = new LinkedList<Integer>();

		for (int i = 0; i < node.length; i++)
			node[i].prevEdgeIndex = -1;
		while (true) {
			int lastEdge = growthStage();
			if (lastEdge == -1)
				return;
			augmentationStage(lastEdge);
			adoptionStage();
		}

	}

	/**
	 * Phase 1 : growth Stage Build the entire tree by setting prevEdgeIndex for
	 * each node
	 * 
	 * @return the last edge index of the path to Sink, -1 if no path
	 */
	private int growthStage() {
		// if(debug) System.out.println("growthStage osize:"+orphan.size());
		if (isInS[1])
			return node[1].prevEdgeIndex;
		int curNodeIndex, curStartingEdgeIndex;
		Edge curEdge;
		while (!active.isEmpty()) {
			curNodeIndex = active.peek();
			// Speed up we just flag an active node for deletion
			if (!isInA[curNodeIndex]) {
				active.poll();
				continue;
			}

			for (curStartingEdgeIndex = 0; curStartingEdgeIndex < startingEdge[curNodeIndex].length; curStartingEdgeIndex++) {
				curEdge = edge[startingEdge[curNodeIndex][curStartingEdgeIndex]];
				if (curEdge.capacity - curEdge.flow <= eps)
					continue;
				if (!isInS[curEdge.terminal_vertex]) { // if is in T
					active.add(curEdge.terminal_vertex);
					isInA[curEdge.terminal_vertex] = true;
					isInS[curEdge.terminal_vertex] = true;
					node[curEdge.terminal_vertex].prevEdgeIndex = startingEdge[curNodeIndex][curStartingEdgeIndex];
				}
				if (curEdge.terminal_vertex == 1) {
					return startingEdge[curNodeIndex][curStartingEdgeIndex];
				}
			}
			active.poll();
			isInA[curNodeIndex] = false;
		}
		return -1;
	}

	/**
	 * Phase 2 : augmentation Stage saturate the path starting with the edge
	 * lastEdgeIndex
	 * 
	 * @param lastEdgeIndex
	 */
	private void augmentationStage(int lastEdgeIndex) {
		double bottleNeckCap;
		try {
			bottleNeckCap = edge[lastEdgeIndex].capacity
					- edge[lastEdgeIndex].flow;
			for (int curNodeIndex = edge[lastEdgeIndex].initial_vertex; curNodeIndex != 0; curNodeIndex = edge[node[curNodeIndex].prevEdgeIndex].initial_vertex) {
				if (bottleNeckCap > edge[node[curNodeIndex].prevEdgeIndex].capacity
						- edge[node[curNodeIndex].prevEdgeIndex].flow) {
					bottleNeckCap = edge[node[curNodeIndex].prevEdgeIndex].capacity
							- edge[node[curNodeIndex].prevEdgeIndex].flow;
				}
			}
		} catch (Exception e) {
			// TODO : This should never happens
			e.printStackTrace();
			// if(node[edge[lastEdgeIndex].initial_vertex].prevEdgeIndex==-1)
			isInS = new boolean[nbNode];
			isInS[0] = true;
			active = new LinkedList<Integer>();
			active.add(0);
			isInA = new boolean[nbNode];
			isInA[0] = true;
			orphan = new LinkedList<Integer>();
			return;
		}
		int prevEdgeIndex = -1;
		for (int curEdgeIndex = lastEdgeIndex; curEdgeIndex != -1; curEdgeIndex = prevEdgeIndex) {
			edge[curEdgeIndex].flow += bottleNeckCap;
			edge[edge[curEdgeIndex].invEdgeIndex].flow -= bottleNeckCap;
			prevEdgeIndex = node[edge[curEdgeIndex].initial_vertex].prevEdgeIndex;
			if (edge[curEdgeIndex].capacity - edge[curEdgeIndex].flow <= eps) {
				node[edge[curEdgeIndex].terminal_vertex].prevEdgeIndex = -1;
				orphan.addFirst(edge[curEdgeIndex].terminal_vertex);
			}
		}
	}

	private int getRootOf(int nodeIndex) {
		int curRootNodeIndex = nodeIndex;
		while (node[curRootNodeIndex].prevEdgeIndex > 0) {
			curRootNodeIndex = edge[node[curRootNodeIndex].prevEdgeIndex].initial_vertex;
		}
		return curRootNodeIndex;
	}

	/**
	 * Phase 3 : adoption Stage Repair the search tree by processing orphans
	 */
	private void adoptionStage() {
		int curNodeIndex, curStartingEdgeIndex;
		Edge curEdge;
		while (!orphan.isEmpty()) {
			curNodeIndex = orphan.poll();
			boolean hasFindParent = false;
			// searching parent
			for (curStartingEdgeIndex = 0; !hasFindParent
					&& curStartingEdgeIndex < startingEdge[curNodeIndex].length; curStartingEdgeIndex++) {
				// For each incoming edge
				curEdge = edge[edge[startingEdge[curNodeIndex][curStartingEdgeIndex]].invEdgeIndex];
				if (curEdge.capacity - curEdge.flow <= eps)
					continue;
				if (!isInS[curEdge.initial_vertex])
					continue;
				int curRootNodeIndex = curEdge.initial_vertex;
				while (node[curRootNodeIndex].prevEdgeIndex > 0) {
					curRootNodeIndex = edge[node[curRootNodeIndex].prevEdgeIndex].initial_vertex;
				}
				if (curRootNodeIndex != 0)
					continue;
				hasFindParent = true;
				node[curNodeIndex].prevEdgeIndex = edge[startingEdge[curNodeIndex][curStartingEdgeIndex]].invEdgeIndex;
				break;
			}
			// no parents possible
			if (!hasFindParent) {
				isInS[curNodeIndex] = false;
				// Speed up we just flag an active node for deletion
				isInA[curNodeIndex] = false;
				for (curStartingEdgeIndex = 0; curStartingEdgeIndex < startingEdge[curNodeIndex].length; curStartingEdgeIndex++) {
					curEdge = edge[startingEdge[curNodeIndex][curStartingEdgeIndex]];
					// Children becomes orphans
					if (node[curEdge.terminal_vertex].prevEdgeIndex == startingEdge[curNodeIndex][curStartingEdgeIndex]) {
						node[curEdge.terminal_vertex].prevEdgeIndex = -1;
						orphan.addLast(curEdge.terminal_vertex);
					}
					if (!isInS[curEdge.terminal_vertex])
						continue;
					curEdge = edge[curEdge.invEdgeIndex];
					// Add in active if not already here
					if (curEdge.capacity - curEdge.flow > eps
							&& !isInA[curEdge.initial_vertex]) {
						active.add(curEdge.initial_vertex);
						isInA[curEdge.initial_vertex] = true;
					}
				}
			}
		}

	}	
}