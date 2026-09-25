# Contrato JWT de WhatPlan

WhatPlan solo acepta un JWT firmado como bearer cuando cumple todas estas condiciones:

- firma RSA `RS256` con una clave pública RSA de al menos 2048 bits;
- `iss` coincide exactamente con `AUTH_JWT_ISSUER`;
- `aud` contiene `AUTH_JWT_AUDIENCE` (sin valor por defecto);
- `token_type` es exactamente `access`;
- `sub` es un UUID en formato canónico;
- están presentes `iat`, `nbf` y `exp`, y el token está vigente;
- el emisor no fija `iat` más de 60 segundos en el futuro y la vigencia `exp - iat` no excede `AUTH_MAX_ACCESS_TOKEN_TTL_SECONDS` (por defecto 900, máximo configurable 3600).

El parser aplica 60 segundos de tolerancia a los límites temporales. No se acepta un refresh token como bearer. Un token inválido deja la petición anónima y las rutas protegidas devuelven 401 RFC 9457. La búsqueda del usuario local y la resolución de membresía ocurren fuera del bloque que captura fallos criptográficos; errores de PostgreSQL no se silencian como si el token fuera inválido.

## Configuración

`compose.yml` pasa `AUTH_JWT_ISSUER`, `AUTH_JWT_AUDIENCE` y `AUTH_PUBLIC_KEY_PEM` explícitamente. El servicio no inicia si issuer/audience faltan, el TTL está fuera de rango o una clave es inválida. `AUTH_PREVIOUS_PUBLIC_KEY_PEMS` acepta hasta dos PEM adicionales separados por comas, para un solapamiento acotado durante la rotación. Mantener cada PEM público completo en un solo valor de configuración y nunca registrar tokens.

Rotación coordinada: publicar primero WhatPlan con clave nueva como `AUTH_PUBLIC_KEY_PEM` y la anterior en `AUTH_PREVIOUS_PUBLIC_KEY_PEMS`; después cambiar el emisor a la clave nueva; esperar como mínimo el TTL máximo más el margen de reloj; finalmente retirar la anterior y volver a desplegar. Si el emisor requiere `kid`, debe existir un adaptador explícito y probado antes de cambiar el contrato; hoy WhatPlan intenta verificar contra el pequeño conjunto local de claves permitidas y no confía en un `kid` recibido.

## Dependencia externa de lanzamiento

El repositorio del emisor central no está incluido aquí. Antes de habilitar registro público, obtener evidencia de que emite `RS256`, `iss`, `aud`, `token_type=access`, `sub` UUID canónico, `iat`, `nbf` y `exp` dentro del TTL acordado, y de que refresh usa tipo distinto. Ejecutar integración contra el emisor real y acordar la rotación. Mientras eso no ocurra, el backend falla de forma cerrada; no relajar las validaciones para recuperar compatibilidad.
