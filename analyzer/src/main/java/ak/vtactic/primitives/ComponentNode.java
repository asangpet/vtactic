package ak.vtactic.primitives;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import scala.actors.threadpool.Arrays;
import ak.vtactic.analyzer.DependencyTool;
import ak.vtactic.collector.RequestExtractor;
import ak.vtactic.collector.ResponseCollector;
import ak.vtactic.math.DiscreteProbDensity;

import com.google.common.base.Objects;

@Component
@Scope("prototype")
public class ComponentNode {
	@Autowired
	DependencyTool dependencyTool;
	
	Expression expression;
	DiscreteProbDensity measuredResponse;
	DiscreteProbDensity processingTime;
	DiscreteProbDensity interarrival;
	DiscreteProbDensity lag;
	
	Map<String, Integer> dependencies;

	String host;
	int port;
	
	private ComponentNode() {}
	
	public ComponentNode host(String host) {
		this.host = host;
		return this;
	}
	public ComponentNode port(int port) {
		this.port = port;
		return this;
	}
	
	public Map<String, DiscreteProbDensity> findModel(double start, double stop) {
		RequestExtractor extractor = dependencyTool.extract(host, port, start, stop);
		interarrival = extractor.getInterarrival();
		dependencies = extractor.getDependencies();
		expression = extractor.calculateExpression();
		
		Map<String, DiscreteProbDensity> responses = dependencyTool.collectResponse(host, port, start, stop);
		measuredResponse = responses.get(ResponseCollector.RESPONSE_KEY);
		processingTime = findProcessing(measuredResponse, responses);
		
		return responses;
	}
	
	/*
	public Map<String, DiscreteProbDensity> findModel(String clientHost, double start, double split, double stop) {
		RequestExtractor extractor = dependencyTool.extract(host, port, start, split);
		expression = extractor.calculateExpression();
		interarrival = extractor.getInterarrival();
		
		Map<String, DiscreteProbDensity> responses = dependencyTool.collectResponse(host, port, start, split);
		DiscreteProbDensity subSystem = expression.eval(responses);
		measuredResponse = responses.get(clientHost);
		processingTime = DiscreteProbDensity.lucyDeconv(measuredResponse, subSystem);
		
		// cross-validate
		return dependencyTool.collectResponse(host, port, split, stop);
	}
	*/
	
	public ComponentNode expression(Expression expression) {
		this.expression = expression;
		return this;
	}
	
	public ComponentNode measured(DiscreteProbDensity measureResponse) {
		this.measuredResponse = new DiscreteProbDensity(measureResponse);
		return this;
	}
	
	public DiscreteProbDensity findProcessing(DiscreteProbDensity measured, Map<String, DiscreteProbDensity> bind) {
		DiscreteProbDensity subSystem = expression.eval(bind);
		boolean needCombine = false;
		if (subSystem != null) {
			DiscreteProbDensity contended = measured;
			if (expression instanceof Distributed) {
				// check for independent processing
				Distributed ex = (Distributed) expression;
				if (ex.getIndependentWeight() > 0) {
					contended = measured.getUpperDistribution(ex.getIndependentWeight());
					needCombine = true;
				}
			}
			processingTime = DiscreteProbDensity.lucyDeconv(contended, subSystem);
			if (needCombine) {
				Distributed ex = (Distributed) expression;
				processingTime = DiscreteProbDensity.distribute(new double[] {
						ex.getIndependentWeight(), 1-ex.getIndependentWeight()
				}, new DiscreteProbDensity[] {
						measured.getLowerDistribution(ex.getIndependentWeight()),
						processingTime
				});
			}
		} else {
			processingTime = new DiscreteProbDensity(measured);
		}
		return processingTime;
	}
	
	public DiscreteProbDensity subsystem(Map<String, DiscreteProbDensity> bind) {
		return expression.eval(bind);
	}
	
	public DiscreteProbDensity estimate(Map<String, DiscreteProbDensity> bind) {
		return estimate(bind, processingTime);
	}
	
	public DiscreteProbDensity estimate(Map<String, DiscreteProbDensity> bind, DiscreteProbDensity expectedProcTime) {
		DiscreteProbDensity subsystem = expression.eval(bind);
		if (subsystem == null) {
			return expectedProcTime;
		} else {
			DiscreteProbDensity result = null;
			Distributed distExp = (Distributed) expression;
			if (distExp.getIndependentWeight() > 0) {
				result = DiscreteProbDensity.distribute(new double[] {
						distExp.getIndependentWeight(), 
						1.0-distExp.getIndependentWeight()},
					new DiscreteProbDensity[] {
						expectedProcTime.getLowerDistribution(distExp.getIndependentWeight()), 
						subsystem.tconv(expectedProcTime.getUpperDistribution(distExp.getIndependentWeight()))});
			} else {
				result = subsystem.tconv(expectedProcTime);
			}
			// trim lower bound
			result.getPdf()[0] = 0;
			return result.normalize();
		}
	}
	
