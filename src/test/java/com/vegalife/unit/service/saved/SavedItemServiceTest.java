package com.vegalife.unit.service.saved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.saved.SavedListRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.dto.response.recipe.RecipeResponse;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.saved.SavedItem;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.saved.SavedItemRepository;
import com.vegalife.service.post.PostService;
import com.vegalife.service.recipe.RecipeService;
import com.vegalife.service.saved.SavedItemService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SavedItemServiceTest {

  @Mock private SavedItemRepository savedItemRepository;
  @Mock private PostRepository postRepository;
  @Mock private RecipeRepository recipeRepository;
  @Mock private PostService postService;
  @Mock private RecipeService recipeService;

  @InjectMocks private SavedItemService savedItemService;

  private final UUID userId = UUID.randomUUID();

  @Test
  void savePost_publishedPostIsStored() {
    UUID postId = UUID.randomUUID();
    Post post = Post.builder().id(postId).status(PostStatus.published).build();
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, PostStatus.published))
        .thenReturn(Optional.of(post));
    when(savedItemRepository.findByUserIdAndPostId(userId, postId)).thenReturn(Optional.empty());

    savedItemService.savePost(userId, postId);

    ArgumentCaptor<SavedItem> captor = ArgumentCaptor.forClass(SavedItem.class);
    verify(savedItemRepository).save(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    assertThat(captor.getValue().getPostId()).isEqualTo(postId);
    assertThat(captor.getValue().getRecipeId()).isNull();
  }

  @Test
  void savePost_isIdempotentAndKeepsOriginalSave() {
    UUID postId = UUID.randomUUID();
    Post post = Post.builder().id(postId).status(PostStatus.published).build();
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, PostStatus.published))
        .thenReturn(Optional.of(post));
    when(savedItemRepository.findByUserIdAndPostId(userId, postId))
        .thenReturn(Optional.of(SavedItem.builder().userId(userId).postId(postId).build()));

    savedItemService.savePost(userId, postId);

    verify(savedItemRepository, never()).save(any());
  }

  @Test
  void savePost_invisiblePostReturnsNotFound() {
    UUID postId = UUID.randomUUID();
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, PostStatus.published))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> savedItemService.savePost(userId, postId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");

    verify(savedItemRepository, never()).save(any());
  }

  @Test
  void unsavePost_delegatesToDelete() {
    UUID postId = UUID.randomUUID();

    savedItemService.unsavePost(userId, postId);

    verify(savedItemRepository).deleteSavedPost(userId, postId);
  }

  @Test
  void saveRecipe_approvedRecipeIsStored() {
    UUID recipeId = UUID.randomUUID();
    Recipe recipe =
        Recipe.builder().id(recipeId).moderationStatus(ModerationStatus.APPROVED).build();
    when(recipeRepository.findByIdAndDeletedAtIsNull(recipeId)).thenReturn(Optional.of(recipe));
    when(savedItemRepository.findByUserIdAndRecipeId(userId, recipeId))
        .thenReturn(Optional.empty());

    savedItemService.saveRecipe(userId, recipeId);

    ArgumentCaptor<SavedItem> captor = ArgumentCaptor.forClass(SavedItem.class);
    verify(savedItemRepository).save(captor.capture());
    assertThat(captor.getValue().getRecipeId()).isEqualTo(recipeId);
    assertThat(captor.getValue().getPostId()).isNull();
  }

  @Test
  void saveRecipe_pendingRecipeReturnsNotFound() {
    UUID recipeId = UUID.randomUUID();
    Recipe recipe =
        Recipe.builder().id(recipeId).moderationStatus(ModerationStatus.PENDING).build();
    when(recipeRepository.findByIdAndDeletedAtIsNull(recipeId)).thenReturn(Optional.of(recipe));

    assertThatThrownBy(() -> savedItemService.saveRecipe(userId, recipeId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Recipe not found");

    verify(savedItemRepository, never()).save(any());
  }

  @Test
  void saveRecipe_rejectedRecipeReturnsNotFound() {
    UUID recipeId = UUID.randomUUID();
    Recipe recipe =
        Recipe.builder().id(recipeId).moderationStatus(ModerationStatus.REJECTED).build();
    when(recipeRepository.findByIdAndDeletedAtIsNull(recipeId)).thenReturn(Optional.of(recipe));

    assertThatThrownBy(() -> savedItemService.saveRecipe(userId, recipeId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void unsaveRecipe_delegatesToDelete() {
    UUID recipeId = UUID.randomUUID();

    savedItemService.unsaveRecipe(userId, recipeId);

    verify(savedItemRepository).deleteSavedRecipe(userId, recipeId);
  }

  @Test
  void listSavedPosts_returnsPostsInSaveOrder() {
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    Pageable pageable = PageRequest.of(0, 20);
    Page<SavedItem> page =
        new PageImpl<>(
            List.of(
                SavedItem.builder().postId(firstId).build(),
                SavedItem.builder().postId(secondId).build()),
            pageable,
            2);
    when(savedItemRepository.findVisibleSavedPosts(userId, PostStatus.published, pageable))
        .thenReturn(page);
    when(postService.readPublishedPostsByIds(eq(userId), anyCollection()))
        .thenReturn(
            Map.of(
                firstId, PostListResponse.builder().id(firstId).build(),
                secondId, PostListResponse.builder().id(secondId).build()));

    PageResponse<PostListResponse> response =
        savedItemService.listSavedPosts(userId, SavedListRequest.builder().build());

    assertThat(response.getContent())
        .extracting(PostListResponse::getId)
        .containsExactly(firstId, secondId);
    assertThat(response.getTotalElements()).isEqualTo(2);
  }

  @Test
  void listSavedPosts_skipsPostsThatAreNoLongerVisible() {
    UUID visibleId = UUID.randomUUID();
    UUID hiddenId = UUID.randomUUID();
    Pageable pageable = PageRequest.of(0, 20);
    Page<SavedItem> page =
        new PageImpl<>(
            List.of(
                SavedItem.builder().postId(visibleId).build(),
                SavedItem.builder().postId(hiddenId).build()),
            pageable,
            2);
    when(savedItemRepository.findVisibleSavedPosts(userId, PostStatus.published, pageable))
        .thenReturn(page);
    when(postService.readPublishedPostsByIds(eq(userId), anyCollection()))
        .thenReturn(Map.of(visibleId, PostListResponse.builder().id(visibleId).build()));

    PageResponse<PostListResponse> response =
        savedItemService.listSavedPosts(userId, SavedListRequest.builder().build());

    assertThat(response.getContent())
        .extracting(PostListResponse::getId)
        .containsExactly(visibleId);
  }

  @Test
  void listSavedRecipes_returnsApprovedRecipesOnly() {
    UUID recipeId = UUID.randomUUID();
    Pageable pageable = PageRequest.of(0, 20);
    Page<SavedItem> page =
        new PageImpl<>(List.of(SavedItem.builder().recipeId(recipeId).build()), pageable, 1);
    when(savedItemRepository.findVisibleSavedRecipes(userId, ModerationStatus.APPROVED, pageable))
        .thenReturn(page);
    when(recipeService.readApprovedRecipesByIds(anyList()))
        .thenReturn(Map.of(recipeId, RecipeResponse.builder().id(recipeId).build()));

    PageResponse<RecipeResponse> response =
        savedItemService.listSavedRecipes(userId, SavedListRequest.builder().build());

    assertThat(response.getContent()).extracting(RecipeResponse::getId).containsExactly(recipeId);
    assertThat(response.getTotalElements()).isEqualTo(1);
  }

  @Test
  void listSavedPosts_usesBoundedPageSize() {
    Pageable pageable = PageRequest.of(2, 5);
    when(savedItemRepository.findVisibleSavedPosts(userId, PostStatus.published, pageable))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));
    when(postService.readPublishedPostsByIds(eq(userId), anyCollection())).thenReturn(Map.of());

    PageResponse<PostListResponse> response =
        savedItemService.listSavedPosts(userId, SavedListRequest.builder().page(2).size(5).build());

    assertThat(response.getPage()).isEqualTo(2);
    assertThat(response.getSize()).isEqualTo(5);
  }
}
