package com.wherefood.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Application input for the legacy reusable-plan compatibility contract. */
public record FunPlanInput(String name, String address, LocalDate scheduledAt, Long categoryId,
        Long subcategoryId, List<Schedule> schedules, Long zoneId, UUID stageId) {
    public record Schedule(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {}
}
