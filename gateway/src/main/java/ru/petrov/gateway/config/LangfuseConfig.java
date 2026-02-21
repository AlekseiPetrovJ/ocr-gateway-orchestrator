package ru.petrov.gateway.config;

import com.langfuse.client.LangfuseClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class LangfuseConfig {
    @Value("${app.langfuse.public-key}")
    private String publicKey;
    @Value("${app.langfuse.secret-key}")
    private String secretKey;
    @Value("${app.langfuse.host}")
    private String host;

    @Bean
    public LangfuseClient langfuseClient() {
        return LangfuseClient.builder()
                .url(host)
                .credentials(publicKey, secretKey)
                .build();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}