# Auditoría Android de HOTD2RE

Revisión del 18 de noviembre de 2026. El código se consultó en `upstream/hotd2-re`, revisión `90538bc7798fd2f020bfe117378d8a0e5692bcc4`. El `BUILD.txt` de la distribución local `hotd2re/` identifica el mismo SHA y declara un binario Windows x64 de la versión 2.3. La distribución local incluye datos de juego y se mantuvo fuera del diagnóstico Android.

## Plataforma y objetivos de CMake

| Componente | Hallazgo en la revisión auditada |
|---|---|
| Simulación y formatos | El CMake raíz construye `game` y `game_sound` como bibliotecas estáticas. `world*.cpp` contiene simulación, flujo y menús. `model.cpp`, `anim.cpp`, `camera.cpp`, `event.cpp`, `texture.cpp` y `prs.cpp` contienen lectura y decodificación. `save_data.cpp` mantiene datos de guardado. Son candidatos para pruebas de portabilidad por módulo. |
| Opciones de arquitectura | `HOTD2_ALLOW_64BIT` levanta la comprobación de `CMAKE_SIZEOF_VOID_P`. `notes/agents/platform.md` documenta cobertura con MSVC x64 y advierte sobre contracción FMA en ARM. Windows x64 valida LLP64. Android ARM64 usa LP64, donde también cambia el ancho de `long`. Los formatos y sus operaciones deben revisarse por separado de la anchura del host. |
| Integración SDL | `src/port/CMakeLists.txt` crea `port_platform`, `port_audio_sdl` y `port_gpu_sdl` con SDL3. El CMake raíz descarga SDL 3.4.18 por defecto y fuerza enlace estático. `play` es un ejecutable de terminal y `hotd2` un ejecutable GUI bajo `WIN32`. El punto de entrada existente no dispone de actividad ni empaquetado Android. |
| Dependencias de Windows | El hook y Detours se limitan a Windows de 32 bits. `src/port/CMakeLists.txt` condiciona Direct3D 11/12, DXGI, D3DCompiler, XAudio2, GDI, HID/SetupAPI, WinHTTP y Winsock. La ruta de red incluye ramas POSIX en `netplay.cpp` y `leaderboard.cpp`. Las funciones de actualización que dependen de WinHTTP y los proveedores gráficos de escritorio requieren una decisión de alcance propia. |
| Almacenamiento | `play.cpp` localiza `game/Hod2.exe` desde la ubicación de `argv[0]` y puede cambiar el directorio de trabajo. También usa rutas relativas para recursos y configuración. SAF entrega URI de proveedor. El código de archivos espera rutas POSIX y la biblioteca nativa puede residir dentro de la APK. El diagnóstico usa shaders compilados dentro de la biblioteca y prescinde de acceso a almacenamiento externo. |

## GPU y shaders

`SdlGpuDevice::Init` en `src/port/render/sdl_gpu_device.cpp` asigna `SDL_GPU_SHADERFORMAT_DXBC` bajo `_WIN32` y cero en las demás plataformas. `CreateShaders` también tiene una rama exclusiva de Windows. Llama a `CompileShaderCached` con perfiles `vs_5_1` y `ps_5_1`, y declara la cantidad de samplers y uniformes de cada etapa. El antialiasing compute conserva otra ruta DXBC.

`hlsl_shaders.h` contiene modelo, sprite y presentación. `CreateShaders` enumera también profundidad, oclusión ambiental, niebla, reflejos, profundidad de campo, motion vectors, TAA, mapas de sombras, SMAA, FSR y máscaras reactivas. Sprites y presentación cubren una primera pasada de imagen con texturas. Modelos y profundidad agregan vértices, transformaciones y oclusión. Los efectos posteriores requieren pruebas adicionales de formatos, cómputo y recursos.

Para SPIR-V, SDL_GPU reserva los sets 0 y 1 para recursos y uniformes de vértice, respectivamente. En fragmento usa los sets 2 y 3. Los bindings deben ser consecutivos y los samplers de Vulkan son descriptores combinados. Las macros DXBC del motor también expresan semánticas de vértice, atributos empaquetados y variantes de iluminación. Todo ello requiere comprobar las locations, los offsets del bloque de uniformes y el layout de los pipelines por etapa. [SDL_CreateGPUShader](https://wiki.libsdl.org/SDL3/SDL_CreateGPUShader)

## Entrada, audio y ciclo de vida

- `Platform::Init` en `src/port/platform/platform_sdl.cpp` elige `Input::Touch` en Android e iOS. La bomba de eventos pasa los eventos de dedos y gamepads a sus gestores y registra cambios de foco y tamaño de ventana. Falta verificar una integración con el ciclo de vida Android y con acciones de pausa y regreso.
- `TouchGuns` en `src/port/input/touch_guns.cpp` distingue apuntado, disparo, recarga por segundo dedo, pulsación prolongada y área externa. También convierte el tercer dedo en Start. `aim_map.cpp` y `aim_calibration.cpp` son módulos independientes del backend de la ventana.
- `SdlAudioBackend::Init` abre un dispositivo SDL. `Feed` alimenta streams desde PCM decodificado y las operaciones de canal pausan o reanudan el stream. Los archivos, rangos de bucle y audio de aplicación en segundo plano necesitan sus propias pruebas. `tests/sdl_audio_test.cpp` y `tests/audio_loop_region_test.cpp` ofrecen cobertura de escritorio.

## Formatos y compatibilidad

- `ExeImage::Load` y `ExeImage::Read` en `src/game/exe_image.cpp` usan enteros de 32 bits para direcciones virtuales del archivo y las convierten a offsets de secciones. El archivo se abre como datos binarios. La posición leída para `ImageBase` corresponde a PE32. La rutina asume little-endian y requiere comprobaciones de variante, límites de la tabla de secciones y aritmética de offsets antes de cualquier selección de archivos no confiables.
- Android usa rutas sensibles a mayúsculas y minúsculas en su almacenamiento privado. La distribución local contiene `game/sound`, mientras el enunciado menciona `game/Sound`. La carga y validación deben comprobar nombres y variantes en el sistema de archivos objetivo.
- `scenes/` contiene cuatro descriptores `.scene`. El árbol `tests/` contiene familias de pruebas de entrada, audio, simulación, modelos y renderizado. Varias de escena cargan datos de referencia. `touch_guns_test` y `gun_input_test` aíslan la lógica de entrada sin abrir ventanas.
- `LICENSE` concede MIT para el código de HOTD2RE. `THIRD_PARTY_NOTICES.txt` trata componentes gráficos de terceros por separado. La existencia de esos permisos de código no determina los derechos de redistribución de los datos originales.

## Referencias de integración

Los proyectos complementarios usan recompilación estática como estrategia central. Sirven como antecedentes para separar la aplicación Android y la cadena NDK, con un mecanismo de ejecución distinto al C++ y SDL3 de HOTD2RE. [Max Payne 2 Android Evolved](https://github.com/codepdbh/mp2-androidevolved) · [Hitman 2 Android Evolved](https://github.com/codepdbh/hitman2-android-evolved)

## Comprobaciones del código base

Win32 Release generó `hotd2.exe` y `play.exe` con el código auditado. Con Android API 29, NDK 28.2, `HOTD2_ALLOW_64BIT=ON` y SDL desactivado para aislar bibliotecas, el objetivo `port_input_core` generó `libport_input_core.a`. El objetivo `game` se detuvo en `world_typing_drill_game.cpp:473` porque Clang tokeniza `0xB8E+` como un literal con sufijo inválido. El checkout conserva el SHA original. [Resultados de pruebas](TEST_RESULTS.md)
