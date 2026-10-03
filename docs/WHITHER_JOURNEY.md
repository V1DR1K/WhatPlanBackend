# Whither Journey y contexto de ubicación

## Comportamiento

La ciudad de origen se guarda en `couples.origin_city_id`. Rosario (`zones.id = 1`, Argentina) es el origen inicial de parejas existentes y nuevas. Cualquier integrante activo puede cambiarla; esa operación no actualiza fichas ni experiencias históricas.

El filtro ofrece origen, todas las ciudades y etapas no archivadas. Seleccionar una etapa filtra los catálogos por **ciudad**, y propone esa etapa en los formularios. Los viajes repetidos a una ciudad comparten el catálogo y mantienen puntos, movimientos y archivos independientes. Las etiquetas incluyen ciudad, viaje y fechas; si coinciden, añaden un identificador breve.

Los restaurantes y actividades tienen ciudad física. Películas y recetas se recuperan por su ciudad inicial o por asociaciones con experiencias/puntos de viaje. Cada experiencia guarda `city_id` y una referencia opcional a etapa. Las ocurrencias de WhenDates pueden vincularse directamente a una etapa, incluso futuras y sin experiencias.

## APIs

Todas las rutas usan el prefijo `/api`. El contexto privado se resuelve desde la identidad autenticada; no se acepta un identificador de pareja enviado por el cliente.

| Rutas | Uso |
|---|---|
| `GET /cities/countries` | Países ISO, con nombres en español. |
| `GET /cities?countryCode=AR&search=Ros` | Sugerencias de ciudades guardadas, hasta 100 coincidencias. |
| `POST /cities`, `GET /cities/{id}` | Resolver una ciudad por país/nombre o identificarla. |
| `GET /location-context`, `PUT /location-context/origin` | Origen compartido y opciones del filtro; incluye límite de archivos. |
| `GET/POST /whither-journey`, `GET/PUT/DELETE /whither-journey/{id}` | Catálogo paginado y viaje con sus etapas. |
| `PUT /whither-journey/{id}/archive` | Conservar historial y retirar etapas del filtro. |
| `POST/PUT /whither-journey/{id}/{points,stays,packing,movements}` | Recursos de organización; para actualizar, agregar el UUID del recurso. |
| `PUT /whither-journey/{id}/points/order` | Ordenar una lista de UUID de puntos del viaje. |
| `DELETE /whither-journey/{points,stays,packing,movements,files}/{id}` | Borrar recursos sin dependencias; reubicar contenido asociado antes de borrar. |
| `PUT /whither-journey/{id}/reviews/me` | Reseña propia del viaje o alojamiento, por integrante. |
| `POST /whither-journey/{id}/dates` | Crear o seleccionar una fecha importante y vincular su ocurrencia. |
| `POST /whither-journey/{id}/files` | Multipart con parte `file` y referencias opcionales a etapa/punto/estadía/movimiento; `hotelPhoto=true` asigna la foto. |
| `GET /whither-journey/files/{id}/content` | Original autenticado; `download=true` entrega como descarga. |
| `PUT /whither-journey/files/{id}/links` | Reubicar vinculaciones conservando los bytes originales. |
| `GET /whither-journey/catalog/{FOOD,FILM,COOK,FUN}` | Buscar fichas por `cityId` y `search`. |
| `GET /whither-journey/experiences/{section}/{entityId}` | Experiencias, con `from`, `to` y `page`; páginas de 100. |
| `GET/PUT /whither-journey/experiences/{section}/{entityId}/{experienceId}/location` | Consultar/cambiar ciudad, etapa y punto de una experiencia. |
| `PUT /when-dates/special-dates/{id}/occurrences/{date}/location` | Ubicación directa de una ocurrencia. |

Los listados anteriores mantienen `zoneId` y aceptan `cityId`. Si ambos están presentes y difieren, responden 400. Los formularios existentes admiten `cityId`, `stageId` y `pointId` en experiencias; las altas de ficha admiten `stageId` para crear su punto pendiente. Las respuestas de experiencias incluyen su ubicación histórica.

## Integridad y dinero

V64 mantiene los identificadores de zonas e incorpora país; asigna todos los catálogos y experiencias anteriores a Rosario sin crear viajes ni movimientos. Las tablas nuevas usan UUID, RLS forzada y referencias compuestas por pareja/viaje. También se comprueba que una experiencia corresponda a su ficha y que el integrante pertenezca a la pareja.

Las escrituras son transaccionales. Registrar una experiencia y completar su punto se confirma en la misma transacción. Editar una experiencia conserva el punto y los movimientos; un vínculo inválido revierte también la experiencia y los resúmenes derivados. Eliminar una experiencia devuelve su punto a pendiente conservando archivos y pagos.

