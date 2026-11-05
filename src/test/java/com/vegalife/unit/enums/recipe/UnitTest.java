package com.vegalife.unit.enums.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.enums.recipe.Unit;
import org.junit.jupiter.api.Test;

/**
 * Unit normalisation (ADR-022). Only spelling variants are accepted: a real different unit such as
 * {@code kg} is refused, because translating it would silently change the quantity.
 */
class UnitTest {

  @Test
  void fromInput_acceptsSpellingVariantsAndCaseInsensitively() {
    assertThat(Unit.fromInput("g")).contains(Unit.G);
    assertThat(Unit.fromInput(" G ")).contains(Unit.G);
    assertThat(Unit.fromInput("grams")).contains(Unit.G);
    assertThat(Unit.fromInput("gam")).contains(Unit.G);
    assertThat(Unit.fromInput("ml")).contains(Unit.ML);
    assertThat(Unit.fromInput("Mililit")).contains(Unit.ML);
    assertThat(Unit.fromInput("piece")).contains(Unit.PIECE);
    assertThat(Unit.fromInput("quả")).contains(Unit.PIECE);
  }

  @Test
  void fromInput_refusesDifferentUnitsRatherThanGuessing() {
    // Each of these names a quantity the three codes cannot express without a conversion.
    assertThat(Unit.fromInput("kg")).isEmpty();
    assertThat(Unit.fromInput("tbsp")).isEmpty();
    assertThat(Unit.fromInput("cup")).isEmpty();
    assertThat(Unit.fromInput("bó")).isEmpty();
    assertThat(Unit.fromInput("vừa đủ")).isEmpty();
  }

  @Test
  void fromInput_nullAndBlankAreEmpty() {
    assertThat(Unit.fromInput(null)).isEmpty();
    assertThat(Unit.fromInput("   ")).isEmpty();
  }
}
