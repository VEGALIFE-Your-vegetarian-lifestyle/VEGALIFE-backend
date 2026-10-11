package com.vegalife.integration.controller.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vegalife.model.post.Post;
import com.vegalife.model.post.Vote;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.post.VoteRepository;
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
class VoteControllerIntegrationTest {

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

  @Autowired private VoteRepository voteRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private User user;
  private User otherUser;
  private String accessToken;

  @BeforeEach
  void setUp() {
    voteRepository.deleteAll();
    postRepository.deleteAll();
    userRepository.deleteAll();

    user = saveUser("voter", "voter@example.com");
    otherUser = saveUser("postauthor", "postauthor@example.com");
    accessToken = jwtTokenService.generateAccessToken(user);
  }

  @Test
  void castVote_firstVoteIsRecorded() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);

    mockMvc
        .perform(
            put("/api/posts/{postId}/votes", post.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"voteType\":\"UPVOTE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.likeCount").value(1))
        .andExpect(jsonPath("$.data.dislikeCount").value(0))
        .andExpect(jsonPath("$.data.userVote").value("UPVOTE"));

    assertThat(voteRepository.findByUserIdAndPostId(user.getId(), post.getId())).isPresent();
  }

  @Test
  void castVote_sameTypeAgainRemovesVote() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    voteRepository.saveAndFlush(
        Vote.builder()
            .userId(user.getId())
            .postId(post.getId())
            .voteType(Vote.VoteType.UPVOTE)
            .build());

    mockMvc
        .perform(
            put("/api/posts/{postId}/votes", post.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"voteType\":\"UPVOTE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.likeCount").value(0))
        .andExpect(jsonPath("$.data.userVote").doesNotExist());

    assertThat(voteRepository.findByUserIdAndPostId(user.getId(), post.getId())).isEmpty();
  }

  @Test
  void castVote_differentTypeSwitchesVote() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    voteRepository.saveAndFlush(
        Vote.builder()
            .userId(user.getId())
            .postId(post.getId())
            .voteType(Vote.VoteType.UPVOTE)
            .build());

    mockMvc
        .perform(
            put("/api/posts/{postId}/votes", post.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"voteType\":\"DOWNVOTE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.likeCount").value(0))
        .andExpect(jsonPath("$.data.dislikeCount").value(1))
        .andExpect(jsonPath("$.data.userVote").value("DOWNVOTE"));
  }

  @Test
  void castVote_withoutAuthenticationReturns401() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);

    mockMvc
        .perform(
            put("/api/posts/{postId}/votes", post.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"voteType\":\"UPVOTE\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void castVote_unpublishedPostReturns404() throws Exception {
    Post post = savePost(otherUser, Post.Status.created);

    mockMvc
        .perform(
            put("/api/posts/{postId}/votes", post.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"voteType\":\"UPVOTE\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Post not found"));
  }

  @Test
  void getVotes_withoutAuthenticationSucceedsWithNullUserVote() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    voteRepository.saveAndFlush(
        Vote.builder()
            .userId(otherUser.getId())
            .postId(post.getId())
            .voteType(Vote.VoteType.UPVOTE)
            .build());

    mockMvc
        .perform(get("/api/posts/{postId}/votes", post.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.likeCount").value(1))
        .andExpect(jsonPath("$.data.userVote").doesNotExist());
  }

  @Test
  void getVotes_withAuthenticationShowsOwnVote() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    voteRepository.saveAndFlush(
        Vote.builder()
            .userId(user.getId())
            .postId(post.getId())
            .voteType(Vote.VoteType.DOWNVOTE)
            .build());

    mockMvc
        .perform(
            get("/api/posts/{postId}/votes", post.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.dislikeCount").value(1))
        .andExpect(jsonPath("$.data.userVote").value("DOWNVOTE"));
  }

  @Test
  void getVotes_missingPostReturns404() throws Exception {
    mockMvc
        .perform(get("/api/posts/{postId}/votes", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Post not found"));
  }

  private User saveUser(String username, String email) {
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

  private Post savePost(User owner, Post.Status status) {
    return postRepository.saveAndFlush(
        Post.builder()
            .user(owner)
            .title("Published post")
            .content("Post body")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .status(status)
            .viewCount(0)
            .build());
  }
}
