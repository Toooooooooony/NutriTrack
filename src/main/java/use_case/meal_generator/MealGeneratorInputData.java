package use_case.meal_generator;

import entity.FoodItem;

import java.util.List;

public final class MealGeneratorInputData {
    private final List<FoodItem> ingredients;
    private final double targetCalories;

    public MealGeneratorInputData(List<FoodItem> ingredients, double targetCalories) {
        this.ingredients = ingredients;
        this.targetCalories = targetCalories;
    }

    public List<FoodItem> getIngredients() {
        return ingredients;
    }

    public double getTargetCalories() {
        return targetCalories;
    }
}
