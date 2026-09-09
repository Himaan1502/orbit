package com.orbit.platform.analytics.trend;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure ISO-week bucketing for analytics series (ADR-027 §2). Weeks run
 * Monday–Sunday. Produces a <em>dense</em> list of week-starts across the range
 * so a chart shows real gaps, and helps callers accumulate counts per week.
 */
public final class WeekBuckets {

    private WeekBuckets() {
    }

    /** Every Monday from the week containing {@code from} through the week containing {@code to}. */
    public static List<LocalDate> weekStarts(LocalDate from, LocalDate to) {
        LocalDate start = monday(from);
        LocalDate end = monday(to);
        List<LocalDate> weeks = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusWeeks(1)) {
            weeks.add(d);
        }
        return weeks;
    }

    public static LocalDate monday(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** A zero-initialised {@code weekStart → mutable int[1]} accumulator over the range. */
    public static Map<LocalDate, int[]> accumulator(LocalDate from, LocalDate to) {
        Map<LocalDate, int[]> acc = new LinkedHashMap<>();
        for (LocalDate w : weekStarts(from, to)) {
            acc.put(w, new int[] {0});
        }
        return acc;
    }

    /** Add {@code amount} to the bucket for the week containing {@code date}, if in range. */
    public static void add(Map<LocalDate, int[]> acc, LocalDate date, int amount) {
        int[] cell = acc.get(monday(date));
        if (cell != null) {
            cell[0] += amount;
        }
    }
}
