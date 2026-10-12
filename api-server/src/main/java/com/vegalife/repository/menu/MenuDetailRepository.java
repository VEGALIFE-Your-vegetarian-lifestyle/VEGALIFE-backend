package com.vegalife.repository.menu;

import com.vegalife.model.menu.MenuDetail;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuDetailRepository extends JpaRepository<MenuDetail, UUID> {

  /** A menu's days, earliest first, for the detail view. */
  List<MenuDetail> findByMenuIdOrderByDateAscMealTypeAsc(UUID menuId);

  /**
   * The days of several menus with each dish loaded alongside, so assembling a multi-menu meal plan
   * is a bounded number of queries rather than one lazy load per meal. Callers must not pass an
   * empty collection — {@code IN ()} is not valid JPQL.
   */
  @Query(
      """
      SELECT md FROM MenuDetail md
      JOIN FETCH md.dish
      WHERE md.menuId IN :menuIds
      ORDER BY md.menuId ASC, md.date ASC, md.mealType ASC
      """)
  List<MenuDetail> findByMenuIdInWithDish(@Param("menuIds") Collection<UUID> menuIds);

  /** An update replaces the whole day set in one transaction (BR-MENU-006). */
  void deleteByMenuId(UUID menuId);
}
