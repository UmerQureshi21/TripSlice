package com.umerqureshicodes.tripslice.limits;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LimitService {

    private final LimitRepo limitRepo;
    private final long maxMillis = 360L * 60L * 1000L; // 360 minutes (6 hours) of video across all users
    // TwelveLabs' free plan minutes are cumulative and don't come back when videos are deleted,
    // so this total is never decreased

    // I AM TESTING RUG

    public LimitService(LimitRepo limitRepo) {
        this.limitRepo = limitRepo;
    }

    @Transactional
    public boolean exceededLimit(long uploadedVideosDuration) {
        Limit limit = limitRepo.getSingletonForUpdate()
                .orElseGet(() -> limitRepo.save(new Limit(0)));

        long newTotal = limit.getTotalVideoMillis()+ uploadedVideosDuration;

        if (newTotal >= maxMillis) { // use >= if you want to block exactly-at-limit too
            return true;
        }

        limit.setTotalVideoMillis(newTotal);
        return false;
    }

    /**
     * Gives back minutes reserved for a video that TwelveLabs never indexed (indexing was refused
     * or failed), since those minutes weren't used.
     */
    @Transactional
    public void release(long videoDuration) {
        limitRepo.getSingletonForUpdate().ifPresent(limit ->
                limit.setTotalVideoMillis(Math.max(0, limit.getTotalVideoMillis() - videoDuration)));
    }

}
