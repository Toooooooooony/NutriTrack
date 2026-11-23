package use_case.daily_summary;

/**
 * Accepts requests for recorded nutrition on a selected date, including past dates.
 * Controllers depend on this boundary rather than a concrete interactor.
 */
public interface DailySummaryInputBoundary {

    // TODO: ERIC [Requirements] Decide how execute should reject a null request object.

    /**
     * Requests a summary for the date carried by the input data.
     * The date selects recorded intake, not planned meals; this operation does
     * not list available historical dates or records.
     *
     * @param inputData selected-date request
     */
    void execute(DailySummaryInputData inputData);
}
