# The House of the Dead 2 — Android Evolved

Port nativo ARM64 de [HOTD2RE](https://github.com/GitFist/hotd2-re), basado en el commit `90538bc7798fd2f020bfe117378d8a0e5692bcc4` de GitFist y colaboradores. Usa SDL3, Vulkan y los datos de una instalación compatible que el jugador importa desde su teléfono.

Compila la APK desde PowerShell con `./scripts/build_android.ps1 -Game`. En la aplicación, selecciona la carpeta `h2evolved` con los datos del juego, elige el formato de pantalla y pulsa **Jugar**. La aplicación de diagnóstico de SDL3 se compila por separado.

- [Compilar y comprobar con ADB](docs/ANDROID_BUILD.md)
- [Port del motor HOTD2RE para Android](docs/ANDROID_GAME_PORT.md)
- [Adaptador de almacenamiento Android](docs/ANDROID_STORAGE.md)
- [Auditoría de HOTD2RE](docs/ANDROID_PORT_AUDIT.md)
- [Pipeline SPIR-V](docs/SHADER_PORT.md)
- [Resultados de pruebas](docs/TEST_RESULTS.md)
