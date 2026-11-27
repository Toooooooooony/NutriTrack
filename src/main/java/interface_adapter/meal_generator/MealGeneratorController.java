package interface_adapter.meal_generator;

import entity.FoodItem;
import use_case.meal_generator.MealGeneratorInputBoundary;
import use_case.meal_generator.MealGeneratorInputData;

import java.util.List;

public final class MealGeneratorController {
    private final MealGeneratorInputBoundary useCase;

    public MealGeneratorController(MealGeneratorInputBoundary useCase) {
        this.useCase = useCase;
    }

    public void execute(List<FoodItem> ingredients, double targetCalories) {
        useCase.execute(new MealGeneratorInputData(ingredients, targetCalories));
    }
}
