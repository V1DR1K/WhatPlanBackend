package com.wherefood.web;

import com.wherefood.domain.GlobalSettings;
import com.wherefood.repo.Repositories.Settings;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operations on the intentionally global application settings singleton. */
@Service
@Transactional(readOnly = true)
public class GlobalSettingsService {
    private static final int SINGLETON_ID = 1;
    private final Settings settings;

    public GlobalSettingsService(Settings settings) { this.settings = settings; }

    public GlobalSettings get() { return settings(); }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public GlobalSettings update(SettingsRequest request) {
        GlobalSettings value = settings();
        value.catalogPageSize = request.catalogPageSize();
        return settings.save(value);
    }

    private GlobalSettings settings() {
        return settings.findById(SINGLETON_ID).orElseGet(() -> {
            settings.insertDefaultIfMissing();
            return settings.findById(SINGLETON_ID).orElseThrow(() ->
                    new IllegalStateException("No se pudo crear la configuración global"));
        });
    }
}
