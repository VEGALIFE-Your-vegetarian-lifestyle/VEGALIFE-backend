package com.vegalife.repository.menu;

import com.vegalife.model.menu.Menu;
import com.vegalife.model.menu.MenuStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuRepository extends JpaRepository<Menu, UUID> {

  /** BR-MENU-001: a menu is visible only to its owner. */
  Optional<Menu> findByIdAndUserId(UUID id, UUID userId);

  /**
   * Own menus intersecting an optional inclusive window (BR-MENU-002) and optional status. Each
   * bound is applied only when non-null; with no bounds the result is every own menu. A menu
   * intersects when {@code startDate <= to AND endDate >= from}.
   */
  @Query(
      "select m from Menu m where m.userId = :userId"
          + " and (:from is null or m.endDate >= :from)"
          + " and (:to is null or m.startDate <= :to)"
          + " and (:status is null or m.status = :status)")
  Page<Menu> findOwnMenus(
      @Param("userId") UUID userId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("status") MenuStatus status,
      Pageable pageable);
}
