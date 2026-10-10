package com.vegalife.repository.menu;

import com.vegalife.model.menu.MenuDetail;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuDetailRepository extends JpaRepository<MenuDetail, UUID> {

  /** A menu's days, earliest first, for the detail view. */
  List<MenuDetail> findByMenuIdOrderByDateAscMealTypeAsc(UUID menuId);
}
