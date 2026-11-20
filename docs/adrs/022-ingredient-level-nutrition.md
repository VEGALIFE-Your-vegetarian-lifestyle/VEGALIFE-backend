# ADR-022: Derive Recipe Nutrition From Ingredient Nutrition Measured Per One Unit

## Status

Accepted

## Date

2026-11-05

## Deciders

Project owner (confirmed: one unit per ingredient, no ingredient-group table,
no `measurement_type`, unit changes allowed silently, "resolved" means all five
values plus a unit, recipe totals persisted and recomputed on ingredient edits,
`null` never coerced to `0`, seed 50 ingredients from USDA FDC), backend
implementation.

Supersedes the nutrition parts of V42 and V43, and BR-RECP-003's claim that a
recipe line requires a unit.

## Context

Recipe nutrition was author-entered and stored directly on `recipe` (V42), after
V43 dropped per-100 g columns from `ingredient` because they had no recorded
basis and could not be converted into a dish figure. That arrangement has three
problems:

1. **It is unfalsifiable.** A recipe's calorie figure is a number the author
   typed. Nothing connects it to the ingredients listed. Two recipes with the
   same ingredients can carry wildly different totals, and no query can tell
   which is wrong.
2. **It is unusable for planning.** Meal-plan generation and the suggest-dishes
   filters need comparable figures across recipes. Author-entered totals are not
   comparable, so the filters have to be advisory rather than trustworthy.
3. **It asks the wrong person.** A member writing down their grandmother's
   recipe knows the amounts, not the grams of protein. Asking them for a
   nutrition table produces guesses stored as facts.

The alternative considered first was an AI-suggested estimate. It was built and
then reverted: an estimate that cannot be traced to a source is not
distinguishable from a made-up number, and nutrition is a claim the product
should be able to defend.

The third-party-API route was also rejected. It solves the wrong problem — it can
price a dish, but it cannot tell you the protein in the specific ingredient row
the catalogue uses, and it introduces a network dependency and a per-request cost
into a read path that runs on every suggestion.

What the data actually needs is a basis: **how many grams are in one unit of this
ingredient, and how much nutrition is in one of those units.**

## Decision

**1. Nutrition lives on `ingredient`, denominated per one unit.** Five columns —
`calories`, `protein_g`, `carbohydrate_g`, `fat_g`, `fiber_g` — all
`DECIMAL(12,6)`. There is no separate nutrition table and no per-100 g
convention. A row states its own basis, so a value can never be silently
misread.

`DECIMAL(12,6)` rather than `(10,2)` is load-bearing. Cooked rice is about
0.0130 g of protein per gram; at two decimal places every cereal would store
`0.01` and a 300 g portion would lose a fifth of its protein.

**2. Each ingredient has exactly one unit: `G`, `ML` or `PIECE`.** The unit is a
column on `ingredient`, not a free-text field on the recipe line, and there is no
`measurement_type`, no ingredient group, and no density column.

Three codes is the whole design. A system that also carried teaspoons, cups,
bunches or cloves needs a density table plus a portion table to reach a number,
because those units cannot be converted without knowing the specific food: a cup
of flour, a cup of oil and a cup of chopped kale are three different masses. Once
an ingredient is pinned to one unit, a recipe total is a plain sum:

```
total(macro) = Σ over lines of (line.amount × ingredient.macro_per_unit)
```

There is no conversion anywhere, which means there is no conversion to get wrong.

**3. `PIECE` is the honest exception.** A piece has no constant size, so the
admin's per-piece value is a curation decision, not a measurement. That estimate
is what the row stores — there is deliberately no separate piece-weight column,
because the per-piece figure already contains the mass assumption and a second
field would only be a number nothing reads.

A tomato illustrates the limit: real pieces span roughly 10 g (cherry) to 300 g
(beefsteak), and USDA keeps seven separate portion rows for it. The mitigation is
to split the ingredient (`cà chua bi` vs `cà chua`), not to build a portion table.

**4. Recipe nutrition is derived, persisted, and recomputed in the same
transaction.** `recipe` keeps its five nutrition columns, but they are outputs,
never inputs:

- On recipe create, every line is written and then the recipe's totals are
  computed from those lines.
- On any admin edit that changes an ingredient's unit or any nutrition value,
  every recipe referencing that ingredient is recomputed inside the same
  transaction.

Persisting rather than computing on read is deliberate: the suggest-dishes
filters run in SQL over paginated dish queries, and a derived-on-read total
cannot be filtered, sorted or indexed without a join-and-aggregate per row. The
cost is one write per affected recipe; the benefit is that calorie filtering stays
a column comparison.

