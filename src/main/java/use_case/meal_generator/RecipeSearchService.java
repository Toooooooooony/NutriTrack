package use_case.meal_generator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import data_access.SpoonacularRecipeClient;
import data_access.TheMealDbClient;
import entity.FoodItem;
import com.google.gson.JsonArray;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import use_case.import_food.FoodDataGateway;

public final class RecipeSearchService {
    private static final Pattern MASS = Pattern.compile(
            "^(\\d+(?:\\.\\d+)?)\\s*(g|grams?|kg|kilograms?|oz|ounces?|lbs?|pounds?)$",
            Pattern.CASE_INSENSITIVE);
    private static final String SPOONACULAR = "Spoonacular";
    private static final String THE_MEAL_DB = "TheMealDB";

    private final SpoonacularRecipeClient spoonacular;
    private final String mealDbKey;

    private final FoodDataGateway nutritionGateway;

    public RecipeSearchService(String spoonacularKey, String mealDbKey) {
        this(spoonacularKey, mealDbKey, null);
    }

    public RecipeSearchService(String spoonacularKey, String mealDbKey,
                               FoodDataGateway nutritionGateway) {
        spoonacular = spoonacularKey == null || spoonacularKey.isBlank()
                ? null : new SpoonacularRecipeClient(spoonacularKey);
        this.mealDbKey = mealDbKey;
        this.nutritionGateway = nutritionGateway;
    }

    public Result search(List<FoodItem> foods, double targetCalories)
            throws IOException, InterruptedException {
        return search(foods, targetCalories, Cuisine.ANY);
    }

    public Result search(List<FoodItem> foods, double targetCalories, Cuisine cuisine)
            throws IOException, InterruptedException {
        if (cuisine != Cuisine.ANY) {
            Result preferred = searchOnce(foods, targetCalories, cuisine);
            if (preferred != null) {
                return preferred;
            }
        }
        return searchOnce(foods, targetCalories, Cuisine.ANY);
    }

    private Result searchOnce(List<FoodItem> foods, double targetCalories, Cuisine cuisine)
            throws IOException, InterruptedException {
        Map<String, FoodItem> available = new LinkedHashMap<>();
        for (FoodItem food : foods) {
            available.putIfAbsent(selectedIngredientKey(food.getName()), food);
        }
        List<String> terms = new ArrayList<>(available.keySet());
        var mealDbSearch = searchAsync(() -> fromTheMealDb(available, terms, cuisine));
        var spoonSearch = spoonacular == null
                ? CompletableFuture.completedFuture(List.<Candidate>of())
                : searchAsync(() -> fromSpoonacular(terms, cuisine));

        List<Candidate> candidates = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        collect(spoonSearch, SPOONACULAR, candidates, errors);
        collect(mealDbSearch, THE_MEAL_DB, candidates, errors);
        if (candidates.isEmpty()) {
            if (cuisine != Cuisine.ANY) {
                return null;
            }
            String details = errors.isEmpty() ? "" : " " + String.join(" ", errors);
            throw new IOException("No recipes found for your selected foods." + details);
        }

        candidates.sort(Comparator
                .comparingInt((Candidate recipe) -> recipe.used).reversed()
                .thenComparing(recipe -> !THE_MEAL_DB.equals(recipe.source))
                .thenComparingInt(recipe -> recipe.missing.size()));
        for (Candidate candidate : candidates) {
            if (THE_MEAL_DB.equals(candidate.source) && candidate.used == available.size()) {
                Result result = scaleMainIngredients(candidate, targetCalories);
                if (result.calories != null) {
                    return result;
                }
            }
        }
        for (Candidate candidate : candidates) {
            try {
                Result result = THE_MEAL_DB.equals(candidate.source)
                        ? scaleMainIngredients(candidate, targetCalories)
                        : scaleSpoonacular(candidate, targetCalories);
                if (result.calories != null) {
                    return result;
                }
            } catch (IOException exception) {
                // This recipe could not be calculated. Try the next one.
                System.err.println("Nutrition calculation failed for " + candidate.title
                        + ": " + exception.getMessage());
                System.err.println("Nutrition calculation failed for " + candidate.title
                        + ": " + exception.getMessage());
            }
        }

        if (cuisine != Cuisine.ANY) {
            return null;
        }

        for (Candidate candidate : candidates) {
            if (THE_MEAL_DB.equals(candidate.source)) {
                return scaleMainIngredients(candidate, targetCalories);
            }
        }

        throw new IOException("Found recipes, but could not calculate their nutrition.");
    }