	public Expression getExpression() {
		return expression;
	}
	
	public DiscreteProbDensity getMeasuredResponse() {
		return measuredResponse;
	}
	
	public DiscreteProbDensity getProcessingTime() {
		return processingTime;
	}
	
	public DiscreteProbDensity getInterarrival() {
		return interarrival;
	}
	
	public Map<String, Integer> getDependencies() {
		return dependencies;
	}
	
	public DiscreteProbDensity getLag() {
		return lag;
	}
	
	public String getHost() {
		return host;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ComponentNode)) {
			return false;
		}
		ComponentNode comp = (ComponentNode)obj;
		return comp.host.equals(host) && comp.port == port;
	}
	
	@Override
	public int hashCode() {
		return host.hashCode();
	}
	
	public void setInterarrival(DiscreteProbDensity interarrival) {
		this.interarrival = interarrival;
	}
	
	@Override
	public String toString() {
		return Objects.toStringHelper(this).add("host", host).add("port", port).toString();
	}
	
	// Find co-arrival of b with respsect to a (e.g how many request as a percentage of a overlap with b)
	// Quick and fix here, we use sort of a simulator. By randomly choose the lag time and processing time (from the distribution)
	// we determine the co-arrival probability using those number instead of actually go and measure the real traffic
	// Primary assumption here is the distribution of these lags are stable (not really true for dependent traffic, but
	// should get us close enough to co-arrival
	public double findExpectedCoarrivalProb(Collection<ComponentNode> others, DiscreteProbDensity rootInterArrival) {
		if (others.isEmpty()) {
			return 0;
		}
		
		int simulatorCount = 10000;
		
		int[] interarrivals = new int[simulatorCount];
		int[] rootarrivals = new int[simulatorCount];	// arrival time
		Map<ComponentNode, int[]> otherArrivals = new HashMap<ComponentNode, int[]>(others.size());
		Map<ComponentNode, int[]> otherProcs = new HashMap<ComponentNode, int[]>(others.size());
		
		int[] nodeArrivals = new int[simulatorCount];
		int[] nodeProc = new int[simulatorCount];
		for (ComponentNode node : others) {
			otherArrivals.put(node, new int[simulatorCount]);
			otherProcs.put(node, new int[simulatorCount]);
		}
		
		// Populate test requests
		for (int i = 0; i < simulatorCount; i++) {
			interarrivals[i] = rootInterArrival.random();
			if (i > 0) {
				rootarrivals[i] = rootarrivals[i-1] + interarrivals[i];
			} else {
				rootarrivals[i] = interarrivals[i];
			}
			
			nodeArrivals[i] = rootarrivals[i] + getLag().random();
			nodeProc[i] = getProcessingTime().random();
			for (Map.Entry<ComponentNode, int[]> otherArrival : otherArrivals.entrySet()) {
				ComponentNode other = otherArrival.getKey();
				otherArrival.getValue()[i] = rootarrivals[i] + other.getLag().random();
				otherProcs.get(other)[i] = other.getProcessingTime().random();
			}						
		}
		
		// Find expected Co-arrival
		double sumCo = 0.0;
		for (Map.Entry<ComponentNode, int[]> otherArrival : otherArrivals.entrySet()) {
			double co = findCo(nodeArrivals, nodeProc, otherArrival.getValue(), otherProcs.get(otherArrival.getKey()));
			sumCo += co;
		}
		return sumCo;
	}
	
	public double findCo(int[] aRequest, int[] aProc, int[] bRequest, int[] bProc) {
		double co = 0;
		Arrays.sort(aRequest);
		Arrays.sort(bRequest);
		int begin = 0;
		for (int ra=0;ra<aRequest.length;ra++) {
			for (int idx=begin;idx<bRequest.length;idx++) {
				if (bRequest[idx] > aRequest[ra] + aProc[ra]) break;
				if (bRequest[idx] + bProc[idx] < aRequest[ra]) begin = idx; // slide starting window
				if ((bRequest[idx] + bProc[idx] >= aRequest[ra] && bRequest[idx] + bProc[idx] <= aRequest[ra]+aProc[ra]) ||
					(bRequest[idx] >= aRequest[ra] && bRequest[idx] <= aRequest[ra]+aProc[ra])) {
					co++; break;
				}
			}
		}
		return co/aRequest.length;
	}
	
}