**5. "Resolved" is all-or-nothing.** A recipe has nutrition only when every line's
ingredient has a non-null unit **and** all five values. Otherwise all five recipe
columns are `null`. A partial sum is never returned: it would look authoritative
while silently omitting a macro, and the difference between "2 g of fat" and
"2 g of fat that we know about" is not visible to the reader.

`null` means "not provided" and stays distinct from `0`. Water is legitimately
0 kcal with 0 g of everything; treating a missing value as zero would make every
uncurated ingredient look like water.

**6. Members do not enter nutrition.** `RecipeCreateRequest` lost all five
nutrition fields, and `recipe_ingredient` lost its `unit` column. A member
supplies a name, an amount, and — only when the name is brand new — a unit. If the
ingredient already exists, the caller's unit is ignored: the row's own unit
already governs every recipe that references it, and honouring the caller's text
would silently re-denominate a shared ingredient and change the quantity in other
people's recipes.

**7. Admins author nutrition, and a half-answer is refused.** Nutrition is
all-or-nothing per request *after the merge with the stored row*, not per request
payload. Sending only `calories` is accepted when the other four already exist
(so a single wrong value can be corrected) and refused when the ingredient has
none. Recording nutrition without a unit is refused outright.

**8. Changing an ingredient's unit is allowed silently.** No guard, no blocking,
no confirmation. This is a real hazard and was accepted deliberately: stored
amounts are bare numbers once `recipe_ingredient.unit` is gone, so switching rice
from `G` to `ML` turns `200 g` (≈730 kcal) into `200 ml` (≈260 kcal) with no
error and no trace. Every affected recipe is recomputed and the change is logged
at WARN with the affected recipe count so the consequence is at least
reconstructible afterwards.

**9. Seed 50 ingredients from USDA FoodData Central (SR Legacy, CC0).** Values
are copied per 100 g and converted mechanically: divide by 100 for `G` and `ML`;
for `PIECE`, multiply by USDA's measured `foodPortion.gramWeight` and divide by
100. Existing rows win — the seed never overwrites an ingredient a member
already created, because that row's unit may have been resolved differently.

**10. A unit is read-contract data, not admin-only metadata.** Anything that
returns a quantity to a member must also return the unit that quantity is in,
because after decision 6 the number alone is meaningless. That makes `unit`
part of `IngredientResponse` (the create-recipe autocomplete) and of every recipe
line. Withholding it is not conservative: a client that cannot see the unit
cannot describe the amount, and the resulting recipe is silently wrong rather
than refused.

The same argument does not extend to the dish ingredient suggestion surfaces
(`DishIngredientSuggestionResponse`, `AdminDishIngredientResponse`). Those return
a curated *name list* with no `amount`, so there is no quantity for a unit to
govern. Adding one there would imply an amount that does not exist.

