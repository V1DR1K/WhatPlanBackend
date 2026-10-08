package com.wherefood.config;

import java.util.UUID;

/** Couple scope requested by an authenticated administrator for workspace maintenance. */
public final class AdminCoupleContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private AdminCoupleContext() {}

    public static UUID current() {
        return CURRENT.get();
    }

    public static void set(UUID coupleId) {
        if (coupleId == null) CURRENT.remove();
        else CURRENT.set(coupleId);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
