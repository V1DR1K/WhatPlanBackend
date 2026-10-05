package com.wherefood.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SpecialDateOccurrenceWindowTest {
    @Test
    void annualRangeMatchesEveryDayAndReturnsTheConcreteYear() {
        SpecialDate template = date("2026-10-10", "2026-10-12", SpecialDateRecurrence.ANNUAL);

        var window = SpecialDateOccurrenceWindow.forDate(template, LocalDate.parse("2027-10-11"));

        assertTrue(window.isPresent());
        assertEquals(LocalDate.parse("2027-10-10"), window.get().startsOn());
        assertEquals(LocalDate.parse("2027-10-12"), window.get().endsOn());
        assertTrue(SpecialDateOccurrenceWindow.forDate(template, LocalDate.parse("2027-10-12")).isPresent());
        assertFalse(SpecialDateOccurrenceWindow.forDate(template, LocalDate.parse("2027-10-13")).isPresent());
    }

    @Test
    void monthlyRangeClampsItsStartingDayToShorterMonths() {
        SpecialDate template = date("2026-01-30", "2026-01-31", SpecialDateRecurrence.MONTHLY);

        var window = SpecialDateOccurrenceWindow.forDate(template, LocalDate.parse("2027-03-01"));

        assertTrue(window.isPresent());
        assertEquals(LocalDate.parse("2027-02-28"), window.get().startsOn());
        assertEquals(LocalDate.parse("2027-03-01"), window.get().endsOn());
        assertFalse(SpecialDateOccurrenceWindow.forDate(template, LocalDate.parse("2027-03-02")).isPresent());
    }

    @Test
    void dailyRecurrenceMatchesOneDayAtATime() {
        SpecialDate template = date("2026-01-30", "2026-01-30", SpecialDateRecurrence.DAILY);

        var window = SpecialDateOccurrenceWindow.forDate(template, LocalDate.parse("2027-03-01"));

        assertTrue(window.isPresent());
        assertEquals(LocalDate.parse("2027-03-01"), window.get().startsOn());
        assertEquals(LocalDate.parse("2027-03-01"), window.get().endsOn());
    }

    private static SpecialDate date(String start, String end, SpecialDateRecurrence recurrence) {
        SpecialDate value = new SpecialDate();
        value.date = LocalDate.parse(start);
        value.endsOn = LocalDate.parse(end);
        value.recurrence = recurrence;
        return value;
    }
}
