package com.vegalife.service.admin;

import com.vegalife.dto.mapper.admin.AdminRecipeMapper;
import com.vegalife.dto.request.admin.RecipeListRequest;
import com.vegalife.dto.response.admin.AdminRecipeListResponse;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.recipe.RecipeSpecifications;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRecipeService {

  private static final Set<String> SORTABLE_PROPERTIES = Set.of("createdAt", "updatedAt", "name");

  private static final String SORTABLE_PROPERTIES_MESSAGE =
      "Sort property must be one of: createdAt, updatedAt, name";

  private final RecipeRepository recipeRepository;
  private final AdminRecipeMapper adminRecipeMapper;

  @Transactional(readOnly = true)
  public PageResponse<AdminRecipeListResponse> listRecipes(RecipeListRequest request) {
    UUID userId = request.getUserId();
    UUID categoryId = request.getCategoryId();
    Instant createdFrom = request.getCreatedFrom();
    Instant createdTo = request.getCreatedTo();

    if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
      throw new ValidationException("createdFrom must be before createdTo");
    }

    Pageable pageable =
        PageRequest.of(request.getPage(), request.getSize(), parseSort(request.getSort()));
    Specification<Recipe> spec =
        RecipeSpecifications.allWithFilters(userId, categoryId, createdFrom, createdTo);
    Page<Recipe> page = recipeRepository.findAll(spec, pageable);

    log.debug(
        "Listed recipes page={} size={} total={}",
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());

    Page<AdminRecipeListResponse> mapped = page.map(adminRecipeMapper::toResponse);
    return PageResponse.from(mapped);
  }

  private Sort parseSort(String sort) {
    String[] parts = sort.split(",");
    if (parts.length == 0 || parts.length > 2) {
      throw new ValidationException("Sort must be in the form property,asc|desc");
    }
    String property = parts[0].trim();
    if (property.isBlank()) {
      throw new ValidationException("Sort property must not be blank");
    }
    if (!SORTABLE_PROPERTIES.contains(property)) {
      throw new ValidationException(SORTABLE_PROPERTIES_MESSAGE);
    }
    Sort.Direction direction =
        parts.length == 2 && parts[1].trim().equalsIgnoreCase("asc")
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
    return Sort.by(direction, property);
  }
}
