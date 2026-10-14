package com.vegalife.unit.service.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.mapper.location.LocationMapper;
import com.vegalife.dto.request.location.LocationListRequest;
import com.vegalife.dto.response.location.LocationResponse;
import com.vegalife.model.post.Location;
import com.vegalife.repository.post.LocationRepository;
import com.vegalife.service.location.LocationService;
import com.vegalife.shared.dto.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  @Mock private LocationRepository locationRepository;

  @Mock private LocationMapper locationMapper;

  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private LocationService locationService;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient()
        .when(locationMapper.toResponse(any(Location.class)))
        .thenAnswer(
            invocation -> {
              Location location = invocation.getArgument(0);
              return LocationResponse.builder()
                  .id(location.getId())
                  .name(location.getName())
                  .build();
            });
  }

  @Test
  void listLocations_returnsEmptyPageWhenNoMatches() {
    when(locationRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of());

    PageResponse<LocationResponse> response =
        locationService.listLocations(LocationListRequest.builder().build());

    assertThat(response.getContent()).isEmpty();
    assertThat(response.getTotalElements()).isZero();
  }

  @Test
  void listLocations_mapsEveryMatchWhenWithinOnePage() {
    Location first = location("Vegan Bistro", new BigDecimal("4.5"));
    Location second = location("Plant Cafe", new BigDecimal("4.0"));
    when(locationRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of(first, second));

    PageResponse<LocationResponse> response =
        locationService.listLocations(LocationListRequest.builder().build());

    assertThat(response.getContent()).hasSize(2);
    assertThat(response.getTotalElements()).isEqualTo(2);
    assertThat(response.isFirst()).isTrue();
    assertThat(response.isLast()).isTrue();
  }

  @Test
  void listLocations_paginatesInMemoryAfterFiltering() {
    List<Location> matches =
        List.of(
            location("A", new BigDecimal("5.0")),
            location("B", new BigDecimal("4.5")),
            location("C", new BigDecimal("4.0")));
    when(locationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(matches);

    PageResponse<LocationResponse> firstPage =
        locationService.listLocations(LocationListRequest.builder().page(0).size(2).build());
    PageResponse<LocationResponse> secondPage =
        locationService.listLocations(LocationListRequest.builder().page(1).size(2).build());

    assertThat(firstPage.getContent()).hasSize(2);
    assertThat(firstPage.isLast()).isFalse();
    assertThat(secondPage.getContent()).hasSize(1);
    assertThat(secondPage.isLast()).isTrue();
    assertThat(firstPage.getTotalElements()).isEqualTo(3);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
  }

  @Test
  void listLocations_pageBeyondResultsReturnsEmptyContent() {
    when(locationRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of(location("A", new BigDecimal("5.0"))));

    PageResponse<LocationResponse> response =
        locationService.listLocations(LocationListRequest.builder().page(5).size(10).build());

    assertThat(response.getContent()).isEmpty();
    assertThat(response.getTotalElements()).isEqualTo(1);
  }

  @Test
  void listLocations_openNowExcludesLocationsWithoutMatchingHours() {
    Location open = location("Open now", new BigDecimal("4.0"));
    open.setOpeningHours(
        currentDayJson(LocalTime.now().minusHours(1), LocalTime.now().plusHours(1)));
    Location closed = location("Closed now", new BigDecimal("4.9"));
    closed.setOpeningHours(
        currentDayJson(LocalTime.now().plusHours(2), LocalTime.now().plusHours(3)));
    Location noHours = location("No opening hours", new BigDecimal("5.0"));
    when(locationRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of(noHours, closed, open));

    PageResponse<LocationResponse> response =
        locationService.listLocations(LocationListRequest.builder().openNow(true).build());

    assertThat(response.getContent())
        .extracting(LocationResponse::getName)
        .containsExactly("Open now");
  }

  @Test
  void listLocations_validPlaceTypeIsParsedWithoutError() {
    when(locationRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of());

    PageResponse<LocationResponse> response =
        locationService.listLocations(LocationListRequest.builder().placeType("cafe").build());

    assertThat(response.getContent()).isEmpty();
  }

  private Location location(String name, BigDecimal rating) {
    return Location.builder()
        .id(UUID.randomUUID())
        .name(name)
        .address("123 Vegan St")
        .latitude(new BigDecimal("10.000000"))
        .longitude(new BigDecimal("106.000000"))
        .placeType(Location.PlaceType.restaurant)
        .rating(rating)
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
