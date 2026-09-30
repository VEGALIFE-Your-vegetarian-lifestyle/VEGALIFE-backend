package com.vegalife.unit.service.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.category.CategoryMapper;
import com.vegalife.dto.request.category.CategoryCreateRequest;
import com.vegalife.dto.request.category.CategoryUpdateRequest;
import com.vegalife.dto.response.category.CategoryResponse;
import com.vegalife.model.post.Category;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.service.category.CategoryService;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Optional;
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

  @Test
  void updateCategoryAppliesTrimmedNameAndDescriptionWhenProvided() {
    UUID categoryId = UUID.randomUUID();
    Category existing = Category.builder().id(categoryId).name("Vegan").description("Old").build();
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existing));
    when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot(
            "High-Protein Vegan", categoryId))
        .thenReturn(false);
    when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
    when(categoryMapper.toResponse(any(Category.class)))
        .thenReturn(CategoryResponse.builder().id(categoryId).build());

    CategoryUpdateRequest request =
        CategoryUpdateRequest.builder()
            .name("  High-Protein Vegan  ")
            .description("  Updated  ")
            .build();

    categoryService.updateCategory(categoryId, request);

    ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
    verify(categoryRepository).save(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("High-Protein Vegan");
    assertThat(captor.getValue().getDescription()).isEqualTo("Updated");
  }

  @Test
  void updateCategoryLeavesOmittedFieldsUnchanged() {
    UUID categoryId = UUID.randomUUID();
    Category existing = Category.builder().id(categoryId).name("Vegan").description("Old").build();
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existing));
    when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
    when(categoryMapper.toResponse(any(Category.class)))
        .thenReturn(CategoryResponse.builder().build());

    categoryService.updateCategory(
        categoryId, CategoryUpdateRequest.builder().description("New").build());

    ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
    verify(categoryRepository).save(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("Vegan");
    assertThat(captor.getValue().getDescription()).isEqualTo("New");
    verify(categoryRepository, never())
        .existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot(any(), eq(categoryId));
  }

  @Test
  void updateCategoryRejectsDuplicateNameAmongOtherActiveCategories() {
    UUID categoryId = UUID.randomUUID();
    Category existing = Category.builder().id(categoryId).name("Vegan").build();
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existing));
    when(categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot("Dessert", categoryId))
        .thenReturn(true);

    CategoryUpdateRequest request = CategoryUpdateRequest.builder().name("Dessert").build();

    assertThatThrownBy(() -> categoryService.updateCategory(categoryId, request))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Category name already exists");

    verify(categoryRepository, never()).save(any());
  }

  @Test
  void updateCategoryThrowsNotFoundWhenMissingOrSoftDeleted() {
    UUID categoryId = UUID.randomUUID();
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

    CategoryUpdateRequest request = CategoryUpdateRequest.builder().name("Vegan").build();

    assertThatThrownBy(() -> categoryService.updateCategory(categoryId, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Category not found");
  }

  @Test
  void updateCategoryTreatsSoftDeletedCategoryAsNotFound() {
    UUID categoryId = UUID.randomUUID();
    Category deleted =
        Category.builder().id(categoryId).name("Vegan").deletedAt(Instant.now()).build();
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(deleted));

    CategoryUpdateRequest request = CategoryUpdateRequest.builder().name("New Name").build();

    assertThatThrownBy(() -> categoryService.updateCategory(categoryId, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Category not found");
  }

  @Test
  void deleteCategorySoftDeletesAnActiveCategory() {
    UUID categoryId = UUID.randomUUID();
    Category existing = Category.builder().id(categoryId).name("Vegan").build();
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existing));
    when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

    categoryService.deleteCategory(categoryId);

    ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
    verify(categoryRepository).save(captor.capture());
    assertThat(captor.getValue().getDeletedAt()).isNotNull();
  }

  @Test
  void deleteCategoryThrowsNotFoundWhenMissing() {
    UUID categoryId = UUID.randomUUID();
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> categoryService.deleteCategory(categoryId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Category not found");

    verify(categoryRepository, never()).save(any());
  }

  @Test
  void deleteCategoryTreatsAlreadySoftDeletedCategoryAsNotFound() {
    UUID categoryId = UUID.randomUUID();
    Category deleted =
        Category.builder().id(categoryId).name("Vegan").deletedAt(Instant.now()).build();
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(deleted));

    assertThatThrownBy(() -> categoryService.deleteCategory(categoryId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Category not found");

    verify(categoryRepository, never()).save(any());
  }
}
