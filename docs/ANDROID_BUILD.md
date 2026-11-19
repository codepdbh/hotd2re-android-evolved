# Compilación del diagnóstico SDL Vulkan

`SDL Vulkan Probe` es una aplicación de diagnóstico independiente. Contiene una textura sintética de cuatro colores y genera un tono al recibir entrada. Usa `SDLActivity`, C++20, SDL_GPU y Vulkan. El identificador predeterminado es `dev.sdlprobe.android`.

## Entorno comprobado

| Herramienta | Versión |
|---|---|
| Git | 2.52.0.windows.1 |
| Python | 3.14.2 |
| CMake / Ninja instalados por el SDK | 3.30.5 / 1.10.2 |
| Android SDK | Compilación y objetivo API 35, mínimo API 29 |
| Build Tools | 34.0.0 |
| NDK / Clang | 28.2.13676358 / 19.0.1, `arm64-v8a` |
| JDK usado | JBR 21.0.10 |
| Gradle / Android Gradle Plugin | 8.9 / 8.7.3 |
| ADB | 1.0.41, platform-tools 37.0.0 |
| DXC detectado | 1.9.2602.17, Windows SDK 10.0.26100.0 |
| SPIR-V Tools | `spirv-val` del NDK configurado |
| MSVC de referencia | Visual Studio Build Tools 2022 17.14, herramientas 14.44 |

AGP 8.7 admite compilar con API 35 y exige al menos JDK 17. El wrapper fija Gradle 8.9 y registra el SHA-256 de su distribución. [Compatibilidad de AGP 8.7](https://developer.android.com/build/releases/agp-8-7-0-release-notes)

## APK de depuración

Este comando se ejecuta desde la raíz del workspace en PowerShell.

```powershell
./scripts/build_android.ps1
```

El script detecta el SDK mediante `ANDROID_HOME`, `ANDROID_SDK_ROOT` o la instalación local estándar. Usa `JAVA_HOME` o el JBR de Android Studio. `-Sdk` y `-Jdk` permiten indicar instalaciones existentes. La primera ejecución descarga SDL 3.4.18 bajo `.work/deps/` y verifica su SHA-256 antes de extraerlo. `-Offline` reutiliza el archivo y las dependencias de Gradle en caché.

La salida es `android/app/build/outputs/apk/debug/app-debug.apk`, firmada con la clave de depuración local. El script imprime su SHA-256. Para cambiar el identificador después de preparar las dependencias, ejecutar `gradlew.bat -PprobeApplicationId=dev.example.probe :app:assembleDebug` desde `android/` con el SDK y JDK configurados.

## Prueba con ADB

Este comando requiere un dispositivo ARM64 con Vulkan, desbloqueado y autorizado.

```powershell
./scripts/test_android.ps1
```

El script instala la APK de diagnóstico, abre la actividad, registra el dispositivo Vulkan, toca el área de prueba, verifica el cambio de color en la captura y comprueba que se envía un nuevo frame tras volver desde Inicio. Guarda capturas y Logcat en `.work/device-test/`. Usa `-Serial` si hay más de un dispositivo y `-Package` si se cambió el identificador. La comprobación de audio registra la apertura del backend y el envío de muestras.

Para extender la prueba a dos contactos simultáneos, compilar e instalar también el APK de instrumentación.

```powershell
./scripts/build_android.ps1 -WithTests
./scripts/test_android.ps1 -Multitouch
```

El APK adicional está en `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. El test usa `UiAutomation` para enviar eventos táctiles de Android y comprobar los píxeles presentados. Pone la actividad en segundo plano con dos dedos activos y comprueba que el primer frame al volver recupera el color base. Los resultados quedan en `.work/device-test/multitouch-instrumentation.txt` y `multitouch-logcat.txt`. [Android UiAutomation](https://developer.android.com/reference/android/app/UiAutomation)

## Prueba de Windows sin ventana

La prueba usa los mismos shaders y la misma pasada de renderizado que el diagnóstico Android. Lee los píxeles devueltos por Vulkan y comprueba los cuatro colores, el cambio de tinte y las barras. Requiere MSVC x64, el driver Vulkan de Windows y las herramientas de shaders del NDK. Estos comandos se ejecutan después de obtener SDL mediante el script de compilación.

```powershell
$sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$cmake = Join-Path $sdk 'cmake/3.30.5/bin/cmake.exe'
$shaderTools = Join-Path $sdk 'ndk/28.2.13676358/shader-tools/windows-x86_64'
$sdl = (Resolve-Path .work/deps/SDL3-3.4.18).Path
& $cmake -S native/android -B .work/windows-probe -G 'Visual Studio 17 2022' -A x64 "-DSDL_SOURCE_DIR=$sdl" "-DGLSLC=$shaderTools/glslc.exe" "-DSPIRV_VAL=$shaderTools/spirv-val.exe"
& $cmake --build .work/windows-probe --config Release --target main --parallel 4
$env:SDL_AUDIODRIVER = 'dummy'
$env:PATH = "$(Resolve-Path .work/windows-probe/sdl/Release);$env:PATH"
& ./.work/windows-probe/Release/main.exe
```
