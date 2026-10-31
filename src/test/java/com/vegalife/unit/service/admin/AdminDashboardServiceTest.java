package com.vegalife.unit.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.admin.DashboardRequest;
import com.vegalife.dto.response.admin.DashboardResponse;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.post.Post;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.post.VoteRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.admin.AdminDashboardService;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PostRepository postRepository;
  @Mock private RecipeRepository recipeRepository;
  @Mock private CommentRepository commentRepository;
  @Mock private VoteRepository voteRepository;

  @InjectMocks private AdminDashboardService adminDashboardService;

  @Test
  void getDashboardAggregatesAllMetricsForDefaultWeekRange() {
    when(userRepository.countByDeletedAtIsNull()).thenReturn(100L);
    when(userRepository.countByCreatedAtBetweenAndDeletedAtIsNull(any(), any())).thenReturn(5L);
    when(postRepository.countByDeletedAtIsNull()).thenReturn(50L);
    when(postRepository.countByCreatedAtBetweenAndDeletedAtIsNull(any(), any())).thenReturn(3L);
    when(postRepository.countByFlagAndDeletedAtIsNull(Post.Flag.NEEDS_REVIEW)).thenReturn(2L);
    when(postRepository.findDistinctAuthorIdsInRange(any(), any()))
        .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
    when(recipeRepository.countByDeletedAtIsNull()).thenReturn(20L);
    when(recipeRepository.countByCreatedAtBetweenAndDeletedAtIsNull(any(), any())).thenReturn(1L);
    when(recipeRepository.countByModerationStatusAndDeletedAtIsNull(ModerationStatus.PENDING))
        .thenReturn(4L);
    when(commentRepository.countByDeletedAtIsNull()).thenReturn(200L);
    when(commentRepository.countByCreatedAtBetweenAndDeletedAtIsNull(any(), any())).thenReturn(10L);
    when(commentRepository.countByReportCountGreaterThanAndDeletedAtIsNull(0)).thenReturn(6L);
    when(commentRepository.findDistinctCommenterIdsInRange(any(), any()))
        .thenReturn(List.of(UUID.randomUUID()));
    when(voteRepository.countByCreatedAtBetween(any(), any())).thenReturn(15L);

    DashboardResponse response =
        adminDashboardService.getDashboard(DashboardRequest.builder().range("week").build());

    assertThat(response.getUsers().getTotal()).isEqualTo(100L);
    assertThat(response.getUsers().getNewInRange()).isEqualTo(5L);
    assertThat(response.getUsers().getActiveInRange()).isEqualTo(3L);
    assertThat(response.getContent().getTotalPosts()).isEqualTo(50L);
    assertThat(response.getContent().getNewRecipes()).isEqualTo(1L);
    assertThat(response.getEngagement().getVotesInRange()).isEqualTo(15L);
    assertThat(response.getModerationQueue().getPendingPosts()).isEqualTo(2L);
    assertThat(response.getModerationQueue().getPendingRecipes()).isEqualTo(4L);
    assertThat(response.getModerationQueue().getReportedComments()).isEqualTo(6L);
    assertThat(response.getRange().getType()).isEqualTo("week");
  }

  @Test
  void getDashboardWithCustomRangeUsesSuppliedDates() {
    stubZeroCounts();

    DashboardRequest request =
        DashboardRequest.builder()
            .range("custom")
            .from(LocalDate.of(2026, 1, 1))
            .to(LocalDate.of(2026, 1, 31))
            .build();

    DashboardResponse response = adminDashboardService.getDashboard(request);

    assertThat(response.getRange().getFrom()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(response.getRange().getTo()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
  }

  @Test
  void getDashboardRejectsCustomRangeWithFromAfterTo() {
    DashboardRequest request =
        DashboardRequest.builder()
            .range("custom")
            .from(LocalDate.of(2026, 2, 1))
            .to(LocalDate.of(2026, 1, 1))
            .build();

    assertThatThrownBy(() -> adminDashboardService.getDashboard(request))
        .isInstanceOf(ValidationException.class);
  }

  private void stubZeroCounts() {
    when(userRepository.countByDeletedAtIsNull()).thenReturn(0L);
    when(userRepository.countByCreatedAtBetweenAndDeletedAtIsNull(any(), any())).thenReturn(0L);
    when(postRepository.countByDeletedAtIsNull()).thenReturn(0L);
    when(postRepository.countByCreatedAtBetweenAndDeletedAtIsNull(any(), any())).thenReturn(0L);
    when(postRepository.countByFlagAndDeletedAtIsNull(any())).thenReturn(0L);
    when(postRepository.findDistinctAuthorIdsInRange(any(), any())).thenReturn(List.of());
    when(recipeRepository.countByDeletedAtIsNull()).thenReturn(0L);
    when(recipeRepository.countByCreatedAtBetweenAndDeletedAtIsNull(any(), any())).thenReturn(0L);
    when(recipeRepository.countByModerationStatusAndDeletedAtIsNull(any())).thenReturn(0L);
    when(commentRepository.countByDeletedAtIsNull()).thenReturn(0L);
    when(commentRepository.countByCreatedAtBetweenAndDeletedAtIsNull(any(), any())).thenReturn(0L);
    when(commentRepository.countByReportCountGreaterThanAndDeletedAtIsNull(0)).thenReturn(0L);
    when(commentRepository.findDistinctCommenterIdsInRange(any(), any())).thenReturn(List.of());
    when(voteRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
  }
}
