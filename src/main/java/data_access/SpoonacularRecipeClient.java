package data_access;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class SpoonacularRecipeClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String apiKey;

    public SpoonacularRecipeClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Set SPOONACULAR_API_KEY first.");
        }
        this.apiKey = apiKey.strip();
    }

    public JsonArray findRecipes(List<String> ingredientNames)
            throws IOException, InterruptedException {
        if (ingredientNames.isEmpty()) {
            throw new IllegalArgumentException("Select at least one ingredient.");
        }

        String ingredients = URLEncoder.encode(
                String.join(",", ingredientNames), StandardCharsets.UTF_8);
        return get("/recipes/findByIngredients?ingredients=" + ingredients
                + "&number=5&ranking=2&ignorePantry=false").getAsJsonArray();
    }
    public JsonArray findRecipes(List<String> ingredientNames, String cuisine)
            throws IOException, InterruptedException {
        if (cuisine.isEmpty()) {
            return findRecipes(ingredientNames);
        }

        String ingredients = URLEncoder.encode(
                String.join(",", ingredientNames), StandardCharsets.UTF_8);
        String encodedCuisine = URLEncoder.encode(cuisine, StandardCharsets.UTF_8);

        return get("/recipes/complexSearch?cuisine=" + encodedCuisine
                + "&includeIngredients=" + ingredients
                + "&fillIngredients=true&ignorePantry=false"
                + "&sort=min-missing-ingredients&number=5")
                .getAsJsonObject().getAsJsonArray("results");
    }

    public JsonObject getRecipe(int recipeId)
            throws IOException, InterruptedException {
        return get("/recipes/" + recipeId + "/information?includeNutrition=true")
                .getAsJsonObject();
    }

    public double toGrams(String name, double amount, String unit)
            throws IOException, InterruptedException {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IOException("Invalid recipe amount for " + name + ".");
        }

        String sourceUnit = unit == null || unit.isBlank() ? "piece" : unit.strip();
        if ("g".equalsIgnoreCase(sourceUnit)
                || "gram".equalsIgnoreCase(sourceUnit)
                || "grams".equalsIgnoreCase(sourceUnit)) {
            return amount;
        }

        JsonObject converted = get("/recipes/convert?ingredientName="
                + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&sourceAmount=" + amount
                + "&sourceUnit=" + URLEncoder.encode(sourceUnit, StandardCharsets.UTF_8)
                + "&targetUnit=grams").getAsJsonObject();

        JsonElement target = converted.get("targetAmount");
        if (target == null || !target.isJsonPrimitive()
                || !target.getAsJsonPrimitive().isNumber()) {
            throw new IOException("Spoonacular returned no weight for " + name + ".");
        }

        double grams = target.getAsDouble();
        if (!Double.isFinite(grams) || grams <= 0) {
            throw new IOException("Spoonacular returned an invalid weight for " + name + ".");
        }
        return grams;
    }

    public JsonArray parseIngredientNutrition(List<String> ingredientLines)
            throws IOException, InterruptedException {
        String body = "ingredientList="
                + URLEncoder.encode(String.join("\n", ingredientLines), StandardCharsets.UTF_8)
                + "&servings=1&includeNutrition=true";

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://api.spoonacular.com/recipes/parseIngredients"))
                .header("x-api-key", apiKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Spoonacular returned HTTP " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonArray();
    }

    private JsonElement get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://api.spoonacular.com" + path))
                .header("x-api-key", apiKey)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Spoonacular returned HTTP " + response.statusCode());
        }

        return JsonParser.parseString(response.body());
    }
}