**11. Line nutrition is exposed per line, rounded independently.** A recipe read
returns each line's contribution alongside the totals. Each line is rounded
`HALF_UP` at scale 2; the total stays rounded once over the exact sum (decision 4
and the calculator's contract). The two therefore need not add up exactly: three
lines of `0.005` kcal display as `0.03` against a total of `0.02`.

The alternative — leaving per-line values unrounded — would keep the identity
exact but push a `DECIMAL(12,6)`-derived product, which can carry many more
digits than that, onto every client to format. Rounding to the same scale as the
total is the smaller surface, and the discrepancy is below the display precision
of any kcal or gram figure the product shows. The cost is that no client may
assert "these lines sum to this total".

## Consequences

**Positive:**

- A recipe total is traceable to its ingredients. Correcting an ingredient
  corrects every recipe that uses it, everywhere, at once.
- Members are asked only for what they know. The product no longer stores a
  guess as a fact.
- Totals are comparable across recipes, so the suggest-dishes filters can be
  strict instead of advisory.
- One unit per ingredient makes arithmetic a sum. There is no density, no
  portion table, no conversion factor, and therefore no conversion bug.
- Fixing a data-entry error in one ingredient improves every dish using it.

**Negative / trade-offs:**

- **Silent unit change is a real data hazard.** `200 g` of rice and `200 ml` of
  rice are the same number in the same column. The design accepts a
  mis-reinterpretation that produces no error, with only a WARN log to find it
  afterwards.
- **No member recipe-update endpoint exists.** A bad unit on an existing recipe
  is uncorrectable except by deleting the recipe and creating it again.
- **`PIECE` is a curation decision wearing a measurement's clothes.** The stored
  per-piece value is only as good as the estimate behind it, and that estimate is
  per ingredient, not per recipe.
- **`amount` is `@NotNull @Positive`.** `vừa đủ` ("as much as needed") is
  structurally inexpressible: an ingredient like salt-to-taste cannot be listed
  at all unless it carries a number.
- **Resolving the catalogue is a data project, not a code change.** Until an
  ingredient is curated, every recipe using it has `null` nutrition, and null
  fails every nutrition bound — so dish suggestions and AI menu building return
  fewer results in the interim.
- **`admin` creating an ingredient sets `APPROVED`, and V39 backfilled every
  pre-existing ingredient to `APPROVED`.** Therefore "approved implies curated"
  is false immediately after deploy; approval and nutrition are independent
  facts.
- **The nutrition values are only as good as the catalogue.** USDA's values are
  means over samples, and vegan Vietnamese staples (rau muống, nước tương,
  sả) are approximated by the closest FDC entry. The ADR records the source so
  the approximation is inspectable, not hidden.

## Alternatives considered

**Per-100 g canonical values plus a portion table (USDA's design).** This is the
right model for a general-purpose food database: one canonical value, many
portions. Rejected here because it moves the conversion problem into our code:
every read becomes a lookup that can fail, and every ingredient needs one to
seven portion rows before a recipe can be priced. It also cannot express
`PIECE` honestly, which is precisely the case we need for produce.

**A separate `ingredient_nutrition` table with a unit-basis column.** Normalises
better and would allow several bases per ingredient. Rejected: it permits *two*
bases for the same ingredient, which is exactly the ambiguity the design
removes. One row, one basis.

**An `ingredient_unit_factor` table, density, or `measurement_type`.** All three
were proposed and dropped. Each reintroduces a conversion step whose inputs
(spoons, cups, bunches) are not knowable from the name.

**AI-suggested nutrition estimates.** Built, then reverted. A generated estimate
is indistinguishable from a fabricated number, and the product would be
publishing nutrition claims it cannot source.

**Call a third-party nutrition API at read time.** Rejected: per-request cost and
latency on a hot read path, an external availability dependency, and it still
cannot answer "what is in *this* catalogue row".

**Derive recipe totals on read instead of persisting them.** Would guarantee
consistency with no recompute step. Rejected because the suggest-dishes filter is
a paginated SQL query: filtering on a derived aggregate forces a group-by over
every recipe on every page, and the existing `minCalories`/`maxCalories` bounds
would have to move into an unindexed subquery.

**Keep the unit on `recipe_ingredient` as well.** Would let two lines of the same
ingredient use different units. Rejected: it creates two sources of truth for one
fact, and the response would have to choose which to echo. Dropping it makes the
amount's meaning come from exactly one place.

**Require existing ingredients' units to be non-null in the migration.** Would
guarantee every row is computable. Rejected: the legacy free text contains `bó`,
`vừa đủ` and typos that have no honest mapping. Guessing would invent a basis;
leaving it `null` makes the recipe explicitly uncomputable, which is true.

**Block a unit change, or require a confirming flag.** Would prevent the
mis-reinterpretation above. Rejected by the owner: the catalogue is still being
built, and a guard would make correcting a mistake harder than making one.

**A `piece_grams` column recording the assumed mass behind a `PIECE` value.**
Built, then removed before release. The per-piece figure already contains that
mass, so the column was a number nothing read and that could silently disagree
with the value it supposedly explained: change `calories` without changing
`piece_grams` and the row contradicts itself, with no check able to detect it.
An admin entering the per-piece estimate directly is the whole model; a second
field to justify it adds a maintenance obligation without adding information the
stored value does not already carry. USDA's gram weight is still what the seed
uses to *produce* each estimate, but it lives in that migration, not in the
schema.

## References

- Feature: [Ingredient-Level Recipe Nutrition](../feats/ingredient-nutrition.md)
- Feature: [Nutrition on Read Surfaces](../feats/nutrition-read-surfaces.md)
- Business rules: [Recipes](../brs/recipes.md) (BR-RECP-004, BR-RECP-005,
  BR-RECP-006 added)
- API: [POST /api/recipes](../apis/recipes/post-recipes.md),
  [GET /api/recipes/me](../apis/recipes/get-recipes-me.md),
  [GET /api/recipes/{recipeId}](../apis/recipes/get-recipes-recipeid.md),
  [GET /api/dishes/suggestions](../apis/recipes/get-dishes-suggestions.md),
  [Admin dish and ingredient CRUD](../apis/admin/dish-and-ingredient-crud.md)
- Migrations: `V42__recipe_nutrition_totals.sql` (totals on `recipe`, now
  derived), `V43__drop_ingredient_nutrition.sql` (the drop this ADR reverses),
  `V49__ingredient_unit_and_nutrition.sql`, `V50__seed_ingredient_nutrition.sql`
- [ADR-016: Moderate components without per-occurrence verdict storage](016-component-level-moderation.md)
  — why dishes and ingredients are shared, unowned rows
- Source data: USDA FoodData Central, SR Legacy (public domain / CC0),
  https://fdc.nal.usda.gov/
