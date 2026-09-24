package com.wherefood.web;

import com.wherefood.domain.SpecialDate;
import com.wherefood.domain.SpecialDateRecurrence;
import com.wherefood.repo.Repositories.SpecialDates;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

record SpecialDateRequest(@NotNull LocalDate date, @NotBlank @Size(max = 160) String label, @NotNull SpecialDateRecurrence recurrence) {}
record SpecialDateDto(Long id, LocalDate date, String label, SpecialDateRecurrence recurrence, Instant createdAt, Instant updatedAt) {}

@RestController
@RequestMapping("/api/special-dates")
public class SpecialDateApi {
 private final SpecialDates specialDates;

 public SpecialDateApi(SpecialDates specialDates) { this.specialDates = specialDates; }

 @GetMapping List<SpecialDateDto> list() { return specialDates.findAllByCoupleIdOrderByDateAscLabelAscIdAsc(CoupleContext.current()).stream().map(SpecialDateApi::specialDate).toList(); }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) SpecialDateDto add(@RequestBody @Valid SpecialDateRequest request) {
  SpecialDate value = new SpecialDate(); apply(value, request); value.createdAt = value.updatedAt = Instant.now(); return specialDate(specialDates.save(value));
 }
 @PutMapping("/{id}") SpecialDateDto update(@PathVariable Long id, @RequestBody @Valid SpecialDateRequest request) {
  SpecialDate value = specialDates.findByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound()); apply(value, request); value.updatedAt = Instant.now(); return specialDate(specialDates.save(value));
 }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable Long id) { specialDates.delete(specialDates.findByIdAndCoupleId(id, CoupleContext.current()).orElseThrow(() -> notFound())); }

 private static void apply(SpecialDate value, SpecialDateRequest request) { value.date = request.date(); value.label = request.label().trim(); value.recurrence = request.recurrence(); }
 private static SpecialDateDto specialDate(SpecialDate value) { return new SpecialDateDto(value.id, value.date, value.label, value.recurrence, value.createdAt, value.updatedAt); }
 private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Fecha especial no encontrada"); }
}
