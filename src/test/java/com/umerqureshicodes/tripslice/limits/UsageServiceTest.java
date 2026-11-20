package com.umerqureshicodes.tripslice.limits;

import com.umerqureshicodes.tripslice.common.ApiException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsageServiceTest {

    // An in-memory stand-in for the upsert: counts per (user, kind)
    private final Map<String, Integer> counts = new HashMap<>();
    private final DailyUsageRepo repo = mock(DailyUsageRepo.class);

    UsageServiceTest() {
        when(repo.add(anyLong(), any(LocalDate.class), anyString(), anyInt())).thenAnswer(inv ->
                counts.merge(inv.getArgument(0) + "|" + inv.getArgument(2), inv.getArgument(3), Integer::sum));
    }

    // Per user: 5 searches a day. Everyone combined: 8
    private final UsageService service = new UsageService(repo, 30, 5, 10, 300, 8, 100);

    @Test
    void manyAccountsCantGetPastTheGlobalCeiling() {
        for (long user = 1; user <= 4; user++) {
            service.consume(user, UsageService.Kind.SEARCH, 2);
        }
        assertThat(counts.get(UsageService.EVERYONE + "|SEARCH")).isEqualTo(8);
        // A fresh account is still under its own limit, but everyone together is at the ceiling
        assertThatThrownBy(() -> service.consume(99L, UsageService.Kind.SEARCH, 1))
                .isInstanceOf(ApiException.class)
                .hasMessage("TripSlice has reached its daily limit for this. Please try again tomorrow.");
    }

    @Test
    void oneAccountStillHitsItsOwnLimitFirst() {
        service.consume(1L, UsageService.Kind.SEARCH, 5);
        assertThatThrownBy(() -> service.consume(1L, UsageService.Kind.SEARCH, 1))
                .isInstanceOf(ApiException.class)
                .hasMessage("You've reached today's search limit. Please try again tomorrow.");
    }

    @Test
    void everyKindHasBothLimits() {
        UsageService tiny = new UsageService(repo, 1, 1, 1, 1, 1, 1);
        tiny.consume(1L, UsageService.Kind.ANALYZE, 1);
        tiny.consume(1L, UsageService.Kind.MONTAGE, 1);
        assertThatThrownBy(() -> tiny.consume(2L, UsageService.Kind.ANALYZE, 1)).hasMessageContaining("TripSlice has reached");
        assertThatThrownBy(() -> tiny.consume(2L, UsageService.Kind.MONTAGE, 1)).hasMessageContaining("TripSlice has reached");
    }
}
