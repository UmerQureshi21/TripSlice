package com.umerqureshicodes.tripslice.limits;

import com.umerqureshicodes.tripslice.common.ApiException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Per user daily budgets for anything that costs TwelveLabs credits. The global minutes cap in
 * LimitService only covers indexing; analyze and embed calls are billed separately and would
 * otherwise be unlimited.
 */
@Service
public class UsageService {

    public enum Kind { ANALYZE, SEARCH, MONTAGE }

    // Usage across every account is counted under this id (no real user has id 0), so creating
    // many accounts can't get around the limits
    static final long EVERYONE = 0L;

    private final DailyUsageRepo repo;
    private final int maxAnalyze;
    private final int maxSearches;
    private final int maxMontages;
    private final int globalAnalyze;
    private final int globalSearches;
    private final int globalMontages;

    public UsageService(DailyUsageRepo repo,
                        @Value("${limits.daily.analyze-calls:30}") int maxAnalyze,
                        @Value("${limits.daily.searches:100}") int maxSearches,
                        @Value("${limits.daily.montages:10}") int maxMontages,
                        @Value("${limits.global-daily.analyze-calls:300}") int globalAnalyze,
                        @Value("${limits.global-daily.searches:2000}") int globalSearches,
                        @Value("${limits.global-daily.montages:100}") int globalMontages) {
        this.repo = repo;
        this.maxAnalyze = maxAnalyze;
        this.maxSearches = maxSearches;
        this.maxMontages = maxMontages;
        this.globalAnalyze = globalAnalyze;
        this.globalSearches = globalSearches;
        this.globalMontages = globalMontages;
    }

    /**
     * Uses up {@code amount} of today's budget for this user and for everyone combined, or throws
     * 429 without using any of either. The exception rolls both increments back, so refused
     * requests don't count.
     */
    @Transactional
    public void consume(Long userId, Kind kind, int amount) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (repo.add(EVERYONE, today, kind.name(), amount) > globalLimitFor(kind)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "TripSlice has reached its daily limit for this. Please try again tomorrow.");
        }
        if (repo.add(userId, today, kind.name(), amount) > limitFor(kind)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, messageFor(kind));
        }
    }

    private int limitFor(Kind kind) {
        return switch (kind) {
            case ANALYZE -> maxAnalyze;
            case SEARCH -> maxSearches;
            case MONTAGE -> maxMontages;
        };
    }

    private int globalLimitFor(Kind kind) {
        return switch (kind) {
            case ANALYZE -> globalAnalyze;
            case SEARCH -> globalSearches;
            case MONTAGE -> globalMontages;
        };
    }

    private String messageFor(Kind kind) {
        return switch (kind) {
            case ANALYZE -> "You've used today's video analysis allowance. Please try again tomorrow.";
            case SEARCH -> "You've reached today's search limit. Please try again tomorrow.";
            case MONTAGE -> "You've reached today's montage limit. Please try again tomorrow.";
        };
    }
}
