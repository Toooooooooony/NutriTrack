package data_access;
import use_case.import_food.FoodDataGateway;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import entity.FoodItem;
import entity.NutritionTotals;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UsdaFoodClient implements FoodDataGateway {
    private static final String BASE_URL = "https://api.nal.usda.gov/fdc/v1";
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String apiKey;

    public UsdaFoodClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Set USDA_API_KEY first.");
        }
        this.apiKey = apiKey;
    }

    public Map<Integer, String> search(String query)
            throws IOException, InterruptedException {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Enter a food name.");
        }
        JsonObject response = get("/foods/search?query=" + encode(query.strip())
                + "&dataType=Foundation,SR%20Legacy&pageSize=15");

        Map<Integer, String> foods = new LinkedHashMap<>();
        for (JsonElement element : response.getAsJsonArray("foods")) {
            JsonObject food = element.getAsJsonObject();
            foods.put(food.get("fdcId").getAsInt(), food.get("description").getAsString());
        }
        return foods;
    }

    public FoodItem getFood(int fdcId) throws IOException, InterruptedException {
        JsonObject food = get("/food/" + fdcId + "?format=full");
        Map<Integer, Double> nutrients = new HashMap<>();

        for (JsonElement element : food.getAsJsonArray("foodNutrients")) {
            JsonObject row = element.getAsJsonObject();
            JsonElement amount = row.get("amount");
            if (amount != null && !amount.isJsonNull()) {
                int id = row.getAsJsonObject("nutrient").get("id").getAsInt();
                nutrients.put(id, amount.getAsDouble());
            }
        }

        Double calories = nutrients.getOrDefault(1008,
                nutrients.getOrDefault(2048, nutrients.get(2047)));
        Double protein = nutrients.get(1003);
        Double carbs = nutrients.get(1005);
        Double fat = nutrients.get(1004);
        if (calories == null || protein == null || carbs == null || fat == null) {
            throw new IOException("USDA nutrition data is incomplete for food " + fdcId);
        }

        return new FoodItem("usda-" + fdcId, food.get("description").getAsString(),
                new NutritionTotals(calories, protein, carbs, fat));
    }

    private JsonObject get(String pathAndQuery) throws IOException, InterruptedException {
        URI uri = URI.create(BASE_URL + pathAndQuery + "&api_key=" + encode(apiKey));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("USDA request failed (HTTP " + response.statusCode() + ")");
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
