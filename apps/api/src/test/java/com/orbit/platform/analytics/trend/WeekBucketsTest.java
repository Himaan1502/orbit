package com.orbit.platform.analytics.trend;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure ISO-week bucketing (ADR-027 §2). */
class WeekBucketsTest {

    @Test
    void mondayIsTheWeekStart() {
        // 2026-09-09 is a Wednesday
        assertThat(WeekBuckets.monday(LocalDate.of(2026, 9, 9))).isEqualTo(LocalDate.of(2026, 9, 7));
        // a Monday maps to itself
        assertThat(WeekBuckets.monday(LocalDate.of(2026, 9, 7))).isEqualTo(LocalDate.of(2026, 9, 7));
    }

    @Test
    void weekStartsAreDenseAndInclusive() {
        var weeks = WeekBuckets.weekStarts(LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 30));
        assertThat(weeks).containsExactly(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28));
    }

    @Test
    void addLandsInTheContainingWeekAndIgnoresOutOfRange() {
        Map<LocalDate, int[]> acc = WeekBuckets.accumulator(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 14));
        WeekBuckets.add(acc, LocalDate.of(2026, 9, 3), 2);   // week of Aug 31
        WeekBuckets.add(acc, LocalDate.of(2026, 9, 10), 5);  // week of Sep 7
        WeekBuckets.add(acc, LocalDate.of(2026, 12, 1), 9);  // out of range — dropped

        assertThat(acc.get(LocalDate.of(2026, 8, 31))[0]).isEqualTo(2);
        assertThat(acc.get(LocalDate.of(2026, 9, 7))[0]).isEqualTo(5);
        assertThat(acc.values().stream().mapToInt(c -> c[0]).sum()).isEqualTo(7);
    }
}
