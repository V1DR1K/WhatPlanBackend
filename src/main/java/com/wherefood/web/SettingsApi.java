package com.wherefood.web;

import com.wherefood.domain.GlobalSettings;
import com.wherefood.repo.Repositories.Settings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

record SettingsRequest(@NotNull @Min(1) @Max(50) Integer catalogPageSize) {}
record SettingsDto(int catalogPageSize) {}

@RestController
@RequestMapping("/api/settings")
public class SettingsApi {
 private static final int SINGLETON_ID = 1;
 private final Settings settings;

 public SettingsApi(Settings settings) { this.settings = settings; }

 @GetMapping @PreAuthorize("isAuthenticated()") @Transactional SettingsDto get() { return settings(settings()); }
 @PutMapping @PreAuthorize("hasRole('ADMIN')") @Transactional SettingsDto update(@RequestBody @Valid SettingsRequest request) {
  GlobalSettings value = settings();
  value.catalogPageSize = request.catalogPageSize();
  return settings(settings.save(value));
 }

 private GlobalSettings settings() {
  return settings.findById(SINGLETON_ID).orElseGet(() -> {
   settings.insertDefaultIfMissing();
   return settings.findById(SINGLETON_ID).orElseThrow(() -> new IllegalStateException("No se pudo crear la configuración global"));
  });
 }

 private static SettingsDto settings(GlobalSettings value) { return new SettingsDto(value.catalogPageSize); }
}
