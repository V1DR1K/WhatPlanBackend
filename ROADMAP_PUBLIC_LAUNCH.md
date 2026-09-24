# WhatPlan: roadmap de seguridad y lanzamiento público

Fecha de auditoría: 2026-09-24. Este documento es un plan de implementación para otra IA o equipo. **No certifica que la aplicación sea segura ni que cumpla la normativa.** La salida pública queda bloqueada hasta completar los criterios finales y una revisión independiente.

## 1. Alcance y estado observado

- Backend: `WhatPlanBackend`, rama `CoupleExpantion`, base auditada `d32e37b`.
- Frontend/PWA: `WhatPlanFrontend`, rama local `CoupleExpantion`, base auditada `c91a795` (el commit PWA local estaba un commit por delante de `origin/CoupleExpantion` al auditar). No mezclar con los cambios posteriores de `main` sin una comparación explícita.
- Autenticación central: servicio externo referenciado por `CentralAuthClient`; su código, despliegue, registro y política de contraseñas **no están en estos dos repositorios**. Son dependencias de lanzamiento por verificar, no controles que se puedan dar por implementados aquí.
- Verificación ejecutada: `npm audit --omit=dev` informó 0 vulnerabilidades conocidas de dependencias de producción; `npm test -- --run` pasó 26 tests en 8 archivos; detector de interfaz de Impeccable devolvió `[]`. Ninguna de estas pruebas demuestra aislamiento entre parejas. Backend sin ejecución local: solo hay Java 8, no hay Maven en PATH y el daemon de Docker no estaba disponible. CI actual ejecuta `mvn verify` en PR a `main`, pero no contiene pruebas de PostgreSQL/Testcontainers de multitenencia.
- Supuesto legal inicial: operación desde Argentina; antes de publicar, definir países de residencia de usuarios y proveedores. Si se ofrece fuera de Argentina, revisar las obligaciones adicionales de cada jurisdicción.

### Hallazgos que impiden abrir el registro público

| Prioridad | Evidencia comprobada | Riesgo / decisión |
| --- | --- | --- |
| P0 | `compose.yml` usa `${POSTGRES_USER}` como `DATABASE_USER`; la imagen oficial de PostgreSQL crea `POSTGRES_USER` como superusuario. `V45__add_couple_isolation.sql` usa RLS, incluso `FORCE`. PostgreSQL documenta que superusuarios y roles `BYPASSRLS` siempre eluden RLS. | **El aislamiento efectivo no está garantizado**. Con ese rol, consultas privadas globales como `Places.findAll()` o `Films.findAll()` pueden leer otras parejas. Verificar el rol efectivo de producción y sustituirlo. |
| P0 | `Repositories.java` expone `findAll()`, `findById()` y consultas sin `couple_id` para contenido privado; los controladores las usan directamente. Las entidades privadas no modelan explícitamente `couple_id`; dependen de un default SQL y `TenantAwareDataSource`. | Una configuración o ruta que eluda RLS se convierte en acceso cruzado. Se necesitan filtros explícitos, servicios de autorización y pruebas con PostgreSQL real. |
| P0 | `LocalUserProvisioner.provision()` vincula un usuario central nuevo a una fila local por `username` cuando no encuentra `authUserId`. El histórico Tomás/Avril se vincula también por nombre en V45. | Si un nombre heredado queda libre o se reasigna en el sistema central, puede heredarse una cuenta local, rol y pareja. Vincular por identidad central verificada y migración controlada. |
| P0 operativo | `application.yml` exige `${AUTH_JWT_ISSUER}`, pero `compose.yml` no lo pasa al backend; tampoco pasa `AUTH_JWT_AUDIENCE` ni `AUTH_ACCESS_TOKEN_CLAIM`. | El despliegue descrito puede fallar al iniciar; además, el tipo de token queda opcional por defecto. Probar la configuración real del host. |
| P1 | `CentralJwt` permite `app.auth-access-token-claim` vacío; `CentralJwtFilter` acepta cualquier JWT firmado que cumpla el resto y no consulta estado/revocación del usuario central. | Un refresh token con las claims apropiadas, o una cuenta central deshabilitada con access token aún válido, podría seguir siendo aceptado según el contrato externo. Verificarlo con el emisor real. |
| P1 | `RequestRateLimitFilter` es un mapa en memoria por instancia, sin límite de claves ni limpieza global. `CentralAuthClient` no configura tiempos de espera explícitos. | Los límites se evaden entre réplicas y una carga de IPs puede consumir memoria; un upstream lento puede agotar hilos. |
| P1 | Los endpoints de fotos marcan `Cache-Control: private, max-age=30 días`; el frontend retiene blobs en `mediaBlobCache` y datos en React Query. La salida de pareja no limpia esos cachés. | Tras desvincularse, el navegador puede seguir mostrando fotos/datos ya descargados; la revocación no elimina copias locales previas. Documentar límites y reducir cacheo sensible. |
| P1 | `PhotoStorage` persiste foto y miniatura Base64 en PostgreSQL, sin cuota por pareja ni límites de concurrencia de procesado. | Costos, saturación de disco/memoria y abuso de uploads al escalar. |
| P1 | No hay página de privacidad, términos, política de retención, exportación/supresión de cuenta ni canal publicado para derechos sobre datos. `CoupleService.leave()` conserva el histórico. | No hay información suficiente para que una persona comprenda el tratamiento de sus fotos y contenido compartido o ejerza sus derechos. |
| P1 | `LoginPage` y `InvitePage` hablan de registrarse, pero no existe flujo de registro en este frontend ni endpoint de registro en `CentralAuthClient`. | Una pareja nueva no puede completar el onboarding por sí sola. |

