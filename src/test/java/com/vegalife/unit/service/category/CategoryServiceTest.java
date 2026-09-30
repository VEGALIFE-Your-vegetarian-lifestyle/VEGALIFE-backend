package com.vegalife.unit.service.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.category.CategoryMapper;
import com.vegalife.dto.request.category.CategoryCreateRequest;
import com.vegalife.dto.response.category.CategoryResponse;
import com.vegalife.model.post.Category;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.service.category.CategoryService;
import com.vegalife.shared.exception.DuplicateResourceException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

  @Mock private CategoryRepository categoryRepository;

  @Mock private CategoryMapper categoryMapper;

  @InjectMocks private CategoryService categoryService;

  @Test
  void createCategoryPersistsTrimmedNameAndDescription() {
    CategoryCreateRequest request =
        CategoryCreateRequest.builder()
            .name("  Pure Vegan  ")
            .description("  Strictly plant-based  ")
            .build();
    when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Pure Vegan"))
        .thenReturn(false);
    Category saved =
        Category.builder()
            .id(UUID.randomUUID())
            .name("Pure Vegan")
            .description("Strictly plant-based")
            .createdAt(Instant.now())
            .build();
    when(categoryRepository.save(any(Category.class))).thenReturn(saved);
    CategoryResponse expectedResponse =
        CategoryResponse.builder().id(saved.getId()).name(saved.getName()).build();
    when(categoryMapper.toResponse(saved)).thenReturn(expectedResponse);

    CategoryResponse result = categoryService.createCategory(request);

    assertThat(result).isEqualTo(expectedResponse);
    ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
    verify(categoryRepository).save(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("Pure Vegan");
    assertThat(captor.getValue().getDescription()).isEqualTo("Strictly plant-based");
  }

  @Test
  void createCategoryAllowsBlankDescriptionToBeStoredAsNull() {
    CategoryCreateRequest request =
        CategoryCreateRequest.builder().name("Vegan").description("   ").build();
    when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Vegan")).thenReturn(false);
    Category saved = Category.builder().id(UUID.randomUUID()).name("Vegan").build();
    when(categoryRepository.save(any(Category.class))).thenReturn(saved);
    when(categoryMapper.toResponse(saved)).thenReturn(CategoryResponse.builder().build());

    categoryService.createCategory(request);

    ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
    verify(categoryRepository).save(captor.capture());
    assertThat(captor.getValue().getDescription()).isNull();
  }

  @Test
  void createCategoryRejectsDuplicateNameCaseInsensitively() {
    CategoryCreateRequest request = CategoryCreateRequest.builder().name("vegan").build();
    when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("vegan")).thenReturn(true);

    assertThatThrownBy(() -> categoryService.createCategory(request))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Category name already exists");

    verify(categoryRepository, never()).save(any());
  }
}
