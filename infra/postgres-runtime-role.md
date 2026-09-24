# PostgreSQL roles de WhatPlan

La aplicación debe conectarse con `DATABASE_RUNTIME_USER` / `DATABASE_RUNTIME_PASSWORD`. Flyway usa `DATABASE_MIGRATION_USER` / `DATABASE_MIGRATION_PASSWORD`. Ninguna de estas credenciales debe ser el usuario configurado por `POSTGRES_USER`; ese usuario es la cuenta administrativa creada durante la inicialización de PostgreSQL.

## Provisionamiento y cambio de credenciales

1. Crear contraseñas aleatorias distintas para `whatplan_runtime` y `whatplan_migrator` en el gestor de secretos del entorno. No reutilizar `POSTGRES_PASSWORD`.
2. Con la base existente ya iniciada, ejecutar `infra/provision-runtime-role.sql` conectado como administrador. El script crea/actualiza ambos roles, transfiere el esquema `public` y sus tablas/secuencias al migrator, otorga DML al runtime y verifica que ambos carezcan de `SUPERUSER` y `BYPASSRLS`.
3. Guardar las cuatro variables nuevas en el gestor de secretos y reiniciar backend. La cuenta inicial `POSTGRES_USER` permanece solo para administración/bootstrap; no debe configurarse en el servicio backend.
4. Verificar con `SELECT current_user, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user;` desde una conexión de runtime. El resultado debe indicar el usuario runtime y `false` en ambos privilegios.
5. Confirmar la salud de la app y que Flyway valide el historial. En error, restaurar las variables previas solo como recuperación temporal y mantener el registro público cerrado hasta reparar el provisioning; nunca declarar aislamiento válido con una cuenta superusuario.

El script no elimina datos ni volúmenes. El cambio de owner no revierte automáticamente si se restaura solo la configuración; la recuperación de privilegios debe hacerse deliberadamente con una cuenta admin. Los permisos de runtime cubren DML sobre tablas de `public`; migraciones futuras deben mantener `FORCE ROW LEVEL SECURITY` en tablas privadas y configurar `app.couple_id` explícitamente para backfills de datos. Esta separación protege las consultas normales de la app, pero la cuenta migrator sigue siendo poderosa sobre el esquema y debe permanecer como secreto de servicio.