### Aspectos positivos a preservar

JWT firmado con clave pública y comprobación de `issuer`/audiencia; access token en memoria y refresh en cookie `HttpOnly; Secure; SameSite=Lax`; invitaciones con token aleatorio de 32 bytes y hash SHA-256; límite de tamaño/dimensiones de imagen; roles `ADMIN` en catálogos globales; errores 404 para varias búsquedas; headers CSP/HSTS configurados en Caddy. **Son defensas parciales** y deben conservarse mientras se corrigen los límites descritos.

## 2. Contrato de seguridad que debe cumplir el resultado

1. La identidad sale exclusivamente de un access token validado; pareja y membresía activa se consultan en DB. `ADMIN` no autoriza leer contenido privado. Usuario sin pareja solo puede gestionar onboarding, perfil y sesión.
2. Una pareja tiene uno o dos miembros activos. Contenido compartido: ambos pueden crear/editar/borrar según reglas visibles. Reseñas y comentarios personales: solo su autor puede editarlos o borrarlos. El resultado ajeno se responde como 404 sin enumerar IDs.
3. Cada recurso privado, hijo, foto, resumen, exportación, búsqueda, caché y trabajo asíncrono lleva contexto de pareja. La protección se aplica tanto en repositorio/servicio como en PostgreSQL con un rol sin `BYPASSRLS`.
4. `couple_id` de un hijo debe coincidir con el de su agregado padre mediante invariantes de servicio y, donde sea viable, constraints compuestas. La desvinculación invalida nuevas lecturas, sesiones/cachés de la app y enlaces de invitación; la retención histórica tiene una política explícita.
5. TLS para tráfico público y conexiones internas que crucen redes no confiables; cifrado de volúmenes/backups y claves separadas/rotables. No prometer cifrado de extremo a extremo sin diseñarlo e implementarlo como tal.
6. Políticas públicas separadas para privacidad y términos, visibles antes del registro, coherentes con el sistema real; aceptación/versionado cuando corresponda. Decisiones legales finales revisadas por una persona competente.

## 3. Instrucciones para ejecutar este roadmap

