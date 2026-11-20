package com.vegalife.unit.mapper.ingredient;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.dto.mapper.ingredient.IngredientMapper;
import com.vegalife.dto.response.ingredient.IngredientResponse;
import com.vegalife.enums.recipe.Unit;
import com.vegalife.model.recipe.Ingredient;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Whitebox coverage for the parts of the mapping MapStruct cannot infer: the enum-to-wire-code unit
 * and the derived {@code nutritionResolved} flag. Runs the real mapper, no mocks — a mocked mapper
 * would assert nothing about the expression the annotation compiles.
 */
class IngredientMapperTest {

  private final IngredientMapper mapper = IngredientMapper.INSTANCE;

  @Test
  void toResponse_mapsUnitToItsWireCodeAndReportsResolved() {
    Ingredient tofu =
        Ingredient.builder()
            .id(UUID.randomUUID())
            .name("đậu hũ")
            .unit(Unit.G)
            .calories(new BigDecimal("0.740000"))
            .proteinG(new BigDecimal("0.080000"))
            .carbohydrateG(new BigDecimal("0.019000"))
            .fatG(new BigDecimal("0.048000"))
            .fiberG(new BigDecimal("0.003000"))
            .build();

    IngredientResponse response = mapper.toResponse(tofu);

    assertThat(response.getUnit()).isEqualTo("G");
    assertThat(response.getCalories()).isEqualByComparingTo("0.74");
    assertThat(response.isNutritionResolved()).isTrue();
  }

  @Test
  void toResponse_leavesUnitNullAndUnresolvedWhenUncurated() {
    Ingredient bare = Ingredient.builder().id(UUID.randomUUID()).name("muối").build();

    IngredientResponse response = mapper.toResponse(bare);

    assertThat(response.getUnit()).isNull();
    assertThat(response.getCalories()).isNull();
    assertThat(response.isNutritionResolved()).isFalse();
  }

  @Test
  void toResponse_unitWithoutNutritionIsNotResolved() {
    Ingredient half =
        Ingredient.builder().id(UUID.randomUUID()).name("bí").unit(Unit.PIECE).build();

    IngredientResponse response = mapper.toResponse(half);

    assertThat(response.getUnit()).isEqualTo("PIECE");
    assertThat(response.isNutritionResolved()).isFalse();
  }
}
