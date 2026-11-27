package data_access;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TheMealDbClientTest {
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void searchesSixIngredientsAndLooksUpOnlyThreeRecipes() throws Exception {
        TheMealDbClient client = startServer(request -> {
            if (request.startsWith("/1/filter.php?")) {
                return "{\"meals\":[{\"idMeal\":\"101\"},{\"idMeal\":\"102\"},"
                        + "{\"idMeal\":\"103\"},{\"idMeal\":\"104\"}]}";
            }

            String id = request.substring(request.indexOf('=') + 1);
            return "{\"meals\":[{\"idMeal\":\"" + id + "\"}]}";
        });

        var recipes = client.findRecipes(
                List.of("egg", "milk", "flour", "butter", "salt", "chicken breast", "rice"));

        assertEquals(3, recipes.size());
        assertEquals(List.of(
                "/1/filter.php?i=egg",
                "/1/filter.php?i=milk",
                "/1/filter.php?i=flour",
                "/1/filter.php?i=butter",
                "/1/filter.php?i=salt",
                "/1/filter.php?i=chicken_breast",
                "/1/lookup.php?i=101",
                "/1/lookup.php?i=102",
                "/1/lookup.php?i=103"
        ), requests);
    }

    @Test
    void searchesByNameWhenIngredientFilterReturnsNoMeals() throws Exception {
        TheMealDbClient client = startServer(request ->
                request.equals("/1/search.php?s=omelette")
                        ? "{\"meals\":[{\"idMeal\":\"201\",\"strMeal\":\"Omelette\"}]}"
                        : "{\"meals\":null}");

        var recipes = client.findRecipes(List.of("omelette"));

        assertEquals(1, recipes.size());
        assertEquals("Omelette", recipes.get(0).get("strMeal").getAsString());
        assertEquals(List.of(
                "/1/filter.php?i=omelette",
                "/1/search.php?s=omelette"
        ), requests);
    }

    private TheMealDbClient startServer(Function<String, String> responseFor)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String request = exchange.getRequestURI().toString();
            requests.add(request);
            byte[] body = responseFor.apply(request).getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        URI apiRoot = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        return new TheMealDbClient(null, apiRoot);
    }
}
