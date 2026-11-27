
package app;

import view.FoodBankView;
import view.MealGeneratorPanel;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AppBuilder appBuilder = new AppBuilder();
            FoodBankView foodBankView =
                    appBuilder.buildFoodBankView();
            MealGeneratorPanel mealGeneratorView = appBuilder.buildMealGeneratorView();

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Add Food", appBuilder.buildAddFoodView());
            tabs.addTab("Food Bank", foodBankView);
            tabs.addTab("Create Plan", appBuilder.buildCreatePlanView());
            tabs.addTab("Add Planned Meal",
                    appBuilder.buildAddPlannedMealView());
            tabs.addTab("Meal Generator", mealGeneratorView);

            tabs.addChangeListener(event -> {
                if (tabs.getSelectedComponent() == foodBankView) {
                    foodBankView.refreshFoodBank();
                } else if (tabs.getSelectedComponent() == mealGeneratorView) {
                    mealGeneratorView.refreshIngredients();
                }
            });

            JFrame frame = new JFrame("NutriTrack");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(tabs);
            frame.setSize(800, 450);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
