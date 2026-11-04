package com.vegalife.dto.mapper.subscription;

import com.vegalife.dto.response.subscription.AvailablePlanResponse;
import com.vegalife.model.subscription.AiPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

  SubscriptionMapper INSTANCE = Mappers.getMapper(SubscriptionMapper.class);

  @Mapping(target = "price.amount", source = "priceAmount")
  @Mapping(target = "price.currency", source = "priceCurrency")
  AvailablePlanResponse toAvailablePlan(AiPlan plan);
}
