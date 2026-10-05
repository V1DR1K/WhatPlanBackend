package com.wherefood.web;

import com.wherefood.domain.SpecialDateRecurrence;
import com.wherefood.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

record SpecialDateRequest(@NotNull LocalDate date, LocalDate endsOn, @NotBlank @Size(max = 160) String label, @NotNull SpecialDateRecurrence recurrence) {
 SpecialDateRequest(LocalDate date, String label, SpecialDateRecurrence recurrence) {
  this(date, date, label, recurrence);
 }
}
record SpecialDateDto(Long id, LocalDate date, LocalDate endsOn, String label, SpecialDateRecurrence recurrence, Instant createdAt, Instant updatedAt) {}

@RestController
@RequestMapping("/api/special-dates")
public class SpecialDateApi {
 private final SpecialDateService service;

 public SpecialDateApi(SpecialDateService service) { this.service = service; }

 @GetMapping List<SpecialDateDto> list(@AuthenticationPrincipal User actor) { return service.list(actor).stream().map(SpecialDateApi::specialDate).toList(); }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) SpecialDateDto add(@RequestBody @Valid SpecialDateRequest request, @AuthenticationPrincipal User actor) {
  return specialDate(service.create(request, actor));
 }
 @PutMapping("/{id}") SpecialDateDto update(@PathVariable Long id, @RequestBody @Valid SpecialDateRequest request, @AuthenticationPrincipal User actor) {
  return specialDate(service.update(id, request, actor));
 }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable Long id, @AuthenticationPrincipal User actor) { service.delete(id, actor); }

 private static SpecialDateDto specialDate(com.wherefood.domain.SpecialDate value) { return new SpecialDateDto(value.id, value.date, value.endsOn, value.label, value.recurrence, value.createdAt, value.updatedAt); }
}