    private Result scaleMainIngredients(Candidate candidate, double target)
            throws InterruptedException {
        List<Row> originalRows = new ArrayList<>();
        List<String> missingLines = new ArrayList<>();

        for (MeasuredIngredient ingredient : candidate.ingredients) {
            double weight = mealDbGrams(ingredient);
            Double grams = Double.isFinite(weight) && weight > 0 ? weight : null;
            Double kcal = null;

            if (isMainIngredient(ingredient.name)) {
                if (grams != null) {
                    Double kcalPer100g = mainCaloriesPer100g(ingredient);
                    if (kcalPer100g != null) {
                        kcal = kcalPer100g * grams / 100.0;
                    }
                }
                if (grams == null || kcal == null) {
                    missingLines.add(recipeLine(ingredient));
                }
            }
            originalRows.add(new Row(ingredient.name, grams, kcal, ingredient.measure));
        }

        Map<String, JsonObject> estimates = estimateRecipePortions(missingLines);
        double mainTotal = 0;
        boolean allMainAmountsKnown = true;

        for (int i = 0; i < originalRows.size(); i++) {
            MeasuredIngredient ingredient = candidate.ingredients.get(i);
            if (!isMainIngredient(ingredient.name)) {
                continue;
            }

            Row row = originalRows.get(i);
            Double grams = row.grams;
            Double kcal = row.calories;
            JsonObject estimate = estimates.get(recipeLine(ingredient));

            if (estimate != null && (grams == null || kcal == null)) {
                JsonObject weight = estimate.getAsJsonObject("weightPerServing");
                if (weight != null && "g".equalsIgnoreCase(text(weight, "unit"))
                        && weight.has("amount") && !weight.get("amount").isJsonNull()) {
                    double estimatedGrams = weight.get("amount").getAsDouble();
                    if (Double.isFinite(estimatedGrams) && estimatedGrams > 0) {
                        if (grams == null) {
                            grams = estimatedGrams;
                        }
                        if (kcal == null) {
                            Double kcalPer100g = mainCaloriesPer100g(ingredient);
                            if (kcalPer100g != null) {
                                kcal = kcalPer100g * grams / 100.0;
                            } else {
                                Double estimatedKcal = readCalories(estimate.getAsJsonArray("nutrients"));
                                if (estimatedKcal != null) {
                                    kcal = estimatedKcal * grams / estimatedGrams;
                                }
                            }
                        }
                    }
                }
            }

            if (grams == null || kcal == null || !Double.isFinite(kcal) || kcal < 0) {
                allMainAmountsKnown = false;
            } else {
                mainTotal += kcal;
            }
            originalRows.set(i, new Row(row.ingredient, grams, kcal, row.originalMeasure));
        }

        if (!allMainAmountsKnown || !Double.isFinite(mainTotal) || mainTotal <= 0) {
            return new Result(candidate, originalRows, null);
        }

        double factor = target / mainTotal;
        List<Row> scaledRows = new ArrayList<>();
        double scaledMainTotal = 0;

        for (Row row : originalRows) {
            Double grams = row.grams == null ? null : row.grams * factor;
            Double kcal = row.calories == null ? null : row.calories * factor;
            if (kcal != null) {
                scaledMainTotal += kcal;
            }

            String measure = grams == null && !row.originalMeasure.isBlank()
                    ? row.originalMeasure + " (original)"
                    : "";
            scaledRows.add(new Row(row.ingredient, grams, kcal, measure));
        }

        return new Result(candidate, scaledRows, scaledMainTotal, true);
    }

