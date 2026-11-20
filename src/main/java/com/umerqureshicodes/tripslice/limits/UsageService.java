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

    private final DailyUsageRepo repo;
    private final int maxAnalyze;
    private final int maxSearches;
    private final int maxMontages;

    public UsageService(DailyUsageRepo repo,
                        @Value("${limits.daily.analyze-calls:30}") int maxAnalyze,
                        @Value("${limits.daily.searches:100}") int maxSearches,
                        @Value("${limits.daily.montages:10}") int maxMontages) {
        this.repo = repo;
        this.maxAnalyze = maxAnalyze;
        this.maxSearches = maxSearches;
        this.maxMontages = maxMontages;
    }

    /**
     * Uses up {@code amount} of today's budget, or throws 429 without using any of it.
     * The exception rolls the increment back, so refused requests don't count.
     */
    @Transactional
    public void consume(Long userId, Kind kind, int amount) {
        int total = repo.add(userId, LocalDate.now(ZoneOffset.UTC), kind.name(), amount);
        if (total > limitFor(kind)) {
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

    private String messageFor(Kind kind) {
        return switch (kind) {
            case ANALYZE -> "You've used today's video analysis allowance. Please try again tomorrow.";
            case SEARCH -> "You've reached today's search limit. Please try again tomorrow.";
            case MONTAGE -> "You've reached today's montage limit. Please try again tomorrow.";
        };
    }
}
