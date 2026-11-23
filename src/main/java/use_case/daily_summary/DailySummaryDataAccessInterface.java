package use_case.daily_summary;

import entity.DailyFoodLog;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Provides read-only access to a selected day's recorded intake.
 * Storage format and concrete persistence remain outside this use case.
 */
public interface DailySummaryDataAccessInterface {

    // TODO: ERIC [Coordination] Agree findByDate and storage-failure reporting with
    // the persistence owner; distinguish retrieval failure from an absent log.
    // TODO: ERIC [Coordination] Decide whether adapters return detached logs or
    // shared mutable instances, and document caller ownership before integration.

    /**
     * Looks up a daily log without creating or changing recorded intake.
     * An absent log is distinct from an existing log containing no meals.
     * This lookup does not decide how either case is presented to the user.
     * The returned entity can be mutable; read-only describes this operation,
     * not an immutable return type. Object ownership remains to be agreed.
     *
     * @param date calendar date to retrieve
     * @return the stored log, or an empty Optional when no log exists
     */
    Optional<DailyFoodLog> findByDate(LocalDate date);
}
