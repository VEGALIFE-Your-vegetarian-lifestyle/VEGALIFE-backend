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
import com.vegalife.dto.request.recipe.DishSuggestionRequest;
import com.vegalife.dto.response.menu.MenuDetailResponse;
import com.vegalife.dto.response.menu.MenuSummaryResponse;
import com.vegalife.dto.response.recipe.DishSuggestionResponse;
import com.vegalife.service.ai.AiToolContext;
import com.vegalife.service.ai.MenuTools;
import com.vegalife.service.menu.MenuQueryFilter;
import com.vegalife.service.menu.MenuQueryFilterResolver;
import com.vegalife.service.menu.MenuService;
import com.vegalife.service.recipe.DishSuggestionFacade;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
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

  @Mock private DishSuggestionFacade dishSuggestionFacade;

  private MenuTools menuTools;

  @BeforeEach
  void setUp() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    menuTools =
        new MenuTools(
            menuService,
            new MenuQueryFilterResolver(),
            dishSuggestionFacade,
            validator,
            new ObjectMapper());
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
  void suggestDishes_defaultsPagingAndForwardsFilters() {
    when(dishSuggestionFacade.suggest(eq(USER), any())).thenReturn(emptySuggestionPage());

    String result =
        menuTools.suggestDishes(
            "pad", "Thai", "EASY", 10, 20, 30, null, null, null, null, null, null, null, null, null,
            null, null, null, null, CONTEXT);

    assertThat(result).contains("\"success\":true").contains("Dish suggestions retrieved");
    ArgumentCaptor<DishSuggestionRequest> request =
        ArgumentCaptor.forClass(DishSuggestionRequest.class);
    verify(dishSuggestionFacade).suggest(eq(USER), request.capture());
    assertThat(request.getValue().getName()).isEqualTo("pad");
    assertThat(request.getValue().getCuisineType()).isEqualTo("Thai");
    assertThat(request.getValue().getDifficulty()).isEqualTo("EASY");
    assertThat(request.getValue().getMaxTotalTime()).isEqualTo(30);
    // Paging defaults are applied server-side, so the model never has to compute them.
    assertThat(request.getValue().getPage()).isZero();
    assertThat(request.getValue().getSize()).isEqualTo(20);
    assertThat(request.getValue().getRecipesPerDish()).isEqualTo(3);
  }

  @Test
  void suggestDishes_forwardsEveryNutritionBound() {
    when(dishSuggestionFacade.suggest(eq(USER), any())).thenReturn(emptySuggestionPage());

    menuTools.suggestDishes(
        null,
        null,
        null,
        null,
        null,
        null,
        new BigDecimal("300"),
        new BigDecimal("700"),
        new BigDecimal("10"),
        new BigDecimal("40"),
        new BigDecimal("20"),
        new BigDecimal("60"),
        new BigDecimal("5"),
        new BigDecimal("25"),
        new BigDecimal("8"),
        new BigDecimal("20"),
        null,
        null,
        null,
        CONTEXT);

    ArgumentCaptor<DishSuggestionRequest> request =
        ArgumentCaptor.forClass(DishSuggestionRequest.class);
    verify(dishSuggestionFacade).suggest(eq(USER), request.capture());
    DishSuggestionRequest sent = request.getValue();
    assertThat(sent.getMinCalories()).isEqualByComparingTo("300");
    assertThat(sent.getMaxCalories()).isEqualByComparingTo("700");
    assertThat(sent.getMinProtein()).isEqualByComparingTo("10");
    assertThat(sent.getMaxProtein()).isEqualByComparingTo("40");
    assertThat(sent.getMinCarbohydrate()).isEqualByComparingTo("20");
    assertThat(sent.getMaxCarbohydrate()).isEqualByComparingTo("60");
    assertThat(sent.getMinFat()).isEqualByComparingTo("5");
    assertThat(sent.getMaxFat()).isEqualByComparingTo("25");
    assertThat(sent.getMinFiber()).isEqualByComparingTo("8");
    assertThat(sent.getMaxFiber()).isEqualByComparingTo("20");
  }

  @Test
  void suggestDishes_clampsRecipesPerDishAndBlankFilters() {
    when(dishSuggestionFacade.suggest(eq(USER), any())).thenReturn(emptySuggestionPage());

    menuTools.suggestDishes(
        "   ", null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, 0, 999, 99, CONTEXT);

    ArgumentCaptor<DishSuggestionRequest> request =
        ArgumentCaptor.forClass(DishSuggestionRequest.class);
    verify(dishSuggestionFacade).suggest(eq(USER), request.capture());
    // A blank string from the model means "no filter", not a filter for the empty string.
    assertThat(request.getValue().getName()).isNull();
    assertThat(request.getValue().getCuisineType()).isNull();
    assertThat(request.getValue().getRecipesPerDish()).isLessThanOrEqualTo(10);
    assertThat(request.getValue().getSize()).isLessThanOrEqualTo(50);
  }

  @Test
  void suggestDishes_neverAcceptsAnAllergyParameter() {
    // The tool must resolve allergies from the profile only: an allergen-shaped filter is ignored.
    when(dishSuggestionFacade.suggest(eq(USER), any())).thenReturn(emptySuggestionPage());

    menuTools.suggestDishes(
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, CONTEXT);

    ArgumentCaptor<DishSuggestionRequest> request =
        ArgumentCaptor.forClass(DishSuggestionRequest.class);
    verify(dishSuggestionFacade).suggest(eq(USER), request.capture());
    assertThat(request.getValue().getMinCalories()).isNull();
    assertThat(request.getValue().getMaxCalories()).isNull();
    assertThat(request.getValue().getMinProtein()).isNull();
  }

  @Test
  void suggestDishes_refusesWhenTheCallerIsUnattributed() {
    String result =
        menuTools.suggestDishes(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            NO_IDENTITY);

    assertThat(result).isEqualTo(REFUSED_IDENTITY);
    verifyNoInteractions(dishSuggestionFacade);
  }

  @Test
  void createMenu_mapsTheDaysAndMealsOntoTheServiceRequest() {
    when(menuService.createMenu(eq(USER), any()))
        .thenReturn(MenuDetailResponse.builder().id(MENU_ID).status("drafted").build());

    String result =
        menuTools.createMenu("2026-10-19", breakfastDay(), "High-protein week", CONTEXT);

    assertThat(result)
        .contains("\"success\":true")
        .contains("Menu created successfully")
        .doesNotStartWith("\"");
    ArgumentCaptor<CreateMenuRequest> sent = ArgumentCaptor.forClass(CreateMenuRequest.class);
    verify(menuService).createMenu(eq(USER), sent.capture());
    CreateMenuRequest request = sent.getValue();
    assertThat(request.getStartDate()).isEqualTo(LocalDate.parse("2026-10-19"));
    assertThat(request.getNotes()).isEqualTo("High-protein week");
    assertThat(request.getDays()).hasSize(1);
    assertThat(request.getDays().get(0).getDate()).isEqualTo(LocalDate.parse("2026-10-19"));
    assertThat(request.getDays().get(0).getMeals()).hasSize(1);
    assertThat(request.getDays().get(0).getMeals().get(0).getMealType()).isEqualTo("BREAKFAST");
    assertThat(request.getDays().get(0).getMeals().get(0).getDishId()).isEqualTo(DISH_ID);
    assertThat(request.getDays().get(0).getMeals().get(0).getServings()).isEqualTo(2);
  }

  @Test
  void createMenu_rejectsAMalformedStartDate() {
    String result = menuTools.createMenu("19/10/2026", breakfastDay(), "summary", CONTEXT);

    assertThat(result).isEqualTo("error: startDate must be a date in YYYY-MM-DD format");
    verifyNoInteractions(menuService);
  }

  @Test
  void createMenu_rejectsAMalformedDishId() {
    MenuTools.Days days =
        days(new MenuTools.Day("2026-10-19", List.of(meal("BREAKFAST", "nope", 2))));

    String result = menuTools.createMenu("2026-10-19", days, "summary", CONTEXT);

    assertThat(result).isEqualTo("error: days[0].meals[0].dishId must be a UUID");
    verifyNoInteractions(menuService);
  }

  /**
   * The REST layer enforces these through {@code @Valid} on the controller method; a tool calls the
   * service directly, so the tool must run the same validator or a null would reach the domain.
   */
  @Test
  void createMenu_runsTheBeanValidationTheRestControllerApplies() {
    MenuTools.Days days =
        days(new MenuTools.Day("2026-10-19", List.of(meal("BREAKFAST", DISH_ID.toString(), null))));

    String result = menuTools.createMenu("2026-10-19", days, "summary", CONTEXT);

    assertThat(result).isEqualTo("error: servings is required");
    verifyNoInteractions(menuService);
  }

  @Test
  void updateMenu_reachesTheServiceWhenTheTargetIsAnUpcomingDraft() {
    when(menuService.isUpcomingDraft(USER, MENU_ID)).thenReturn(true);
    when(menuService.updateMenu(eq(USER), eq(MENU_ID), any()))
        .thenReturn(MenuDetailResponse.builder().id(MENU_ID).status("drafted").build());

    String result = menuTools.updateMenu(MENU_ID.toString(), breakfastDay(), "summary", CONTEXT);

    assertThat(result).contains("\"success\":true").contains("Menu updated successfully");
    verify(menuService).updateMenu(eq(USER), eq(MENU_ID), any());
  }

  @Test
  void updateMenu_refusesWhenTheTargetIsNotAnUpcomingDraft() {
    when(menuService.isUpcomingDraft(USER, MENU_ID)).thenReturn(false);

    String result = menuTools.updateMenu(MENU_ID.toString(), breakfastDay(), "summary", CONTEXT);

    assertThat(result)
        .isEqualTo(
            "update refused: the target must be the caller's own drafted menu that starts tomorrow"
                + " or later");
    verify(menuService, never()).updateMenu(any(), any(), any());
  }

  @Test
  void updateMenu_requiresAMenuId() {
    String result = menuTools.updateMenu(null, breakfastDay(), "summary", CONTEXT);

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

  /**
   * Issue #190: a provider that emits {@code days} as a string instead of an array must not kill
   * the chat turn. Before the fix this threw {@code ToolExecutionException} out of the tool
   * callback (surfacing to the member as a 502); the week must instead bind and reach the service.
   */
  @Test
  void createMenu_acceptsTheWeekHandedOverAsALooseJsonString() {
    when(menuService.createMenu(eq(USER), any()))
        .thenReturn(MenuDetailResponse.builder().id(MENU_ID).status("drafted").build());

    // Single-quoted keys/values: what a weaker OpenAI-compatible model actually sends.
    String arguments =
        "{\"startDate\":\"2026-10-19\",\"notes\":\"summary\",\"days\":\"[{date:'2026-10-19',"
            + "meals:[{mealType:'BREAKFAST',dishId:'"
            + DISH_ID
            + "',servings:2}]}]\"}";

    String result = toolNamed("create_menu").call(arguments, CONTEXT);

    assertThat(result).contains("\"success\":true").contains("Menu created successfully");
    ArgumentCaptor<CreateMenuRequest> sent = ArgumentCaptor.forClass(CreateMenuRequest.class);
    verify(menuService).createMenu(eq(USER), sent.capture());
    assertThat(sent.getValue().getDays()).hasSize(1);
    assertThat(sent.getValue().getDays().get(0).getDate()).isEqualTo(LocalDate.parse("2026-10-19"));
    assertThat(sent.getValue().getDays().get(0).getMeals().get(0).getDishId()).isEqualTo(DISH_ID);
  }

  /** Same tolerance for the array wrapped in its own object, another common model emission. */
  @Test
  void createMenu_acceptsTheWeekWrappedInADaysObject() {
    when(menuService.createMenu(eq(USER), any()))
        .thenReturn(MenuDetailResponse.builder().id(MENU_ID).status("drafted").build());

    String arguments =
        "{\"startDate\":\"2026-10-19\",\"notes\":\"summary\",\"days\":{\"days\":[{\"date\":\"2026-10-19\","
            + "\"meals\":[{\"mealType\":\"BREAKFAST\",\"dishId\":\""
            + DISH_ID
            + "\",\"servings\":2}]}]}}";

    String result = toolNamed("create_menu").call(arguments, CONTEXT);

    assertThat(result).contains("\"success\":true");
    verify(menuService).createMenu(eq(USER), any());
  }

  /**
   * The tolerance must not change the contract the model reads: {@code days} stays a JSON array of
   * day objects, so a prompt that already produces the right shape keeps working unchanged.
   */
  @Test
  void createMenu_advertisesDaysAsAnArrayOfObjects() {
    String schema = toolNamed("create_menu").getToolDefinition().inputSchema();

    assertThat(schema)
        .contains("\"days\"")
        .contains("\"type\" : \"array\"")
        .contains("\"date\"")
        .contains("\"meals\"")
        .contains("\"additionalProperties\" : false");
  }

  /**
   * A value that is not a week at all must degrade to the backend's own readable rule, not a bind
   * failure — the model still gets a sentence it can act on.
   */
  @Test
  void createMenu_reportsUnreadableDaysAsTheBackendRule() {
    String arguments =
        "{\"startDate\":\"2026-10-19\",\"notes\":\"summary\",\"days\":\"a nice week of meals\"}";

    String result = toolNamed("create_menu").call(arguments, CONTEXT);

    assertThat(result).isEqualTo("error: days must contain exactly the 7 dates of the week");
    verifyNoInteractions(menuService);
  }

  private ToolCallback toolNamed(String name) {
    return Arrays.stream(
            MethodToolCallbackProvider.builder().toolObjects(menuTools).build().getToolCallbacks())
        .filter(callback -> callback.getToolDefinition().name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no tool named " + name));
  }

  private static MenuTools.Days breakfastDay() {
    MenuTools.Days days = new MenuTools.Days();
    days.add(new MenuTools.Day("2026-10-19", List.of(meal("BREAKFAST", DISH_ID.toString(), 2))));
    return days;
  }

  private static MenuTools.Days days(MenuTools.Day... entries) {
    MenuTools.Days days = new MenuTools.Days();
    days.addAll(List.of(entries));
    return days;
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

  private static PageResponse<DishSuggestionResponse> emptySuggestionPage() {
    return PageResponse.<DishSuggestionResponse>builder()
        .content(List.of())
        .page(0)
        .size(20)
        .totalElements(0)
        .totalPages(0)
        .first(true)
        .last(true)
        .build();
  }
}
