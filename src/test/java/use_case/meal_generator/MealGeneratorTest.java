package use_case.meal_generator;

import entity.FoodItem;
import entity.FoodPortion;
import entity.NutritionTotals;
import org.junit.jupiter.api.Test;
import use_case.meal_generator.MealGenerator.GeneratedMeal;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MealGeneratorTest {
    private final MealGenerator generator = new MealGenerator();
    private final FoodItem rice = new FoodItem("rice", "Rice", new NutritionTotals(120, 3, 25, 1));
    private final FoodItem tofu = new FoodItem("tofu", "Tofu", new NutritionTotals(160, 16, 4, 9));
    private final FoodItem water = new FoodItem("water", "Water", new NutritionTotals(0, 0, 0, 0));

    @Test
    void meetsTheTargetUsingEachSelectedIngredientInOrder() {
        GeneratedMeal meal = generator.generate(List.of(rice, tofu), 350.5);

        List<FoodPortion> portions = meal.getPortions();
        double expectedGrams = 350.5 * 100 / 280;
        assertEquals(2, portions.size());
        assertEquals("rice", portions.get(0).getFoodId());
        assertEquals("tofu", portions.get(1).getFoodId());
        assertEquals(expectedGrams, portions.get(0).getQuantityGrams(), 1e-9);
        assertEquals(expectedGrams, portions.get(1).getQuantityGrams(), 1e-9);
        assertEquals(350.5, meal.getTotalNutrition().getCalories(), 1e-9);
        assertEquals(19 * expectedGrams / 100, meal.getTotalNutrition().getProtein(), 1e-9);
    }

    @Test
    void includesCalorieFreeIngredientsWhenAnotherIngredientProvidesCalories() {
        GeneratedMeal meal = generator.generate(List.of(water, tofu), 80);

        assertEquals(2, meal.getPortions().size());
        assertEquals(50, meal.getPortions().get(0).getQuantityGrams());
        assertEquals(0, meal.getPortions().get(0).getNutrition().getCalories());
        assertEquals(80, meal.getTotalNutrition().getCalories());
    }

    @Test
    void rejectsSelectionsThatCannotGenerateAMeal() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of(), 450));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of(water), 450));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of(rice, rice), 450));
    }

    @Test
    void rejectsInvalidCalorieTargets() {
        for (double target : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of(rice), target));
        }
    }
}
