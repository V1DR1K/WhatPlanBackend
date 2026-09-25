# Seguridad de invitaciones

- El secreto se genera con 32 bytes criptográficamente aleatorios, se muestra una sola vez y solo se guarda su hash SHA-256.
- El backend acepta únicamente el formato base64url de 43 caracteres y valida el tamaño/formato antes de calcular el hash. La API recibe el secreto en el cuerpo JSON (`POST /api/couple/invitations/accept`), no en la URL; el filtro de intentos también usa una ruta estable que no contiene secretos.
- Cada pareja puede generar como máximo 10 invitaciones en cualquier ventana móvil de 24 horas. La cuenta es compartida por la pareja, se realiza bajo bloqueo de la fila de pareja y usa un índice `(couple_id, created_at)` para mantener el costo acotado. PostgreSQL mantiene además como máximo una invitación pendiente por pareja.
- Crear otra invitación revoca la pendiente anterior; aceptar, revocar, expirar y dejar la pareja tienen eventos de auditoría con IDs y usuario, nunca con token. Aceptación, revocación y desvinculación se serializan por bloqueo de pareja/invitación; el token es de un solo uso y vence a los siete días.
- El endpoint aplica rate limit temporal por IP en cada instancia como defensa adicional. No confiar en ese límite local como cuota de producción distribuida; C10 requiere un límite compartido en edge/Redis.

La ruta de navegación web actual todavía puede llevar el token en la ruta de la SPA (`/invite/<token>`), aunque la aceptación al backend ya lo envía en el cuerpo. Antes de producción, sustituir esa ruta por un fragmento no enviado al servidor y no incluir secretos en analítica, telemetría o logs del proxy. Ningún acceso al token en logs del proveedor externo se puede confirmar desde este repositorio.
