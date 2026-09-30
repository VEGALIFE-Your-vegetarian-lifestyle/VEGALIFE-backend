package com.vegalife.integration.controller.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
class AdminVideoControllerIntegrationTest {

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

  @Autowired private UserRepository userRepository;

  @Autowired private PostRepository postRepository;

  @Autowired private MediaRepository mediaRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private User admin;
  private User member;
  private User uploader;
  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    mediaRepository.deleteAll();
    userRepository.deleteAll();

    admin = createUser("adminvideo", "admin.video@example.com", User.Role.ADMIN);
    member = createUser("membervideo", "member.video@example.com", User.Role.USER);
    uploader = createUser("chefvideo", "chef.video@example.com", User.Role.USER);
    adminToken = jwtTokenService.generateAccessToken(admin);
    userToken = jwtTokenService.generateAccessToken(member);
  }

  @Test
  void listVideos_asAdmin_returns200WithFullItemFieldsAndAttachedPosts() throws Exception {
    Media withPost = createVideo(uploader, "video/mp4", Media.Status.succeed, 100);
    Media withoutPost = createVideo(uploader, "video/webm", Media.Status.uploading, 200);
    Post attached =
        postRepository.save(
            Post.builder()
                .user(member)
                .title("Stir fry write-up")
                .content("Post content")
                .media(Set.of(withPost))
                .status(Post.Status.published)
                .flag(Post.Flag.PASSED)
                .viewCount(3)
                .build());
    createVideo(uploader, "image/png", Media.Status.succeed, 300);
    createVideo(uploader, "video/mp4", Media.Status.succeed, 400, Instant.now());

    mockMvc
        .perform(
            get("/api/admin/videos")
                .header("Authorization", "Bearer " + adminToken)
                .param("sort", "durationSeconds,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Videos retrieved successfully"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(
            jsonPath("$.data.content[*].durationSeconds").value(containsInAnyOrder(100, 200)))
        .andExpect(jsonPath("$.data.content[0].id").value(withPost.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].mediaUrl").exists())
        .andExpect(jsonPath("$.data.content[0].status").value("succeed"))
        .andExpect(jsonPath("$.data.content[0].mimeType").value("video/mp4"))
        .andExpect(jsonPath("$.data.content[0].durationSeconds").value(100))
        .andExpect(jsonPath("$.data.content[0].fileSizeBytes").exists())
        .andExpect(jsonPath("$.data.content[0].createdAt").exists())
        .andExpect(jsonPath("$.data.content[0].updatedAt").exists())
        .andExpect(jsonPath("$.data.content[0].userId").value(uploader.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].username").value("chefvideo"))
        .andExpect(jsonPath("$.data.content[0].email").value("chef.video@example.com"))
        .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].posts.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].posts[0].id").value(attached.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].posts[0].title").value("Stir fry write-up"))
        .andExpect(jsonPath("$.data.content[0].posts[0].status").value("published"))
        .andExpect(jsonPath("$.data.content[1].status").value("uploading"))
        .andExpect(jsonPath("$.data.content[1].posts.length()").value(0));
  }

  @Test
  void listVideos_returnsAllUploadersAndAllMediaStatuses() throws Exception {
    createVideo(uploader, "video/mp4", Media.Status.uploading, 100);
    createVideo(member, "video/mp4", Media.Status.succeed, 200);
    createVideo(member, "video/webm", Media.Status.failed, 300);

    mockMvc
        .perform(get("/api/admin/videos").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.content.length()").value(3))
        .andExpect(
            jsonPath("$.data.content[*].status")
                .value(containsInAnyOrder("uploading", "succeed", "failed")))
        .andExpect(
            jsonPath("$.data.content[*].username")
                .value(containsInAnyOrder("chefvideo", "membervideo", "membervideo")));
  }

  @Test
  void listVideos_filterByStatus_returnsOnlyMatchingVideos() throws Exception {
    createVideo(uploader, "video/mp4", Media.Status.succeed, 100);
    createVideo(uploader, "video/mp4", Media.Status.failed, 200);
    createVideo(uploader, "video/mp4", Media.Status.uploading, 300);

    mockMvc
        .perform(
            get("/api/admin/videos")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "failed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].status").value("failed"))
        .andExpect(jsonPath("$.data.content[0].durationSeconds").value(200));
  }

  @Test
  void listVideos_filterByUserId_returnsOnlyThatUploadersVideos() throws Exception {
    createVideo(uploader, "video/mp4", Media.Status.succeed, 100);
    createVideo(member, "video/mp4", Media.Status.succeed, 200);

    mockMvc
        .perform(
            get("/api/admin/videos")
                .header("Authorization", "Bearer " + adminToken)
                .param("userId", uploader.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].username").value("chefvideo"))
        .andExpect(jsonPath("$.data.content[0].userId").value(uploader.getId().toString()));
  }

  @Test
  void listVideos_filterByCreatedDateRange_returnsOnlyMatchingVideos() throws Exception {
    createVideo(uploader, "video/mp4", Media.Status.succeed, 100);
    createVideo(member, "video/mp4", Media.Status.succeed, 200);

    mockMvc
        .perform(
            get("/api/admin/videos")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "2000-01-01T00:00:00Z")
                .param("createdTo", "2100-01-01T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(2));

    mockMvc
        .perform(
            get("/api/admin/videos")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "2100-01-01T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.data.content.length()").value(0));
  }

  @Test
  void listVideos_withoutJwt_returns401() throws Exception {
    mockMvc.perform(get("/api/admin/videos")).andExpect(status().isUnauthorized());
  }

  @Test
  void listVideos_withNonAdminJwt_returns403() throws Exception {
    mockMvc
        .perform(get("/api/admin/videos").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void listVideos_withPagination_returnsSinglePageItem() throws Exception {
    createVideo(uploader, "video/mp4", Media.Status.succeed, 100);
    createVideo(uploader, "video/mp4", Media.Status.succeed, 200);
    createVideo(uploader, "video/mp4", Media.Status.succeed, 300);

    mockMvc
        .perform(
            get("/api/admin/videos")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "1")
                .param("sort", "durationSeconds,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(3))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].durationSeconds").value(100));
  }

  @Test
  void listVideos_withDisallowedSortProperty_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/videos")
                .header("Authorization", "Bearer " + adminToken)
                .param("sort", "passwordHash,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Sort property must be one of: createdAt, updatedAt, durationSeconds,"
                        + " fileSizeBytes"));
  }

  @Test
  void listVideos_withMalformedSort_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/videos")
                .header("Authorization", "Bearer " + adminToken)
                .param("sort", "createdAt,asc,extra"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Sort must be in the form property,asc|desc"));
  }

  @Test
  void listVideos_withInvalidStatus_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/videos")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "bogus"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listVideos_withInvalidPaging_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/videos")
                .header("Authorization", "Bearer " + adminToken)
                .param("size", "101"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/admin/videos")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listVideos_createdFromAfterCreatedTo_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/videos")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "2100-01-01T00:00:00Z")
                .param("createdTo", "2000-01-01T00:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("createdFrom must be before createdTo"));
  }

  @Test
  void listVideos_withMalformedDate_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/videos")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "not-a-date"))
        .andExpect(status().isBadRequest());
  }

  private User createUser(String username, String email, User.Role role) {
    return userRepository.save(
        User.builder()
            .username(username)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(role)
            .status(User.Status.activated)
            .emailVerified(true)
            .build());
  }

  private Media createVideo(User uploaderUser, String mimeType, Media.Status status, int duration) {
    return createVideo(uploaderUser, mimeType, status, duration, null);
  }

  private Media createVideo(
      User uploaderUser, String mimeType, Media.Status status, int duration, Instant deletedAt) {
    boolean video = mimeType.startsWith("video/");
    return mediaRepository.save(
        Media.builder()
            .mediaUrl(
                video ? "https://cdn.example.com/clip.mp4" : "https://cdn.example.com/pic.png")
            .thumbnailUrl("https://cdn.example.com/thumb.jpg")
            .description("Seeded " + mimeType)
            .status(status)
            .durationSeconds(duration)
            .fileSizeBytes((long) duration * 1000L)
            .mimeType(mimeType)
            .width(1920)
            .height(1080)
            .uploadedBy(uploaderUser)
            .externalId("ext-" + duration)
            .deletedAt(deletedAt)
            .build());
  }
}
