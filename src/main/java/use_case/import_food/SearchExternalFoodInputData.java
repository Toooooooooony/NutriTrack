package use_case.import_food;

public final class SearchExternalFoodInputData {
    private final String query;

    public SearchExternalFoodInputData(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }
}
