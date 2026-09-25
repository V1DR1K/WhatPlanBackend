package com.wherefood.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

record SettingsRequest(@NotNull @Min(1) @Max(50) Integer catalogPageSize) {}
record SettingsDto(int catalogPageSize) {}

@RestController
@RequestMapping("/api/settings")
public class SettingsApi {
 private final GlobalSettingsService service;

 public SettingsApi(GlobalSettingsService service) { this.service = service; }

 @GetMapping @PreAuthorize("isAuthenticated()") SettingsDto get() { return settings(service.get()); }
 @PutMapping @PreAuthorize("hasRole('ADMIN')") SettingsDto update(@RequestBody @Valid SettingsRequest request) { return settings(service.update(request)); }

 private static SettingsDto settings(com.wherefood.domain.GlobalSettings value) { return new SettingsDto(value.catalogPageSize); }
}
