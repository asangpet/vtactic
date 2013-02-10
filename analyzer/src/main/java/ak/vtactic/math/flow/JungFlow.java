package ak.vtactic.math.flow;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections15.Factory;
import org.apache.commons.collections15.Transformer;

import edu.uci.ics.jung.algorithms.flows.EdmondsKarpMaxFlow;
import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;

public class JungFlow {
	static class Vertex {
		String label;
		public Vertex(String label) {
			this.label = label;
		}
	}
	
	static class Edge {
		public String label;
		public int capacity;
		
		public Edge(String label, int capacity) {
			this.capacity = capacity;
			this.label = label;
		}
		
		public Edge(int capacity) {
			this(null, capacity);
		}
		
		@Override
		public String toString() {
			if (label != null) {
				return label;
			}
			return super.toString();
		}
		
		@Override
		public int hashCode() {
			return 39*capacity+17;
		}
	}
	
	public void run() {
		DirectedGraph<Vertex, Edge> dgraph = new DirectedSparseGraph<>();
		Vertex src = new Vertex("src");
		Vertex sink = new Vertex("sink");
		Vertex a = new Vertex("a");
		Vertex b = new Vertex("b");
		Vertex ab = new Vertex("ab");
		
		
		dgraph.addVertex(src);
		dgraph.addVertex(sink);
		dgraph.addVertex(a);
		dgraph.addVertex(b);
		dgraph.addVertex(ab);
				
		int co = 22; // 33 for 100 deadline
		int nonCo = 100-co;
		
		dgraph.addEdge(new Edge("src-a",95), src, a);
		dgraph.addEdge(new Edge("src-b",95), src, b);
		dgraph.addEdge(new Edge("a-ab",co), a, ab);
		dgraph.addEdge(new Edge("b-ab",co), b, ab);
		
		dgraph.addEdge(new Edge("a-sink",nonCo), a, sink);
		dgraph.addEdge(new Edge("b-sink",nonCo), b, sink);
		dgraph.addEdge(new Edge("ab-sink",co), ab, sink);
		
		Transformer<Edge, Integer> edgeCapacityTransformer = new Transformer<Edge, Integer>() {
			@Override
			public Integer transform(Edge input) {
				return input.capacity;
			}
		};
		
		Factory<Edge> edgeFactory = new Factory<Edge>() {
			@Override
			public Edge create() {
				return new Edge(0);
			}
		};

		HashMap<Edge, Integer> flowMap = new HashMap<Edge,Integer>();
		EdmondsKarpMaxFlow maxFlow = new EdmondsKarpMaxFlow(dgraph, src, sink, 
	    		edgeCapacityTransformer, flowMap,
	    		edgeFactory);
		
		maxFlow.evaluate();
		for (Map.Entry<Edge, Integer> edge : flowMap.entrySet()) {
			if (edge.getKey().label != null) {
				System.out.println(edge.getKey()+" = "+edge.getValue());
			}
		}
	}
	
	public static void main(String[] args) {
		JungFlow jf = new JungFlow();
		jf.run();
	}
}
