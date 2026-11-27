package use_case.import_food;

import java.io.IOException;
import java.util.Map;

public final class SearchExternalFoodInteractor
        implements SearchExternalFoodInputBoundary {

    private final FoodDataGateway gateway;
    private final SearchExternalFoodOutputBoundary presenter;

    public SearchExternalFoodInteractor(FoodDataGateway gateway,
                                        SearchExternalFoodOutputBoundary presenter) {
        this.gateway = gateway;
        this.presenter = presenter;
    }

    @Override
    public void execute(SearchExternalFoodInputData inputData) {
        String query = inputData.getQuery();
        if (query == null || query.isBlank()) {
            presenter.presentInvalidQuery();
            return;
        }

        Map<Integer, String> matches;
        try {
            matches = gateway.search(query.strip());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            presenter.presentSearchFailure();
            return;
        } catch (IOException exception) {
            presenter.presentSearchFailure();
            return;
        }

        presenter.presentResults(new SearchExternalFoodOutputData(matches));
    }
}
