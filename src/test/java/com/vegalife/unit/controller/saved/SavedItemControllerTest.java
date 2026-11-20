package com.vegalife.unit.controller.saved;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.controller.saved.SavedItemController;
import com.vegalife.dto.request.saved.SavedListRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.dto.response.recipe.RecipeResponse;
import com.vegalife.service.saved.SavedItemService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class SavedItemControllerTest {

  @Mock private SavedItemService savedItemService;

  @InjectMocks private SavedItemController savedItemController;

  private final UUID userId = UUID.randomUUID();

  @Test
  void savePost_returnsSuccess() {
    UUID postId = UUID.randomUUID();

    ResponseEntity<ApiResponse<Void>> response = savedItemController.savePost(userId, postId);

    verify(savedItemService).savePost(userId, postId);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().isSuccess()).isTrue();
  }

  @Test
  void unsavePost_returnsSuccess() {
    UUID postId = UUID.randomUUID();

    ResponseEntity<ApiResponse<Void>> response = savedItemController.unsavePost(userId, postId);

    verify(savedItemService).unsavePost(userId, postId);
    assertThat(response.getBody().isSuccess()).isTrue();
  }

  @Test
  void saveRecipe_returnsSuccess() {
    UUID recipeId = UUID.randomUUID();

    ResponseEntity<ApiResponse<Void>> response = savedItemController.saveRecipe(userId, recipeId);

    verify(savedItemService).saveRecipe(userId, recipeId);
    assertThat(response.getBody().isSuccess()).isTrue();
  }

  @Test
  void unsaveRecipe_returnsSuccess() {
    UUID recipeId = UUID.randomUUID();

    ResponseEntity<ApiResponse<Void>> response = savedItemController.unsaveRecipe(userId, recipeId);

    verify(savedItemService).unsaveRecipe(userId, recipeId);
    assertThat(response.getBody().isSuccess()).isTrue();
  }

  @Test
  void listSavedPosts_returnsPage() {
    SavedListRequest request = SavedListRequest.builder().build();
    PageResponse<PostListResponse> page =
        PageResponse.<PostListResponse>builder()
            .content(List.of(PostListResponse.builder().id(UUID.randomUUID()).build()))
            .build();
    when(savedItemService.listSavedPosts(userId, request)).thenReturn(page);

    ResponseEntity<ApiResponse<PageResponse<PostListResponse>>> response =
        savedItemController.listSavedPosts(userId, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getData().getContent()).hasSize(1);
  }

  @Test
  void listSavedRecipes_returnsPage() {
    SavedListRequest request = SavedListRequest.builder().build();
    PageResponse<RecipeResponse> page =
        PageResponse.<RecipeResponse>builder()
            .content(List.of(RecipeResponse.builder().id(UUID.randomUUID()).build()))
            .build();
    when(savedItemService.listSavedRecipes(userId, request)).thenReturn(page);

    ResponseEntity<ApiResponse<PageResponse<RecipeResponse>>> response =
        savedItemController.listSavedRecipes(userId, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getData().getContent()).hasSize(1);
  }
}