- Trabajar **solo** en `CoupleExpantion` de cada repositorio. Cada `Cxx` de abajo es **un commit atómico** en el repo indicado, con mensaje sugerido, pruebas y documentación del mismo cambio. No crear un mega-commit ni desplegar la rama por el mero hecho de pushearla. El commit que agrega este roadmap no cuenta como implementación.
- Antes de cada `Cxx`: `git fetch`, comprobar rama/estado, leer código actual y verificar si el hallazgo sigue vigente. Registrar evidencia de pruebas y decisiones en el commit o PR. No sobrescribir el stash PWA existente ni traer `main` a ciegas.
- API/DB/frontend: seguir expansión → compatibilidad → migración/backfill → contracción. **No modificar V45 si ya fue ejecutada**. Para una instalación donde V45 aún no corrió, comprobar datos/usuarios antes; si V45 falla, resolver su precondición y documentar la estrategia antes de desplegar. Las nuevas migraciones empiezan en V46 o versión libre posterior.
- La aplicación central de autenticación queda fuera de estos repositorios. Solicitar su contrato, pruebas y responsable. Un `Cxx` dependiente de ese servicio no se da por terminado con un mock solamente.
- Prioridades: P0 = bloqueo de exposición pública; P1 = obligatorio antes de lanzamiento; P2 = estabilidad/escala y pulido necesarios para crecimiento. `Dep.` indica commits que deben estar integrados antes. Las pruebas negativas son obligatorias: pedir recursos de pareja B con identidad A debe responder 404 o no retornar datos.

## 4. Secuencia de commits

### Fase A: cierre del límite entre parejas

**C01 · Backend · P0 · `fix(db): run application without RLS bypass`**
Dep.: ninguna. Separar rol de migraciones/propietario y rol runtime `NOSUPERUSER NOBYPASSRLS`, privilegios mínimos por tabla/secuencia; `compose.yml` y procedimiento de provisión sin credenciales superusuario en la app. Fallar al iniciar o en health de seguridad si el runtime tiene `rolsuper`/`rolbypassrls`; probar `current_user`, `row_security_active()` y reutilización de conexión con dos parejas. Definir cómo migrar credenciales en un volumen PostgreSQL ya existente y cómo revertir sin exponer datos. La infraestructura externa debe verificar el rol efectivo, no inferirlo del YAML.

**C02 · Backend · P0 · `test(tenant): prove PostgreSQL isolation across two couples`**
Dep.: C01. Añadir Testcontainers/PostgreSQL, Flyway, rol runtime real y fixtures A/B con usuario sin pareja y `ADMIN`. Probar SELECT/INSERT/UPDATE/DELETE directos para todas las tablas privadas de V45; fotos, reseñas, nested, agregados de calendario y consultas por ID; test de pool con conexiones reutilizadas y de ausencia de `CoupleContext`. Una lectura cruzada retorna vacío y una escritura cruzada falla; un admin no ve A/B por ser admin. Si los tests muestran fugas, corregirlas en este mismo commit.

**C03 · Backend · P0 · `refactor(tenant): scope private repositories explicitly`**
Dep.: C02. Añadir `coupleId` a los modelos privados y sustituir las consultas privadas globales de `Repositories.java` por `findByIdAndCoupleId`, listas/páginas por pareja y búsquedas de hijos por padre+pareja. Revisar `Api`, `FilmApi`, `HomeRecipeApi`, `WhyFunApi`, `WhyFunActivityApi`, `SpecialDateApi`, `WhenDatesApi` y todos los endpoints de media. Nada privado debe invocar `findAll()`/`findById()` sin scope, ni filtrar en memoria después de leer globalmente. Mantener RLS como segunda defensa. Tests de API A/B por módulo.

**C04 · Backend · P0 · `refactor(authz): centralize couple and author permissions`**
Dep.: C03. Mover autorización y transacciones de los controladores a servicios. Matriz de permisos en un documento/test: miembros activos para contenido compartido, autor para reseña/comentario propio, `ADMIN` solo catálogos globales. Verificar relación padre-hijo y `couple_id` antes de mutaciones, borrados y media; errores 404 para recursos de otra pareja. Probar endpoints con IDs de A/B, usuario sin pareja y admin.

**C05 · Backend · P0 · `fix(identity): bind legacy users by central UUID`**
Dep.: C01. Eliminar el fallback `findByUsernameIgnoreCase` para apropiar una fila histórica. Migrar las identidades Tomás/Avril con UUID verificados del emisor central mediante procedimiento auditado; fallar si faltan, se duplican o contradicen. Nuevos usuarios se crean solo por subject UUID; los nombres pasan a ser mutables y no identificadores. Probar intento de registro con `tomas`/`avril` y que no herede rol, membresía ni datos. Documentar preflight de V45/backfill y recuperación en instalaciones ya migradas.

