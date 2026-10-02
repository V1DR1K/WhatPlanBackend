# Inventario interno de datos de WhatPlan

Estado: inventario técnico preliminar, no es una política pública ni asesoramiento legal. Revisado contra el código local de `CoupleExpantion` el 2026-09-25. Debe volver a verificarse antes de publicar y después de incorporar proveedores o cambios de producto.

## Propósito y límites

Este documento ayuda a redactar la política de privacidad, términos y procedimientos de derechos con hechos verificables. No afirma cumplimiento normativo. Los datos legales del operador, proveedores contratados, países de alojamiento, transferencias internacionales y plazos de conservación no se deducen del repositorio y deben ser confirmados por el responsable de WhatPlan.

## Datos tratados observados en el código

| Categoría | Datos que puede contener | Finalidad visible en el producto/código | Alcance |
| --- | --- | --- | --- |
| Cuenta e identidad | ID interno, UUID del proveedor central de identidad, nombre de usuario, rol; el backend conserva un campo heredado de hash de contraseña que el provisionador actual deja nulo | Vincular la sesión central con el usuario local, mostrar autoría y aplicar permisos | Identidad gestionada por el proveedor externo; el backend local mantiene una fila de usuario |
| Pareja e invitación | Estado y fechas de pareja/membresía, nombre visible elegido, slot, autor y estado; invitación con hash SHA-256, expiración, aceptación o revocación y usuario asociado | Compartir el espacio privado entre sus integrantes e impedir reuso de invitaciones | El token de invitación original se devuelve al creador para compartirlo; el repositorio almacena su hash, no el token en claro |
| Contenido de lugares y visitas | Nombre, dirección escrita por usuarios, URL de origen/mapa, categoría, etiquetas, fechas de visita, artículos consumidos, imágenes y reseñas/comentarios | Organizar y recordar lugares y experiencias de la pareja | Espacio privado de la pareja; puede revelar hábitos y ubicaciones introducidas por usuarios |
| Películas | ID TMDB, título, sinopsis, fecha de estreno, conteo de vistas, fecha de visualización, imágenes y reseñas, puntuaciones, personaje favorito y otros atributos del catálogo | Mantener listas y recuerdos de películas compartidos | Contenido de usuario aislado por pareja; metadatos de catálogo proceden de TMDB |
| Recetas y comidas | Nombre, ingredientes, instrucciones, porciones, fecha, tipo de comida, imágenes y reseñas | Guardar y compartir recetas y comidas | Espacio privado de la pareja |
| Actividades y salidas | Nombre, dirección introducida por usuarios, categorías/subcategorías, horarios, fecha, visitas, fotografías y reseñas | Planificar y recordar actividades | Espacio privado de la pareja; una dirección puede ser domicilio u otra ubicación sensible aunque el modelo no la clasifique así |
| Fechas especiales | Fechas, etiquetas, ocurrencias, comentarios, imágenes y autoría | Calendario/recuerdos compartidos | Espacio privado de la pareja; puede incluir eventos personales sensibles |
| Fotografías | Bytes de la imagen y miniatura codificados en Base64, dimensiones y fecha de creación | Mostrar fotos de lugares, visitas, películas, recetas, actividades y fechas | Actualmente almacenadas en PostgreSQL. No se observó almacenamiento externo de fotos en el código revisado |
| Ajustes/catálogos | Tamaño de página global, categorías, etiquetas, géneros y categorías de actividades | Configurar la interfaz y clasificar contenido | Datos compartidos globales; catálogos están destinados a administración, no son contenido de pareja |
| Sesión | Access token JWT de corta duración en la respuesta; refresh token en cookie `HttpOnly`, `Secure`, `SameSite=Lax`, limitada a `/api/auth`; nombre de usuario/rol en datos de respuesta | Autenticación, renovación y cierre de sesión | El frontend mantiene el access token en memoria según el flujo actual; no se debe prometer que esto elimina capturas del navegador o del dispositivo |
| Seguridad/operación | Dirección de cliente usada por rate limiting solo según la configuración de proxy confiable; eventos de auditoría de pareja, IDs internos, acción, fecha/expiración y estado | Limitar abuso e investigar invitaciones/cambios de membresía | Confirmar configuración de proxy, logs, acceso, retención y copias de seguridad en el entorno desplegado |

## Destinatarios y transferencias visibles

