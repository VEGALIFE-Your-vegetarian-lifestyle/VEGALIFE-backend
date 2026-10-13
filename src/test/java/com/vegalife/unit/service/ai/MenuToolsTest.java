package com.vegalife.unit.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.request.menu.CreateMenuRequest;
import com.vegalife.dto.response.menu.MenuDetailResponse;
import com.vegalife.dto.response.menu.MenuSummaryResponse;
import com.vegalife.service.ai.AiToolContext;
import com.vegalife.service.ai.MenuTools;
import com.vegalife.service.menu.MenuQueryFilter;
import com.vegalife.service.menu.MenuQueryFilterResolver;
import com.vegalife.service.menu.MenuService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/**
 * The five menu tools in-process (ADR-013), replacing {@code mcp-server}'s MenuToolsTest. The tool
 * surface — names, descriptions, parameter schemas and the readable result convention — must be
 * unchanged, so these tests pin delegation to {@link MenuService} and the exact text the model
 * sees.
 *
 * <p>What moved: date/status/range validation is no longer duplicated in the tool layer, it comes
 * from {@link MenuQueryFilterResolver} — the same component the REST endpoints already use, so a
 * tool call and a controller call now reject identical input with identical wording.
 */
@ExtendWith(MockitoExtension.class)
class MenuToolsTest {

  private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");
  private static final UUID MENU_ID = UUID.fromString("7c1f2b90-0000-4000-8000-000000000000");
  private static final UUID DISH_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");

  /** Carries a user id, as the chat endpoint supplies for an authenticated turn. */
  private static final ToolContext CONTEXT = new ToolContext(AiToolContext.forUser(USER));

  /** Holds the key with a null value — an unattributed turn, which the tools must refuse. */
  private static final ToolContext NO_IDENTITY = new ToolContext(AiToolContext.forUser(null));

  private static final String REFUSED_IDENTITY =
      "error: caller identity missing (no caller identity on this tool invocation)";

  @Mock private MenuService menuService;

  private MenuTools menuTools;

