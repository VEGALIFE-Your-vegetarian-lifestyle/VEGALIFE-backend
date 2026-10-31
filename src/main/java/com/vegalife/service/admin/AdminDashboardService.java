package com.vegalife.service.admin;

import com.vegalife.dto.request.admin.DashboardRequest;
import com.vegalife.dto.response.admin.DashboardContentMetrics;
import com.vegalife.dto.response.admin.DashboardEngagementMetrics;
import com.vegalife.dto.response.admin.DashboardModerationQueueMetrics;
import com.vegalife.dto.response.admin.DashboardRangeResponse;
import com.vegalife.dto.response.admin.DashboardResponse;
import com.vegalife.dto.response.admin.DashboardUserMetrics;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.post.Post;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.post.VoteRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.shared.config.CacheConfig;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregate platform-health metrics for the admin dashboard (issue #12). Results are cached for
 * {@link com.vegalife.shared.config.CacheConfig}'s TTL, keyed by the resolved time range, so
 * repeated dashboard loads within the window don't re-run every count query.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

  private final UserRepository userRepository;
  private final PostRepository postRepository;
  private final RecipeRepository recipeRepository;
  private final CommentRepository commentRepository;
  private final VoteRepository voteRepository;

  @Transactional(readOnly = true)
  @Cacheable(
      cacheNames = CacheConfig.ADMIN_DASHBOARD_CACHE,
      key = "#request.range + ':' + #request.from + ':' + #request.to")
  public DashboardResponse getDashboard(DashboardRequest request) {
    Instant from = resolveFrom(request);
    Instant to = resolveTo(request);
    if (from.isAfter(to)) {
      throw new ValidationException("from must not be after to");
    }

    return DashboardResponse.builder()
        .range(DashboardRangeResponse.builder().type(request.getRange()).from(from).to(to).build())
        .users(userMetrics(from, to))
        .content(contentMetrics(from, to))
        .engagement(engagementMetrics(from, to))
        .moderationQueue(moderationQueueMetrics())
        .build();
  }

  private DashboardUserMetrics userMetrics(Instant from, Instant to) {
    Set<UUID> activeUsers = new HashSet<>();
    activeUsers.addAll(postRepository.findDistinctAuthorIdsInRange(from, to));
    activeUsers.addAll(commentRepository.findDistinctCommenterIdsInRange(from, to));

    return DashboardUserMetrics.builder()
        .total(userRepository.countByDeletedAtIsNull())
        .newInRange(userRepository.countByCreatedAtBetweenAndDeletedAtIsNull(from, to))
        .activeInRange(activeUsers.size())
        .build();
  }

  private DashboardContentMetrics contentMetrics(Instant from, Instant to) {
    return DashboardContentMetrics.builder()
        .totalPosts(postRepository.countByDeletedAtIsNull())
        .newPosts(postRepository.countByCreatedAtBetweenAndDeletedAtIsNull(from, to))
        .totalRecipes(recipeRepository.countByDeletedAtIsNull())
        .newRecipes(recipeRepository.countByCreatedAtBetweenAndDeletedAtIsNull(from, to))
        .totalComments(commentRepository.countByDeletedAtIsNull())
        .newComments(commentRepository.countByCreatedAtBetweenAndDeletedAtIsNull(from, to))
        .build();
  }

  private DashboardEngagementMetrics engagementMetrics(Instant from, Instant to) {
    return DashboardEngagementMetrics.builder()
        .votesInRange(voteRepository.countByCreatedAtBetween(from, to))
        .commentsInRange(commentRepository.countByCreatedAtBetweenAndDeletedAtIsNull(from, to))
        .build();
  }

  private DashboardModerationQueueMetrics moderationQueueMetrics() {
    return DashboardModerationQueueMetrics.builder()
        .pendingPosts(postRepository.countByFlagAndDeletedAtIsNull(Post.Flag.NEEDS_REVIEW))
        .pendingRecipes(
            recipeRepository.countByModerationStatusAndDeletedAtIsNull(ModerationStatus.PENDING))
        .reportedComments(commentRepository.countByReportCountGreaterThanAndDeletedAtIsNull(0))
        .build();
  }

  private Instant resolveFrom(DashboardRequest request) {
    if ("custom".equals(request.getRange())) {
      return request.getFrom().atStartOfDay(ZoneOffset.UTC).toInstant();
    }
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate start =
        switch (request.getRange()) {
          case "day" -> today;
          case "month" -> today.minusMonths(1);
          default -> today.minusWeeks(1);
        };
    return start.atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  private Instant resolveTo(DashboardRequest request) {
    if ("custom".equals(request.getRange())) {
      return request.getTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
    return Instant.now();
  }
}
