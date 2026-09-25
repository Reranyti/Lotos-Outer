package com.lotusblight.data;

/**
 * Custom in-world calendar, built purely from elapsed Minecraft days (world game time), not tied
 * to any real-world date. 12 months named after the real calendar for familiarity, but compressed:
 * February is 13 days (two weeks, 7 then 6), every other month is 14 days (two weeks, 7 then 7).
 * Weeks reset at the start of each month rather than running continuously - "13,7 и 6" / "14,7 и 7"
 * describes each month as exactly two week-chunks. A year is 11*14 + 13 = 167 days long.
 */
public final class LotusCalendar {
    private static final String[] MONTH_NAMES = {
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    };
    private static final int FEBRUARY_INDEX = 1;
    private static final int FEBRUARY_LENGTH = 13;
    private static final int ORDINARY_MONTH_LENGTH = 14;
    private static final int FIRST_WEEK_LENGTH = 7;

    public static final int MONTHS_PER_YEAR = MONTH_NAMES.length;
    public static final int YEAR_LENGTH_DAYS =
            (MONTHS_PER_YEAR - 1) * ORDINARY_MONTH_LENGTH + FEBRUARY_LENGTH;

    private LotusCalendar() {
    }

    private static int monthLength(int monthIndex) {
        return monthIndex == FEBRUARY_INDEX ? FEBRUARY_LENGTH : ORDINARY_MONTH_LENGTH;
    }

    /** 1-indexed day-of-month -> 1-indexed day-of-week, resetting at the start of each month (first chunk is always 7 days). */
    private static int dayOfWeekFor(int dayOfMonth) {
        return dayOfMonth <= FIRST_WEEK_LENGTH ? dayOfMonth : dayOfMonth - FIRST_WEEK_LENGTH;
    }

    /** 1-indexed week-of-month: 1 for the first (always 7-day) chunk, 2 for the remainder. */
    private static int weekOfMonthFor(int dayOfMonth) {
        return dayOfMonth <= FIRST_WEEK_LENGTH ? 1 : 2;
    }

    /**
     * @param elapsedGameDays total whole Minecraft days since world creation (gameTime / 24000).
     */
    public static Date dateFor(long elapsedGameDays) {
        long day = elapsedGameDays;
        int year = (int) (day / YEAR_LENGTH_DAYS) + 1;
        int dayOfYear = (int) (day % YEAR_LENGTH_DAYS); // 0-indexed within the year

        int month = 0;
        int remaining = dayOfYear;
        while (true) {
            int length = monthLength(month);
            if (remaining < length) break;
            remaining -= length;
            month++;
        }
        int dayOfMonth = remaining + 1; // 1-indexed

        return new Date(year, month, dayOfMonth, weekOfMonthFor(dayOfMonth), dayOfWeekFor(dayOfMonth));
    }

    public static String monthName(int monthIndex) {
        return MONTH_NAMES[monthIndex];
    }

    /** @param month 0-indexed (0 = Январь). @param dayOfMonth, weekOfMonth, dayOfWeek all 1-indexed. */
    public record Date(int year, int month, int dayOfMonth, int weekOfMonth, int dayOfWeek) {
        public String monthName() {
            return LotusCalendar.monthName(month);
        }
    }
}
