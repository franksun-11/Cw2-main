package uk.ac.ed.acp.cw2.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class IlpEndpointConfig {

    @Bean
    public String ilpEndpoint() {
        return System.getenv().getOrDefault(
                "ILP_ENDPOINT",
                "https://ilp-rest-2025-bvh6e9hschfagrgy.ukwest-01.azurewebsites.net"
        );
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}