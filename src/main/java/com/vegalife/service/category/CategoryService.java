package com.vegalife.service.category;

import com.vegalife.dto.mapper.category.CategoryMapper;
import com.vegalife.dto.request.category.CategoryCreateRequest;
import com.vegalife.dto.request.category.CategoryUpdateRequest;
import com.vegalife.dto.response.category.CategoryResponse;
import com.vegalife.model.post.Category;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BR-ADMIN-003: only an Administrator may create, edit, retire, or remove content categories. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final CategoryMapper categoryMapper;

  /** Every user (including anonymous callers) may browse active categories. */
  @Transactional(readOnly = true)
  public List<CategoryResponse> listCategories() {
    List<Category> categories = categoryRepository.findByDeletedAtIsNullOrderByNameAsc();
    return categoryMapper.toResponseList(categories);
  }

  @Transactional
  public CategoryResponse createCategory(CategoryCreateRequest request) {
    String name = request.getName().trim();

    if (categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(name)) {
      throw new DuplicateResourceException("Category name already exists");
    }

    String description = normalizeDescription(request.getDescription());

    Category category = Category.builder().name(name).description(description).build();
    Category saved = categoryRepository.save(category);

    log.info("Category {} created: {}", saved.getId(), saved.getName());

    return categoryMapper.toResponse(saved);
  }

  /**
   * Partial update: an omitted or null field keeps its current value; the description cannot be
   * cleared by sending {@code null} (mirrors {@code PostService}'s edit semantics).
   */
  @Transactional
  public CategoryResponse updateCategory(UUID categoryId, CategoryUpdateRequest request) {
    Category category = findActiveCategory(categoryId);

    if (request.getName() != null) {
      String name = request.getName().trim();
      if (categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot(name, categoryId)) {
        throw new DuplicateResourceException("Category name already exists");
      }
      category.setName(name);
    }

    if (request.getDescription() != null) {
      category.setDescription(normalizeDescription(request.getDescription()));
    }

    Category saved = categoryRepository.save(category);

    log.info("Category {} updated: {}", saved.getId(), saved.getName());

    return categoryMapper.toResponse(saved);
  }

  /**
   * BR-ADMIN-003: a category is never hard-deleted, whether or not existing content still
   * references it — retiring only sets {@code deletedAt} so existing posts keep their category
   * links intact and the category simply stops being assignable to new/edited posts (BR-CONTENT-004
   * filters on {@code deletedAt IS NULL} at assignment time).
   */
  @Transactional
  public void deleteCategory(UUID categoryId) {
    Category category = findActiveCategory(categoryId);
    category.setDeletedAt(Instant.now());
    categoryRepository.save(category);

    log.info("Category {} retired", category.getId());
  }

  private Category findActiveCategory(UUID categoryId) {
    return categoryRepository
        .findById(categoryId)
        .filter(c -> c.getDeletedAt() == null)
        .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
  }

  private String normalizeDescription(String description) {
    return description == null || description.isBlank() ? null : description.trim();
  }
}
