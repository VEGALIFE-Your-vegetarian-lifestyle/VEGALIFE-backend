package com.vegalife.repository.post;

import com.vegalife.model.post.Category;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

  List<Category> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

  boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

  boolean existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot(String name, UUID id);

  List<Category> findByDeletedAtIsNullOrderByNameAsc();
}
