package use_case.daily_summary;

/**
 * Receives summary outcomes without depending on a GUI or concrete presenter.
 * A future presenter implements this interface; no presentation is implemented here.
 */
public interface DailySummaryOutputBoundary {

    // TODO: ERIC [Coordination] Agree callback nullability and invocation/threading
    // expectations with the presenter owner before connecting a GUI.

    /**
     * Receives a successfully retrieved daily summary.
     *
     * @param outputData selected date and recorded nutrition totals
     */
    void presentSummary(DailySummaryOutputData outputData);

    /**
     * Receives a failure description for presentation.
     *
     * @param message description of the failure
     */
    void presentError(String message);

    // TODO: ERIC [Requirements] Agree the failures reported through presentError
    // and who supplies user-facing wording; do not expose raw storage exceptions.
}
