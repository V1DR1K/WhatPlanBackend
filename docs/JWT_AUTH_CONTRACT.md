# Contrato JWT de WhatPlan

WhatPlan solo acepta un JWT firmado como bearer cuando cumple todas estas condiciones:

- firma RSA `RS256` con una clave pública RSA de al menos 2048 bits;
- `iss` coincide exactamente con `AUTH_JWT_ISSUER`;
- `aud` contiene `AUTH_JWT_AUDIENCE` (el emisor central desplegado usa `central-auth`);
- si `token_type` está presente, es exactamente `access`; para el contrato legado del emisor central debe faltar `token_type` y estar presentes `uid` igual a `sub` y `username` no vacío;
- `sub` es un UUID en formato canónico;
- están presentes `iat` y `exp`; los tokens modernos con `token_type=access` también deben incluir `nbf`, mientras que el contrato legado no lo emite;
- el emisor no fija `iat` más de 60 segundos en el futuro y la vigencia `exp - iat` no excede `AUTH_MAX_ACCESS_TOKEN_TTL_SECONDS` (por defecto 900, máximo configurable 3600).

El parser aplica 60 segundos de tolerancia a los límites temporales. El emisor central desplegado usa access JWT firmados con `uid`/`username`; los refresh tokens son valores aleatorios opacos, no JWT. El contrato moderno requiere `token_type=access` y `nbf`; la forma anterior solo se acepta si el token firmado contiene `uid` igual a `sub` y `username` no vacío. Un token inválido deja la petición anónima y las rutas protegidas devuelven 401 RFC 9457. La búsqueda del usuario local y la resolución de membresía ocurren fuera del bloque que captura fallos criptográficos; errores de PostgreSQL no se silencian como si el token fuera inválido.

## Configuración

`compose.yml` pasa `AUTH_JWT_ISSUER`, `AUTH_JWT_AUDIENCE` y `AUTH_PUBLIC_KEY_PEM` explícitamente. El valor esperado para el emisor central desplegado es `AUTH_JWT_AUDIENCE=central-auth`. El servicio no inicia si issuer/audience faltan, el TTL está fuera de rango o una clave es inválida. `AUTH_PREVIOUS_PUBLIC_KEY_PEMS` acepta hasta dos PEM adicionales separados por comas, para un solapamiento acotado durante la rotación. Mantener cada PEM público completo en un solo valor de configuración y nunca registrar tokens.

Rotación coordinada: publicar primero WhatPlan con clave nueva como `AUTH_PUBLIC_KEY_PEM` y la anterior en `AUTH_PREVIOUS_PUBLIC_KEY_PEMS`; después cambiar el emisor a la clave nueva; esperar como mínimo el TTL máximo más el margen de reloj; finalmente retirar la anterior y volver a desplegar. Si el emisor requiere `kid`, debe existir un adaptador explícito y probado antes de cambiar el contrato; hoy WhatPlan intenta verificar contra el pequeño conjunto local de claves permitidas y no confía en un `kid` recibido.

## Dependencia externa de lanzamiento

El fuente desplegado del emisor central se inspeccionó en producción: emite `RS256`, `iss`, `aud=central-auth`, `sub` UUID, `uid`, `username`, `iat` y `exp`; no emite `token_type` ni `nbf`. Su refresh token es opaco. WhatPlan mantiene firma, emisor, audiencia, subject, identidad duplicada, expiración y TTL bajo validación, y admite `token_type=access` para una futura actualización compatible del emisor.