**C06 · Backend · P0 · `fix(db): enforce same-couple relationships and membership invariants`**
Dep.: C03, C05. Nueva migración aditiva con constraints/índices compuestos `couple_id` + padre donde aplique; validar/backfillear primero registros existentes. Mantener único miembro activo por usuario y dos slots; tests concurrentes de creación/aceptación que impidan tercer miembro e invitaciones duplicadas. Aclarar `ACTIVE/PENDING/CLOSED` al salir uno o ambos; asegurar que histórico no se reasigne accidentalmente al crear otra pareja.

### Fase B: identidad, sesión e invitaciones

**C07 · Backend · P0 · `fix(jwt): require access token contract and fail closed`**
Dep.: C05. Hacer obligatorios `issuer`, `audience`, `sub` UUID, `exp`, `nbf` según contrato del emisor y claim inequívoca `token_type=access`; validar algoritmo/clave permitidos, reloj y rotación de clave. No aceptar refresh token como bearer. Definir TTL máximo y respuesta 401 consistente; distinguir token inválido de caída de DB/auth para observabilidad. Tests de issuer/audiencia/tipo/exp/nbf/firma/subject incorrectos y rotación. El emisor central debe producir ese contrato.

**C08 · Backend · P1 · `fix(auth): harden refresh cookie and revocation`**
Dep.: C07. Contrato de refresh rotatorio y revocable con el servicio central; invalidación en logout/cambio de contraseña/deshabilitación, detección de reutilización y TTL explícito. Eliminar compatibilidad de refresh por body si ya no hay clientes legítimos; cookie `HttpOnly; Secure; SameSite` y `Path` mínimo. Proteger las mutaciones que usan cookie con validación Origin/CSRF acorde al despliegue y probar solicitudes de origen ajeno; `Cache-Control: no-store` también en respuestas de error auth. No almacenar refresh en JS.

**C09 · Backend · P1 · `fix(invites): make invitation flows bounded and auditable`**
Dep.: C04, C07. Acotar creación/aceptación/revocación por membresía; proteger contra replay, expiración, carrera y enumeración; rechazar valores demasiado largos antes de hash. Limitar número de invitaciones y longitud del path, no registrar tokens ni URL completa, añadir eventos auditables sin secretos. Tests concurrentes de aceptación, revocación y salida. Evitar mensajes que revelen identidad de otra pareja.

**C10 · Backend · P1 · `fix(abuse): distribute rate limits and time out auth upstream`**
Dep.: C07. Sustituir mapa local ilimitado por límite compartido en edge/Redis o almacén acotado con expiración; cuotas por IP y cuenta/token sin confiar ciegamente en `X-Forwarded-For`. Login, refresh, registro, invitación, TMDB y media/upload con límites diferenciados y `Retry-After`. Configurar connect/read timeout de `CentralAuthClient`, máximo de concurrencia y circuit breaker solo donde tenga sentido. Probar ráfagas, varias instancias y IPs cambiantes.

**C11 · Backend · P1 · `feat(auth): expose public registration contract`**
Dep.: C07, C08. Integrar el registro seguro del servicio central: verificación de correo si se usa, reglas de nombres, recuperación de contraseña, estado de cuenta y baja. El servicio central debe implementar y probar los endpoints/flujo en su **propio** repositorio; registrar aquí su versión/contrato y test de integración, sin duplicar contraseñas en WhatPlan. `AUTH_DEFAULT_ROLE` siempre `USER` para autocreación; alta `ADMIN` solo por operación explícita y auditada. No marcar completo hasta verificar el servicio real.

**C12 · Frontend · P1 · `feat(onboarding): complete register and invite journey`**
Dep.: C09, C11. Añadir registro y recuperación de acceso, conservar invitación a través de login/registro, crear pareja si no existe, aceptar una vez y presentar errores/expiración. Ocultar el token tras aceptación y evitar enviarlo a telemetría; revisar clipboard (mostrar éxito solo si `writeText` concluye). Tests de invitación expirada, usuario con otra pareja, cuenta nueva y doble clic; navegación por teclado y lector de pantalla.

**C13 · Frontend · P1 · `fix(session): clear private state on membership change`**
Dep.: C08, C12. Vaciar React Query, blobs/object URLs y estado de vistas en logout, cambio de cuenta, aceptar invitación y dejar pareja. Coordinar varias pestañas sin persistir access/refresh tokens en storage. Mostrar 401/403/404/429 sin datos previos; tests de usuario A → salida → B en la misma pestaña y después de recarga. Backend: en el commit correspondiente de C08/C04, no permitir nuevas lecturas tras salida.

