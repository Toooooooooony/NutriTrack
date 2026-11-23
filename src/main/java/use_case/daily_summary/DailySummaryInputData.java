package use_case.daily_summary;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable request carrying a selected date without GUI parsing or formatting.
 * Construction enforces only that the date is non-null.
 */
public final class DailySummaryInputData {

    // TODO: ERIC [Requirements] Confirm whether selectable dates have any limits;
    // keep this carrier free of additional date rules until those limits are agreed.
    private final LocalDate date;

    /**
     * Creates a request containing a non-null date.
     *
     * @param date selected calendar date
     * @throws NullPointerException if date is null
     */
    public DailySummaryInputData(LocalDate date) {
        this.date = Objects.requireNonNull(date, "Selected date cannot be null.");
    }

    /**
     * Returns the selected calendar date.
     *
     * @return selected date
     */
    public LocalDate getDate() {
        return date;
    }
}
