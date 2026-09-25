# C06: invariantes de pareja y relaciones privadas

V47 conserva todas las filas históricas y agrega índices parciales para el estado vigente: un usuario puede tener una sola membresía `ACTIVE`; una pareja solo puede tener un integrante activo por slot 1/2; el mismo usuario/slot puede aparecer en membresías históricas `LEFT`; y puede existir como máximo una invitación `PENDING` por pareja. Checks validan también las fechas de salida y los campos de aceptación/revocación de invitaciones. Cuando una persona se va, su plaza queda disponible para una invitación nueva. La pareja permanece `ACTIVE` mientras quede alguien con acceso y pasa a `CLOSED` al salir el último; una pareja cerrada no se reactiva. Crear otra pareja genera otro UUID y no reasigna su histórico.

La migración añade claves foráneas compuestas `(padre_id, couple_id)` en contenido privado y relaciones de portada, por lo que PostgreSQL rechaza cruces aunque una ruta de aplicación omita una validación. También incorpora `film_review_metrics` a RLS, derivando su `couple_id` de la reseña padre. Las tablas `film_view_photos` y `cooking_photos` ya no existen desde V32 y no forman parte del esquema actual.

## Preflight V47

Antes de aplicar, hacer backup restaurable, confirmar V46 y revisar:

```sql
-- No pueden existir slots activos repetidos, ni un mismo usuario activo dos veces.
SELECT couple_id, slot, count(*) FROM couple_members WHERE status = 'ACTIVE'
GROUP BY couple_id, slot HAVING count(*) > 1;
SELECT user_id, count(*) FROM couple_members WHERE status = 'ACTIVE'
GROUP BY user_id HAVING count(*) > 1;
-- V47 limita invitaciones pendientes por pareja.
SELECT couple_id, count(*) FROM couple_invitations WHERE status = 'PENDING'
GROUP BY couple_id HAVING count(*) > 1;
-- Estas consultas deben devolver cero filas antes de agregar FKs compuestas.
SELECT v.id FROM place_visits v JOIN places p ON p.id = v.place_id WHERE v.couple_id <> p.couple_id;
SELECT i.id FROM items i JOIN place_visits v ON v.id = i.visit_id WHERE i.couple_id <> v.couple_id;
SELECT o.id FROM special_date_occurrences o JOIN special_dates d ON d.id = o.special_date_id WHERE o.couple_id <> d.couple_id;
```

Revisar del mismo modo relaciones de fotos/portadas, reseñas, recetas, películas y WhyFun. No corregir silenciosamente filas discrepantes: identificar si el dato está mal asignado y resolver con una corrección auditada aprobada. La migración es transaccional y fallará completa si un índice único o FK no puede crearse. Corregido el preflight, reintentar tras confirmar que Flyway registró V47 como fallida y que la base volvió al estado anterior; si la restauración transaccional no puede confirmarse, restaurar backup en entorno aislado antes de reparar.

## Verificación tras aplicar

- Confirmar en `pg_constraint`, `pg_indexes` y `pg_policies` las FKs compuestas, índices parciales y la política RLS de `film_review_metrics`.
- Ejecutar tests PostgreSQL concurrentes de aceptación, creación de pareja e invitaciones; comprobar que nunca aparecen tres miembros activos, dos miembros en el mismo slot, dos parejas activas para un usuario ni dos invitaciones pendientes para una pareja.
- Verificar salida con un miembro restante, salida del último, reutilización del slot histórico y que el contenido conserva el UUID original.
- En una transacción de prueba, insertar un hijo que declare el `couple_id` de B y un padre de A; debe fallar. Probar también una portada de otra ocurrencia/visita.
- No desplegar si las consultas de preflight dejan discrepancias o si las pruebas concurrentes dependen de una sola instancia de aplicación: los índices y bloqueos PostgreSQL son la garantía compartida entre instancias.
