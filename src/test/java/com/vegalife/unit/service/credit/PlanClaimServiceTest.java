package com.vegalife.unit.service.credit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.model.subscription.AiPlan;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.service.credit.PlanClaimService;
import com.vegalife.shared.exception.DuplicateResourceException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanClaimServiceTest {

  @Mock private AiSubscriptionRepository subscriptionRepository;

  @InjectMocks private PlanClaimService planClaimService;

  private UUID userId;
  private AiPlan freePlan;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    freePlan = AiPlan.builder().id(UUID.randomUUID()).code("FREE").priceAmount(0L).build();
  }

  @Test
  void claimOrThrow_succeedsWhenThePartialUniqueIndexAcceptedTheRow() {
    when(subscriptionRepository.insertClaim(userId, freePlan.getId())).thenReturn(1);

    planClaimService.claimOrThrow(userId, freePlan);

    verify(subscriptionRepository).insertClaim(userId, freePlan.getId());
  }

  @Test
  void claimOrThrow_conflictMeansAlreadyClaimed() {
    when(subscriptionRepository.insertClaim(userId, freePlan.getId())).thenReturn(0);

    assertThatThrownBy(() -> planClaimService.claimOrThrow(userId, freePlan))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Plan already claimed");
  }
}
