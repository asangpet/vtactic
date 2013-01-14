package ak.vtactic.primitives;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import ak.vtactic.analyzer.DependencyTool;
import ak.vtactic.collector.RequestExtractor;
import ak.vtactic.collector.ResponseCollector;
import ak.vtactic.math.DiscreteProbDensity;

@Component
@Scope("prototype")
public class ComponentNode {
	@Autowired
	DependencyTool dependencyTool;
	
	Expression expression;
	DiscreteProbDensity measuredResponse;
	DiscreteProbDensity processingTime;

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
		expression = extractor.getExpression();
		
		Map<String, DiscreteProbDensity> responses = dependencyTool.collectResponse(host, port, start, stop);
		DiscreteProbDensity subSystem = expression.eval(responses);
		measuredResponse = responses.get(ResponseCollector.RESPONSE_KEY);
		processingTime = DiscreteProbDensity.lucyDeconv(measuredResponse, subSystem);
		
		return responses;
	}
	
	public Map<String, DiscreteProbDensity> findModel(String clientHost, double start, double split, double stop) {
		RequestExtractor extractor = dependencyTool.extract(host, port, start, split);
		expression = extractor.getExpression();
		
		Map<String, DiscreteProbDensity> responses = dependencyTool.collectResponse(host, port, start, split);
		DiscreteProbDensity subSystem = expression.eval(responses);
		measuredResponse = responses.get(clientHost);
		processingTime = DiscreteProbDensity.lucyDeconv(measuredResponse, subSystem);
		
		// cross-validate
		return dependencyTool.collectResponse(host, port, split, stop);
	}
	
	public ComponentNode expression(Expression expression) {
		this.expression = expression;
		return this;
	}
	
	public ComponentNode measured(DiscreteProbDensity measureResponse) {
		this.measuredResponse = new DiscreteProbDensity(measureResponse);
		return this;
	}
	
	public DiscreteProbDensity findProcessing(Map<String, DiscreteProbDensity> bind) {
		DiscreteProbDensity subsystem = expression.eval(bind);
		processingTime = DiscreteProbDensity.lucyDeconv(measuredResponse, subsystem);
		return processingTime;
	}
	
	public DiscreteProbDensity estimate(Map<String, DiscreteProbDensity> bind) {
		DiscreteProbDensity subsystem = expression.eval(bind);
		return subsystem.tconv(processingTime);
	}
	
	public DiscreteProbDensity estimate(Map<String, DiscreteProbDensity> bind, DiscreteProbDensity expectedProcTime) {
		DiscreteProbDensity subsystem = expression.eval(bind);
		return subsystem.tconv(expectedProcTime);
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
}
