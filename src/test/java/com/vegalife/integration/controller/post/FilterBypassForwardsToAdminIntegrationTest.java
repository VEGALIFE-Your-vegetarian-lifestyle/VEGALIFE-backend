package com.vegalife.integration.controller.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Filter bypass (interp. A): with {@code app.filter.enabled=false}, a published post is forwarded
 * straight to the admin review queue ({@code flag=NEEDS_REVIEW}, {@code status=unpublished}) with
 * no embedding call and no outbound row, and an admin can then moderate it to published.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@TestPropertySource(properties = "app.filter.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class FilterBypassForwardsToAdminIntegrationTest {

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

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private OutboundMessageRepository outboundMessageRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private User admin;
  private User member;
  private String adminToken;
  private String memberToken;
  private Category category;

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();

    admin = createUser("bypassadmin", "bypass.admin@example.com", User.Role.ADMIN);
    member = createUser("bypassmember", "bypass.member@example.com", User.Role.USER);
    adminToken = jwtTokenService.generateAccessToken(admin);
    memberToken = jwtTokenService.generateAccessToken(member);
    category = categoryRepository.saveAndFlush(Category.builder().name("Dinner").build());
  }

  @Test
  void publishWithFilterDisabled_landsInAdminQueue_andModerationPublishesIt() throws Exception {
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Bát đậu hũ non sốt mè rang",
                      "content": "Công thức bát đậu hũ non mềm, sốt mè rang béo nhẹ.",
                      "rawContent": {},
                      "categoryIds": ["%s"],
                      "publish": true
                    }
                    """
                        .formatted(category.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("unpublished"))
        .andExpect(jsonPath("$.data.flag").value("NEEDS_REVIEW"));

    UUID postId = findPostId("Bát đậu hũ non sốt mè rang");
    assertThat(outboundMessageRepository.findAll())
        .as("filter bypass must not enqueue a CONTENT_FILTER row")
        .isEmpty();

    // Admin default list (no flag param) shows the forwarded post.
    mockMvc
        .perform(get("/api/admin/posts").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(postId.toString()))
        .andExpect(jsonPath("$.data.content[0].flag").value("NEEDS_REVIEW"));

    // Admin publishes it through the moderation endpoint.
    mockMvc
        .perform(
            post("/api/admin/posts/" + postId + "/moderate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PUBLISH\",\"reason\":\"Bypass demo\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("published"))
        .andExpect(jsonPath("$.data.flag").value("NEEDS_REVIEW"))
        .andExpect(jsonPath("$.data.publishedAt").exists());
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

  private UUID findPostId(String title) {
    return postRepository.findAll().stream()
        .filter(p -> title.equals(p.getTitle()))
        .map(Post::getId)
        .findFirst()
        .orElseThrow();
  }
}
