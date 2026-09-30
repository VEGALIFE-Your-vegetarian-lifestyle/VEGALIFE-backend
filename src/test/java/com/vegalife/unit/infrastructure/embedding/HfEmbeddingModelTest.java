package com.vegalife.unit.infrastructure.embedding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vegalife.infrastructure.embedding.HfEmbeddingModel;
import com.vegalife.infrastructure.embedding.HfEmbeddingProperties;
import com.vegalife.shared.exception.EmbeddingClientException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class HfEmbeddingModelTest {

  private static final String ENDPOINT = "https://hf.test/models/org/model-x";

  private MockRestServiceServer mockServer;
  private HfEmbeddingModel model;

  @BeforeEach
  void setUp() {
    HfEmbeddingProperties properties = new HfEmbeddingProperties();
    properties.setBaseUrl("https://hf.test/models/");
    properties.setModel("org/model-x");
    properties.setApiKey("test-key");

    RestClient.Builder builder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(builder).build();
    model = new HfEmbeddingModel(builder.build(), properties);
  }

  @Test
  void embedsBatchAndMapsRowsInOrder() {
    mockServer
        .expect(requestTo(ENDPOINT))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
        .andExpect(jsonPath("$.inputs").isArray())
        .andExpect(jsonPath("$.inputs[0]").value("first text"))
        .andExpect(jsonPath("$.inputs[1]").value("second text"))
        .andExpect(jsonPath("$.normalize").value(true))
        .andRespond(withSuccess("[[0.1,0.2],[0.3,0.4]]", MediaType.APPLICATION_JSON));
    mockServer
        .expect(requestTo(ENDPOINT))
        .andRespond(withSuccess("[[0.1,0.2],[0.3,0.4]]", MediaType.APPLICATION_JSON));

    EmbeddingResponse response =
        model.call(new EmbeddingRequest(List.of("first text", "second text"), null));

    assertEquals(2, response.getResults().size());
    Embedding first = response.getResults().get(0);
    assertEquals(0, first.getIndex());
    assertArrayEquals(new float[] {0.1f, 0.2f}, first.getOutput(), 1e-6f);
    Embedding second = response.getResults().get(1);
    assertEquals(1, second.getIndex());
    assertArrayEquals(new float[] {0.3f, 0.4f}, second.getOutput(), 1e-6f);

    List<float[]> viaEmbed = model.embed(List.of("first text", "second text"));
    assertEquals(2, viaEmbed.size());
    assertArrayEquals(new float[] {0.1f, 0.2f}, viaEmbed.get(0), 1e-6f);
    mockServer.verify();
  }

  @Test
  void emptyInputSkipsHttp() {
    EmbeddingResponse response = model.call(new EmbeddingRequest(List.of(), null));

    assertTrue(response.getResults().isEmpty());
    mockServer.verify();
  }

  @Test
  void rowCountMismatchIsRejected() {
    mockServer
        .expect(requestTo(ENDPOINT))
        .andRespond(withSuccess("[[0.1,0.2]]", MediaType.APPLICATION_JSON));

    EmbeddingClientException ex =
        assertThrows(
            EmbeddingClientException.class,
            () -> model.call(new EmbeddingRequest(List.of("a", "b"), null)));

    assertTrue(ex.getMessage().contains("1 rows for 2 inputs"));
    assertTrue(ex.getMessage().contains("org/model-x"));
  }

  @Test
  void wrapsHttpErrorInEmbeddingClientException() {
    mockServer.expect(requestTo(ENDPOINT)).andRespond(withServerError());

    EmbeddingClientException ex =
        assertThrows(
            EmbeddingClientException.class,
            () -> model.call(new EmbeddingRequest(List.of("text"), null)));

    assertTrue(ex.getMessage().contains("org/model-x"));
    assertInstanceOf(RestClientResponseException.class, ex.getCause());
  }
}
