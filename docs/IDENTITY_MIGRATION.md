# C05: vinculación auditada de identidades históricas

WhatPlan ya no vincula una cuenta local por coincidencia de nombre. `sub` (UUID validado del JWT central) es la única identidad de autenticación. Si una cuenta nueva intenta usar `tomas` o `avril` mientras existe una fila histórica sin UUID, la provisión responde `409` y no modifica esa fila, su rol, membresías ni contenido. El conflicto solo se resuelve verificando el UUID con el emisor central y ejecutando el procedimiento de abajo.

## Preflight obligatorio

1. Detener escrituras de usuarios y confirmar en el servicio central que las dos cuentas están activas. Iniciar sesión con cada cuenta y consultar `/api/me` usando su propio access token. Registrar `id`/`sub`, nombre, fecha, operador y ticket de cambio en el registro operativo protegido. No obtener UUID a partir del nombre, datos de la base WhatPlan ni tokens enviados por otra persona.
2. Confirmar que los UUID son distintos y que cada respuesta central declara la identidad esperada. Un nombre mutable no prueba propiedad de una cuenta.
3. Hacer y verificar un backup restaurable de PostgreSQL. Confirmar `flyway info` y que V45 está aplicada sin error. Si V45 aún no se ejecutó, seguir primero su preflight/backfill; este procedimiento no crea parejas ni reubica contenido.
4. En la base objetivo, revisar las filas `tomas`/`avril`, roles, `auth_user_id`, parejas/membresías activas y conteos de contenido privado por pareja. Debe haber exactamente una fila local para cada nombre, no debe haber una asociación previa contradictoria ni colisiones de username ignorando mayúsculas (`SELECT lower(username), count(*) FROM users GROUP BY lower(username) HAVING count(*) > 1`). Resolver discrepancias manualmente antes de proceder. V46 añade un índice único case-insensitive y fallará de forma segura si queda alguna colisión.

## Ejecución

Usar una conexión con privilegios limitados para actualizar `users`, contra la base respaldada correcta. Proporcionar los UUID leídos directamente del emisor central (no guardar access/refresh tokens en argumentos, historial o logs):

```powershell
psql $env:DATABASE_URL `
  -v tomas_auth_uuid=$env:TOMAS_AUTH_UUID `
  -v avril_auth_uuid=$env:AVRIL_AUTH_UUID `
  -f ops/identity/bind_legacy_users.sql
```

El script usa una transacción, bloquea ambas filas y aborta si falta una, hay duplicados, los UUID coinciden o cualquiera ya está asociado a otra fila. Es idempotente para la misma asociación. No cambia username, rol, pareja, membresías ni contenido. Conservar el log de ejecución y el cambio aprobado sin registrar tokens.

Después, revisar que los dos `auth_user_id` coincidan exactamente con los UUID verificados; repetir login y `/api/auth/me` con cada cuenta; verificar que ambas conservan sus membresías/contenido históricos y que un UUID nuevo que reclame esos nombres recibe conflicto sin obtener acceso. Volver a habilitar escrituras solo tras completar esas comprobaciones.

## Fallo o recuperación

Si cualquier comprobación falla, la transacción aborta y no se debe habilitar tráfico. Si el commit se completó con UUID incorrectos, detener autenticación/escrituras, preservar logs y backup, y hacer una corrección aprobada tras verificar nuevamente la identidad con el emisor; nunca resolverlo borrando la fila ni reasignando la pareja/contenido. Si no puede determinarse con certeza qué cambió, restaurar el backup en un entorno aislado y comparar antes de intervenir producción.

No se incluyen UUID concretos en el repositorio: deben ser obtenidos y aprobados por el operador para cada instalación.
