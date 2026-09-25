# Sesión y refresh de WhatPlan

WhatPlan conserva el access token solo en memoria en el frontend. El refresh token se transporta únicamente en la cookie `whatplan_refresh`: `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/api/auth`, sin valor en JSON y con vida máxima configurada por `AUTH_REFRESH_COOKIE_TTL_SECONDS` (por defecto siete días). El TTL configurado debe ser menor o igual a la vigencia efectiva del refresh token central.

`/api/auth/refresh` y `/api/auth/logout` requieren `Origin` exacto presente y permitido en `AUTH_COOKIE_ALLOWED_ORIGINS`, una lista de orígenes HTTP(S) sin comodines. El backend rechaza otros orígenes antes de llamar al servicio central. Para ambas rutas, incluso en error, se manda `Cache-Control: no-store` y `Pragma: no-cache`. Login y refresh requieren que el servicio central devuelva un refresh token; si falta, WhatPlan falla cerrado. Al recibir una rotación, el backend establece primero la cookie nueva, antes de provisionar datos locales, para no perderla si la base cae. Logout borra la cookie local incluso si el servicio central no responde.

## Contrato pendiente del servicio central

El cliente central recibe refresh/logout vía POST autenticado por el token contenido en el cuerpo interno de la llamada entre servicios. Este repositorio no puede garantizar que el emisor invalide el token anterior, detecte reutilización, revoque familias, cierre sesiones en cambio de contraseña/deshabilitación ni impida reuso tras logout. Antes de abrir el registro público, verificar esas propiedades con tests e integración del servicio real. WhatPlan borra su cookie aunque el logout upstream falle; esto cierra la sesión del navegador, pero no prueba que el token robado haya sido revocado.

También comprobar el origen público real detrás del proxy y configurarlo sin confiar en `X-Forwarded-*`. No usar HTTP en producción ni incluir localhost en la lista pública. Las acciones con cookie no deben aceptar llamadas sin `Origin`, incluyendo clientes no-browser; cualquier integración de servidor deberá usar un canal administrativo separado, no una excepción amplia a esta regla.