### Fase C: datos, cifrado y operación

**C14 · Backend · P1 · `fix(media): revoke cached private media on membership loss`**
Dep.: C04, C13. Revisar todos los endpoints de fotos; autorización por pareja antes de recuperar bytes, headers `no-store` o TTL muy corto para contenido privado, `Vary` apropiado y no CDN público. Planificar la invalidez práctica de imágenes ya cacheadas por 30 días (no se pueden borrar remotamente): versionar URL/cache key y explicarlo en política. Tests GET cruzado/tras salida y cabeceras. Mantener imágenes públicas de TMDB separadas.

**C15 · Backend · P1 · `feat(media): move photos to private object storage`**
Dep.: C03, C14. Migración gradual de Base64 a almacenamiento privado con clave `couple_id/recurso/id`, referencias sin URL pública, cifrado en reposo y claves administradas. Lectura autenticada o URL firmada corta, sin permitir acceso por key elegida por usuario. Backfill idempotente con conteos/checksums, lectura dual durante transición, rollback y borrado de Base64 solo después de verificar backups/restauración. Tests de A/B, delete, migración y fallo de storage.

**C16 · Backend · P1 · `fix(media): enforce quotas and safe processing`**
Dep.: C15. Cuotas de bytes, conteo y tasa por pareja; límites de concurrencia, memoria/CPU/tiempo de decodificación y procesos externos; rechazar formatos no permitidos, eliminar metadatos sensibles, comprobar tipo por contenido. Limitar tamaño de respuesta, número de fotos, y probar bombas de descompresión/archivos truncados/carreras. Métricas por pareja sin exponer identificadores en etiquetas de alta cardinalidad.

**C17 · Backend · P1 · `fix(deploy): supply validated runtime configuration`**
Dep.: C01, C07. Pasar issuer/audience/tipo de token y demás variables requeridas en `compose.yml`; validar secretos, URL del auth service, DB y modo de cookie al iniciar. Conectar backend al network correcto del auth service, controlar puertos y no exponer PostgreSQL. TLS en proxy público y, según topología, DB/auth internos; no confiar en `X-Forwarded-*` sin lista de proxies. Smoke test en entorno efímero con configuración parecida a producción, sin secretos en logs/CI.

**C18 · Frontend · P1 · `fix(edge): verify TLS, CSP and third-party assets`**
Dep.: C17. Probar encabezados en el dominio público real, no solo `Caddyfile :80`: HTTPS/HSTS, CSP, `frame-ancestors`, `X-Content-Type-Options`, referrer y caché. La CSP actual permite `img-src https:` pero bloquea la hoja de Google Fonts declarada en `index.html`; autoalojar fuentes o autorizar orígenes concretos. Ajustar CSP mínima para flujos reales sin `unsafe-inline` si es factible, y escanear enlaces/recursos externos que reciben referrer. Tests de cabeceras y smoke de navegador.

**C19 · Backend · P1 · `feat(data): export, deletion and retention workflow`**
Dep.: C04, C15. Decidir y documentar ciclo de vida: cuenta, pareja, contenido compartido, reseñas personales, pareja cerrada, backups y auditoría. Exportación autenticada acotada a pareja/autor; acceso/corrección/supresión con comprobación de identidad y trazabilidad. Resolver explícitamente conflictos entre derechos de dos miembros y contenido conjunto antes de implementar borrado; soft delete y ventana de recuperación cuando proceda. Coordinar baja con auth central y object storage; tests de salida, cuenta borrada, pareja cerrada y restauración.

**C20 · Backend · P1 · `ops: backup, restore and incident readiness`**
Dep.: C15, C17. Infra/documentación versionada: backups cifrados PostgreSQL + objetos, PITR si se define, retención/ubicación, control de acceso, rotación de secretos/llaves, prueba periódica de restauración y objetivos RPO/RTO. Logs de auditoría estructurados para login administrativo, invitación y cambios de membresía sin tokens/fotos. Alertas de 5xx, 401/429 anómalos, errores de Flyway, capacidad DB/storage; playbook de incidente y contacto de seguridad. Demostrar restauración de una pareja de prueba en entorno aislado.

