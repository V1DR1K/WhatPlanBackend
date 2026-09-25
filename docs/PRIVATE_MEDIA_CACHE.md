# Private media caching

All WhatPlan photo endpoints now return `Cache-Control: no-store`. The response is authorized against the active couple before bytes are loaded; the browser and shared intermediaries must not retain a durable copy after membership or session changes. This intentionally trades repeat-download performance for privacy while images remain in PostgreSQL.

This does not erase files that a browser, proxy, screenshot, export, or user already copied before the change. Previously cached media URLs could remain in browser caches until eviction; users must sign out/clear site data to remove local copies. Do not put private media on a public CDN. When object storage is introduced, use authenticated reads or short-lived signed URLs scoped to an opaque object key and version; membership loss cannot recall a downloaded copy.

TMDB poster assets are third-party public media and are separate from these authenticated WhatPlan photo endpoints.
