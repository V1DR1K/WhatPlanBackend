package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.SpecialDate;
import com.wherefood.domain.SpecialDateRecurrence;
import com.wherefood.domain.User;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpecialDateApiTest {
 @Test
 void supportsMultipleLabelsForTheSameExactDate() {
  SpecialDateService service = mock(SpecialDateService.class);
  User actor = new User();
  SpecialDate first = specialDate(4L, LocalDate.of(2026, 12, 25), "Navidad", SpecialDateRecurrence.ANNUAL);
  SpecialDate second = specialDate(5L, LocalDate.of(2026, 12, 25), "Cena familiar", SpecialDateRecurrence.ONCE);
  SpecialDate addedValue = specialDate(6L, LocalDate.of(2027, 1, 1), "Año nuevo", SpecialDateRecurrence.ONCE);
  SpecialDate updatedValue = specialDate(4L, LocalDate.of(2026, 12, 25), "Navidad familiar", SpecialDateRecurrence.MONTHLY);
  addedValue.createdAt = java.time.Instant.now();
  when(service.list(actor)).thenReturn(List.of(second, first));
  when(service.create(any(SpecialDateRequest.class), org.mockito.ArgumentMatchers.same(actor))).thenReturn(addedValue);
  when(service.update(org.mockito.ArgumentMatchers.eq(4L), any(SpecialDateRequest.class), org.mockito.ArgumentMatchers.same(actor))).thenReturn(updatedValue);
  SpecialDateApi api = new SpecialDateApi(service);

  List<SpecialDateDto> listed = api.list(actor);
  SpecialDateDto added = api.add(new SpecialDateRequest(LocalDate.of(2027, 1, 1), "Año nuevo", SpecialDateRecurrence.ONCE), actor);
  SpecialDateDto updated = api.update(4L, new SpecialDateRequest(LocalDate.of(2026, 12, 25), "Navidad familiar", SpecialDateRecurrence.MONTHLY), actor);
  api.delete(4L, actor);

  assertEquals(List.of("Cena familiar", "Navidad"), listed.stream().map(SpecialDateDto::label).toList());
  assertEquals(LocalDate.of(2027, 1, 1), added.date());
  assertEquals(SpecialDateRecurrence.ONCE, added.recurrence());
  assertNotNull(added.createdAt());
  assertEquals("Navidad familiar", updated.label());
  assertEquals(SpecialDateRecurrence.MONTHLY, updated.recurrence());
  verify(service).delete(4L, actor);
 }

 @Test
 void delegatesSharedDateMutationsWithTheAuthenticatedMember() {
  SpecialDateService service = mock(SpecialDateService.class);
  User actor = new User();
  SpecialDateRequest request = new SpecialDateRequest(LocalDate.of(2026, 12, 25), "Navidad", SpecialDateRecurrence.ANNUAL);
  when(service.create(request, actor)).thenReturn(specialDate(5L, request.date(), request.label(), request.recurrence()));
  when(service.update(4L, request, actor)).thenReturn(specialDate(4L, request.date(), request.label(), request.recurrence()));
  SpecialDateApi api = new SpecialDateApi(service);

  api.add(request, actor);
  api.update(4L, request, actor);
  api.delete(4L, actor);

  verify(service).create(request, actor);
  verify(service).update(4L, request, actor);
  verify(service).delete(4L, actor);
 }

 @Test
 void validatesTheDateLabelAndRecurrence() throws Exception {
  MockMvc mvc = MockMvcBuilders.standaloneSetup(new SpecialDateApi(mock(SpecialDateService.class))).build();

  mvc.perform(post("/api/special-dates").contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"  \"}"))
    .andExpect(status().isBadRequest());
  mvc.perform(post("/api/special-dates").contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-02-14\",\"label\":\"San Valentín\"}"))
    .andExpect(status().isBadRequest());
  mvc.perform(post("/api/special-dates").contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-02-14\",\"label\":\"San Valentín\",\"recurrence\":\"WEEKLY\"}"))
    .andExpect(status().isBadRequest());
 }

 @Test
 void returnsTheSelectedRecurrence() throws Exception {
  SpecialDateService service = mock(SpecialDateService.class);
  when(service.create(any(SpecialDateRequest.class), any())).thenReturn(
          specialDate(6L, LocalDate.of(2026, 2, 14), "San Valentín", SpecialDateRecurrence.ANNUAL));
  MockMvc mvc = MockMvcBuilders.standaloneSetup(new SpecialDateApi(service)).build();

  mvc.perform(post("/api/special-dates").contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-02-14\",\"label\":\"San Valentín\",\"recurrence\":\"ANNUAL\"}"))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.recurrence").value("ANNUAL"));
 }

 private static SpecialDate specialDate(Long id, LocalDate date, String label, SpecialDateRecurrence recurrence) {
  SpecialDate value = new SpecialDate();
  value.id = id;
  value.date = date;
  value.label = label;
  value.recurrence = recurrence;
  return value;
 }
}
