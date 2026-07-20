package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WhyFunApiTest {
 @Test
 void acceptsSchedulesThatCloseAfterMidnight() {
  assertDoesNotThrow(() -> WhyFunApi.validateSchedules(List.of(
    new FunScheduleRequest(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(3, 0))
  )));
 }

 @Test
 void rejectsOverlappingSchedulesThatCloseAfterMidnight() {
  assertThrows(ResponseStatusException.class, () -> WhyFunApi.validateSchedules(List.of(
    new FunScheduleRequest(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(3, 0)),
    new FunScheduleRequest(DayOfWeek.FRIDAY, LocalTime.of(22, 0), LocalTime.of(2, 0))
  )));
 }
}
