# The House of the Dead 2 — Android Evolved

[Español](#español) · [English](#english) · [APK de la versión 1.0 / Version 1.0 APK](https://github.com/codepdbh/hotd2re-android-evolved/releases/tag/v1.0)

## Español

Port nativo de [HOTD2RE](https://github.com/GitFist/hotd2-re) para Android 10 o posterior, con CPU ARM64 (`arm64-v8a`) y Vulkan. Incluye controles táctiles, pantalla panorámica y cooperativo online. La APK contiene el port. Los datos del juego se importan desde tu copia original de PC.

### Prepara los archivos

Conecta el teléfono por USB en modo **Transferencia de archivos** y crea `h2evolved` en **Memoria interna**. Copia el contenido de la carpeta instalada en tu PC con esta estructura mínima.

```text
Memoria interna/h2evolved/
├── Hod2.exe       (ejecutable original de PC de 32 bits)
├── cam/
├── coli/
├── evt/
├── mot/
├── pol/
├── sound/        (también se acepta Sound/)
└── tex/
```

Copia completas las carpetas indicadas. Si tu instalación contiene `Hod2.ini`, ponlo junto a `Hod2.exe`. Los archivos `naomi_audio.bank`, `naomi_visuals.bank` y `typing_reference.bank` son opcionales si tu instalación de HOTD2RE ya los tiene. El importador crea una segunda copia privada para ejecutar el juego. Reserva espacio para ambas copias.

### Instala y juega

1. Descarga `HOTD2RE-Android-Evolved-v1.0-arm64.apk` en [GitHub Releases](https://github.com/codepdbh/hotd2re-android-evolved/releases/tag/v1.0). Abre la APK y, si Android lo solicita, permite instalar apps desde la aplicación con la que abriste el archivo.
2. Espera a que termine la transferencia por USB. En el lanzador, toca **Importar carpeta del juego → Memoria interna → h2evolved → Usar esta carpeta**. `Hod2.exe` y las carpetas deben estar directamente dentro de `h2evolved`.
3. Cuando aparezca **Listos para jugar**, puedes elegir el idioma del lanzador y el formato de pantalla en **Configuración**. Pulsa **Jugar** para iniciar o **Online** para entrar a las salas.

Para jugar entre dos teléfonos, instala la misma versión en ambos. En el primero abre **Online → Peer to Peer → Host** y deja abierta la sala. En el segundo abre **Online → Peer to Peer → Join** y toca los cinco números del código en el teclado del juego. La conexión empieza al tocar el quinto dígito.

Si ocurre un error, **Configuración → Enviar registros a GitHub** muestra el diagnóstico y abre un borrador de incidencia. Puedes revisarlo y enviarlo desde GitHub.

## English

A native [HOTD2RE](https://github.com/GitFist/hotd2-re) port for Android 10 or later with an ARM64 (`arm64-v8a`) CPU and Vulkan. It includes touch controls, widescreen and online co-op. The APK contains the port. Import the game data from your original PC copy.

### Prepare the files

Connect the phone over USB in **File transfer** mode and create `h2evolved` in **Internal storage**. Copy the contents of the game’s installation folder on your PC using this minimum layout.

```text
Internal storage/h2evolved/
├── Hod2.exe       (original 32-bit PC executable)
├── cam/
├── coli/
├── evt/
├── mot/
├── pol/
├── sound/        (Sound/ is also accepted)
└── tex/
```

Copy each listed folder in full. If your installation has `Hod2.ini`, put it beside `Hod2.exe`. The `naomi_audio.bank`, `naomi_visuals.bank`, and `typing_reference.bank` files are optional if your HOTD2RE setup already has them. The importer makes a second, private copy to run the game. Allow storage for both copies.

### Install and play

1. Download `HOTD2RE-Android-Evolved-v1.0-arm64.apk` from [GitHub Releases](https://github.com/codepdbh/hotd2re-android-evolved/releases/tag/v1.0). Open the APK and, if Android prompts you, allow installs from the app you used to open the file.
2. Wait for the USB transfer to finish. In the launcher, tap **Import game folder → Internal storage → h2evolved → Use this folder**. Put `Hod2.exe` and the data folders directly inside `h2evolved`.
3. Once the launcher says **Ready to play**, choose your launcher language and display format in **Settings**. Tap **Play** to start the game or **Online** to open the lobby.

To play on two phones, install the same release on both. On the first phone, open **Online → Peer to Peer → Host** and keep the room open. On the second phone, open **Online → Peer to Peer → Join** and tap the five digits on the in-game keypad. The connection starts after the fifth digit.

If you run into a problem, **Settings → Send logs to GitHub** shows the diagnostics and opens a draft issue. You can review and submit it on GitHub.

## Desarrollo / Development

Basado en / Based on HOTD2RE `90538bc7798fd2f020bfe117378d8a0e5692bcc4` by GitFist and contributors.

```powershell
./scripts/build_android.ps1 -Game
./scripts/build_android.ps1 -Game -Release
```

La compilación `-Release` genera una APK sin firmar. / The `-Release` build produces an unsigned APK.

- [Guía del port y compilación](docs/ANDROID_GAME_PORT.md)
- [Resultados de pruebas](docs/TEST_RESULTS.md)
- [Licencia / License](LICENSE)
