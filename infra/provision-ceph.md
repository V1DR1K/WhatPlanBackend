# Ceph/RGW mononodo

Ceph queda fuera de `compose.yml`: el compose de la aplicación contiene únicamente frontend, backend y PostgreSQL.

1. En un VPS Ubuntu con recursos suficientes, instalar `cephadm`, inicializar un clúster mononodo y habilitar RGW.
2. Crear el usuario S3 y el bucket privado `wherefood`; asignar acceso de lectura/escritura al usuario de la aplicación.
3. Exponer el endpoint RGW solo mediante el proxy HTTPS del VPS o una red privada. Configurar una ruta `/media` que entregue los objetos WebP con `Cache-Control` inmutable.
4. Completar los secretos S3 de `/opt/wherefood/.env`. Las fotografías se escriben con claves aleatorias; las tarjetas cargan la miniatura de 480 px.

Un Ceph mononodo no ofrece redundancia: realizar respaldos externos del volumen de Ceph y de PostgreSQL.
