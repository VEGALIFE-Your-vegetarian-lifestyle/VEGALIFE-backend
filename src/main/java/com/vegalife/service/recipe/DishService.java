package com.vegalife.service.recipe;

import com.vegalife.dto.mapper.recipe.DishMapper;
import com.vegalife.dto.request.recipe.DishListRequest;
import com.vegalife.dto.response.recipe.DishResponse;
import com.vegalife.model.recipe.Dish;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.DishSpecifications;
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

/** Read-only dish queries backing the recipe-create dish suggestions (#93). */
@Service
@RequiredArgsConstructor
@Slf4j
public class DishService {

  private final DishRepository dishRepository;
  private final DishMapper dishMapper;

  /** Any authenticated member may browse active dishes to autocomplete a dishName. */
  @Transactional(readOnly = true)
  public PageResponse<DishResponse> listDishes(DishListRequest request) {
    Pageable pageable =
        PageRequest.of(request.getPage(), request.getSize(), parseSort(request.getSort()));
    Specification<Dish> spec = DishSpecifications.activeWithNameFilter(request.getName());
    Page<Dish> page = dishRepository.findAll(spec, pageable);

    return PageResponse.from(page.map(dishMapper::toResponse));
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