Las fechas de etapas y contenido deben pertenecer al viaje. Etapas pueden compartir días y repetir ciudades. Cambios que invaliden fechas/ciudades o contenido asociado responden 409 y requieren reubicación explícita. Cancelar puntos conserva pagos. Archivar conserva el historial; la eliminación definitiva exige que el viaje no tenga contenido asociado.

El registro único `journey_movements` usa `NUMERIC(18,4)` / `BigDecimal`, con importe positivo y código ISO de moneda. El saldo es fondos menos gastos más reintegros, separado por moneda; no hay conversiones. Precio de alojamiento y pagos son independientes. En el JSON del módulo, importes y saldos se entregan como **cadenas decimales** y los formularios los envían sin convertirlos a punto flotante, para conservar precisión al editar. Los saldos se calculan en el backend y se desglosan por etapa.

## Archivos y configuración

Se conservan los originales en `BYTEA`; los listados consultan metadatos sin recuperar su contenido. Se admiten PDF, JPEG, PNG y WebP mediante firma de bytes, hasta 10 MiB inicialmente. `JOURNEY_MAX_UPLOAD_BYTES` modifica el límite del módulo; la configuración multipart adapta el sobre de la solicitud a los límites de viaje/imágenes y conserva el resto de propiedades del servidor. El proxy de entrada debe permitir el tamaño configurado más el encabezado multipart.

Los originales se entregan con autenticación, `Cache-Control: no-store`, `Vary: Authorization, Cookie`, `nosniff` y nombre sanitizado. El frontend obtiene un blob autenticado, libera su URL al cerrar y usa el visor actual de imágenes o controles de página/zoom/descarga del navegador para PDF.

## Publicación y compatibilidad

1. Respaldar la base y publicar este backend desde `WhatPlanBackend-login-fix`, con V64. Mantener roles separados de migración y ejecución según `infra/postgres-runtime-role.md`; los permisos por defecto deben incluir las nuevas tablas y secuencias. V64 relaja temporalmente FORCE RLS de los cuatro catálogos dentro de su transacción para efectuar el backfill con el rol propietario sin BYPASSRLS, y la restaura antes de finalizar.
2. Confirmar versión de Flyway, acceso bajo Rosario, fotos/reseñas intactas y compatibilidad de `zoneId` con el frontend anterior.
3. Publicar `WhatPlanFrontend` actualizado. Verificar origen compartido, viaje con dos destinos, experiencia vinculada, archivos y saldos por moneda.
4. Conservar las APIs de zonas y el alias durante la comprobación del nuevo flujo. No ejecutar migraciones descendentes para retirar la interfaz: preservar el backend compatible y los datos nuevos.

La implementación local no publica ni modifica entornos remotos y no toca la otra copia `WhatPlanBackend`.

## Verificación

Resultado local del 3 de octubre de 2026: 233 pruebas de backend aprobadas, 36 pruebas unitarias de frontend y 31 recorridos de navegador aprobados entre las ejecuciones completas y las verificaciones finales específicas. El frontend compila y lint no informa errores (22 advertencias). Tres integraciones anteriores dependientes de Docker quedaron excluidas porque Docker Desktop no pudo iniciar en este equipo; las nuevas integraciones de migración y viajes se ejecutaron con PostgreSQL real aislado, detenido al finalizar.

Pruebas de servicios, contratos y repositorios más PostgreSQL real cubren migración restringida, conservación de fotos/reseñas, separación por pareja, claves foráneas, cambios de origen sin reasignar historia, viajes repetidos, varios países con traslados el mismo día, reutilización de fichas, operación atómica de experiencias, archivos originales, cancelaciones y saldos. También verifican fechas futuras directas de WhenDates, archivado y selección de experiencias antiguas por páginas.

Normalmente las integraciones nuevas usan Testcontainers. Para ejecutarlas sin Docker, se admite `-Djourney.test.jdbc-url=jdbc:postgresql://127.0.0.1:PUERTO/postgres` en una instancia **exclusiva de pruebas**, con usuario de test `journey_test`: cada clase crea una base UUID separada y un usuario de ejecución NOSUPERUSER/NOBYPASSRLS. No utilizar una base de producción. Las tres integraciones anteriores que requieren Docker/Redis conservan su configuración original.

Las pruebas de navegador de `WhatPlanFrontend/e2e/journey.spec.ts` usan respuestas simuladas del contrato para el flujo de creación/organización en 1440 y 390 px, archivos, gastos, valijas, teclado, movimiento reducido y protección de borradores durante guardado. Se complementan con integraciones reales del backend; el recorrido de navegador no se presenta como un ensayo contra producción.
