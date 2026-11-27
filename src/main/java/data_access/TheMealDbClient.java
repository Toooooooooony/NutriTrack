package data_access;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.Set;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TheMealDbClient {
    private static final int MAX_SEARCH_TERMS = 6;
    private static final int MAX_LOOKUPS = 3;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4)).build();
    private final String baseUrl;

    public TheMealDbClient(String apiKey) {
        this(apiKey, URI.create("https://www.themealdb.com/api/json/v1/"));
    }

    TheMealDbClient(String apiKey, URI apiRoot) {
        String key = apiKey == null || apiKey.isBlank() ? "1" : apiKey.strip();
        if (!key.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid TheMealDB API key format.");
        }
        baseUrl = apiRoot.resolve(key + "/").toString();
    }

    public List<JsonObject> findRecipes(List<String> terms)
            throws IOException, InterruptedException {
        return findRecipes(terms, List.of());
    }

    public List<JsonObject> findRecipes(List<String> terms, List<String> areas)
            throws IOException, InterruptedException {
        Set<String> allowedIds = new HashSet<>();
        for (String area : areas) {
            for (JsonElement entry : request("filter.php?a=", area)) {
                allowedIds.add(entry.getAsJsonObject().get("idMeal").getAsString());
            }
        }
        if (!areas.isEmpty() && allowedIds.isEmpty()) {
            return List.of();
        }
        Map<String, JsonObject> meals = new LinkedHashMap<>();
        Map<String, Integer> ingredientMatches = new LinkedHashMap<>();
        for (String term : terms.subList(0, Math.min(terms.size(), MAX_SEARCH_TERMS))) {
            JsonArray filtered = request("filter.php?i=", term.replace(' ', '_'));

            for (JsonElement entry : filtered) {
                String id = entry.getAsJsonObject().get("idMeal").getAsString();
                if (areas.isEmpty() || allowedIds.contains(id)) {
                    if (areas.isEmpty() || allowedIds.contains(id)) {
                        ingredientMatches.merge(id, 1, Integer::sum);
                    }
                }
            }

            if (filtered.isEmpty()) {
                JsonArray named = request("search.php?s=", term);
                for (JsonElement entry : named) {
                    JsonObject meal = entry.getAsJsonObject();
                    String id = meal.get("idMeal").getAsString();
                    if (areas.isEmpty() || allowedIds.contains(id)) {
                        meals.putIfAbsent(id, meal);
                    }
                }
            }
        }

        List<String> lookupIds = new ArrayList<>();
        ingredientMatches.entrySet().stream()
                .filter(entry -> !meals.containsKey(entry.getKey()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(MAX_LOOKUPS)
                .forEach(entry -> lookupIds.add(entry.getKey()));
        for (String id : lookupIds) {
            JsonArray details = request("lookup.php?i=", id);
            if (!details.isEmpty()) {
                meals.put(id, details.get(0).getAsJsonObject());
            }
        }
        return new ArrayList<>(meals.values());
    }

    private JsonArray request(String path, String value) throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + path + URLEncoder.encode(value, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("TheMealDB returned HTTP " + response.statusCode());
        }
        JsonElement meals = JsonParser.parseString(response.body()).getAsJsonObject().get("meals");
        return meals == null || meals.isJsonNull() ? new JsonArray() : meals.getAsJsonArray();
    }
}
