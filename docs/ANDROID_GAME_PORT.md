# Port Android de HOTD2RE

La aplicación `com.codepdbh.hotd2evolved` usa el punto de entrada `PlayMain` del código existente de HOTD2RE. La base del port es `90538bc7798fd2f020bfe117378d8a0e5692bcc4` de GitFist. El módulo Android de diagnóstico se compila por separado.

## Compilar e instalar

Desde la raíz del workspace, ejecutar `./scripts/build_android.ps1 -Game`. La primera compilación puede necesitar acceso a los repositorios de Gradle. Para generar los shaders se requiere Python 3 en el host. El NDK proporciona `glslc` y `spirv-val`. La APK de depuración resultante está en `android/game/build/outputs/apk/debug/game-debug.apk`.

```powershell
adb install -r android/game/build/outputs/apk/debug/game-debug.apk
adb shell am start -W -n com.codepdbh.hotd2evolved/.DataActivity
```

Desde la aplicación, seleccionar **Importar carpeta del juego → Memoria interna → h2evolved** y conceder acceso al árbol en el selector del sistema. Se crea una copia privada para que el motor pueda usar sus rutas POSIX. En el teléfono que recibió los datos, esa copia requiere unos 971 MB adicionales. Al terminar la validación se habilita **Jugar**.

Antes de pulsar **Jugar**, elegir **Clásico (4:3)**, **Panorámico (16:9)** o **Ancho del teléfono**. El modo panorámico amplía el encuadre horizontal con la proyección de HOTD2RE. La puntería se calcula sobre el área presentada y el HUD queda centrado. Durante la cuenta regresiva del jugador 1 se puede pulsar **Continuar** en pantalla o el botón físico **Subir volumen**. Ambos envían la acción Start del motor.

La APK aporta la biblioteca ARM64 de HOTD2RE, SDL3, el descriptor de escena del repositorio y los avisos de licencia. Las tablas, voces, texturas y modelos provienen de la carpeta que selecciona el usuario. El motor abre `Hod2.exe` como un archivo PE32 de datos.

## Componentes adaptados

- `android_entry.cpp` inicia el motor existente desde `SDLActivity`, fija el directorio privado como base y envía stdout y stderr a Logcat bajo `HOTD2RE`.
- `GameDataImport` copia los datos mediante SAF a un directorio temporal privado, valida la cabecera PE32 y las carpetas mínimas y publica la copia con un renombrado atómico.
- `compile_sdl_spirv.py` compila siete etapas a partir de los shaders HLSL que ya usa SDL_GPU. La ruta Vulkan conserva los modelos, sprites y presentación. Las capacidades avanzadas que usan otros shaders, como TAA, SMAA, CMAA2 y mapas de sombras, quedan deshabilitadas en ese perfil inicial.
- Se reutiliza `TouchGuns` para disparo y recarga. Se cancelan los contactos al perder foco y el botón Atrás se entrega al motor como Escape. La fase de pruebas en Android debe comprobar los gestos y la pausa con el juego abierto.

## Reproducir la captura Vulkan de escritorio

Configurar el checkout con MSVC Win32 y activar `HOTD2_SDL_GPU_SPIRV=ON`. Indicar las rutas de `glslc.exe` y `spirv-val.exe` del NDK mediante `HOTD2_GLSLC` y `HOTD2_SPIRV_VAL`. Compilar `play` en Release. Desde el checkout, usar el backend SDL_GPU y el modo captura para renderizar sin ventana.

```powershell
$data = 'C:/ruta/a/los/datos-del-juego'
$capture = Join-Path $PWD.Path 'port-stage1-sdlgpu-2300.png'
./build-audit-win32/src/port/Release/play.exe $data --renderer sdlgpu --screenshot $capture --width 960 --height 540 --viewport 16:9 --frame 2300 --direct --no-update-check
```

Con `--renderer d3d11` y otro nombre de salida se puede comparar el mismo frame con el backend de referencia.
