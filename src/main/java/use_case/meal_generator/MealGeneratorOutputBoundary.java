package use_case.meal_generator;

public interface MealGeneratorOutputBoundary {
    void prepareSuccessView(MealGeneratorOutputData outputData);

    void prepareFailView(String errorMessage);
}