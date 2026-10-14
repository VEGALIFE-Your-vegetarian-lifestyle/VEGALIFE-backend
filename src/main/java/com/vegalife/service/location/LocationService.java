package com.vegalife.service.location;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.mapper.location.LocationMapper;
import com.vegalife.dto.request.location.LocationListRequest;
import com.vegalife.dto.response.location.LocationResponse;
import com.vegalife.model.post.Location;
import com.vegalife.repository.post.LocationRepository;
import com.vegalife.repository.post.LocationSpecifications;
import com.vegalife.shared.dto.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recommend-locations list (issue #9): active locations filtered by rating/type/price/open-now,
 * sorted by rating descending (unrated last), then creation time and id ascending for stable
 * paging. Open-now, pagination, and the tie-break sort stay a single code path by loading every
 * active match for the structured filters up front and paging it in memory: a location with no
 * opening hours can only be excluded after parsing its document, which the database cannot do.
 */
@Service
@RequiredArgsConstructor
public class LocationService {

  private static final Sort RESULT_ORDER =
      Sort.by(
          Sort.Order.desc("rating").nullsLast(), Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

  private final LocationRepository locationRepository;
  private final LocationMapper locationMapper;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public PageResponse<LocationResponse> listLocations(LocationListRequest request) {
    Location.PlaceType placeType = parsePlaceType(request.getPlaceType());
    Specification<Location> spec =
        LocationSpecifications.activeWithFilters(
            request.getMinRating(), placeType, request.getMaxPriceLevel());

    List<Location> matches = locationRepository.findAll(spec, RESULT_ORDER);
    List<Location> filtered = request.isOpenNow() ? filterOpenNow(matches) : matches;

    Page<Location> page = paginate(filtered, request.getPage(), request.getSize());
    return PageResponse.from(page.map(locationMapper::toResponse));
  }

  private List<Location> filterOpenNow(List<Location> locations) {
    LocalDateTime now = LocalDateTime.now();
    return locations.stream()
        .filter(
            location ->
                OpeningHoursEvaluator.isOpenNow(location.getOpeningHours(), objectMapper, now))
        .toList();
  }

  private Page<Location> paginate(List<Location> locations, int page, int size) {
    int fromIndex = Math.min(page * size, locations.size());
    int toIndex = Math.min(fromIndex + size, locations.size());
    List<Location> pageContent = locations.subList(fromIndex, toIndex);
    return new PageImpl<>(pageContent, PageRequest.of(page, size), locations.size());
  }

  private Location.PlaceType parsePlaceType(String placeType) {
    return placeType == null || placeType.isBlank() ? null : Location.PlaceType.valueOf(placeType);
  }
}
