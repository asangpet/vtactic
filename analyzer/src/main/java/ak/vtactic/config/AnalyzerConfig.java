package ak.vtactic.config;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import ak.vtactic.math.MatlabService;

@Configuration
@ComponentScan("ak.vtactic")
public class AnalyzerConfig {
	
	@Bean
	public ObjectMapper mapper() throws Exception {
    	ObjectMapper mapper = new ObjectMapper();
    	mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    	return mapper;
	}
	
	@Bean
	public MatlabService matlab() {
		MatlabService matlab = new MatlabService();
		return matlab;
	}

}
