package com.vegalife.service.post;

import com.vegalife.dto.mapper.post.PostMapper;
import com.vegalife.dto.request.post.PostCreateRequest;
import com.vegalife.dto.request.post.PostListRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.model.post.Post;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final PostMapper postMapper;

  @Transactional
  public PostListResponse createPost(UUID userId, PostCreateRequest request) {
    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    Post post = postMapper.toEntity(request);
    post.setUser(user);
    post.setStatus(Post.Status.created);
    post.setViewCount(0);

    return postMapper.toListResponse(postRepository.saveAndFlush(post));
  }

  @Transactional(readOnly = true)
  public PageResponse<PostListResponse> listUserPosts(UUID userId, PostListRequest request) {
    Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
    Page<Post> posts =
        postRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable);

    return PageResponse.from(posts.map(postMapper::toListResponse));
  }
}
