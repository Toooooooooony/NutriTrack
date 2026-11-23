package use_case.daily_summary;

import java.util.Objects;

/**
 * Coordinates selected-date summary retrieval through injected boundaries.
 * Only dependency wiring is complete; execution remains an explicit stub.
 * Historical dates use the same request structure. Listing historical dates or
 * records is deferred until the team specifies that operation.
 */
public final class DailySummaryInteractor implements DailySummaryInputBoundary {

    private final DailySummaryDataAccessInterface dataAccess;
    private final DailySummaryOutputBoundary outputBoundary;

    /**
     * Supplies the non-null gateway and output boundary for future execution.
     *
     * @param dataAccess read-only recorded-intake gateway
     * @param outputBoundary recipient of summary outcomes
     * @throws NullPointerException if either dependency is null
     */
    public DailySummaryInteractor(DailySummaryDataAccessInterface dataAccess,
                                  DailySummaryOutputBoundary outputBoundary) {
        this.dataAccess = Objects.requireNonNull(dataAccess, "Data access cannot be null.");
        this.outputBoundary = Objects.requireNonNull(outputBoundary, "Output boundary cannot be null.");
    }

    /**
     * Marks execution as unavailable during skeleton development.
     *
     * @param inputData selected-date request
     * @throws UnsupportedOperationException until Phase 2 implementation
     */
    @Override
    public void execute(DailySummaryInputData inputData) {
        // TODO: ERIC [Requirements] Agree outcomes for an absent log versus an
        // existing empty log; do not silently substitute a zero-valued summary.
        // TODO: ERIC [Implementation] Apply the agreed request-validation policy
        // before reading inputData.getDate() or calling the gateway.
        // TODO: ERIC [Implementation] Retrieve the selected day's log through
        // dataAccess.findByDate(inputData.getDate()) without creating a log.
        // TODO: ERIC [Implementation] Handle an absent result using the agreed
        // missing-log outcome before accessing the returned entity.
        // TODO: ERIC [Implementation] Obtain totals from DailyFoodLog.getTotalNutrition()
        // and construct DailySummaryOutputData using that log's date and totals.
        // TODO: ERIC [Implementation] Deliver a successful result through
        // outputBoundary.presentSummary once retrieval and aggregation succeed.
        // TODO: ERIC [Implementation] Translate agreed retrieval failures through
        // outputBoundary.presentError without masking programming errors.
        throw new UnsupportedOperationException(
                "Daily nutrition summary execution is not implemented; this is the Phase 1 skeleton.");
    }
}
