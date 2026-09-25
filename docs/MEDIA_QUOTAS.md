# Cuotas de fotos por pareja

WhatPlan aplica las cuotas en PostgreSQL mediante triggers para que rigen igual en todos los módulos y también ante cargas simultáneas. El valor pertenece a `couples`, no se acepta desde una solicitud HTTP y no se expone en los DTO públicos.

## Límites iniciales

- 512 MiB de contenido Base64 por pareja (`media_quota_bytes`).
- 2.000 fotos por pareja (`media_quota_photos`).
- Hasta 40 solicitudes de carga por pareja y hora, además del límite de 20 por usuario y hora.
- Cada foto consume el tamaño en bytes de sus campos `image_base64` y `thumbnail_base64`.

Estos son valores iniciales operativos, no límites de producto inmutables. Se pueden elevar o reducir por pareja con una operación administrativa de base de datos. La restricción impide configurar una cuota por debajo del uso actual; para reducirla, primero hay que borrar suficiente contenido. La app no ofrece todavía una interfaz de facturación o autoservicio para cambiar estos límites.

El total contabilizado es el contenido Base64 lógico, no el espacio físico exacto de PostgreSQL: no incluye cabeceras de filas, índices ni sobrecarga TOAST. Es adecuado como control de volumen de la aplicación, pero no reemplaza métricas de almacenamiento de la base.

## Cobertura y consistencia

La migración `V49` contabiliza fotos de lugares, ítems, visitas, películas, recetas, recetas del hogar, actividades, visitas de actividades y fechas especiales. Antes de activar triggers, calcula el uso existente. Las parejas que ya superaban los valores iniciales quedan con una cuota igual o superior a su uso actual; no se elimina ni bloquea contenido histórico.

Los triggers insertan, descuentan y recalculan diferencias cuando se reemplaza una imagen. Actualizan el contador y verifican el límite dentro de la misma transacción que modifica la foto. La fila de `couples` serializa las actualizaciones concurrentes de esa pareja. Una foto no puede trasladarse a otra pareja mediante una actualización directa. Redis aplica también una ventana móvil de cargas compartida por pareja, además del tope individual; el identificador se almacena como hash en la llave del rate limiter.

Al alcanzar una cuota, la API devuelve HTTP 413 con `errorCode: MEDIA_QUOTA_EXCEEDED` y un detalle genérico. El fallo revierte la escritura y no revela el SQL ni datos de la pareja.

## Operación y evolución

Las cuotas actuales controlan las columnas Base64 guardadas en PostgreSQL. Al migrar a almacenamiento privado de objetos (roadmap C15), habrá que sustituir el cálculo de bytes por el tamaño real de los objetos y contemplar cargas pendientes, reintentos, borrados diferidos y reconciliación; los triggers de `V49` no medirán objetos externos. Mantener temporalmente la cuota de cantidad como defensa adicional puede ser conveniente.

La cuenta de servicio necesita permiso para actualizar las columnas de contabilidad de `couples`, además de los permisos de escritura en las tablas de fotos. No se debe otorgar a usuarios finales acceso directo a esas columnas ni confiar en valores de cuota recibidos del cliente.
