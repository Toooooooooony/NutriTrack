package interface_adapter.meal_generator;

import entity.FoodPortion;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;

public final class MealGeneratorViewModel {
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    private List<FoodPortion> portions = List.of();
    private double totalCalories;
    private double targetCalories;
    private String errorMessage;

    public List<FoodPortion> getPortions() {
        return portions;
    }

    public double getTotalCalories() {
        return totalCalories;
    }

    public double getTargetCalories() {
        return targetCalories;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setSuccessResult(List<FoodPortion> portions,
                                 double totalCalories, double targetCalories) {
        this.portions = List.copyOf(portions);
        this.totalCalories = totalCalories;
        this.targetCalories = targetCalories;
        this.errorMessage = null;
        support.firePropertyChange("mealGeneratorResult", null, this);
    }

    public void setFailureResult(String errorMessage) {
        this.portions = List.of();
        this.totalCalories = 0;
        this.targetCalories = 0;
        this.errorMessage = errorMessage;
        support.firePropertyChange("mealGeneratorResult", null, this);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }
}
