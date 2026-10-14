package com.vegalife.unit.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vegalife.dto.request.search.SearchType;
import com.vegalife.repository.search.SearchDao;
import com.vegalife.repository.search.SearchRow;
import com.vegalife.service.search.SearchService;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SearchServiceTest {

  private SearchService searchService;
  private SearchDao searchDao;
  private String receivedQuery;

  @BeforeEach
  void setUp() {
    searchDao =
        new SearchDao(new JdbcTemplate()) {
          @Override
          public SearchPage search(
              String query,
              SearchType type,
              Instant from,
              Instant to,
              UUID categoryId,
              int page,
              int size) {
            receivedQuery = query;
            return pageResult;
          }
        };
    searchService = new SearchService(searchDao);
  }

  @Test
  void search_trimsQueryAndReturnsEscapedHighlights() {
    UUID id = UUID.randomUUID();
    SearchRow row =
        new SearchRow(
            id,
            SearchType.POST,
            "Vegan <Soup>",
            "A vegan soup",
            null,
            null,
            null,
            UUID.randomUUID(),
            Instant.parse("2026-10-01T00:00:00Z"),
            0.8,
            "Try <vegan> soup & enjoy");
    pageResult = new SearchDao.SearchPage(List.of(row), 1);

    var response = searchService.search(" vegan ", null, null, null, null, 0, 20);

    assertThat(response.getTotalElements()).isEqualTo(1);
    assertThat(response.getContent().getFirst().title()).isEqualTo("Vegan <Soup>");
    assertThat(response.getContent().getFirst().highlightedTitle())
        .isEqualTo("<mark>Vegan</mark> &lt;Soup&gt;");
    assertThat(response.getContent().getFirst().highlightedSnippet())
        .isEqualTo("Try &lt;<mark>vegan</mark>&gt; soup &amp; enjoy");
    assertThat(receivedQuery).isEqualTo("vegan");
  }

  @Test
  void search_rejectsInvertedDateRange() {
    Instant from = Instant.parse("2026-10-02T00:00:00Z");
    Instant to = Instant.parse("2026-10-01T00:00:00Z");

    assertThatThrownBy(() -> searchService.search("meal", null, from, to, null, 0, 20))
        .isInstanceOf(ValidationException.class)
        .hasMessage("'to' must be greater than or equal to 'from'");
  }

  private SearchDao.SearchPage pageResult;
}
