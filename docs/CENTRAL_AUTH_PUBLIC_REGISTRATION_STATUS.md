# Estado del registro público

## Contrato implementado

WhatPlan mantiene las contraseñas exclusivamente en central-auth. El backend expone `POST /api/auth/register`, limita los intentos por IP y delega la creación mediante `POST /api/register`. El servicio central normaliza el usuario a minúsculas, acepta entre 3 y 80 caracteres ASCII (`a-z`, números, punto, guion y guion bajo), exige contraseña de 10 a 128 caracteres, devuelve una respuesta genérica ante usuarios duplicados y crea cuentas comunes sin opción de asignar rol. La respuesta emite la sesión habitual; WhatPlan conserva el refresh token en una cookie `HttpOnly`, `Secure`, `SameSite=Lax` y marca la respuesta como `no-store`.

## Código y validación

El código central está en `/opt/projects/auth-service/back/auth-service-backend` en el VPS. Se agregó `POST /api/register` y su prueba de servicio. La carpeta central no tiene metadatos Git; el parche versionado que reproduce el cambio está en [`central-auth-public-registration.patch`](central-auth-public-registration.patch). La copia aislada compiló con Java 21/Maven y pasaron sus pruebas unitarias.

El backend WhatPlan crea el registro local con rol `USER`, deja que el usuario cree o acepte una pareja durante el onboarding y no habilita el panel admin. El límite público de registro es de cinco intentos por IP cada treinta minutos.

## Estado del host

Se actualizó el código fuente canónico, pero no se reconstruyó ni reinició el servicio central en ejecución. El endpoint todavía no está disponible en producción hasta publicar ese código en el flujo de despliegue de auth. La carpeta no está versionada, por lo que el parche adjunto conserva el cambio para aplicarlo y versionarlo cuando auth tenga un repositorio Git. Se guardó una copia previa de esos archivos en `/tmp/whatplan-central-auth-backup-01a11c16.tar` en el VPS.

No se incorporaron recuperación de contraseña, verificación por email ni baja de cuenta: el servicio actual no tiene correo registrado ni esos contratos, y no son necesarios para crear la cuenta solicitada. El cambio no incluye despliegue.
