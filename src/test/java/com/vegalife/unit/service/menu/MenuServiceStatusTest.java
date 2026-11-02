package com.vegalife.unit.service.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vegalife.dto.response.menu.MenuDetailResponse;
import com.vegalife.enums.menu.MenuStatus;
import com.vegalife.model.menu.Menu;
import com.vegalife.repository.menu.MenuDetailRepository;
import com.vegalife.repository.menu.MenuRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.service.menu.MenuService;
import com.vegalife.service.menu.MenuValidator;
import com.vegalife.shared.exception.InvalidMenuTransitionException;
import com.vegalife.shared.exception.MenuOverlapException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuServiceStatusTest {

  private static final UUID USER = UUID.randomUUID();
  private static final UUID MENU_ID = UUID.randomUUID();
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

  @Mock private MenuRepository menuRepository;

  @Mock private MenuDetailRepository menuDetailRepository;

  @Mock private MenuValidator menuValidator;

  @Mock private DishRepository dishRepository;

  @InjectMocks private MenuService menuService;

  @Test
  void schedulingADraftRunsTheOverlapPreCheckAndPersistsTheStatus() {
    Menu draft = menu(MenuStatus.drafted);
    when(menuRepository.findByIdAndUserId(MENU_ID, USER)).thenReturn(Optional.of(draft));
    when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(menuDetailRepository.findByMenuIdOrderByDateAscMealTypeAsc(MENU_ID)).thenReturn(List.of());

    MenuDetailResponse response = menuService.changeStatus(USER, MENU_ID, "scheduled");

    verify(menuRepository)
        .existsByUserIdAndStatusAndIdNotAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            USER, MenuStatus.scheduled, MENU_ID, MONDAY, MONDAY.plusDays(6));
    ArgumentCaptor<Menu> menuCaptor = ArgumentCaptor.forClass(Menu.class);
    verify(menuRepository).save(menuCaptor.capture());
    assertThat(menuCaptor.getValue().getStatus()).isEqualTo(MenuStatus.scheduled);
    assertThat(response.getStatus()).isEqualTo("scheduled");
  }

  @Test
  void schedulingIsRefusedWhenAnotherScheduledMenuOverlaps() {
    when(menuRepository.findByIdAndUserId(MENU_ID, USER))
        .thenReturn(Optional.of(menu(MenuStatus.drafted)));
    when(menuRepository
            .existsByUserIdAndStatusAndIdNotAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                any(), any(), any(), any(), any()))
        .thenReturn(true);

    assertThatThrownBy(() -> menuService.changeStatus(USER, MENU_ID, "scheduled"))
        .isInstanceOf(MenuOverlapException.class)
        .hasMessageContaining("overlaps");

    verify(menuRepository, never()).save(any(Menu.class));
    verify(menuDetailRepository, never()).findByMenuIdOrderByDateAscMealTypeAsc(any());
  }

  @Test
  void cancellingAndCompletingRunNoOverlapCheck() {
    when(menuRepository.findByIdAndUserId(MENU_ID, USER))
        .thenReturn(Optional.of(menu(MenuStatus.scheduled)));
    when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(menuDetailRepository.findByMenuIdOrderByDateAscMealTypeAsc(MENU_ID)).thenReturn(List.of());

    MenuDetailResponse response = menuService.changeStatus(USER, MENU_ID, "completed");

    verifyNoInteractions(menuValidator);
    verify(menuRepository, never())
        .existsByUserIdAndStatusAndIdNotAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            any(), any(), any(), any(), any());
    assertThat(response.getStatus()).isEqualTo("completed");
  }

  @Test
  void cancellingADraftIsLegal() {
    when(menuRepository.findByIdAndUserId(MENU_ID, USER))
        .thenReturn(Optional.of(menu(MenuStatus.drafted)));
    when(menuRepository.save(any(Menu.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(menuDetailRepository.findByMenuIdOrderByDateAscMealTypeAsc(MENU_ID)).thenReturn(List.of());

    assertThat(menuService.changeStatus(USER, MENU_ID, "cancelled").getStatus())
        .isEqualTo("cancelled");
  }

  @Test
  void everyIllegalTransitionIsAConflictAndChangesNothing() {
    for (MenuStatus from : MenuStatus.values()) {
      for (String target : List.of("scheduled", "cancelled", "completed")) {
        if (isLegal(from, target)) {
          continue;
        }
        when(menuRepository.findByIdAndUserId(MENU_ID, USER)).thenReturn(Optional.of(menu(from)));

        assertThatThrownBy(() -> menuService.changeStatus(USER, MENU_ID, target))
            .isInstanceOf(InvalidMenuTransitionException.class)
            .hasMessageContaining("Cannot transition a " + from + " menu to");
      }
    }
    verify(menuRepository, never()).save(any(Menu.class));
    verify(menuDetailRepository, never()).findByMenuIdOrderByDateAscMealTypeAsc(any());
  }

  @Test
  void targetsOutsideTheVocabularyAreRejectedBeforeTheMenuIsLoaded() {
    for (String target : List.of("drafted", "DRAFTED", "archived", "", "   ")) {
      assertThatThrownBy(() -> menuService.changeStatus(USER, MENU_ID, target))
          .isInstanceOf(ValidationException.class)
          .hasMessageContaining("scheduled, cancelled, completed");
    }
    assertThatThrownBy(() -> menuService.changeStatus(USER, MENU_ID, null))
        .isInstanceOf(ValidationException.class);
    verifyNoInteractions(menuRepository);
  }

  @Test
  void foreignOrUnknownIdIsNotFound() {
    when(menuRepository.findByIdAndUserId(MENU_ID, USER)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> menuService.changeStatus(USER, MENU_ID, "cancelled"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Menu not found");
    verify(menuRepository, never()).save(any(Menu.class));
  }

  /** BR-MENU-007's table, restated so a table change fails this test rather than slipping by. */
  private boolean isLegal(MenuStatus from, String target) {
    return switch (from) {
      case drafted -> target.equals("scheduled") || target.equals("cancelled");
      case scheduled -> target.equals("cancelled") || target.equals("completed");
      case cancelled, completed -> false;
    };
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
