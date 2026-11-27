package view;

import data_access.UsdaFoodClient;
import entity.FoodItem;
import entity.FoodPortion;
import interface_adapter.meal_generator.MealGeneratorController;
import interface_adapter.meal_generator.MealGeneratorViewModel;
import use_case.meal_generator.RecipeSearchService;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MealGeneratorPanel extends JPanel {
    private final Supplier<List<FoodItem>> foodCatalog;
    private final UsdaFoodClient usda;
    private final MealGeneratorController controller;
    private final MealGeneratorViewModel viewModel;
    private final JPanel choices = new JPanel();
    private final Map<JCheckBox, FoodItem> ingredients = new LinkedHashMap<>();
    private final Set<String> ingredientIds = new HashSet<>();
    private final JLabel emptyCatalog = new JLabel("Add a food to start selecting ingredients.");
    private final JTextField calorieTarget = new JTextField(8);
    private final JComboBox<RecipeSearchService.Cuisine> cuisineChoice =
            new JComboBox<>(RecipeSearchService.Cuisine.values());
    private final JLabel status = new JLabel(" ");
    private final TitledBorder resultBorder =
            BorderFactory.createTitledBorder("Generated portions");
    private final DecimalFormat decimal = new DecimalFormat("0.##",
            DecimalFormatSymbols.getInstance(Locale.ROOT));
    private final DecimalFormat scientific = new DecimalFormat("0.##E0",
            DecimalFormatSymbols.getInstance(Locale.ROOT));
    private final DefaultTableModel portions = new DefaultTableModel(
            new String[]{"Ingredient", "Amount", "Calories (kcal)"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    public MealGeneratorPanel(MealGeneratorController controller,
                              MealGeneratorViewModel viewModel,
                              Supplier<List<FoodItem>> foodCatalog) {
        this(controller, viewModel, foodCatalog, null);
    }

    public MealGeneratorPanel(MealGeneratorController controller,
                              MealGeneratorViewModel viewModel,
                              Supplier<List<FoodItem>> foodCatalog,
                              UsdaFoodClient usda) {
        super(new BorderLayout(0, 12));
        this.controller = controller;
        this.viewModel = viewModel;
        this.foodCatalog = foodCatalog;
        this.usda = usda;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS));
        choices.add(emptyCatalog);
        refreshIngredients();
        JScrollPane ingredientList = new JScrollPane(choices);
        ingredientList.setPreferredSize(new Dimension(460, 190));
        ingredientList.setBorder(BorderFactory.createTitledBorder("Preferred ingredients"));

        JLabel targetLabel = new JLabel("Target calories (kcal)");
        targetLabel.setLabelFor(calorieTarget);
        JButton generate = new JButton("Generate meal");
        generate.addActionListener(event -> generate(generate));
        calorieTarget.addActionListener(event -> generate.doClick(0));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        controls.add(targetLabel);
        controls.add(calorieTarget);
        JLabel cuisineLabel = new JLabel("Cuisine");
        cuisineLabel.setLabelFor(cuisineChoice);
        controls.add(cuisineLabel);
        controls.add(cuisineChoice);
        controls.add(generate);

        if (usda != null) {
            add(createUsdaControls(usda), BorderLayout.NORTH);
        }

        JPanel inputs = new JPanel(new BorderLayout(0, 12));
        inputs.add(ingredientList, BorderLayout.CENTER);
        inputs.add(controls, BorderLayout.SOUTH);

        JTable results = new JTable(portions);
        results.setFillsViewportHeight(true);
        results.setPreferredScrollableViewportSize(new Dimension(460, 140));
        results.getColumnModel().getColumn(0).setPreferredWidth(220);

        JScrollPane resultList = new JScrollPane(results);
        resultList.setBorder(resultBorder);

        JPanel meal = new JPanel(new GridLayout(2, 1, 0, 12));
        meal.add(inputs);
        meal.add(resultList);

        add(meal, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        viewModel.addPropertyChangeListener(event -> {
            if ("mealGeneratorResult".equals(event.getPropertyName())) {
                updateView();
            }
        });
    }

    public void refreshIngredients() {
        for (FoodItem food : foodCatalog.get()) {
            addIngredient(food, false);
        }
        choices.revalidate();
        choices.repaint();
    }

    private boolean addIngredient(FoodItem food, boolean selected) {
        if (!ingredientIds.add(food.getId())) {
            return false;
        }
        String calories = format(food.getNutritionPer100g().getCalories());
        JCheckBox choice = new JCheckBox(food.getName() + " (" + calories + " kcal per 100 g)");
        choice.setSelected(selected);
        ingredients.put(choice, food);
        choices.add(choice);
        emptyCatalog.setVisible(false);
        return true;
    }

    private JPanel createUsdaControls(UsdaFoodClient usda) {
        JTextField query = new JTextField(16);
        JButton search = new JButton("Search USDA");
        JComboBox<UsdaSearchResult> results = new JComboBox<>();
        results.setPrototypeDisplayValue(new UsdaSearchResult(0, "Select a food from your search results"));
        JButton add = new JButton("Add ingredient");
        add.setEnabled(false);
        JLabel searchStatus = new JLabel(" ");

        Runnable finishRequest = () -> {
            search.setEnabled(true);
            results.setEnabled(true);
            add.setEnabled(results.getSelectedIndex() >= 0);
        };

        search.addActionListener(event -> {
            String foodName = query.getText().strip();
            if (foodName.isEmpty()) {
                searchStatus.setText("Enter a food name.");
                return;
            }

            search.setEnabled(false);
            add.setEnabled(false);
            results.setEnabled(false);
            results.removeAllItems();
            searchStatus.setText("Searching USDA...");

            runUsdaRequest(() -> usda.search(foodName), matches -> {
                matches.forEach((id, name) -> results.addItem(new UsdaSearchResult(id, name)));
                searchStatus.setText(matches.isEmpty() ? "No matching foods found."
                        : "Select a result and click Add ingredient.");
            }, "Could not search USDA foods.", searchStatus, finishRequest);
        });
        query.addActionListener(event -> search.doClick(0));

        add.addActionListener(event -> {
            UsdaSearchResult result = results.getItemAt(results.getSelectedIndex());
            search.setEnabled(false);
            add.setEnabled(false);
            results.setEnabled(false);
            searchStatus.setText("Loading nutrition data...");

            runUsdaRequest(() -> usda.getFood(result.id), food -> {
                if (!addIngredient(food, true)) {
                    searchStatus.setText("This ingredient is already in the list.");
                    return;
                }

                choices.revalidate();
                choices.repaint();
                searchStatus.setText("Ingredient added and selected.");
            }, "Could not load nutrition data for this food.", searchStatus, finishRequest);
        });

        JLabel queryLabel = new JLabel("Food name");
        queryLabel.setLabelFor(query);
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        searchRow.add(queryLabel);
        searchRow.add(query);
        searchRow.add(search);

        JLabel resultsLabel = new JLabel("USDA results");
        resultsLabel.setLabelFor(results);
        JPanel resultsRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        resultsRow.add(resultsLabel);
        resultsRow.add(results);
        resultsRow.add(add);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        searchRow.setAlignmentX(LEFT_ALIGNMENT);
        resultsRow.setAlignmentX(LEFT_ALIGNMENT);
        controls.add(searchRow);
        controls.add(resultsRow);
        controls.add(searchStatus);
        return controls;
    }

    private <T> void runUsdaRequest(Callable<T> request, Consumer<T> onSuccess,
                                    String errorMessage, JLabel searchStatus, Runnable onFinished) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return request.call();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    searchStatus.setText("USDA request interrupted.");
                } catch (ExecutionException exception) {
                    searchStatus.setText(errorMessage);
                } finally {
                    onFinished.run();
                }
            }
        }.execute();
    }

    private static final class UsdaSearchResult {
        private final int id;
        private final String name;

        private UsdaSearchResult(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name + " (" + id + ")";
        }
    }

    private void generate(JButton button) {
        portions.setRowCount(0);
        resultBorder.setTitle("Generated portions");
        repaint();

        double target;
        try {
            target = Double.parseDouble(calorieTarget.getText().strip());
            if (!Double.isFinite(target) || target <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            status.setText("Enter a positive calorie target.");
            return;
        }

        List<FoodItem> availableFoods = new ArrayList<>();
        ingredients.forEach((checkbox, food) -> {
            if (checkbox.isSelected()) {
                availableFoods.add(food);
            }
        });

        if (availableFoods.isEmpty()) {
            status.setText("Select at least one ingredient.");
            return;
        }

        RecipeSearchService recipes = new RecipeSearchService(
                System.getenv("SPOONACULAR_API_KEY"),
                System.getenv("THEMEALDB_API_KEY"),
                usda);
        button.setEnabled(false);
        status.setText("Finding a recipe...");

        RecipeSearchService.Cuisine selectedCuisine =
                (RecipeSearchService.Cuisine) cuisineChoice.getSelectedItem();
        new SwingWorker<RecipeSearchService.Result, Void>() {
            @Override
            protected RecipeSearchService.Result doInBackground() throws Exception {
                return recipes.search(availableFoods, target, selectedCuisine);
            }

            @Override
            protected void done() {
                try {
                    showRecipe(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    status.setText("Recipe search interrupted.");
                } catch (ExecutionException exception) {
                    String message = "Could not generate a meal. " + exception.getCause().getMessage();
                    status.setText(message);
                    System.err.println(message);
                } finally {
                    button.setEnabled(true);
                }
            }
        }.execute();
    }

    private void showRecipe(RecipeSearchService.Result recipe) {
        String title = recipe.title + " (" + recipe.source + ")";
        if (recipe.missingIngredients) {
            resultBorder.setTitle("Missing ingredients");
            status.setText("Closest recipe: " + title);
        } else if (recipe.calories == null) {
            resultBorder.setTitle("Original recipe amounts");
            status.setText(title + " | Some ingredients could not be converted to grams.");
        } else if (recipe.mainIngredientsOnly) {
            resultBorder.setTitle("Scaled recipe amounts");
            status.setText(title + " | Main ingredients: " + format(recipe.calories)
                    + " kcal. Seasonings are excluded from this total.");
        } else {
            status.setText(title + " | Estimated total " + format(recipe.calories) + " kcal");
        }

        for (RecipeSearchService.Row row : recipe.rows) {
            String amount;
            if (row.grams != null) {
                amount = format(row.grams) + " g";
            } else {
                amount = row.originalMeasure.isBlank() ? "—" : row.originalMeasure;
            }

            portions.addRow(new Object[]{
                    row.ingredient,
                    amount,
                    row.calories == null ? "—" : format(row.calories)
            });
        }
        repaint();
    }

    private void updateView() {
        portions.setRowCount(0);
        if (viewModel.getErrorMessage() != null) {
            status.setText(viewModel.getErrorMessage());
            return;
        }

        for (FoodPortion portion : viewModel.getPortions()) {
            portions.addRow(new Object[]{
                    portion.getFoodName(),
                    format(portion.getQuantityGrams()),
                    format(portion.getNutrition().getCalories())
            });
        }

        status.setText("Meal total " + format(viewModel.getTotalCalories())
                + " kcal   |   Target " + format(viewModel.getTargetCalories()) + " kcal");
    }

    private String format(double value) {
        if ((value > 0 && value < 0.01) || value >= 1e7) {
            return scientific.format(value);
        }
        return decimal.format(value);
    }
}