**C21 · Backend · P2 · `perf(api): paginate private queries in PostgreSQL`**
Dep.: C03. Sustituir `findAll().stream()` y paginación en memoria de lugares, películas, recetas, visitas y calendario por filtros/indexes con `couple_id` en SQL; límites máximos y cursor estable. Probar resultados A/B y carga con volumen representativo, incluyendo agregados/media. No usar un `findAll()` administrativo para contenido privado.

**C22 · Backend · P2 · `fix(api): standardize problem responses`**
Dep.: C04, C10. Usar `ProblemDetail`/`application/problem+json` (RFC 9457) para filtro de seguridad, rate limit, validación, 404 y errores de negocio; no devolver stack traces, claves, SQL ni datos de otra pareja. Mantener códigos de error estables para frontend y `requestId` sin PII. Tests por familia de error y ausencia de enumeración.

### Fase D: privacidad, términos y experiencia pública

**C23 · Backend · P1 · `docs(privacy): publish reviewed privacy and data-use policy`**
Dep.: C19, C20; revisión legal externa. Crear documento versionado de Política de Privacidad separado de Términos, en español claro. Completar datos reales del responsable y domicilio/contacto, categorías de datos (identidad, contenido, fotos, logs), fines/base de tratamiento, acceso del otro miembro, auth/TMDB/hosting/storage/proveedores, transferencias internacionales, medidas de seguridad sin prometer más de lo real, cookies esenciales y cualquier analítica, plazos de conservación por categoría incluidos backups y pareja cerrada, derechos/canal/plazos y cambios de política. No inventar datos legales ni publicar placeholders. Revisar Ley 25.326, guía AAIP, registro de bases y jurisdicciones de lanzamiento con asesoría competente. Registrar fecha, versión y aprobador.

**C24 · Backend · P1 · `docs(terms): publish reviewed terms for shared spaces`**
Dep.: C19, C23; revisión legal externa. Términos independientes: quién puede usar el servicio/edad, regla de dos miembros, invitación de un solo uso, visibilidad y facultad de edición/borrado mutuo, titularidad/licencia técnica del contenido, salida/ruptura y destino del histórico, bajas/exportación, contenido prohibido y denuncias, terceros/TMDB, soporte, cambios/suspensión y contacto. Resolver con producto/legal qué ocurre cuando ambos quieren destinos distintos para las fotos compartidas. Evitar promesas de confidencialidad absoluta o exenciones genéricas sin revisión.

**C25 · Frontend · P1 · `feat(legal): surface policies and record acceptance`**
Dep.: C12, C23, C24. Enlaces permanentes y accesibles desde landing, login/registro, invitaciones y cuenta; presentar aviso de privacidad antes de recoger datos. Mostrar versión/fecha, registrar aceptación de términos cuando corresponda en backend/auth central con timestamp/version/usuario (no solo `localStorage`), permitir consultar versiones previas. Si se añaden cookies no esenciales, definir mecanismo de consentimiento según jurisdicción; la cookie esencial de sesión no implica automáticamente un banner. Tests mobile, teclado, lector de pantalla y enlaces sin sesión.

**C26 · Frontend · P1 · `fix(ux): explain pair permissions and account lifecycle`**
Dep.: C13, C19, C25. Onboarding, dashboard y acciones destructivas deben explicar quién ve/edita cada cosa, que el otro miembro conserva acceso al contenido compartido al salir uno, qué pasa al cerrar la pareja, y cómo exportar/borrar datos. Sustituir `Home.TOMAS/AVRIL` por IDs de miembro/espacio migrados en backend y controles dinámicos; probar una pareja nueva y un miembro pendiente. Estados vacíos, offline, errores, texto largo/zoom 200 %, pantallas pequeñas y navegación por teclado.

**C27 · Frontend · P2 · `test(ui): cover critical public flows and accessibility`**
Dep.: C12, C25, C26. Tests de login/registro, invitación, dos parejas, salida, consentimiento, media protegida y 401/429; axe/teclado y responsive en login, dashboard, invitaciones y políticas. Evitar capturas con tokens reales. Ejecutar detector Impeccable al finalizar cambios de interfaz y documentar falsos positivos comprobados.

### Fase E: puerta de lanzamiento

