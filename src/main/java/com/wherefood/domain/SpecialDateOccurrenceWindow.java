package com.wherefood.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** Calculates the concrete date window represented by a recurring special-date template. */
public final class SpecialDateOccurrenceWindow {
    private SpecialDateOccurrenceWindow() {}

    public record Window(LocalDate startsOn, LocalDate endsOn) {}

    public static Optional<Window> forDate(SpecialDate template, LocalDate date) {
        if (template == null || template.date == null || date == null) return Optional.empty();
        LocalDate templateEnd = template.endsOn == null ? template.date : template.endsOn;
        long duration = ChronoUnit.DAYS.between(template.date, templateEnd);
        SpecialDateRecurrence recurrence = template.recurrence == null
                ? SpecialDateRecurrence.ONCE : template.recurrence;

        if (recurrence == SpecialDateRecurrence.ONCE) {
            return !date.isBefore(template.date) && !date.isAfter(templateEnd)
                    ? Optional.of(new Window(template.date, templateEnd)) : Optional.empty();
        }
        if (recurrence == SpecialDateRecurrence.DAILY) {
            return Optional.of(new Window(date, date));
        }

        LocalDate latestStart = null;
        if (recurrence == SpecialDateRecurrence.ANNUAL) {
            for (int year = date.getYear(); year >= date.getYear() - 1; year--) {
                LocalDate candidate = annualStart(template.date, year);
                if (!date.isBefore(candidate) && !date.isAfter(candidate.plusDays(duration))
                        && (latestStart == null || candidate.isAfter(latestStart))) {
                    latestStart = candidate;
                }
            }
        } else {
            YearMonth month = YearMonth.from(date);
            for (int offset = 0; offset <= 1; offset++) {
                YearMonth candidateMonth = month.minusMonths(offset);
                LocalDate candidate = candidateMonth.atDay(
                        Math.min(template.date.getDayOfMonth(), candidateMonth.lengthOfMonth()));
                if (!date.isBefore(candidate) && !date.isAfter(candidate.plusDays(duration))
                        && (latestStart == null || candidate.isAfter(latestStart))) {
                    latestStart = candidate;
                }
            }
        }
        return latestStart == null ? Optional.empty()
                : Optional.of(new Window(latestStart, latestStart.plusDays(duration)));
    }

    private static LocalDate annualStart(LocalDate templateStart, int year) {
        YearMonth month = YearMonth.of(year, templateStart.getMonth());
        return month.atDay(Math.min(templateStart.getDayOfMonth(), month.lengthOfMonth()));
    }
}
