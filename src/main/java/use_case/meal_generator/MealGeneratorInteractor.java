package use_case.meal_generator;

public final class MealGeneratorInteractor implements MealGeneratorInputBoundary {
    private final MealGenerator generator;
    private final MealGeneratorOutputBoundary outputBoundary;

    public MealGeneratorInteractor(MealGenerator generator,
                                   MealGeneratorOutputBoundary outputBoundary) {
        this.generator = generator;
        this.outputBoundary = outputBoundary;
    }

    @Override
    public void execute(MealGeneratorInputData inputData) {
        if (inputData == null) {
            outputBoundary.prepareFailView("Meal information is required.");
            return;
        }

        MealGenerator.GeneratedMeal meal;
        try {
            meal = generator.generate(inputData.getIngredients(), inputData.getTargetCalories());
        } catch (IllegalArgumentException exception) {
            outputBoundary.prepareFailView(exception.getMessage());
            return;
        }

        outputBoundary.prepareSuccessView(new MealGeneratorOutputData(
                meal.getPortions(),
                meal.getTotalNutrition().getCalories(),
                inputData.getTargetCalories()
        ));
    }
}
