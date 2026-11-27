package use_case.meal_generator;

import entity.FoodItem;
import entity.FoodPortion;
import entity.NutritionTotals;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scales equal-weight portions of the selected ingredients to a calorie target.
 */
public final class MealGenerator {
    private static final BigDecimal GRAMS_PER_REFERENCE_PORTION = BigDecimal.valueOf(100);
    private static final double MAX_RELATIVE_CALORIE_ERROR = 1e-9;

    public GeneratedMeal generate(List<FoodItem> ingredients, double targetCalories) {
        if (!Double.isFinite(targetCalories) || targetCalories <= 0) {
            throw new IllegalArgumentException("Enter a positive, finite calorie target.");
        }
        if (ingredients == null || ingredients.isEmpty()) {
            throw new IllegalArgumentException("Select at least one ingredient.");
        }

        Set<String> foodIds = new HashSet<>();
        BigDecimal caloriesPerReferencePortion = BigDecimal.ZERO;
        for (FoodItem ingredient : ingredients) {
            if (ingredient == null || !foodIds.add(ingredient.getId())) {
                throw new IllegalArgumentException("Select each ingredient only once.");
            }
            caloriesPerReferencePortion = caloriesPerReferencePortion.add(
                    BigDecimal.valueOf(ingredient.getNutritionPer100g().getCalories()));
        }
        if (caloriesPerReferencePortion.signum() == 0) {
            throw new IllegalArgumentException("Select at least one ingredient that contains calories.");
        }

        double gramsPerIngredient = BigDecimal.valueOf(targetCalories)
                .multiply(GRAMS_PER_REFERENCE_PORTION)
                .divide(caloriesPerReferencePortion, MathContext.DECIMAL128)
                .doubleValue();

        List<FoodPortion> portions = new ArrayList<>();
        NutritionTotals total = new NutritionTotals(0, 0, 0, 0);
        try {
            for (FoodItem ingredient : ingredients) {
                FoodPortion portion = FoodPortion.from(ingredient, gramsPerIngredient);
                portions.add(portion);
                total = total.add(portion.getNutrition());
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("The resulting portions or nutrition are out of range.",
                    exception);
        }

        if (Math.abs(total.getCalories() - targetCalories) / targetCalories > MAX_RELATIVE_CALORIE_ERROR) {
            throw new IllegalArgumentException("The resulting portions are too small to meet the calorie target.");
        }
        return new GeneratedMeal(portions, total);
    }

    public static final class GeneratedMeal {
        private final List<FoodPortion> portions;
        private final NutritionTotals totalNutrition;

        private GeneratedMeal(List<FoodPortion> portions, NutritionTotals totalNutrition) {
            this.portions = List.copyOf(portions);
            this.totalNutrition = totalNutrition;
        }

        public List<FoodPortion> getPortions() {
            return portions;
        }

        public NutritionTotals getTotalNutrition() {
            return totalNutrition;
        }
    }
}