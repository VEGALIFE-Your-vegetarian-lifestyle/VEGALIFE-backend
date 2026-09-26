package com.vegalife.unit.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.post.PostMapper;
import com.vegalife.dto.request.post.PostCreateRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.post.PostService;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

  @Mock private PostRepository postRepository;

  @Mock private UserRepository userRepository;

  @Mock private PostMapper postMapper;

  @InjectMocks private PostService postService;

  private UUID userId;
  private User user;
  private PostCreateRequest request;
  private Post post;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    user = User.builder().id(userId).username("postowner").build();
    request =
        PostCreateRequest.builder()
            .title("Vegan tofu bowl")
            .content("A simple plant-based lunch.")
            .featuredImageUrl("https://example.com/tofu-bowl.jpg")
            .build();
    post =
        Post.builder()
            .title(request.getTitle())
            .content(request.getContent())
            .featuredImageUrl(request.getFeaturedImageUrl())
            .build();
  }

  @Test
  void createPost_assignsAuthenticatedUserAndInitialValues() {
    PostListResponse expectedResponse =
        PostListResponse.builder().title(request.getTitle()).status("created").viewCount(0).build();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(postMapper.toEntity(request)).thenReturn(post);
    when(postRepository.saveAndFlush(post)).thenReturn(post);
    when(postMapper.toListResponse(post)).thenReturn(expectedResponse);

    PostListResponse response = postService.createPost(userId, request);

    assertThat(response).isSameAs(expectedResponse);
    assertThat(post.getUser()).isSameAs(user);
    assertThat(post.getStatus()).isEqualTo(Post.Status.created);
    assertThat(post.getViewCount()).isZero();
    verify(postRepository).saveAndFlush(post);
  }

  @Test
  void createPost_throwsWhenAuthenticatedUserNoLongerExists() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> postService.createPost(userId, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found");

    verify(postMapper, never()).toEntity(request);
    verify(postRepository, never()).saveAndFlush(post);
  }
}
