package com.vegalife.service.category;

import com.vegalife.dto.mapper.category.CategoryMapper;
import com.vegalife.dto.request.category.CategoryCreateRequest;
import com.vegalife.dto.response.category.CategoryResponse;
import com.vegalife.model.post.Category;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.shared.exception.DuplicateResourceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BR-ADMIN-003: only an Administrator may create content categories. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final CategoryMapper categoryMapper;

  @Transactional
  public CategoryResponse createCategory(CategoryCreateRequest request) {
    String name = request.getName().trim();

    if (categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(name)) {
      throw new DuplicateResourceException("Category name already exists");
    }

    String description =
        request.getDescription() == null || request.getDescription().isBlank()
            ? null
            : request.getDescription().trim();

    Category category = Category.builder().name(name).description(description).build();
    Category saved = categoryRepository.save(category);

    log.info("Category {} created: {}", saved.getId(), saved.getName());

    return categoryMapper.toResponse(saved);
  }
}
