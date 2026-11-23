package use_case.daily_summary;

import entity.NutritionTotals;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Carries a summary date and existing domain nutrition values without formatting.
 * Fields cannot be reassigned, and no nutrition is calculated by this carrier.
 * NutritionTotals is retained by reference, using its existing immutable value API.
 */
public final class DailySummaryOutputData {

    // TODO: ERIC [Requirements] Confirm whether the summary must include recorded
    // meal details; obtain the required fields before extending this typed output.
    private final LocalDate date;
    private final NutritionTotals totalNutrition;

    /**
     * Creates an output carrier; does not calculate or substitute nutrition values.
     *
     * @param date non-null summary date
     * @param totalNutrition non-null recorded nutrition totals
     * @throws NullPointerException if date or totalNutrition is null
     */
    public DailySummaryOutputData(LocalDate date, NutritionTotals totalNutrition) {
        this.date = Objects.requireNonNull(date, "Summary date cannot be null.");
        this.totalNutrition = Objects.requireNonNull(
                totalNutrition, "Summary nutrition cannot be null.");
    }

    /**
     * Returns the summary date.
     *
     * @return calendar date
     */
    public LocalDate getDate() {
        return date;
    }

    /**
     * Returns the supplied calories and macronutrient values.
     * Calories are in kcal; protein, carbohydrates, and fat are in grams.
     *
     * @return recorded nutrition totals
     */
    public NutritionTotals getTotalNutrition() {
        return totalNutrition;
    }
}
