package com.wherefood.config;

import java.util.UUID;

public final class CoupleContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private CoupleContext() {}

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
