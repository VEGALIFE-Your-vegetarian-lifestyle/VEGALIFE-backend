package com.vegalife.service.ingredient;

import com.vegalife.dto.mapper.ingredient.IngredientMapper;
import com.vegalife.dto.request.ingredient.IngredientListRequest;
import com.vegalife.dto.response.ingredient.IngredientResponse;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.IngredientSpecifications;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only suggestion list over the {@code ingredient} master table — backs the create-recipe form
 * autocomplete. Rows are never written here; #84's recipe creation remains the only writer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientService {

  private final IngredientRepository ingredientRepository;
  private final IngredientMapper ingredientMapper;

  /** Any authenticated caller may browse ingredients; the query is read-only. */
  @Transactional(readOnly = true)
  public PageResponse<IngredientResponse> listIngredients(IngredientListRequest request) {
    Pageable pageable =
        PageRequest.of(request.getPage(), request.getSize(), parseSort(request.getSort()));
    Specification<Ingredient> spec = IngredientSpecifications.withNameFilter(request.getName());
    Page<Ingredient> page = ingredientRepository.findAll(spec, pageable);

    log.debug(
        "Listed ingredients: page={} size={} total={}",
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());

    return PageResponse.from(page.map(ingredientMapper::toResponse));
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
    Sort.Direction direction =
        parts.length == 2 && parts[1].trim().equalsIgnoreCase("desc")
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
    return Sort.by(direction, property);
  }
}
