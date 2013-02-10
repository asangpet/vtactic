package ak.vtactic.math.flow;

import java.util.List;

import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Element;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import com.tinkerpop.pipes.PipeFunction;
import com.tinkerpop.pipes.branch.LoopPipe.LoopBundle;

public class Maxflow {
	
	static String edgeType = "f";
	static String flowTag = "flow";

	public void run() {
		TinkerGraph g = new TinkerGraph();
		Vertex source = g.addVertex("source");
		final Vertex sink = g.addVertex("sink");
		
		Vertex a = g.addVertex("a");
		Vertex b = g.addVertex("b");
		Vertex ab = g.addVertex("ab");

		g.addEdge("a", source, a, edgeType).setProperty(flowTag, 100);
		g.addEdge("b", source, b, edgeType).setProperty(flowTag, 100);		
		
		g.addEdge("a-sink", a, sink, edgeType).setProperty(flowTag, 82);
		g.addEdge("b-sink", b, sink, edgeType).setProperty(flowTag, 82);		

		g.addEdge("ab-sink", ab, sink, edgeType).setProperty(flowTag, 18);		
		g.addEdge("a-ab", a, ab, edgeType).setProperty(flowTag, 18);
		g.addEdge("b-ab", b, ab, edgeType).setProperty(flowTag, 18);
		
		GremlinPipeline<Vertex, Integer> pipe = new GremlinPipeline<>();
		pipe.start(source).outE(edgeType).inV().loop(2, new PipeFunction<LoopBundle<Vertex>, Boolean>() {
			@Override
			public Boolean compute(LoopBundle<Vertex> it) {
				return (!it.getObject().equals(sink));
			};
		}).path(new PipeFunction<Element, Edge>() {
			@Override
			public Edge compute(Element e) {
				if (e instanceof Edge) {
					Edge edge = (Edge)e;
					return edge;
				}
				return null;
			};			
		})
		.gather(new PipeFunction<List, Integer>() {
			int maxFlow = 0;
			
			@Override
			public Integer compute(List list) {
				for (Object path : list) {
					int flow = Integer.MAX_VALUE;
					System.out.println(path+" "+path.getClass());
					List<Edge> edgeList = (List) path;
					for (Edge edge : edgeList) {
						if (edge != null) {
							int flowProp = (Integer)(edge.getProperty(flowTag));
							if (flowProp < flow && flowProp > 0) {
								flow = flowProp;
							}
						}
					}
					if (flow < Integer.MAX_VALUE) {
						maxFlow += flow;
						for (Object edge : edgeList) {
							if (edge != null) {
								int flowProp = (Integer)(((Edge)edge).getProperty(flowTag));
								((Edge)edge).setProperty(flowTag, flowProp - flow);
								System.out.println("   "+edge+" "+(flowProp - flow));
							}
						}						
					}
				}
				return maxFlow;
			};
		});
		
		List<Integer> l = pipe.toList();
		System.out.println(l);
	}
	
	public static void main(String[] args) {
		new Maxflow().run();
	}
}
