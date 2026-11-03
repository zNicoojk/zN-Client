# zN Client

Primera versión del launcher de Minecraft Java para Windows.

## Primer inicio

1. Inicia sesión con una cuenta Microsoft que tenga Minecraft: Java Edition.
2. Elige una versión y la memoria RAM.
3. Pulsa **Jugar ahora**. El instalador incluye el runtime Java compatible con la última versión de Minecraft; si eliges una versión que necesita otro Java, el launcher lo descarga automáticamente. Minecraft descarga sus archivos la primera vez que inicias cada versión.

Los archivos del juego se guardan en la carpeta de datos de zN Client bajo el perfil de Windows. Cada versión usa su propia carpeta `minecraft/instances/<version>` para mundos, ajustes, registros y demás datos; las bibliotecas y los recursos de instalación se comparten para no descargarlos varias veces. La sesión Microsoft vive en memoria mientras la app está abierta; el launcher no guarda la contraseña ni los tokens de la cuenta.

Los mods que agregues se guardan en `minecraft/instances/<version>/znclient-mods`, separados por versión. Al iniciar una versión compatible, zN Client los copia junto con sus propios mods a una carpeta temporal y la elimina al cerrar el juego; sus JAR no se mezclan con los tuyos. La sección **Mods** muestra solo los archivos de la versión elegida. Al primer uso, los datos antiguos se copian a la versión que estaba guardada, no a cualquier versión que se seleccione después. Los mods antiguos se organizan por las etiquetas de versión de sus nombres o metadatos cuando es posible; la carpeta original se conserva como respaldo.

La sección **Mods** muestra los archivos JAR de esa carpeta, sus nombres y los iconos incluidos por cada mod. Usa **Actualizar lista** después de añadir o quitar archivos.

Este proyecto es independiente y no está afiliado con Mojang ni Microsoft.