  @BeforeEach
  void setUp() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    menuTools =
        new MenuTools(menuService, new MenuQueryFilterResolver(), validator, new ObjectMapper());
  }

  @Test
  void getMenu_relaysTheListEnvelopeAndDefaultsPaging() {
    when(menuService.listMenus(eq(USER), any(), anyInt(), anyInt(), any())).thenReturn(emptyPage());

    String result = menuTools.getMenu(null, null, null, null, null, null, CONTEXT);

    assertThat(result)
        .contains("\"success\":true")
        .contains("Menus retrieved successfully")
        .doesNotStartWith("\"");
    ArgumentCaptor<MenuQueryFilter> filter = ArgumentCaptor.forClass(MenuQueryFilter.class);
    verify(menuService).listMenus(eq(USER), filter.capture(), eq(0), eq(20), isNull());
    assertThat(filter.getValue().getFrom()).isNull();
    assertThat(filter.getValue().getStatus()).isNull();
  }

  @Test
  void getMenu_clampsPageAndSizeAndForwardsTheSort() {
    when(menuService.listMenus(eq(USER), any(), anyInt(), anyInt(), any())).thenReturn(emptyPage());

    menuTools.getMenu("2026-10-05", "2026-10-11", "drafted", -3, 500, "startDate,asc", CONTEXT);

    verify(menuService).listMenus(eq(USER), any(), eq(0), eq(100), eq("startDate,asc"));
  }

  @Test
  void getMenu_rejectsMalformedFromBeforeTouchingTheService() {
    String result = menuTools.getMenu("not-a-date", null, null, null, null, null, CONTEXT);

    assertThat(result).isEqualTo("error: Invalid from 'not-a-date'. Use YYYY-MM-DD.");
    verifyNoInteractions(menuService);
  }

  @Test
  void getMenu_rejectsUnknownStatus() {
    String result = menuTools.getMenu(null, null, "archived", null, null, null, CONTEXT);

    assertThat(result)
        .isEqualTo(
            "error: Unknown status 'archived'. Use drafted, scheduled, cancelled or completed.");
    verifyNoInteractions(menuService);
  }

  @Test
  void getMenu_rejectsAReversedRange() {
    String result = menuTools.getMenu("2026-10-11", "2026-10-05", null, null, null, null, CONTEXT);

    assertThat(result).isEqualTo("error: from must be on or before to");
    verifyNoInteractions(menuService);
  }

  @Test
  void getMenu_refusesWhenTheCallerIsUnattributed() {
    String result = menuTools.getMenu(null, null, null, null, null, null, NO_IDENTITY);

    assertThat(result).isEqualTo(REFUSED_IDENTITY);
    verifyNoInteractions(menuService);
  }

  @Test
  void getMealPlan_menuIdSelectsOneMenuAndTheRangeStillParses() {
    when(menuService.listMealPlans(eq(USER), any(), eq(MENU_ID))).thenReturn(List.of());

    String result = menuTools.getMealPlan("2026-10-05", "2026-10-11", MENU_ID.toString(), CONTEXT);

    assertThat(result).contains("\"success\":true").contains("Meal plan retrieved successfully");
    ArgumentCaptor<MenuQueryFilter> filter = ArgumentCaptor.forClass(MenuQueryFilter.class);
    verify(menuService).listMealPlans(eq(USER), filter.capture(), eq(MENU_ID));
    assertThat(filter.getValue().getFrom()).isEqualTo(LocalDate.parse("2026-10-05"));
    assertThat(filter.getValue().getTo()).isEqualTo(LocalDate.parse("2026-10-11"));
  }

  @Test
  void getMealPlan_rejectsAMalformedMenuId() {
    String result = menuTools.getMealPlan(null, null, "not-a-uuid", CONTEXT);

    assertThat(result).isEqualTo("error: menuId must be a UUID");
    verifyNoInteractions(menuService);
  }

  @Test
  void getMealPlan_relaysAMissingOrForeignMenuAsAReadableNotFound() {
    when(menuService.listMealPlans(eq(USER), any(), any()))
        .thenThrow(new ResourceNotFoundException("Menu not found"));

    String result = menuTools.getMealPlan(null, null, MENU_ID.toString(), CONTEXT);

    assertThat(result).isEqualTo("error: Menu not found");
  }

  @Test
  void createMenu_mapsTheDaysAndMealsOntoTheServiceRequest() {
    when(menuService.createMenu(eq(USER), any()))
        .thenReturn(MenuDetailResponse.builder().id(MENU_ID).status("drafted").build());

    String result = menuTools.createMenu("2026-10-19", breakfastDay(), null, null, CONTEXT);

    assertThat(result)
        .contains("\"success\":true")
        .contains("Menu created successfully")
        .doesNotStartWith("\"");
    ArgumentCaptor<CreateMenuRequest> sent = ArgumentCaptor.forClass(CreateMenuRequest.class);
    verify(menuService).createMenu(eq(USER), sent.capture());
    CreateMenuRequest request = sent.getValue();
    assertThat(request.getStartDate()).isEqualTo(LocalDate.parse("2026-10-19"));
    assertThat(request.getDays()).hasSize(1);
    assertThat(request.getDays().get(0).getDate()).isEqualTo(LocalDate.parse("2026-10-19"));
    assertThat(request.getDays().get(0).getMeals()).hasSize(1);
    assertThat(request.getDays().get(0).getMeals().get(0).getMealType()).isEqualTo("BREAKFAST");
    assertThat(request.getDays().get(0).getMeals().get(0).getDishId()).isEqualTo(DISH_ID);
    assertThat(request.getDays().get(0).getMeals().get(0).getServings()).isEqualTo(2);
  }

  @Test
  void createMenu_rejectsAMalformedStartDate() {
    String result = menuTools.createMenu("19/10/2026", breakfastDay(), null, null, CONTEXT);

    assertThat(result).isEqualTo("error: startDate must be a date in YYYY-MM-DD format");
    verifyNoInteractions(menuService);
  }

  @Test
  void createMenu_rejectsAMalformedDishId() {
    List<MenuTools.Day> days =
        List.of(new MenuTools.Day("2026-10-19", List.of(meal("BREAKFAST", "nope", 2))));

    String result = menuTools.createMenu("2026-10-19", days, null, null, CONTEXT);

    assertThat(result).isEqualTo("error: days[0].meals[0].dishId must be a UUID");
    verifyNoInteractions(menuService);
  }

  /**
   * The REST layer enforces these through {@code @Valid} on the controller method; a tool calls the
   * service directly, so the tool must run the same validator or a null would reach the domain.
   */
  @Test
  void createMenu_runsTheBeanValidationTheRestControllerApplies() {
    List<MenuTools.Day> days =
        List.of(
            new MenuTools.Day("2026-10-19", List.of(meal("BREAKFAST", DISH_ID.toString(), null))));

    String result = menuTools.createMenu("2026-10-19", days, null, null, CONTEXT);

    assertThat(result).isEqualTo("error: servings is required");
    verifyNoInteractions(menuService);
  }

  @Test
  void updateMenu_reachesTheServiceWhenTheTargetIsTheCurrentWeekDraft() {
    when(menuService.listMenus(eq(USER), any(), anyInt(), anyInt(), any()))
        .thenReturn(pageContaining(MENU_ID));
    when(menuService.updateMenu(eq(USER), eq(MENU_ID), any()))
        .thenReturn(MenuDetailResponse.builder().id(MENU_ID).status("drafted").build());

    String result = menuTools.updateMenu(MENU_ID.toString(), breakfastDay(), null, null, CONTEXT);

    assertThat(result).contains("\"success\":true").contains("Menu updated successfully");
    verify(menuService).updateMenu(eq(USER), eq(MENU_ID), any());
  }

  @Test
  void updateMenu_refusesWhenTheTargetIsNotTheCurrentWeekDraft() {
    when(menuService.listMenus(eq(USER), any(), anyInt(), anyInt(), any())).thenReturn(emptyPage());

    String result = menuTools.updateMenu(MENU_ID.toString(), breakfastDay(), null, null, CONTEXT);

    assertThat(result)
        .isEqualTo(
            "update refused: the target must be the caller's own current-week menu with status"
                + " drafted");
    verify(menuService, never()).updateMenu(any(), any(), any());
  }

  @Test
  void updateMenu_requiresAMenuId() {
    String result = menuTools.updateMenu(null, breakfastDay(), null, null, CONTEXT);

    assertThat(result).isEqualTo("error: menuId is required");
    verifyNoInteractions(menuService);
  }

  @Test
  void scheduleMenu_asksTheServiceForTheScheduledTransition() {
    when(menuService.changeStatus(eq(USER), eq(MENU_ID), eq("scheduled")))
        .thenReturn(MenuDetailResponse.builder().id(MENU_ID).status("scheduled").build());

    String result = menuTools.scheduleMenu(MENU_ID.toString(), CONTEXT);

    assertThat(result)
        .contains("\"success\":true")
        .contains("Menu status updated successfully")
        .doesNotStartWith("\"");
    verify(menuService).changeStatus(eq(USER), eq(MENU_ID), eq("scheduled"));
  }

  @Test
  void scheduleMenu_refusesWhenTheCallerIsUnattributed() {
    String result = menuTools.scheduleMenu(MENU_ID.toString(), NO_IDENTITY);

    assertThat(result).isEqualTo(REFUSED_IDENTITY);
    verifyNoInteractions(menuService);
  }

  /**
   * The guard that makes the whole move safe: Spring AI's default result converter runs every
   * non-void result through {@code JsonParser.toJson}, which would hand the model an escaped,
   * quoted blob instead of the documented envelope. This asserts the {@code resultConverter}
   * declared on each {@code @Tool} actually keeps the payload single-encoded, end to end through
   * the provider the chat client uses.
   */
  @Test
  void aToolCallbackDeliversTheEnvelopeAsJsonNotAsAnEscapedStringLiteral() {
    when(menuService.listMenus(eq(USER), any(), anyInt(), anyInt(), any())).thenReturn(emptyPage());

    ToolCallback getMenu = toolNamed("get_menu");

    String text = getMenu.call("{}", CONTEXT);

    assertThat(text).doesNotStartWith("\"").startsWith("{").contains("\"success\":true");
  }

  private ToolCallback toolNamed(String name) {
    return Arrays.stream(
            MethodToolCallbackProvider.builder().toolObjects(menuTools).build().getToolCallbacks())
        .filter(callback -> callback.getToolDefinition().name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no tool named " + name));
  }

  private static List<MenuTools.Day> breakfastDay() {
    return List.of(
        new MenuTools.Day("2026-10-19", List.of(meal("BREAKFAST", DISH_ID.toString(), 2))));
  }

  private static MenuTools.Meal meal(String mealType, String dishId, Integer servings) {
    return new MenuTools.Meal(mealType, dishId, servings);
  }

  private static PageResponse<MenuSummaryResponse> emptyPage() {
    return PageResponse.<MenuSummaryResponse>builder()
        .content(List.of())
        .page(0)
        .size(20)
        .totalElements(0)
        .totalPages(0)
        .first(true)
        .last(true)
        .build();
  }

  private static PageResponse<MenuSummaryResponse> pageContaining(UUID menuId) {
    return PageResponse.<MenuSummaryResponse>builder()
        .content(List.of(MenuSummaryResponse.builder().id(menuId).build()))
        .page(0)
        .size(20)
        .totalElements(1)
        .totalPages(1)
        .first(true)
        .last(true)
        .build();
  }
}
