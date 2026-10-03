package com.vegalife.dto.mapper.subscription;

import com.vegalife.dto.response.subscription.AvailablePlanResponse;
import com.vegalife.dto.response.subscription.PaymentResponse;
import com.vegalife.dto.response.subscription.PlanSummaryResponse;
import com.vegalife.dto.response.subscription.SubscriptionUsageResponse;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.PaymentLedger;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

  SubscriptionMapper INSTANCE = Mappers.getMapper(SubscriptionMapper.class);

  @Mapping(target = "price.amount", source = "priceAmount")
  @Mapping(target = "price.currency", source = "priceCurrency")
  PlanSummaryResponse toPlanSummary(AiPlan plan);

  @Mapping(target = "price.amount", source = "priceAmount")
  @Mapping(target = "price.currency", source = "priceCurrency")
  AvailablePlanResponse toAvailablePlan(AiPlan plan);

  PaymentResponse toPaymentResponse(PaymentLedger ledger, String planCode);

  @Mapping(target = "limit", source = "plan.monthlyRequestLimit")
  SubscriptionUsageResponse toUsageResponse(
      long used, AiPlan plan, Instant periodStart, Instant periodEnd);
}