    private Map<String, JsonObject> estimateRecipePortions(List<String> lines)
            throws InterruptedException {
        Map<String, JsonObject> estimates = new LinkedHashMap<>();
        if (spoonacular == null || lines.isEmpty()) {
            return estimates;
        }

        try {
            for (JsonElement entry : spoonacular.parseIngredientNutrition(lines)) {
                JsonObject parsed = entry.getAsJsonObject();
                JsonObject nutrition = parsed.getAsJsonObject("nutrition");
                if (nutrition != null) {
                    estimates.put(text(parsed, "original"), nutrition);
                }
            }
        } catch (IOException exception) {
            System.err.println("Recipe portion lookup failed: " + exception.getMessage());
        }
        return estimates;
    }

    private static String recipeLine(MeasuredIngredient ingredient) {
        return normalizeMeasure(ingredient.measure) + " " + ingredient.name;
    }

    private static boolean isMainIngredient(String name) {
        String key = ingredientKey(name);
        Set<String> seasonings = Set.of(
                "salt", "pepper", "black pepper", "sugar", "honey",
                "mint", "fresh mint", "lime", "lime juice", "sake"
        );
        return !seasonings.contains(key)
                && !key.endsWith(" oil")
                && !key.endsWith(" sauce")
                && !key.endsWith(" vinegar");
    }

    private Double mainCaloriesPer100g(MeasuredIngredient ingredient)
            throws InterruptedException {
        if (ingredient.food != null) {
            return ingredient.food.getNutritionPer100g().getCalories();
        }
        if (nutritionGateway == null) {
            return null;
        }

        String key = ingredientKey(ingredient.name);
        try {
            for (Map.Entry<Integer, String> match : nutritionGateway.search(key).entrySet()) {
                String description = match.getValue();
                boolean exact = ingredientKey(description).equals(key);
                boolean rawMatch = description.toLowerCase(Locale.ROOT).strip().endsWith(", raw")
                        && selectedIngredientKey(description).equals(key);

                if (exact || rawMatch) {
                    return nutritionGateway.getFood(match.getKey())
                            .getNutritionPer100g().getCalories();
                }
            }
        } catch (IOException exception) {
            System.err.println("Nutrition lookup failed for " + ingredient.name
                    + ": " + exception.getMessage());
        }
        return null;
    }

