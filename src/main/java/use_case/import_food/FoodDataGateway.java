package use_case.import_food;

import entity.FoodItem;

import java.io.IOException;
import java.util.Map;

public interface FoodDataGateway {
    Map<Integer, String> search(String query)
            throws IOException, InterruptedException;

    FoodItem getFood(int foodId)
            throws IOException, InterruptedException;
}