- **Proveedor central de autenticación:** el backend remite credenciales al servicio en el endpoint de login, y presenta access token en llamadas de perfil/cambio de contraseña; recibe identidad, tokens, nombre de usuario, estado y requisito de cambio de contraseña. El código y contrato/despliegue de ese servicio no están en estos repositorios. Confirmar su entidad legal, subprocesadores, ubicación, seguridad, retención, registro y mecanismo de eliminación de cuentas.
- **The Movie Database (TMDB):** el backend llama a `api.themoviedb.org` con un token bearer del servidor. Envía una consulta de texto cuando se busca una película, o un ID de TMDB para detalle/recomendaciones, más el idioma configurado en la ruta (`es-AR`). No se observó que envíe IDs de usuario/pareja, reseñas, listas privadas ni el token de acceso de WhatPlan. TMDB entrega metadatos de catálogo; el backend también devuelve enlaces de imágenes bajo `image.tmdb.org`. Confirmar términos/licencia, política aplicable y avisos de atribución del proveedor antes de publicar.
- **Alojamiento y transporte:** `compose.yml` describe contenedores de frontend/backend, PostgreSQL y Redis; la base usa un volumen Docker externo. Esto no identifica de forma fiable al proveedor real, región, cifrado de disco/backups, retención, acceso administrativo o transferencias. Caddy y el dominio/HTTPS dependen de la instalación fuera de este inventario.
- **Distribución de imágenes:** se observó persistencia en PostgreSQL, no un bucket externo ni enlaces firmados. Las imágenes se entregan mediante endpoints autenticados; debe confirmarse la respuesta HTTP, cachés intermedias y protección del volumen/backups en el despliegue real.

## Conservación, acceso y derechos: decisiones pendientes

El repositorio no establece una política completa de conservación. La desvinculación de una pareja conserva histórico en la base; por tanto, no se debe afirmar que salir elimina contenido. Antes de publicar, decidir y documentar:

1. Plazos por tipo de dato (cuenta, contenido, invitaciones, auditoría, logs y backups), disparador de inicio y borrado de copias de respaldo.
2. Flujo autenticado de exportación y eliminación de cuenta/contenido; efectos de borrar una cuenta sobre contenido compartido y sobre la otra persona.
3. Qué ocurre con el contenido y fotos al abandonar/cerrar pareja, y cómo se resuelven solicitudes de supresión cuando el contenido fue creado conjuntamente.
4. Canal verificable para solicitudes de acceso, rectificación, supresión, oposición y consultas; responsable que las atiende, identidad requerida y plazo de respuesta.
5. Base y propósito de cada tratamiento, datos obligatorios/opcionales, edades mínimas y proceso de incidentes.
6. Regiones de alojamiento y respaldo, subprocesadores y transferencias internacionales; controles contractuales y técnicos aplicables.
7. Si el registro está cerrado/invitación-only o abierto. El flujo de registro público no está implementado en el frontend/backend local revisado y el servicio central es externo.

## Reglas de redacción para el aviso público

- Describir el espacio como privado entre miembros de una pareja, con excepciones técnicas/operativas expresamente identificadas; no llamarlo cifrado de extremo a extremo.
- Explicar que ambos miembros pueden ver el contenido compartido y que las reseñas personales conservan autoría; no prometer borrado retroactivo de copias descargadas, capturas o vistas por otra persona.
- Explicar la participación de autenticación central y TMDB, y enlazar sus avisos/términos oficiales tras verificar las URLs y el alcance aplicable.
- No afirmar cifrado en reposo, residencia argentina, eliminación inmediata, anonimización, retención específica, ausencia de acceso del operador ni certificaciones hasta obtener evidencia operacional/contractual.
- Publicar política de privacidad y términos accesibles antes de habilitar registro; versionar el texto y guardar evidencia de aceptación si el asesor legal determina que corresponde.

## Fuentes internas verificadas

- `src/main/java/com/wherefood/auth/AuthApi.java`: login, renovación, cookie, perfil y cambio de contraseña.
- `src/main/java/com/wherefood/auth/LocalUserProvisioner.java` y `src/main/java/com/wherefood/domain/User.java`: vínculo central/local y campos de usuario.
- `src/main/java/com/wherefood/couple/CoupleService.java`, entidades `Couple*` y `docs/INVITATION_SECURITY.md`: membresías, invitaciones y eventos de auditoría.
- Entidades de `src/main/java/com/wherefood/domain/` y DTO/servicios bajo `src/main/java/com/wherefood/web/`: contenido, fotos, direcciones y reseñas.
- `src/main/java/com/wherefood/web/PhotoStorage.java`: procesado y persistencia Base64 en entidades.
- `src/main/java/com/wherefood/web/TmdbClient.java`: solicitudes salientes de búsqueda y catálogo.
- `compose.yml`, `application.yml`, `.env.example`: topología y configuración declarada, no prueba del entorno de producción.