**C28 · Backend · P1 · `ci: gate CoupleExpantion with security integration suite`**
Dep.: C02–C22. CI en pushes/PR de `CoupleExpantion`: Java 21, Maven verify + Testcontainers PostgreSQL, Flyway validate, matriz A/B, análisis de dependencias/secretos y SBOM; build sin auto-deploy. Fijar permisos mínimos de Actions e imágenes/versiones, política de dependencias y gestión de hallazgos. Separar CI de publicación de `main`; fallo de pruebas de aislamiento bloquea merge y release.

**C29 · Frontend · P1 · `ci: gate CoupleExpantion frontend and PWA`**
Dep.: C27. CI en `CoupleExpantion`: `npm ci`, lint, test, build, auditoría de dependencias con umbral definido y E2E crítico; sin auto-deploy. Verificar PWA sin caché de respuestas API privadas y no enviar access token a orígenes ajenos en futuros usos de `fetchMedia`. Comparar antes de fusionar los fixes PWA ya presentes en `main` con el estado de la rama.

**C30 · Backend · P1 · `docs(release): sign off public-launch checklist`**
Dep.: C01–C29 y verificaciones externas. Registrar evidencia de: dos parejas simultáneas y cero lectura/escritura cruzada (incluyendo admin/media/export), auth central y registro reales, políticas aprobadas/publicadas, backups restaurables, TLS/headers reales, retención/supresión, rate limits multi-instancia, carga/quotas, monitoreo e incidentes, revisión de secretos/roles, prueba de migración del histórico. Documentar plan de despliegue escalonado, rollback y responsable de cada decisión. **No marcar este commit como completo mediante checkboxes sin evidencias reproducibles.**

## 5. Decisiones externas pendientes antes de C19/C23/C24

1. Identidad legal, domicilio y contacto del responsable; jurisdicciones/edad mínima y si habrá usuarios de UE/otras regiones.
2. Operador y contrato del servicio central de autenticación; alta, verificación, recuperación, baja y revocación. Comprobar si el emisor entrega `aud`, `token_type` y `nbf` según el contrato C07.
3. Proveedores y países efectivos de hosting, backup, object storage, logs, correo y analítica; base/contratos de transferencias donde correspondan.
4. Política de propiedad, retención y eliminación del contenido compartido tras desvinculación; plazo para pareja cerrada, backups y reseñas personales.
5. Política de soporte, reportes de abuso/seguridad, respuesta a incidentes, RPO/RTO, capacidad/planes de costo.

## 6. Criterio de aceptación final

Dos parejas distintas y un usuario sin pareja pueden usar WhatPlan simultáneamente. Ninguna consulta, escritura, búsqueda, foto, miniatura, reseña, comentario, exportación, caché, log o respuesta de media permite a una identidad observar o alterar datos de otra pareja. Los permisos de autor se cumplen dentro de la pareja. El rol runtime PostgreSQL no puede eludir RLS y los tests de integración lo demuestran. Registro, sesión, salida, exportación y baja funcionan de extremo a extremo. Privacidad y Términos están publicados y revisados, con contacto y retención reales. Una restauración de backup y el procedimiento de incidente han sido probados. Solo entonces decidir el lanzamiento público.

## Fuentes normativas/técnicas para verificar al implementar

- PostgreSQL, RLS y privilegios: https://www.postgresql.org/docs/16/ddl-rowsecurity.html
- Imagen oficial PostgreSQL, `POSTGRES_USER`: https://hub.docker.com/_/postgres
- Ley argentina 25.326, texto actualizado: https://www.argentina.gob.ar/normativa/nacional/64790/actualizacion
- AAIP, contenido de una política de privacidad: https://www.argentina.gob.ar/noticias/politicas-de-privacidad-la-aaip-comparte-informacion-clave-para-la-ciudadania
- AAIP, obligaciones y registro de bases: https://www.argentina.gob.ar/node/53779
- AAIP, derechos de las personas titulares: https://www.argentina.gob.ar/aaip/datospersonales/derechos
- AAIP, transferencias internacionales: https://www.argentina.gob.ar/transferencias-internacionales

Estas fuentes describen el marco general a la fecha de auditoría; las obligaciones concretas dependen de la operación real y deben revisarse antes de publicar políticas o abrir el servicio.
