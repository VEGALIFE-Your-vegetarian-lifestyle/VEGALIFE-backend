package com.vegalife.unit.service.ingredient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.ingredient.IngredientMapper;
import com.vegalife.dto.request.ingredient.IngredientListRequest;
import com.vegalife.dto.response.ingredient.IngredientResponse;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.service.ingredient.IngredientService;
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
class IngredientServiceTest {

  @Mock private IngredientRepository ingredientRepository;

  @Mock private IngredientMapper ingredientMapper;

  @InjectMocks private IngredientService ingredientService;

  @Test
  void listIngredientsWithDefaultsUsesDefaultPaginationAndNameAscSort() {
    Ingredient tofu = Ingredient.builder().id(UUID.randomUUID()).name("tofu").build();
    Pageable expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));
    Page<Ingredient> page = new PageImpl<>(List.of(tofu), expectedPageable, 1);
    when(ingredientRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);
    IngredientResponse response =
        IngredientResponse.builder().id(tofu.getId()).name("tofu").build();
    when(ingredientMapper.toResponse(tofu)).thenReturn(response);

    PageResponse<IngredientResponse> result =
        ingredientService.listIngredients(new IngredientListRequest());

    assertThat(result.getContent()).containsExactly(response);
    assertThat(result.getPage()).isZero();
    assertThat(result.getSize()).isEqualTo(20);
    assertThat(result.getTotalElements()).isEqualTo(1);
    verify(ingredientRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void listIngredientsAppliesCustomPageSizeSortAndNameFilter() {
    IngredientListRequest request =
        IngredientListRequest.builder().page(1).size(5).sort("createdAt,desc").name("tof").build();
    Pageable expectedPageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<Ingredient> page = new PageImpl<>(List.of(), expectedPageable, 0);
    when(ingredientRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);

    PageResponse<IngredientResponse> result = ingredientService.listIngredients(request);

    assertThat(result.getContent()).isEmpty();
    verify(ingredientRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void listIngredientsRejectsMalformedSort() {
    IngredientListRequest request = IngredientListRequest.builder().sort("a,b,c").build();

    assertThatThrownBy(() -> ingredientService.listIngredients(request))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void listIngredientsRejectsBlankSortProperty() {
    IngredientListRequest request = IngredientListRequest.builder().sort(" ,asc").build();

    assertThatThrownBy(() -> ingredientService.listIngredients(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Sort property must not be blank");
  }

  @Test
  void listIngredientsNeverWritesToTheRepository() {
    Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));
    when(ingredientRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

    ingredientService.listIngredients(new IngredientListRequest());

    verify(ingredientRepository).findAll(any(Specification.class), any(Pageable.class));
    verify(ingredientRepository, never()).save(any());
    verify(ingredientRepository, never()).delete(any(Ingredient.class));
  }
}
