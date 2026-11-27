package use_case.import_food;

public interface SearchExternalFoodOutputBoundary {
    void presentResults(SearchExternalFoodOutputData outputData);

    void presentInvalidQuery();

    void presentSearchFailure();
}
