package com.vegalife.repository.menu;

import com.vegalife.model.menu.Menu;
import com.vegalife.model.menu.MenuStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuRepository extends JpaRepository<Menu, UUID>, JpaSpecificationExecutor<Menu> {

  /** BR-MENU-001: a menu is visible only to its owner. */
  Optional<Menu> findByIdAndUserId(UUID id, UUID userId);

  /**
   * BR-MENU-003 / BR-MENU-007: whether the user already has another scheduled menu whose window
   * overlaps {@code [startDate, endDate]} inclusively. Used as the scheduling pre-check so an
   * overlapping schedule is a clear 409 rather than V30's exclusion constraint surfacing as a raw
   * persistence error.
   */
  boolean existsByUserIdAndStatusAndIdNotAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
      UUID userId, MenuStatus status, UUID id, LocalDate startDate, LocalDate endDate);
}
