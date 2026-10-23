package com.vegalife.dto.mapper.admin;

import com.vegalife.dto.response.admin.AdminPlanResponse;
import com.vegalife.model.subscription.AiPlan;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminPlanMapper {

  AdminPlanResponse toResponse(AiPlan plan);
}
