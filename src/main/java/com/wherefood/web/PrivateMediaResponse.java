package com.wherefood.web;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Builds non-cacheable responses for authenticated, couple-private media. */
final class PrivateMediaResponse {
    private PrivateMediaResponse() {}

    static ResponseEntity<byte[]> webp(byte[] bytes) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .varyBy("Authorization", "Cookie")
                .contentType(MediaType.valueOf("image/webp"))
                .body(bytes);
    }
}