    private static CompletableFuture<List<Candidate>> searchAsync(Callable<List<Candidate>> request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return request.call();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CompletionException(exception);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }).orTimeout(12, TimeUnit.SECONDS);
    }

    private static void collect(CompletableFuture<List<Candidate>> search, String source,
                                List<Candidate> candidates, List<String> errors)
            throws InterruptedException {
        try {
            List<Candidate> found = search.get();
            if (found.isEmpty() && THE_MEAL_DB.equals(source)) {
                errors.add("TheMealDB produced no matching recipes.");
            }
            candidates.addAll(found);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            String message = cause.getMessage();
            String reason = message != null && message.matches(
                    "(Spoonacular|TheMealDB) returned HTTP \\d{3}")
                    ? message : cause.getClass().getSimpleName();

            errors.add(source + " search failed (" + reason + ").");
        }
    }

    private List<Candidate> fromSpoonacular(List<String> terms, Cuisine cuisine)
            throws IOException, InterruptedException {
        List<Candidate> candidates = new ArrayList<>();
        for (JsonElement element : spoonacular.findRecipes(terms, cuisine.spoonacularCuisine)) {
            JsonObject row = element.getAsJsonObject();
            List<String> missing = new ArrayList<>();
            for (JsonElement entry : row.getAsJsonArray("missedIngredients")) {
                missing.add(entry.getAsJsonObject().get("name").getAsString());
            }
            int used = row.get("usedIngredientCount").getAsInt();
            if (used > 0) {
                candidates.add(new Candidate(SPOONACULAR, row.get("title").getAsString(),
                        row.get("id").getAsInt(), used, missing, List.of()));
            }
        }
        return candidates;
    }

    private List<Candidate> fromTheMealDb(
            Map<String, FoodItem> available, List<String> terms, Cuisine cuisine)
            throws IOException, InterruptedException {
        List<Candidate> candidates = new ArrayList<>();
        for (JsonObject meal : new TheMealDbClient(mealDbKey).findRecipes(terms, cuisine.mealDbAreas)) {
            List<MeasuredIngredient> ingredients = new ArrayList<>();
            Set<String> missing = new LinkedHashSet<>();
            Set<String> used = new LinkedHashSet<>();
            for (int index = 1; index <= 20; index++) {
                String name = text(meal, "strIngredient" + index);
                if (name.isEmpty()) {
                    continue;
                }
                String key = matchingIngredientKey(name, available);
                FoodItem food = available.get(key);
                if (food == null) {
                    missing.add(name);
                } else {
                    used.add(key);
                }
                ingredients.add(new MeasuredIngredient(name, text(meal, "strMeasure" + index), food));
            }
            if (!used.isEmpty()) {
                candidates.add(new Candidate(THE_MEAL_DB, text(meal, "strMeal"),
                        0, used.size(), new ArrayList<>(missing), ingredients));
            }
        }
        return candidates;
    }

    private Result scaleSpoonacular(Candidate candidate, double target)
            throws IOException, InterruptedException {
        JsonObject recipe = spoonacular.getRecipe(candidate.id);
        JsonObject nutrition = recipe.getAsJsonObject("nutrition");
        Double caloriesPerServing = nutrition == null
                ? null : readCalories(nutrition.getAsJsonArray("nutrients"));
        int servings = recipe.get("servings").getAsInt();

        if (caloriesPerServing == null || caloriesPerServing <= 0 || servings <= 0) {
            throw new IOException("This recipe has no usable calorie information.");
        }

        double servingFactor = target / caloriesPerServing;
        double recipeFactor = servingFactor / servings;
        Map<Integer, Double> caloriesById = ingredientCaloriesPerServing(nutrition);

        JsonArray ingredients = recipe.getAsJsonArray("extendedIngredients");
        if (ingredients == null || ingredients.isEmpty()) {
            throw new IOException("This recipe has no usable ingredient list.");
        }

        List<Double> weights = new ArrayList<>();
        Map<Integer, Double> totalWeightById = new LinkedHashMap<>();

        for (JsonElement entry : ingredients) {
            JsonObject ingredient = entry.getAsJsonObject();
            String name = text(ingredient, "name");
            JsonObject metric = ingredient.getAsJsonObject("measures").getAsJsonObject("metric");
            String unit = text(metric, "unitLong");
            if (unit.isEmpty()) {
                unit = text(metric, "unitShort");
            }

            double grams = spoonacular.toGrams(name, metric.get("amount").getAsDouble(), unit)
                    * recipeFactor;
            if (!Double.isFinite(grams) || grams <= 0) {
                throw new IOException("Could not calculate a weight for " + name + ".");
            }

            weights.add(grams);
            int id = ingredient.get("id").getAsInt();
            totalWeightById.merge(id, grams, Double::sum);
        }

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            JsonObject ingredient = ingredients.get(i).getAsJsonObject();
            int id = ingredient.get("id").getAsInt();
            double grams = weights.get(i);
            Double ingredientCalories = caloriesById.get(id);
            Double scaledCalories = ingredientCalories == null ? null
                    : ingredientCalories * servingFactor * (grams / totalWeightById.get(id));

            rows.add(new Row(text(ingredient, "name"), grams, scaledCalories, ""));
        }

        return new Result(candidate, rows, target);
    }

    private static Map<Integer, Double> ingredientCaloriesPerServing(JsonObject nutrition) {
        Map<Integer, Double> caloriesById = new LinkedHashMap<>();
        JsonArray ingredients = nutrition.getAsJsonArray("ingredients");
        if (ingredients == null) {
            return caloriesById;
        }

        for (JsonElement entry : ingredients) {
            JsonObject ingredient = entry.getAsJsonObject();
            JsonElement id = ingredient.get("id");
            Double calories = readCalories(ingredient.getAsJsonArray("nutrients"));
            if (id != null && id.isJsonPrimitive()
                    && id.getAsJsonPrimitive().isNumber()
                    && id.getAsInt() > 0 && calories != null) {
                caloriesById.merge(id.getAsInt(), calories, Double::sum);
            }
        }
        return caloriesById;
    }

    private static Double readCalories(JsonArray nutrients) {
        if (nutrients == null) {
            return null;
        }

        for (JsonElement entry : nutrients) {
            JsonObject nutrient = entry.getAsJsonObject();
            JsonElement amount = nutrient.get("amount");
            if ("Calories".equalsIgnoreCase(text(nutrient, "name"))
                    && amount != null && amount.isJsonPrimitive()
                    && amount.getAsJsonPrimitive().isNumber()) {
                double calories = amount.getAsDouble();
                return Double.isFinite(calories) && calories >= 0 ? calories : null;
            }
        }
        return null;
    }

    private Result scaleTheMealDb(Candidate candidate, double target)
            throws IOException, InterruptedException {
        List<Double> weights = new ArrayList<>();
        List<String> lookupLines = new ArrayList<>();
        List<String> lineByIngredient = new ArrayList<>();

        for (MeasuredIngredient ingredient : candidate.ingredients) {
            double grams = mealDbGrams(ingredient);
            if (!Double.isFinite(grams) || grams <= 0) {
                return new Result(candidate, List.of(), null);
            }
            weights.add(grams);

            String line = null;
            if (ingredient.food == null) {
                line = java.math.BigDecimal.valueOf(grams).toPlainString()
                        + " g " + ingredient.name;
                lookupLines.add(line);
            }
            lineByIngredient.add(line);
        }

        Map<String, Double> fetchedCalories = new LinkedHashMap<>();
        if (!lookupLines.isEmpty()) {
            if (spoonacular == null) {
                return new Result(candidate, List.of(), null);
            }
            fetchedCalories = fetchIngredientCalories(lookupLines);
        }

        List<Double> originalCalories = new ArrayList<>();
        double originalTotal = 0;
        for (int i = 0; i < candidate.ingredients.size(); i++) {
            MeasuredIngredient ingredient = candidate.ingredients.get(i);
            Double kcal;
            if (ingredient.food == null) {
                kcal = fetchedCalories.get(lineByIngredient.get(i));
            } else {
                kcal = ingredient.food.getNutritionPer100g().getCalories()
                        * (weights.get(i) / 100.0);
            }

            if (kcal == null || !Double.isFinite(kcal) || kcal < 0) {
                return new Result(candidate, List.of(), null);
            }
            originalCalories.add(kcal);
            originalTotal += kcal;
        }

        if (!Double.isFinite(originalTotal) || originalTotal <= 0) {
            return new Result(candidate, List.of(), null);
        }

        double factor = target / originalTotal;
        List<Row> rows = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < candidate.ingredients.size(); i++) {
            double grams = weights.get(i) * factor;
            double kcal = originalCalories.get(i) * factor;
            if (!Double.isFinite(grams) || grams <= 0 || !Double.isFinite(kcal)) {
                return new Result(candidate, List.of(), null);
            }

            rows.add(new Row(candidate.ingredients.get(i).name, grams, kcal, ""));
            total += kcal;
        }
        return new Result(candidate, rows, total);
    }

    private Map<String, Double> fetchIngredientCalories(List<String> lines)
            throws IOException, InterruptedException {
        Map<String, Double> caloriesByLine = new LinkedHashMap<>();
        for (JsonElement entry : spoonacular.parseIngredientNutrition(lines)) {
            JsonObject ingredient = entry.getAsJsonObject();
            JsonObject nutrition = ingredient.getAsJsonObject("nutrition");
            if (nutrition != null) {
                Double kcal = readCalories(nutrition.getAsJsonArray("nutrients"));
                if (kcal != null) {
                    caloriesByLine.put(text(ingredient, "original"), kcal);
                }
            }
        }
        return caloriesByLine;
    }

    private static final Pattern MEAL_DB_MEASURE = Pattern.compile(
            "^(?:(?:(\\d+)\\s+)?(\\d+)\\s*/\\s*(\\d+)|(\\d+(?:\\.\\d+)?))"
                    + "\\s*([a-z]+(?:\\s+[a-z]+)*)?$",
            Pattern.CASE_INSENSITIVE);

    private double mealDbGrams(MeasuredIngredient ingredient) throws InterruptedException {
        Matcher match = MEAL_DB_MEASURE.matcher(normalizeMeasure(ingredient.measure));
        if (!match.matches()) {
            return Double.NaN;
        }

        double amount;
        if (match.group(4) != null) {
            amount = Double.parseDouble(match.group(4));
        } else {
            double denominator = Double.parseDouble(match.group(3));
            if (denominator == 0) {
                return Double.NaN;
            }

            double whole = match.group(1) == null ? 0 : Double.parseDouble(match.group(1));
            amount = whole + Double.parseDouble(match.group(2)) / denominator;
        }

        if (!Double.isFinite(amount) || amount <= 0) {
            return Double.NaN;
        }

        String unit = match.group(5) == null
                ? "piece" : match.group(5).toLowerCase(Locale.ROOT);
        double grams = massGrams(amount + " " + unit);
        if (Double.isFinite(grams)) {
            return grams;
        }

        if (unit.equals("piece")) {
            Double gramsPerPiece = Map.of(
                    "avocado", 136.0,
                    "cucumber", 301.0
            ).get(ingredientKey(ingredient.name));

            if (gramsPerPiece != null) {
                return amount * gramsPerPiece;
            }
        }

        if (unit.equals("tablespoon") || unit.equals("tablespoons") || unit.equals("tbsp")) {
            Double gramsPerSpoon = Map.of(
                    "olive oil", 13.5,
                    "soy sauce", 16.0,
                    "sake", 14.55,
                    "sesame seed", 9.0,
                    "sesame seeds", 9.0
            ).get(ingredientKey(ingredient.name));

            if (gramsPerSpoon != null) {
                return amount * gramsPerSpoon;
            }
        }

        if (spoonacular == null) {
            return Double.NaN;
        }

        try {
            return spoonacular.toGrams(ingredient.name, amount, unit);
        } catch (IOException exception) {
            System.err.println("Weight conversion failed for " + ingredient.name
                    + ": " + exception.getMessage());
            return Double.NaN;
        }
    }

    private static String normalizeMeasure(String measure) {
        return measure
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replaceAll("(\\d)(?=[¼½¾⅓⅔⅛⅜⅝⅞])", "$1 ")
                .replace("¼", "1/4")
                .replace("½", "1/2")
                .replace("¾", "3/4")
                .replace("⅓", "1/3")
                .replace("⅔", "2/3")
                .replace("⅛", "1/8")
                .replace("⅜", "3/8")
                .replace("⅝", "5/8")
                .replace("⅞", "7/8")
                .replace('\u2044', '/')
                .strip();
    }

    private static double massGrams(String measure) {
        Matcher match = MASS.matcher(measure);
        if (!match.matches()) {
            return Double.NaN;
        }
        double amount = Double.parseDouble(match.group(1));
        String unit = match.group(2).toLowerCase(Locale.ROOT);
        if (unit.startsWith("k")) {
            return amount * 1000;
        }
        if (unit.equals("oz") || unit.startsWith("ounce")) {
            return amount * 28.349523125;
        }
        if (unit.startsWith("lb") || unit.startsWith("pound")) {
            return amount * 453.59237;
        }
        return amount;
    }

    private static final Map<String, String> INGREDIENT_ALIASES = Map.ofEntries(
            Map.entry("eggs", "egg"),
            Map.entry("large egg", "egg"),
            Map.entry("large eggs", "egg"),
            Map.entry("whole egg", "egg"),
            Map.entry("egg whole raw fresh", "egg"),

            Map.entry("tomatoes", "tomato"),
            Map.entry("tomato raw", "tomato"),
            Map.entry("tomatoes raw", "tomato"),
            Map.entry("roma tomato", "tomato"),
            Map.entry("roma tomatoes", "tomato"),
            Map.entry("plum tomato", "tomato"),
            Map.entry("plum tomatoes", "tomato"),

            Map.entry("chicken breasts", "chicken breast"),
            Map.entry("chicken breast raw", "chicken breast"),
            Map.entry("chicken breasts raw", "chicken breast"),
            Map.entry("chicken broilers or fryers breast meat only raw", "chicken breast")
    );

    private static String ingredientKey(String value) {
        String name = value.toLowerCase(Locale.ROOT)
                .replaceAll("[,_-]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (name.matches("fish salmon(?: [a-z]+)* raw")) {
            return "salmon";
        }
        if (name.matches("eggs?(?: [a-z0-9]+)* egg whole(?: raw(?: fresh)?)?")) {
            return "egg";
        }
        return INGREDIENT_ALIASES.getOrDefault(name, name);
    }

    private static String selectedIngredientKey(String foodName) {
        String key = ingredientKey(foodName);

        if (foodName.contains(",")) {
            String firstPart = foodName.substring(0, foodName.indexOf(',')).strip();
            if (key.equals(foodName.toLowerCase(Locale.ROOT)
                    .replaceAll("[,_-]+", " ")
                    .replaceAll("\\s+", " ")
                    .strip())) {
                return ingredientKey(firstPart);
            }
        }
        return key;
    }

    private static String matchingIngredientKey(
            String recipeIngredient, Map<String, FoodItem> available) {
        String recipeKey = ingredientKey(recipeIngredient);
        if (available.containsKey(recipeKey)) {
            return recipeKey;
        }

        String ingredientWords = " " + recipeKey + " ";
        for (String selectedKey : available.keySet()) {
            if (ingredientWords.contains(" " + selectedKey + " ")) {
                return selectedKey;
            }
        }
        return recipeKey;
    }

    private static String text(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? "" : value.getAsString().strip();
    }

    private static final class Candidate {
        final String source;
        final String title;
        final int id;
        final int used;
        final List<String> missing;
        final List<MeasuredIngredient> ingredients;

        Candidate(String source, String title, int id, int used, List<String> missing,
                  List<MeasuredIngredient> ingredients) {
            this.source = source;
            this.title = title;
            this.id = id;
            this.used = used;
            this.missing = missing;
            this.ingredients = ingredients;
        }
    }

    private static final class MeasuredIngredient {
        final String name;
        final String measure;
        final FoodItem food;

        MeasuredIngredient(String name, String measure, FoodItem food) {
            this.name = name;
            this.measure = measure;
            this.food = food;
        }
    }

    public static final class Result {
        public final String source;
        public final String title;
        public final boolean missingIngredients;
        public final List<Row> rows;
        public final Double calories;

        public final boolean mainIngredientsOnly;

        private Result(Candidate candidate, List<Row> rows, Double calories) {
            this(candidate, rows, calories, false);
        }

        private Result(Candidate candidate, List<Row> rows, Double calories,
                       boolean mainIngredientsOnly) {
            this.source = candidate.source;
            this.title = candidate.title;
            this.missingIngredients = false;
            this.rows = List.copyOf(rows);
            this.calories = calories;
            this.mainIngredientsOnly = mainIngredientsOnly;
        }
    }

    public enum Cuisine {
        ANY("All cuisines", ""),
        CHINESE("Chinese", "Chinese", "Chinese"),
        JAPANESE("Japanese", "Japanese", "Japanese"),
        WESTERN("Western", "American,British,French,Italian,Spanish",
                "American", "British", "French", "Italian", "Spanish"),
        THAI("Thai", "Thai", "Thai");

        private final String label;
        private final String spoonacularCuisine;
        private final List<String> mealDbAreas;

        Cuisine(String label, String spoonacularCuisine, String... mealDbAreas) {
            this.label = label;
            this.spoonacularCuisine = spoonacularCuisine;
            this.mealDbAreas = List.of(mealDbAreas);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static final class Row {
        public final String ingredient;
        public final Double grams;
        public final Double calories;
        public final String originalMeasure;

        private Row(String ingredient, Double grams, Double calories, String originalMeasure) {
            this.ingredient = ingredient;
            this.grams = grams;
            this.calories = calories;
            this.originalMeasure = originalMeasure;
        }
    }
}
