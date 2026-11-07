# zN Client

zN Client es un launcher independiente para **Minecraft: Java Edition** en Windows. Reúne perfiles, instalaciones, memoria, mods y archivos del juego; además incluye un mod cliente con opciones de personalización dentro de Minecraft.

> **Descargas:** visita el [sitio de zN Client](https://znicoojk.github.io/MC-zN-Client/) o consulta las [versiones publicadas en GitHub](https://github.com/zNicoojk/zN-Client/releases).

## Qué incluye

### Launcher para Windows

- Inicio de sesión con Microsoft y perfiles offline/locales.
- Selección de versiones compatibles y memoria; preparación automática del runtime de Java que necesita cada versión.
- Instalaciones separadas por versión, con bibliotecas y recursos compartidos.
- Secciones para inicio, instalaciones, mods, descargas, registros, configuración y archivos.
- Búsqueda y administración de mods externos por versión, con acceso a su carpeta.
- Consola en vivo, progreso y cancelación de descargas, y acceso a los registros.
- Accesos a mundos, capturas, paquetes de recursos, shaders y configuración.
- Actualizaciones del launcher desde GitHub Releases. Discord Rich Presence es opcional y viene desactivado.

### Mod integrado en el juego

Abre el menú con **Shift derecho**, activa o desactiva módulos y selecciona sus tarjetas para cambiar opciones:

- **Zoom:** ampliación, transición y modo de activación configurables.
- **HUD:** indicadores de FPS, ping y coordenadas. Personaliza posición, tamaño y estilo; arrastra los elementos compatibles para reubicarlos.
- **Nametags:** ajusta etiquetas de jugadores y criaturas, opacidad, fondo, sombra y colores.
- **Lista TAB:** fija o resalta nombres, configura colores y el máximo de jugadores visibles.
- **Viewmodel:** personaliza la posición, escala y rotación de los objetos en primera persona.

La versión actual del launcher es **0.1.6**. Las versiones de Minecraft incluidas y preparadas para el mod son **26.1, 26.2 y 26.3**. El launcher prepara Fabric, Fabric API y el mod específico para la versión elegida. No necesitas instalar el mod en el servidor.

## Requisitos

- Windows 10 u 11 de 64 bits.
- Minecraft: Java Edition para jugar en servidores con autenticación premium y en Realms.
- Los perfiles offline/locales solo funcionan en servidores configurados para aceptar jugadores sin autenticación premium.

El instalador incluye el runtime de Java preparado por el proyecto. El launcher puede descargar automáticamente otro runtime si la versión seleccionada lo necesita. El rendimiento y la memoria disponibles dependen del equipo, de Minecraft y de los mods instalados.

## Privacidad

La contraseña de Microsoft no se almacena. Si eliges guardar una cuenta, su token de renovación se cifra mediante el almacenamiento seguro de Windows; los tokens de acceso se mantienen en la sesión activa. Los perfiles offline solo guardan el nombre y el identificador local. Discord Rich Presence no requiere una contraseña de Discord y no guarda la cuenta.

Consulta la [política de privacidad](https://github.com/zNicoojk/zN-Client/blob/main/PRIVACY.md) y el [código fuente del launcher](https://github.com/zNicoojk/zN-Client).

## Descargas y actualizaciones

El sitio consulta las Releases públicas de [`zNicoojk/zN-Client`](https://github.com/zNicoojk/zN-Client/releases) y muestra el instalador `.exe` de la última versión estable. Los borradores y las versiones preliminares no aparecen como la última versión. El launcher también puede avisar de nuevas versiones publicadas en GitHub.

Para publicar una actualización del launcher, sigue las instrucciones del [repositorio del cliente](https://github.com/zNicoojk/zN-Client). El instalador debe adjuntarse a una Release pública para que los visitantes puedan descargarlo.

### Cómo se publica este sitio

El sitio se aloja desde este repositorio con GitHub Pages. En **Settings → Pages**, selecciona **GitHub Actions** como origen. El workflow de `.github/workflows/deploy-pages.yml` publica los archivos de `site/` al subir cambios a `main` o `master`; los nuevos instaladores se obtienen directamente de las Releases del repositorio del cliente.

## Sobre el proyecto

zN Client es independiente y no está afiliado, aprobado ni patrocinado por Mojang Studios o Microsoft. Minecraft es una marca de Mojang Studios. Revisa la licencia y los avisos del [repositorio del cliente](https://github.com/zNicoojk/zN-Client) antes de redistribuir el proyecto.
