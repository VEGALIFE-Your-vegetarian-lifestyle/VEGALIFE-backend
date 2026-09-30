package com.vegalife.integration.controller.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.integration.config.FakeUploadProvider;
import com.vegalife.model.post.Media;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.media.VerifiedUpload;
import com.vegalife.service.token.JwtTokenService;
import com.vegalife.shared.config.MediaProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class MediaControllerIntegrationTest {

  private static final int IMAGE_CEILING = 5 * 1024 * 1024;
  private static final int VIDEO_CEILING = 50 * 1024 * 1024;
  private static final String SEED_URL = "https://cdn.fake.test/media/seeded.png";

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("vegalife_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(false);

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private MediaRepository mediaRepository;

  @Autowired private JwtTokenService jwtTokenService;

  @Autowired private MediaProperties mediaProperties;

  @Autowired private FakeUploadProvider fakeUploadProvider;

  private User user;
  private String accessToken;

  @BeforeEach
  void setUp() {
    fakeUploadProvider.reset();
    user =
        userRepository
            .findByEmail("mediaowner@example.com")
            .orElseGet(() -> createUser("mediaowner", "mediaowner@example.com"));
    accessToken = jwtTokenService.generateAccessToken(user);
  }

  @Test
  void createUploadGrant_returns201WithServerDerivedPublicId() throws Exception {
    long rowsBefore = mediaRepository.count();

    MvcResult result =
        mockMvc
            .perform(uploadRequest("/api/media/upload", uploadPayload("image/jpeg", 1024L)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Upload grant created"))
            .andExpect(jsonPath("$.data.status").value("uploading"))
            .andExpect(jsonPath("$.data.upload.method").value("POST"))
            .andExpect(jsonPath("$.data.upload.url").value("https://upload.fake.test/v1/signed"))
            .andExpect(jsonPath("$.data.upload.headers").exists())
            .andExpect(jsonPath("$.data.upload.fields.api_key").value("fake-api-key"))
            .andExpect(jsonPath("$.data.upload.fields.max_file_size").value(IMAGE_CEILING))
            .andExpect(jsonPath("$.data.upload.fields.allowed_formats[0]").value("jpg"))
            .andExpect(jsonPath("$.data.expiresAt").exists())
            .andReturn();

    JsonNode data = readData(result);
    UUID mediaId = UUID.fromString(data.path("mediaId").asText());
    String expectedPublicId = "integration-test-cloud/" + user.getId() + "/" + mediaId;
    assertThat(data.path("upload").path("fields").path("public_id").asText())
        .isEqualTo(expectedPublicId);

    assertThat(mediaRepository.count()).isEqualTo(rowsBefore + 1);
    Media row = mediaRepository.findByIdAndDeletedAtIsNull(mediaId).orElseThrow();
    assertThat(row.getStatus()).isEqualTo(Media.Status.uploading);
    assertThat(row.getMimeType()).isEqualTo("image/jpeg");
    assertThat(row.getExternalId()).isEqualTo(expectedPublicId);
    assertThat(row.getUploadedBy().getId()).isEqualTo(user.getId());
    assertThat(row.getMediaUrl()).isNull();
    assertThat(fakeUploadProvider.preparedPublicIds()).containsExactly(expectedPublicId);
  }

  @Test
  void createUploadGrant_unsupportedContentType_returns400WithoutRow() throws Exception {
    long rowsBefore = mediaRepository.count();

    mockMvc
        .perform(uploadRequest("/api/media/upload", uploadPayload("image/gif", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Unsupported content type: image/gif"));

    assertThat(mediaRepository.count()).isEqualTo(rowsBefore);
    assertThat(fakeUploadProvider.preparedPublicIds()).isEmpty();
  }

  @Test
  void createUploadGrant_imageOverCeiling_returns400BeforeRow() throws Exception {
    long rowsBefore = mediaRepository.count();

    mockMvc
        .perform(
            uploadRequest("/api/media/upload", uploadPayload("image/jpeg", IMAGE_CEILING + 1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("File exceeds the 5 MB image limit"));

    assertThat(mediaRepository.count()).isEqualTo(rowsBefore);
    assertThat(fakeUploadProvider.preparedPublicIds()).isEmpty();
  }

  @Test
  void createUploadGrant_videoOverCeiling_returns400() throws Exception {
    long rowsBefore = mediaRepository.count();

    mockMvc
        .perform(uploadRequest("/api/media/upload", uploadPayload("video/mp4", VIDEO_CEILING + 1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("File exceeds the 50 MB limit"));

    assertThat(mediaRepository.count()).isEqualTo(rowsBefore);
    assertThat(fakeUploadProvider.preparedPublicIds()).isEmpty();
  }

  @Test
  void confirmUpload_seededObject_persistsProviderFacts() throws Exception {
    JsonNode data = createGrant("image/jpeg", 1024L);
    UUID mediaId = UUID.fromString(data.path("mediaId").asText());
    seedObject(data, "image/jpeg", 2048L);

    mockMvc
        .perform(confirmRequest(mediaId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Media upload confirmed"))
        .andExpect(jsonPath("$.data.mediaId").value(mediaId.toString()))
        .andExpect(jsonPath("$.data.status").value("succeed"))
        .andExpect(jsonPath("$.data.mediaUrl").value(SEED_URL))
        .andExpect(jsonPath("$.data.mimeType").value("image/jpeg"))
        .andExpect(jsonPath("$.data.fileSizeBytes").value(2048))
        .andExpect(jsonPath("$.data.width").value(800))
        .andExpect(jsonPath("$.data.height").value(600))
        .andExpect(jsonPath("$.data.durationSeconds").doesNotExist())
        .andExpect(jsonPath("$.data.thumbnailUrl").doesNotExist());

    Media row = mediaRepository.findByIdAndDeletedAtIsNull(mediaId).orElseThrow();
    assertThat(row.getStatus()).isEqualTo(Media.Status.succeed);
    assertThat(row.getUploadedBy().getId()).isEqualTo(user.getId());
  }

  @Test
  void confirmUpload_missingObject_returns400AndRowStaysUploading() throws Exception {
    JsonNode data = createGrant("image/jpeg", 1024L);
    UUID mediaId = UUID.fromString(data.path("mediaId").asText());

    mockMvc
        .perform(confirmRequest(mediaId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Upload verification failed"));

    Media row = mediaRepository.findByIdAndDeletedAtIsNull(mediaId).orElseThrow();
    assertThat(row.getStatus()).isEqualTo(Media.Status.uploading);
    assertThat(row.getMediaUrl()).isNull();
  }

  @Test
  void confirmUpload_repeated_returns409() throws Exception {
    JsonNode data = createGrant("image/jpeg", 1024L);
    UUID mediaId = UUID.fromString(data.path("mediaId").asText());
    seedObject(data, "image/jpeg", 2048L);

    mockMvc.perform(confirmRequest(mediaId)).andExpect(status().isOk());

    mockMvc
        .perform(confirmRequest(mediaId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Media upload has already been confirmed"));
  }

  @Test
  void confirmUpload_expiredGrant_returns400() throws Exception {
    JsonNode data = createGrant("image/jpeg", 1024L);
    UUID mediaId = UUID.fromString(data.path("mediaId").asText());
    seedObject(data, "image/jpeg", 2048L);

    Media row = mediaRepository.findByIdAndDeletedAtIsNull(mediaId).orElseThrow();
    row.setCreatedAt(
        Instant.now().minus(mediaProperties.getUpload().getGrantTtl()).minusSeconds(60));
    mediaRepository.saveAndFlush(row);

    mockMvc
        .perform(confirmRequest(mediaId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Upload grant has expired"));

    assertThat(mediaRepository.findByIdAndDeletedAtIsNull(mediaId).orElseThrow().getStatus())
        .isEqualTo(Media.Status.uploading);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void confirmUpload_actualSizeOverCeiling_marksRowFailedAndReturns400() throws Exception {
    JsonNode data = createGrant("image/jpeg", 1024L);
    UUID mediaId = UUID.fromString(data.path("mediaId").asText());
    seedObject(data, "image/jpeg", IMAGE_CEILING + 1L);

    try {
      mockMvc
          .perform(confirmRequest(mediaId))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("File exceeds the 5 MB image limit"));

      assertThat(mediaRepository.findByIdAndDeletedAtIsNull(mediaId).orElseThrow().getStatus())
          .isEqualTo(Media.Status.failed);
    } finally {
      mediaRepository.deleteById(mediaId);
      userRepository.deleteById(user.getId());
    }
  }

  @Test
  void getMedia_uploadingRow_returnsNullMeasurements() throws Exception {
    JsonNode data = createGrant("image/jpeg", 1024L);
    UUID mediaId = UUID.fromString(data.path("mediaId").asText());

    mockMvc
        .perform(
            get("/api/media/{mediaId}", mediaId).header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Media retrieved successfully"))
        .andExpect(jsonPath("$.data.mediaId").value(mediaId.toString()))
        .andExpect(jsonPath("$.data.status").value("uploading"))
        .andExpect(jsonPath("$.data.mimeType").value("image/jpeg"))
        .andExpect(jsonPath("$.data.mediaUrl").doesNotExist())
        .andExpect(jsonPath("$.data.thumbnailUrl").doesNotExist())
        .andExpect(jsonPath("$.data.fileSizeBytes").doesNotExist())
        .andExpect(jsonPath("$.data.width").doesNotExist())
        .andExpect(jsonPath("$.data.height").doesNotExist())
        .andExpect(jsonPath("$.data.durationSeconds").doesNotExist())
        .andExpect(jsonPath("$.data.createdAt").exists());
  }

  @Test
  void getMedia_unknownId_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/media/{mediaId}", UUID.randomUUID())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Media not found"));
  }

  @Test
  void mediaEndpoints_withoutJwt_return401() throws Exception {
    mockMvc
        .perform(
            post("/api/media/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content(uploadPayload("image/jpeg", 1024L)))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(post("/api/media/{mediaId}/confirm", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(get("/api/media/{mediaId}", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  private String uploadPayload(String contentType, Long sizeBytes) {
    return sizeBytes == null
        ? "{\"contentType\":\"%s\"}".formatted(contentType)
        : "{\"contentType\":\"%s\",\"sizeBytes\":%d}".formatted(contentType, sizeBytes);
  }

  private MockHttpServletRequestBuilder uploadRequest(String uri, String payload) {
    return post(uri)
        .header("Authorization", "Bearer " + accessToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload);
  }

  private MockHttpServletRequestBuilder confirmRequest(UUID mediaId) {
    return post("/api/media/%s/confirm".formatted(mediaId))
        .header("Authorization", "Bearer " + accessToken);
  }

  private JsonNode createGrant(String contentType, Long sizeBytes) throws Exception {
    MvcResult result =
        mockMvc
            .perform(uploadRequest("/api/media/upload", uploadPayload(contentType, sizeBytes)))
            .andExpect(status().isCreated())
            .andReturn();
    return readData(result);
  }

  private void seedObject(JsonNode data, String contentType, long fileSizeBytes) {
    fakeUploadProvider.putObject(
        publicIdOf(data), new VerifiedUpload(SEED_URL, contentType, fileSizeBytes, 800, 600, null));
  }

  private String publicIdOf(JsonNode data) {
    return data.path("upload").path("fields").path("public_id").asText();
  }

  private JsonNode readData(MvcResult result) throws Exception {
    return objectMapper
        .readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .path("data");
  }

  private User createUser(String username, String email) {
    return userRepository.save(
        User.builder()
            .username(username)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build());
  }

  @TestConfiguration
  static class FakeUploadProviderConfiguration {

    @Bean
    @Primary
    FakeUploadProvider fakeUploadProvider(MediaProperties mediaProperties) {
      return new FakeUploadProvider(mediaProperties);
    }
  }
}
