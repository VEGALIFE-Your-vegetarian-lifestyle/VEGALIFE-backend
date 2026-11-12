package com.vegalife.unit.service.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.enums.menu.MenuStatus;
import com.vegalife.model.menu.Menu;
import com.vegalife.repository.menu.MenuDetailRepository;
import com.vegalife.repository.menu.MenuRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.service.menu.MenuService;
import com.vegalife.service.menu.MenuValidator;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** BR-MENU-008: deleting an owned menu soft-deletes it, in any status, owner-scoped only. */
@ExtendWith(MockitoExtension.class)
class MenuServiceDeleteTest {

  private static final UUID USER = UUID.randomUUID();
  private static final UUID MENU_ID = UUID.randomUUID();
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

  @Mock private MenuRepository menuRepository;

  @Mock private MenuDetailRepository menuDetailRepository;

  @Mock private MenuValidator menuValidator;

  @Mock private DishRepository dishRepository;

  @InjectMocks private MenuService menuService;

  @Test
  void deletingAnOwnedMenuStampsDeletedAtAndPersistsIt() {
    Menu menu = menu(MenuStatus.drafted);
    when(menuRepository.findByIdAndUserIdAndDeletedAtIsNull(MENU_ID, USER))
        .thenReturn(Optional.of(menu));
    when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Instant before = Instant.now();
    menuService.deleteMenu(USER, MENU_ID);

    ArgumentCaptor<Menu> saved = ArgumentCaptor.forClass(Menu.class);
    verify(menuRepository).save(saved.capture());
    assertThat(saved.getValue().getDeletedAt()).isNotNull().isAfterOrEqualTo(before);
  }

  /** Any status is deletable, including the live scheduled week and the terminal states. */
  @Test
  void everyStatusCanBeDeleted() {
    for (MenuStatus status : MenuStatus.values()) {
      Menu menu = menu(status);
      when(menuRepository.findByIdAndUserIdAndDeletedAtIsNull(MENU_ID, USER))
          .thenReturn(Optional.of(menu));
      when(menuRepository.save(any(Menu.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      menuService.deleteMenu(USER, MENU_ID);

      assertThat(menu.getDeletedAt()).as("status %s is deletable", status).isNotNull();
    }
  }

  @Test
  void foreignOrUnknownIdIsNotFoundAndNothingIsWritten() {
    when(menuRepository.findByIdAndUserIdAndDeletedAtIsNull(MENU_ID, USER))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> menuService.deleteMenu(USER, MENU_ID))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Menu not found");
    verify(menuRepository, never()).save(any(Menu.class));
  }

  /**
   * A repeat delete reads as a missing menu: the second lookup already excludes the now-deleted
   * row, so the caller gets a 404 instead of a silent second success.
   */
  @Test
  void aSecondDeleteIsNotFound() {
    when(menuRepository.findByIdAndUserIdAndDeletedAtIsNull(MENU_ID, USER))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> menuService.deleteMenu(USER, MENU_ID))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  /**
   * Deletion never touches the recorded days: the menu_detail rows stay for the row that remains,
   * so a soft delete cannot lose the plan's history.
   */
  @Test
  void deletionLeavesTheMealRowsAlone() {
    when(menuRepository.findByIdAndUserIdAndDeletedAtIsNull(MENU_ID, USER))
        .thenReturn(Optional.of(menu(MenuStatus.scheduled)));
    when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));

    menuService.deleteMenu(USER, MENU_ID);

    verify(menuDetailRepository, never()).deleteByMenuId(any());
  }

  private Menu menu(MenuStatus status) {
    return Menu.builder()
        .id(MENU_ID)
        .userId(USER)
        .startDate(MONDAY)
        .endDate(MONDAY.plusDays(6))
        .status(status)
        .build();
  }
}
