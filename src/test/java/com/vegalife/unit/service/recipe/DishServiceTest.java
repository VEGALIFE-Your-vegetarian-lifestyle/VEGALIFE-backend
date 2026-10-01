package com.vegalife.unit.service.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.recipe.DishMapper;
import com.vegalife.dto.request.recipe.DishListRequest;
import com.vegalife.dto.response.recipe.DishResponse;
import com.vegalife.model.recipe.Dish;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.service.recipe.DishService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ValidationException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class DishServiceTest {

  @Mock private DishRepository dishRepository;

  @Mock private DishMapper dishMapper;

  @InjectMocks private DishService dishService;

  @Test
  void listDishesWithDefaultsUsesDefaultPaginationAndNameAscSort() {
    Dish pho = Dish.builder().id(UUID.randomUUID()).name("pho chay").build();
    Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));
    Page<Dish> page = new PageImpl<>(List.of(pho), expectedPageable, 1);
    when(dishRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    DishResponse response = DishResponse.builder().id(pho.getId()).name("pho chay").build();
    when(dishMapper.toResponse(pho)).thenReturn(response);

    PageResponse<DishResponse> result = dishService.listDishes(new DishListRequest());

    assertThat(result.getContent()).containsExactly(response);
    assertThat(result.getPage()).isZero();
    assertThat(result.getSize()).isEqualTo(20);
    assertThat(result.getTotalElements()).isEqualTo(1);
    verify(dishRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void listDishesAppliesCustomPageSizeSortAndNameFilter() {
    DishListRequest request =
        DishListRequest.builder().page(1).size(5).sort("createdAt,desc").name("pho").build();
    Pageable expectedPageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<Dish> page = new PageImpl<>(List.of(), expectedPageable, 0);
    when(dishRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

    PageResponse<DishResponse> result = dishService.listDishes(request);

    assertThat(result.getContent()).isEmpty();
    verify(dishRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void listDishesRejectsMalformedSort() {
    DishListRequest request = DishListRequest.builder().sort("a,b,c").build();

    assertThatThrownBy(() -> dishService.listDishes(request))
        .isInstanceOf(ValidationException.class);

    verify(dishRepository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  void listDishesPerformsNoWrites() {
    Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));
    when(dishRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), expectedPageable, 0));

    dishService.listDishes(new DishListRequest());

    verify(dishRepository, never()).save(any());
    verify(dishRepository, never()).delete(any(Dish.class));
    verify(dishRepository, never()).saveAll(any());
  }
}
