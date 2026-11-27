package use_case.meal_generator;

import entity.FoodPortion;

import java.util.List;

public final class MealGeneratorOutputData {
    private final List<FoodPortion> portions;
    private final double totalCalories;
    private final double targetCalories;

    public MealGeneratorOutputData(List<FoodPortion> portions,
                                   double totalCalories, double targetCalories) {
        this.portions = List.copyOf(portions);
        this.totalCalories = totalCalories;
        this.targetCalories = targetCalories;
    }

    public List<FoodPortion> getPortions() {
        return portions;
    }

    public double getTotalCalories() {
        return totalCalories;
    }

    public double getTargetCalories() {
        return targetCalories;
    }
}