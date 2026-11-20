package com.umerqureshicodes.tripslice.limits;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface DailyUsageRepo extends JpaRepository<DailyUsage, DailyUsage.Key> {

    // One atomic statement, so two concurrent requests can't both read the old count
    @Query(value = """
            INSERT INTO daily_usage (user_id, day, kind, count) VALUES (:userId, :day, :kind, :amount)
            ON CONFLICT (user_id, day, kind) DO UPDATE SET count = daily_usage.count + :amount
            RETURNING count
            """, nativeQuery = true)
    int add(@Param("userId") Long userId, @Param("day") LocalDate day,
            @Param("kind") String kind, @Param("amount") int amount);
}
