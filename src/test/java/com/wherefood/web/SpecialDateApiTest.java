package com.wherefood.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wherefood.domain.SpecialDate;
import com.wherefood.domain.SpecialDateRecurrence;
import com.wherefood.repo.Repositories.SpecialDates;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpecialDateApiTest {
 @Test
 void supportsMultipleLabelsForTheSameExactDate() {
  SpecialDates dates = mock(SpecialDates.class);
  SpecialDate first = specialDate(4L, LocalDate.of(2026, 12, 25), "Navidad", SpecialDateRecurrence.ANNUAL);
  SpecialDate second = specialDate(5L, LocalDate.of(2026, 12, 25), "Cena familiar", SpecialDateRecurrence.ONCE);
  when(dates.findAllByOrderByDateAscLabelAscIdAsc()).thenReturn(List.of(second, first));
  when(dates.save(any(SpecialDate.class))).thenAnswer(invocation -> {
   SpecialDate value = invocation.getArgument(0);
   if (value.id == null) value.id = 6L;
   return value;
  });
  when(dates.findById(4L)).thenReturn(Optional.of(first));
  SpecialDateApi api = new SpecialDateApi(dates);

  List<SpecialDateDto> listed = api.list();
  SpecialDateDto added = api.add(new SpecialDateRequest(LocalDate.of(2027, 1, 1), "Año nuevo", SpecialDateRecurrence.ONCE));
  SpecialDateDto updated = api.update(4L, new SpecialDateRequest(LocalDate.of(2026, 12, 25), "Navidad familiar", SpecialDateRecurrence.MONTHLY));
  api.delete(4L);

  assertEquals(List.of("Cena familiar", "Navidad"), listed.stream().map(SpecialDateDto::label).toList());
  assertEquals(LocalDate.of(2027, 1, 1), added.date());
  assertEquals(SpecialDateRecurrence.ONCE, added.recurrence());
  assertNotNull(added.createdAt());
  assertEquals("Navidad familiar", updated.label());
  assertEquals(SpecialDateRecurrence.MONTHLY, updated.recurrence());
  verify(dates).delete(first);
 }

 @Test
 void restrictsSpecialDateMutationsToAdmins() throws Exception {
  assertAdmin("add", SpecialDateRequest.class);
  assertAdmin("update", Long.class, SpecialDateRequest.class);
  assertAdmin("delete", Long.class);
 }

 @Test
 void validatesTheDateLabelAndRecurrence() throws Exception {
  MockMvc mvc = MockMvcBuilders.standaloneSetup(new SpecialDateApi(mock(SpecialDates.class))).build();

  mvc.perform(post("/api/special-dates").contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"  \"}"))
    .andExpect(status().isBadRequest());
  mvc.perform(post("/api/special-dates").contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-02-14\",\"label\":\"San Valentín\"}"))
    .andExpect(status().isBadRequest());
  mvc.perform(post("/api/special-dates").contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-02-14\",\"label\":\"San Valentín\",\"recurrence\":\"WEEKLY\"}"))
    .andExpect(status().isBadRequest());
 }

 @Test
 void returnsTheSelectedRecurrence() throws Exception {
  SpecialDates dates = mock(SpecialDates.class);
  when(dates.save(any(SpecialDate.class))).thenAnswer(invocation -> {
   SpecialDate value = invocation.getArgument(0);
   value.id = 6L;
   return value;
  });
  MockMvc mvc = MockMvcBuilders.standaloneSetup(new SpecialDateApi(dates)).build();

  mvc.perform(post("/api/special-dates").contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-02-14\",\"label\":\"San Valentín\",\"recurrence\":\"ANNUAL\"}"))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.recurrence").value("ANNUAL"));
 }

 private static void assertAdmin(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
  Method method = SpecialDateApi.class.getDeclaredMethod(name, parameterTypes);
  assertEquals("hasRole('ADMIN')", method.getAnnotation(PreAuthorize.class).value());
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
