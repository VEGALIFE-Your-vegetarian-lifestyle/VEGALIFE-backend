package com.vegalife.unit.service.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.mapper.admin.AdminRestaurantMapper;
import com.vegalife.dto.mapper.restaurant.RestaurantMapper;
import com.vegalife.dto.request.restaurant.RestaurantCreateRequest;
import com.vegalife.dto.request.restaurant.RestaurantListRequest;
import com.vegalife.dto.request.restaurant.RestaurantNearbyRequest;
import com.vegalife.dto.request.restaurant.RestaurantUpdateRequest;
import com.vegalife.dto.response.admin.AdminRestaurantResponse;
import com.vegalife.dto.response.restaurant.RestaurantResponse;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.restaurant.Restaurant;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.restaurant.RestaurantRepository;
import com.vegalife.service.restaurant.RestaurantService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  @Mock private RestaurantRepository restaurantRepository;

  @Mock private RestaurantMapper restaurantMapper;

  @Mock private AdminRestaurantMapper adminRestaurantMapper;

  @Mock private ModerationLogRepository moderationLogRepository;

  @Mock private DishRepository dishRepository;

  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private RestaurantService restaurantService;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient()
        .when(adminRestaurantMapper.toResponse(any(Restaurant.class)))
        .thenAnswer(
            invocation -> {
              Restaurant restaurant = invocation.getArgument(0);
              return AdminRestaurantResponse.builder()
                  .id(restaurant.getId())
                  .name(restaurant.getName())
                  .build();
            });
    org.mockito.Mockito.lenient()
        .when(restaurantMapper.toResponse(any(Restaurant.class)))
        .thenAnswer(
            invocation -> {
              Restaurant restaurant = invocation.getArgument(0);
              return RestaurantResponse.builder()
                  .id(restaurant.getId())
                  .name(restaurant.getName())
                  .build();
            });
    org.mockito.Mockito.lenient()
        .when(restaurantMapper.toResponse(any(Restaurant.class), any(Double.class)))
        .thenAnswer(
            invocation -> {
              Restaurant restaurant = invocation.getArgument(0);
              Double distance = invocation.getArgument(1);
              return RestaurantResponse.builder()
                  .id(restaurant.getId())
                  .name(restaurant.getName())
                  .distance(distance)
                  .build();
            });
  }

  @Test
  void listRestaurants_returnsEmptyPageWhenNoMatches() {
    when(restaurantRepository.findAll(any(Specification.class))).thenReturn(List.of());

    PageResponse<RestaurantResponse> response =
        restaurantService.listRestaurants(RestaurantListRequest.builder().build());

    assertThat(response.getContent()).isEmpty();
    assertThat(response.getTotalElements()).isZero();
  }

  @Test
  void listRestaurants_mapsEveryMatchWhenWithinOnePage() {
    Restaurant first = restaurant("Vegan Bistro", new BigDecimal("4.5"));
    Restaurant second = restaurant("Plant Cafe", new BigDecimal("4.0"));
    when(restaurantRepository.findAll(any(Specification.class))).thenReturn(List.of(first, second));

    PageResponse<RestaurantResponse> response =
        restaurantService.listRestaurants(RestaurantListRequest.builder().build());

    assertThat(response.getContent()).hasSize(2);
    assertThat(response.getTotalElements()).isEqualTo(2);
    assertThat(response.isFirst()).isTrue();
    assertThat(response.isLast()).isTrue();
  }

  @Test
  void listRestaurants_paginatesInMemoryAfterFiltering() {
    List<Restaurant> matches =
        List.of(
            restaurant("A", new BigDecimal("5.0")),
            restaurant("B", new BigDecimal("4.5")),
            restaurant("C", new BigDecimal("4.0")));
    when(restaurantRepository.findAll(any(Specification.class))).thenReturn(matches);

    PageResponse<RestaurantResponse> firstPage =
        restaurantService.listRestaurants(RestaurantListRequest.builder().page(0).size(2).build());
    PageResponse<RestaurantResponse> secondPage =
        restaurantService.listRestaurants(RestaurantListRequest.builder().page(1).size(2).build());

    assertThat(firstPage.getContent()).hasSize(2);
    assertThat(firstPage.isLast()).isFalse();
    assertThat(secondPage.getContent()).hasSize(1);
    assertThat(secondPage.isLast()).isTrue();
    assertThat(firstPage.getTotalElements()).isEqualTo(3);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
  }

  @Test
  void listRestaurants_pageBeyondResultsReturnsEmptyContent() {
    when(restaurantRepository.findAll(any(Specification.class)))
        .thenReturn(List.of(restaurant("A", new BigDecimal("5.0"))));

    PageResponse<RestaurantResponse> response =
        restaurantService.listRestaurants(RestaurantListRequest.builder().page(5).size(10).build());

    assertThat(response.getContent()).isEmpty();
    assertThat(response.getTotalElements()).isEqualTo(1);
  }

  @Test
  void listRestaurants_openNowExcludesRestaurantsWithoutMatchingHours() {
    Restaurant open = restaurant("Open now", new BigDecimal("4.0"));
    open.setOpeningHours(
        currentDayJson(LocalTime.now().minusHours(1), LocalTime.now().plusHours(1)));
    Restaurant closed = restaurant("Closed now", new BigDecimal("4.9"));
    closed.setOpeningHours(
        currentDayJson(LocalTime.now().plusHours(2), LocalTime.now().plusHours(3)));
    Restaurant noHours = restaurant("No opening hours", new BigDecimal("5.0"));
    when(restaurantRepository.findAll(any(Specification.class)))
        .thenReturn(List.of(noHours, closed, open));

    PageResponse<RestaurantResponse> response =
        restaurantService.listRestaurants(RestaurantListRequest.builder().openNow(true).build());

    assertThat(response.getContent())
        .extracting(RestaurantResponse::getName)
        .containsExactly("Open now");
  }

  @Test
  void listNearby_keepsOnlyRestaurantsWithinRadiusSortedByDistance() {
    BigDecimal centerLat = new BigDecimal("10.772465");
    BigDecimal centerLng = new BigDecimal("106.698055");
    // ~620m away (well within a 2000m radius).
    Restaurant near =
        restaurantAt("Near", new BigDecimal("10.777529"), new BigDecimal("106.695528"));
    // ~0.2 degrees away (~22km — well outside a 2000m radius).
    Restaurant far = restaurantAt("Far", new BigDecimal("10.972465"), new BigDecimal("106.698055"));
    when(restaurantRepository.findAll(any(Specification.class))).thenReturn(List.of(far, near));

    List<RestaurantResponse> response =
        restaurantService.listNearby(
            RestaurantNearbyRequest.builder()
                .lat(centerLat)
                .lng(centerLng)
                .radius(new BigDecimal("2000"))
                .build());

    assertThat(response).extracting(RestaurantResponse::getName).containsExactly("Near");
    assertThat(response.get(0).getDistance())
        .isCloseTo(620.0, org.assertj.core.data.Offset.offset(50.0));
  }

  @Test
  void listNearby_sortsMultipleMatchesByAscendingDistance() {
    BigDecimal centerLat = new BigDecimal("10.772465");
    BigDecimal centerLng = new BigDecimal("106.698055");
    Restaurant farther =
        restaurantAt("Farther", new BigDecimal("10.777529"), new BigDecimal("106.695528"));
    Restaurant closer =
        restaurantAt("Closer", new BigDecimal("10.773465"), new BigDecimal("106.698055"));
    when(restaurantRepository.findAll(any(Specification.class)))
        .thenReturn(List.of(farther, closer));

    List<RestaurantResponse> response =
        restaurantService.listNearby(
            RestaurantNearbyRequest.builder()
                .lat(centerLat)
                .lng(centerLng)
                .radius(new BigDecimal("5000"))
                .build());

    assertThat(response)
        .extracting(RestaurantResponse::getName)
        .containsExactly("Closer", "Farther");
  }

  @Test
  void listNearby_returnsEmptyListWhenNoCandidatesMatch() {
    when(restaurantRepository.findAll(any(Specification.class))).thenReturn(List.of());

    List<RestaurantResponse> response =
        restaurantService.listNearby(
            RestaurantNearbyRequest.builder()
                .lat(new BigDecimal("10.0"))
                .lng(new BigDecimal("106.0"))
                .radius(new BigDecimal("1000"))
                .build());

    assertThat(response).isEmpty();
  }

  @Test
  void createRestaurant_savesAndRecordsModerationLog() {
    UUID adminId = UUID.randomUUID();
    RestaurantCreateRequest request =
        RestaurantCreateRequest.builder()
            .name("Vegan Garden")
            .address("123 Nguyen Hue")
            .latitude(new BigDecimal("10.776889"))
            .longitude(new BigDecimal("106.700806"))
            .build();
    when(restaurantRepository.saveAndFlush(any(Restaurant.class)))
        .thenAnswer(
            invocation -> {
              Restaurant saved = invocation.getArgument(0);
              saved.setId(UUID.randomUUID());
              return saved;
            });
    AdminRestaurantResponse expected =
        AdminRestaurantResponse.builder().name("Vegan Garden").build();
    when(adminRestaurantMapper.toResponse(any(Restaurant.class))).thenReturn(expected);

    AdminRestaurantResponse response = restaurantService.createRestaurant(adminId, request);

    assertThat(response).isSameAs(expected);
    ArgumentCaptor<Restaurant> savedCaptor = ArgumentCaptor.forClass(Restaurant.class);
    verify(restaurantRepository).saveAndFlush(savedCaptor.capture());
    assertThat(savedCaptor.getValue().getName()).isEqualTo("Vegan Garden");

    ArgumentCaptor<ModerationLog> logCaptor = ArgumentCaptor.forClass(ModerationLog.class);
    verify(moderationLogRepository).save(logCaptor.capture());
    assertThat(logCaptor.getValue().getActorId()).isEqualTo(adminId);
    assertThat(logCaptor.getValue().getAction()).isEqualTo("CREATE_RESTAURANT");
    assertThat(logCaptor.getValue().getTargetType()).isEqualTo("RESTAURANT");
  }

  @Test
  void updateRestaurant_changesOnlySuppliedFields() {
    UUID adminId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    Restaurant existing = restaurantAt("Old Name", new BigDecimal("10.0"), new BigDecimal("106.0"));
    existing.setId(restaurantId);
    existing.setAddress("Old Address");
    when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(existing));
    when(restaurantRepository.saveAndFlush(existing)).thenReturn(existing);
    when(adminRestaurantMapper.toResponse(existing))
        .thenReturn(AdminRestaurantResponse.builder().name("New Name").build());

    RestaurantUpdateRequest request = RestaurantUpdateRequest.builder().name("New Name").build();
    AdminRestaurantResponse response =
        restaurantService.updateRestaurant(adminId, restaurantId, request);

    assertThat(response.getName()).isEqualTo("New Name");
    assertThat(existing.getName()).isEqualTo("New Name");
    assertThat(existing.getAddress()).isEqualTo("Old Address");
    verify(moderationLogRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                log -> "UPDATE_RESTAURANT".equals(log.getAction())));
  }

  @Test
  void updateRestaurant_unknownOrDeletedRestaurantThrowsNotFound() {
    UUID restaurantId = UUID.randomUUID();
    when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                restaurantService.updateRestaurant(
                    UUID.randomUUID(),
                    restaurantId,
                    RestaurantUpdateRequest.builder().name("X").build()))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(restaurantRepository, never()).saveAndFlush(any(Restaurant.class));
  }

  @Test
  void deleteRestaurant_softDeletesAndRecordsModerationLog() {
    UUID adminId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    Restaurant existing =
        restaurantAt("To Delete", new BigDecimal("10.0"), new BigDecimal("106.0"));
    existing.setId(restaurantId);
    when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(existing));
    when(restaurantRepository.saveAndFlush(existing)).thenReturn(existing);

    restaurantService.deleteRestaurant(adminId, restaurantId);

    assertThat(existing.getDeletedAt()).isNotNull();
    verify(moderationLogRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                log ->
                    "DELETE_RESTAURANT".equals(log.getAction())
                        && adminId.equals(log.getActorId())));
  }

  @Test
  void createRestaurant_attachesApprovedDishesAndReturnsThemNested() {
    UUID adminId = UUID.randomUUID();
    Dish pho = dish("Pho Chay", ModerationStatus.APPROVED);
    Dish banhMi = dish("Banh Mi Chay", ModerationStatus.APPROVED);
    RestaurantCreateRequest request =
        RestaurantCreateRequest.builder()
            .name("Vegan Garden")
            .address("123 Nguyen Hue")
            .latitude(new BigDecimal("10.776889"))
            .longitude(new BigDecimal("106.700806"))
            .dishIds(List.of(pho.getId(), banhMi.getId(), pho.getId()))
            .build();
    when(dishRepository.findByIdInAndDeletedAtIsNullAndModerationStatus(
            any(), org.mockito.ArgumentMatchers.eq(ModerationStatus.APPROVED)))
        .thenReturn(List.of(pho, banhMi));
    when(restaurantRepository.saveAndFlush(any(Restaurant.class)))
        .thenAnswer(
            invocation -> {
              Restaurant saved = invocation.getArgument(0);
              saved.setId(UUID.randomUUID());
              return saved;
            });

    AdminRestaurantResponse response = restaurantService.createRestaurant(adminId, request);

    assertThat(response.getDishes())
        .extracting(com.vegalife.dto.response.restaurant.RestaurantDishResponse::getName)
        .containsExactly("Banh Mi Chay", "Pho Chay");
    ArgumentCaptor<Restaurant> savedCaptor = ArgumentCaptor.forClass(Restaurant.class);
    verify(restaurantRepository).saveAndFlush(savedCaptor.capture());
    assertThat(savedCaptor.getValue().getDishes()).containsExactlyInAnyOrder(pho, banhMi);
  }

  @Test
  void createRestaurant_unknownOrUnapprovedDishThrowsNotFoundAndPersistsNothing() {
    UUID adminId = UUID.randomUUID();
    UUID unknownDishId = UUID.randomUUID();
    RestaurantCreateRequest request =
        RestaurantCreateRequest.builder()
            .name("Vegan Garden")
            .address("123 Nguyen Hue")
            .latitude(new BigDecimal("10.776889"))
            .longitude(new BigDecimal("106.700806"))
            .dishIds(List.of(unknownDishId))
            .build();
    when(dishRepository.findByIdInAndDeletedAtIsNullAndModerationStatus(
            any(), org.mockito.ArgumentMatchers.eq(ModerationStatus.APPROVED)))
        .thenReturn(List.of());

    assertThatThrownBy(() -> restaurantService.createRestaurant(adminId, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("One or more dishes are not found or are not approved.");

    verify(restaurantRepository, never()).saveAndFlush(any(Restaurant.class));
    verify(moderationLogRepository, never()).save(any(ModerationLog.class));
  }

  @Test
  void updateRestaurant_nonEmptyDishIdsReplacesExistingDishes() {
    UUID adminId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    Dish oldDish = dish("Old Dish", ModerationStatus.APPROVED);
    Dish newDish = dish("New Dish", ModerationStatus.APPROVED);
    Restaurant existing = restaurantAt("Place", new BigDecimal("10.0"), new BigDecimal("106.0"));
    existing.setId(restaurantId);
    existing.setDishes(new HashSet<>(Set.of(oldDish)));
    when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(existing));
    when(restaurantRepository.saveAndFlush(existing)).thenReturn(existing);
    when(dishRepository.findByIdInAndDeletedAtIsNullAndModerationStatus(
            any(), org.mockito.ArgumentMatchers.eq(ModerationStatus.APPROVED)))
        .thenReturn(List.of(newDish));

    RestaurantUpdateRequest request =
        RestaurantUpdateRequest.builder().dishIds(List.of(newDish.getId())).build();
    AdminRestaurantResponse response =
        restaurantService.updateRestaurant(adminId, restaurantId, request);

    assertThat(existing.getDishes()).containsExactly(newDish);
    assertThat(response.getDishes())
        .extracting(com.vegalife.dto.response.restaurant.RestaurantDishResponse::getName)
        .containsExactly("New Dish");
  }

  @Test
  void updateRestaurant_emptyDishIdsClearsDishes() {
    UUID adminId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    Dish oldDish = dish("Old Dish", ModerationStatus.APPROVED);
    Restaurant existing = restaurantAt("Place", new BigDecimal("10.0"), new BigDecimal("106.0"));
    existing.setId(restaurantId);
    existing.setDishes(new HashSet<>(Set.of(oldDish)));
    when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(existing));
    when(restaurantRepository.saveAndFlush(existing)).thenReturn(existing);

    RestaurantUpdateRequest request = RestaurantUpdateRequest.builder().dishIds(List.of()).build();
    restaurantService.updateRestaurant(adminId, restaurantId, request);

    assertThat(existing.getDishes()).isEmpty();
    verify(dishRepository, never()).findByIdInAndDeletedAtIsNullAndModerationStatus(any(), any());
  }

  @Test
  void updateRestaurant_omittedDishIdsLeavesDishesUnchanged() {
    UUID adminId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    Dish oldDish = dish("Old Dish", ModerationStatus.APPROVED);
    Restaurant existing = restaurantAt("Place", new BigDecimal("10.0"), new BigDecimal("106.0"));
    existing.setId(restaurantId);
    existing.setDishes(new HashSet<>(Set.of(oldDish)));
    when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(existing));
    when(restaurantRepository.saveAndFlush(existing)).thenReturn(existing);

    RestaurantUpdateRequest request = RestaurantUpdateRequest.builder().name("Renamed").build();
    restaurantService.updateRestaurant(adminId, restaurantId, request);

    assertThat(existing.getDishes()).containsExactly(oldDish);
    verify(dishRepository, never()).findByIdInAndDeletedAtIsNullAndModerationStatus(any(), any());
  }

  @Test
  void updateRestaurant_unknownDishIdThrowsNotFoundAndLeavesExistingDishesPersisted() {
    UUID adminId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    Dish oldDish = dish("Old Dish", ModerationStatus.APPROVED);
    Restaurant existing = restaurantAt("Place", new BigDecimal("10.0"), new BigDecimal("106.0"));
    existing.setId(restaurantId);
    existing.setDishes(new HashSet<>(Set.of(oldDish)));
    when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(existing));
    when(dishRepository.findByIdInAndDeletedAtIsNullAndModerationStatus(
            any(), org.mockito.ArgumentMatchers.eq(ModerationStatus.APPROVED)))
        .thenReturn(List.of());

    RestaurantUpdateRequest request =
        RestaurantUpdateRequest.builder().dishIds(List.of(UUID.randomUUID())).build();

    assertThatThrownBy(() -> restaurantService.updateRestaurant(adminId, restaurantId, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("One or more dishes are not found or are not approved.");

    verify(restaurantRepository, never()).saveAndFlush(any(Restaurant.class));
    assertThat(existing.getDishes()).containsExactly(oldDish);
  }

  @Test
  void getRestaurant_returnsActiveRestaurantWithDishesSortedAndSoftDeletedFiltered() {
    UUID restaurantId = UUID.randomUUID();
    Dish zebra = dish("Zebra Dish", ModerationStatus.APPROVED);
    Dish apple = dish("Apple Dish", ModerationStatus.APPROVED);
    Dish removed = dish("Removed Dish", ModerationStatus.APPROVED);
    removed.setDeletedAt(java.time.Instant.now());
    Restaurant restaurant = restaurantAt("Place", new BigDecimal("10.0"), new BigDecimal("106.0"));
    restaurant.setId(restaurantId);
    restaurant.setDishes(new HashSet<>(Set.of(zebra, apple, removed)));
    when(restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId))
        .thenReturn(Optional.of(restaurant));

    RestaurantResponse response = restaurantService.getRestaurant(restaurantId);

    assertThat(response.getDishes())
        .extracting(com.vegalife.dto.response.restaurant.RestaurantDishResponse::getName)
        .containsExactly("Apple Dish", "Zebra Dish");
  }

  @Test
  void getRestaurant_unknownOrDeletedRestaurantThrowsNotFound() {
    UUID restaurantId = UUID.randomUUID();
    when(restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> restaurantService.getRestaurant(restaurantId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Restaurant not found");
  }

  @Test
  void listRestaurants_blankKeywordAppliesNoKeywordFilter() {
    when(restaurantRepository.findAll(any(Specification.class)))
        .thenReturn(List.of(restaurant("Vegan Bistro", new BigDecimal("4.5"))));

    PageResponse<RestaurantResponse> response =
        restaurantService.listRestaurants(RestaurantListRequest.builder().keyword("   ").build());

    assertThat(response.getContent()).hasSize(1);
    verify(restaurantRepository, never()).findDishNameMatches(any());
  }

  @Test
  void listRestaurants_keywordMatchesNameOrDescriptionViaSpecification() {
    Restaurant match = restaurant("Vegan Bistro", new BigDecimal("4.5"));
    when(restaurantRepository.findAll(any(Specification.class))).thenReturn(List.of(match));
    when(restaurantRepository.findDishNameMatches("bistro")).thenReturn(List.of());

    PageResponse<RestaurantResponse> response =
        restaurantService.listRestaurants(
            RestaurantListRequest.builder().keyword("bistro").build());

    assertThat(response.getContent())
        .extracting(RestaurantResponse::getName)
        .containsExactly("Vegan Bistro");
  }

  @Test
  void listRestaurants_keywordUnionsDishMatchedRestaurantsNotAlreadyFound() {
    Restaurant nameMatch = restaurant("Pho Place", new BigDecimal("4.0"));
    Restaurant dishMatchedOnly = restaurant("Unrelated Diner", new BigDecimal("4.0"));
    when(restaurantRepository.findAll(any(Specification.class))).thenReturn(List.of(nameMatch));
    when(restaurantRepository.findDishNameMatches("pho"))
        .thenReturn(
            List.of(
                dishMatch(dishMatchedOnly.getId(), "Pho Chay"),
                dishMatch(nameMatch.getId(), "Pho Chay")));
    when(restaurantRepository.findAllById(List.of(dishMatchedOnly.getId())))
        .thenReturn(List.of(dishMatchedOnly));

    PageResponse<RestaurantResponse> response =
        restaurantService.listRestaurants(RestaurantListRequest.builder().keyword("pho").build());

    assertThat(response.getContent())
        .extracting(RestaurantResponse::getName)
        .containsExactlyInAnyOrder("Pho Place", "Unrelated Diner");
  }

  @Test
  void listRestaurants_dishMatchedRestaurantStillMustPassMinRating() {
    Restaurant lowRated = restaurant("Low Rated Diner", new BigDecimal("2.0"));
    when(restaurantRepository.findAll(any(Specification.class))).thenReturn(List.of());
    when(restaurantRepository.findDishNameMatches("pho"))
        .thenReturn(List.of(dishMatch(lowRated.getId(), "Pho Chay")));
    when(restaurantRepository.findAllById(List.of(lowRated.getId()))).thenReturn(List.of(lowRated));

    PageResponse<RestaurantResponse> response =
        restaurantService.listRestaurants(
            RestaurantListRequest.builder()
                .keyword("pho")
                .minRating(new BigDecimal("4.0"))
                .build());

    assertThat(response.getContent()).isEmpty();
  }

  @Test
  void listRestaurants_keywordWithDishOnlyMatchInDifferentRestaurantIsNotAFalsePositive() {
    // The matched dish belongs only to "Other Place"; "Target" must not appear just because some
    // dish, attached to a different restaurant, matched the keyword (issue #196, point 10).
    Restaurant target = restaurant("Target", new BigDecimal("4.0"));
    Restaurant otherPlace = restaurant("Other Place", new BigDecimal("4.0"));
    when(restaurantRepository.findAll(any(Specification.class))).thenReturn(List.of());
    when(restaurantRepository.findDishNameMatches("pho"))
        .thenReturn(List.of(dishMatch(otherPlace.getId(), "Pho Chay")));
    when(restaurantRepository.findAllById(List.of(otherPlace.getId())))
        .thenReturn(List.of(otherPlace));

    PageResponse<RestaurantResponse> response =
        restaurantService.listRestaurants(RestaurantListRequest.builder().keyword("pho").build());

    assertThat(response.getContent())
        .extracting(RestaurantResponse::getName)
        .containsExactly("Other Place")
        .doesNotContain(target.getName());
  }

  @Test
  void listRestaurants_keywordCombinesWithOpenNowFilter() {
    Restaurant open = restaurant("Vegan Bistro Open", new BigDecimal("4.5"));
    open.setOpeningHours(
        currentDayJson(LocalTime.now().minusHours(1), LocalTime.now().plusHours(1)));
    Restaurant closed = restaurant("Vegan Bistro Closed", new BigDecimal("4.5"));
    closed.setOpeningHours(
        currentDayJson(LocalTime.now().plusHours(2), LocalTime.now().plusHours(3)));
    when(restaurantRepository.findAll(any(Specification.class))).thenReturn(List.of(open, closed));
    when(restaurantRepository.findDishNameMatches("bistro")).thenReturn(List.of());

    PageResponse<RestaurantResponse> response =
        restaurantService.listRestaurants(
            RestaurantListRequest.builder().keyword("bistro").openNow(true).build());

    assertThat(response.getContent())
        .extracting(RestaurantResponse::getName)
        .containsExactly("Vegan Bistro Open");
  }

  private com.vegalife.repository.restaurant.RestaurantRepository.RestaurantDishMatch dishMatch(
      UUID restaurantId, String dishName) {
    return new com.vegalife.repository.restaurant.RestaurantRepository.RestaurantDishMatch() {
      @Override
      public UUID getRestaurantId() {
        return restaurantId;
      }

      @Override
      public String getDishName() {
        return dishName;
      }
    };
  }

  private Dish dish(String name, ModerationStatus status) {
    return Dish.builder().id(UUID.randomUUID()).name(name).moderationStatus(status).build();
  }

  private Restaurant restaurant(String name, BigDecimal rating) {
    Restaurant restaurant =
        Restaurant.builder()
            .id(UUID.randomUUID())
            .name(name)
            .address("123 Vegan St")
            .latitude(new BigDecimal("10.000000"))
            .longitude(new BigDecimal("106.000000"))
            .rating(rating)
            .build();
    restaurant.setCreatedAt(java.time.Instant.now());
    return restaurant;
  }

  private Restaurant restaurantAt(String name, BigDecimal lat, BigDecimal lng) {
    return Restaurant.builder()
        .id(UUID.randomUUID())
        .name(name)
        .address("123 Vegan St")
        .latitude(lat)
        .longitude(lng)
        .build();
  }

  private String currentDayJson(LocalTime open, LocalTime close) {
    String day = LocalDate.now().getDayOfWeek().toString().toLowerCase(java.util.Locale.ROOT);
    return "{\""
        + day
        + "\": [{\"open\": \""
        + TIME_FORMAT.format(open)
        + "\", \"close\": \""
        + TIME_FORMAT.format(close)
        + "\"}]}";
  }
}
