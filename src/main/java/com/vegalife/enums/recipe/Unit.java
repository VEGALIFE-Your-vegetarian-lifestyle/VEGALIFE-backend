package com.vegalife.enums.recipe;

import java.util.Locale;
import java.util.Optional;

/**
 * The single unit an ingredient's nutrition is expressed in (ADR-022).
 *
 * <p>There are deliberately only three. A measurement system that also carried spoons, cups,
 * bunches or cloves would need a density table and a portion table to reach a number, since those
 * units cannot be converted without knowing the specific food. Restricting each ingredient to one
 * of these makes a recipe total a plain sum: {@code amount * value}, with no conversion anywhere.
 *
 * <p>{@link #PIECE} is the honest exception: its size is not a constant, so the admin's per-piece
 * value is a curation decision rather than a measurement. That estimate is what gets stored - there
 * is no separate piece weight column to disagree with it.
 */
public enum Unit {
  /** Grams. Nutrition values are per 1 g. */
  G,

  /** Millilitres. Nutrition values are per 1 ml, so density is already baked into the number. */
  ML,

  /** One piece. Nutrition values are per 1 piece. */
  PIECE;

  /**
   * Maps member-facing unit text onto a code, so a request may say {@code gam} or {@code grams} and
   * still land on {@link #G}.
   *
   * <p>Only spelling variants of the three codes are accepted. A genuine different unit - {@code
   * kg}, {@code tbsp}, {@code bó} - is <em>not</em> translated: doing so would silently change the
   * quantity of an ingredient to something the nutrition figure does not describe. Those return
   * empty and the caller refuses the request.
   */
  public static Optional<Unit> fromInput(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "g", "gram", "grams", "gam", "gr" -> Optional.of(G);
      case "ml", "milliliter", "millilitre", "milliliters", "millilitres", "mililit" ->
          Optional.of(ML);
      case "piece",
          "pieces",
          "pcs",
          "pc",
          "cái",
          "chiếc",
          "quả",
          "trái",
          "củ",
          "cây",
          "miếng",
          "ổ",
          "con" ->
          Optional.of(PIECE);
      default -> Optional.empty();
    };
  }
}
