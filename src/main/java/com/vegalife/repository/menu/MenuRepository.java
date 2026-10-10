package com.vegalife.repository.menu;

import com.vegalife.model.menu.Menu;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuRepository extends JpaRepository<Menu, UUID> {

  /** BR-MENU-001: a menu is visible only to its owner. */
  Optional<Menu> findByIdAndUserId(UUID id, UUID userId);
}
