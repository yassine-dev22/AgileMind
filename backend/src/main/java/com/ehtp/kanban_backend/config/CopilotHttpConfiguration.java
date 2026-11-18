package com.ehtp.kanban_backend.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class CopilotHttpConfiguration {
    @Bean("copilotRestClient")
    public RestClient copilotRestClient(
            RestClient.Builder builder,
            @Value("${app.copilot.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.copilot.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.copilot.read-timeout:30s}") Duration readTimeout
    ) {
        URI uri = URI.create(baseUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("RAG_SERVICE_URL must be an HTTP service URL without credentials, query or fragment");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero() || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("RAG timeouts must be positive");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
