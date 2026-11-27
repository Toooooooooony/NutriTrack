package interface_adapter.meal_generator;

import use_case.meal_generator.MealGeneratorOutputBoundary;
import use_case.meal_generator.MealGeneratorOutputData;

public final class MealGeneratorPresenter implements MealGeneratorOutputBoundary {
    private final MealGeneratorViewModel viewModel;

    public MealGeneratorPresenter(MealGeneratorViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void prepareSuccessView(MealGeneratorOutputData outputData) {
        viewModel.setSuccessResult(outputData.getPortions(),
                outputData.getTotalCalories(), outputData.getTargetCalories());
    }

    @Override
    public void prepareFailView(String errorMessage) {
        viewModel.setFailureResult(errorMessage);
    }
}