package use_case.import_food;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SearchExternalFoodOutputData {
    private final Map<Integer, String> matches;

    public SearchExternalFoodOutputData(Map<Integer, String> matches) {
        this.matches = Collections.unmodifiableMap(new LinkedHashMap<>(matches));
    }

    public Map<Integer, String> getMatches() {
        return matches;
    }
}