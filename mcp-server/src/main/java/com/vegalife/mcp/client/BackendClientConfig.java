package com.vegalife.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wiring for {@link BackendClient}: one {@link RestClient} against the backend with short timeouts.
 * Identity is attached per call, never configured on the shared client.
 */
@Configuration
public class BackendClientConfig {

  @Bean
  RestClient backendRestClient(
      RestClient.Builder restClientBuilder, @Value("${backend.url}") String backendUrl) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(3));
    requestFactory.setReadTimeout(Duration.ofSeconds(15));
    return restClientBuilder.baseUrl(backendUrl).requestFactory(requestFactory).build();
  }

  @Bean
  BackendClient backendClient(
      @Qualifier("backendRestClient") RestClient backendRestClient, ObjectMapper objectMapper) {
    return new BackendClient(backendRestClient, objectMapper);
  }
}
