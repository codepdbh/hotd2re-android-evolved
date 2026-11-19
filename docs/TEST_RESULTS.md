# Resultados de pruebas

Fecha de verificación: 18 de noviembre de 2026. Base de HOTD2RE: `90538bc7798fd2f020bfe117378d8a0e5692bcc4`.

| Área | Resultado observado |
|---|---|
| HOTD2RE Win32 | Compilación Release de `hotd2` y `play` exitosa con MSVC 14.44. Se ejecutaron `touch_guns_test`, `gun_input_test`, `sdl_audio_test` y `audio_loop_region_test` en terminal. Las pruebas de audio usaron PCM sintético y el driver de disco SDL. Las cuatro pruebas pasaron. |
| HOTD2RE ARM64 | Configuración CMake exitosa. Se generó `libport_input_core.a`. `game` se detuvo al compilar `world_typing_drill_game.cpp:473` por `invalid suffix '+' on integer constant`. |
| Diagnóstico Android | APK de depuración generada por `scripts/build_android.ps1 -Offline`. `aapt2` confirmó `arm64-v8a` y API mínima 29. `apksigner` verificó firma v2. `zipalign` verificó la alineación de 16 KiB. `llvm-readelf` identificó `libmain.so` como AArch64 y comprobó la exportación de `SDL_main`. La APK contiene `libmain.so`, `libSDL3.so` y la licencia de SDL. |
| Shaders | Ambos módulos SPIR-V pasaron `spirv-val` para Vulkan 1.0. El desensamblado del fragmento confirmó un sampler combinado en el set 2 y un uniforme en el set 3, ambos en binding 0. |
| Vulkan en Windows x64 | SDL_GPU inicializó el driver `vulkan`. Se leyeron dos render targets de 96 × 54 y se verificaron los cuatro colores del patrón, las barras y el cambio de tinte. El backend de audio `dummy` aceptó 5 760 muestras. Toda la prueba se ejecutó sin ventana. |
| Android físico | `SM-S938B`, `arm64-v8a`, API 37 y Vulkan 1.3 anunciado. ADB instaló la APK y SDL_GPU presentó frames de 2340 × 1080 con Vulkan. Un toque alteró el color capturado y encoló 5 760 muestras en AAudio. Al volver desde Inicio, Android recreó la superficie y la prueba confirmó en la captura que el nuevo frame conservaba el tinte. Logcat no registró ANR ni cierres inesperados para el proceso durante la prueba final. |
| Multitáctil en Android físico | La APK de instrumentación inyectó dos contactos simultáneos mediante el sistema de entrada de Android. Verificó por captura el tinte con un dedo, con dos y tras soltar el segundo. En un segundo gesto, Android envió dos cancelaciones al perder el foco. Logcat confirmó `contacts=0` en el frame presentado tras recrear la superficie, y la captura mostró el color base. |
| Adaptador de almacenamiento | El teléfono concedió un permiso persistente de lectura sobre `h2evolved` mediante el selector del sistema. En otro proceso, C++ leyó los 192 bytes de `Hod2.ini` y los primeros 4096 bytes de `cam/op_train.bin` mediante descriptores SAF. Las huellas de las muestras coincidieron con los archivos locales. La prueba rechazó `../Hod2.ini`. |

Las primeras ejecuciones detectaron dos problemas en el diagnóstico. `SDLActivity` volvía a solicitar cualquier orientación al crear la ventana y los eventos de aplicación de SDL 3.4.18 se despachan a observadores. Se fijó el hint horizontal, se atendieron los eventos de ventana que SDL sí encola y se limitó el render a cambios de estado, entrada y tamaño. La prueba final pasó tras esperar `onStop` antes de reabrir la actividad.

La prueba multitáctil ahora cancela explícitamente sus punteros virtuales al terminar. Android retenía dos punteros de una ejecución anterior y rechazaba los toques inyectados posteriores. Tras la corrección pasaron la prueba instrumentada y la comprobación básica con ADB en ejecuciones consecutivas.

Los registros de compilación y la salida de la prueba Vulkan se guardaron bajo `.work/`. `.work/device-test/` contiene `initial.png`, `after-touch.png`, `resumed.png` y `logcat.txt` de la prueba básica. `multitouch-instrumentation.txt` y `multitouch-logcat.txt` contienen el resultado y la secuencia de eventos del ensayo multitáctil. `.work/storage-instrumentation.txt` y `.work/storage-logcat.txt` registran el ensayo de almacenamiento. La prueba de audio en Android verifica el backend y la cola de muestras. La reproducción acústica, mandos físicos y rendimiento del juego quedan fuera de la evidencia de esta prueba.

## APK del port HOTD2RE

| Área | Resultado observado |
|---|---|
| Android ARM64 | `scripts/build_android.ps1 -Game -Offline` generó `game-debug.apk`. La biblioteca `libmain.so` es AArch64 y exporta `SDL_main`. La APK contiene el motor y SDL3, un descriptor de escena del checkout y los avisos de licencia. |
| Vulkan con datos del juego en Windows | En una captura sin ventana, SDL_GPU con SPIR-V cargó la copia local y renderizó la primera fase en el modo 6. En el fotograma 2300 coincidieron el estado de juego, 117 operaciones de dibujo, 8 sprites y 2 modelos de escenario con Direct3D 11. La comparación RGB detectó 951 píxeles distintos de 307 200. |
| Regresiones Win32 | Pasaron `exe_image_portability_test`, `touch_guns_test`, `gun_input_test`, `sdl_audio_test` y `audio_loop_region_test`. Las pruebas cubren validación PE32, cancelación de contactos, entrada y audio. |
| Android físico | ADB dejó de enumerar el dispositivo antes de instalar la APK del port. Quedan por comprobar la importación en esa APK, el arranque del motor y la primera fase interactiva en Android. |

En una sesión posterior, ADB instaló el port e importó los 2045 archivos. Coincidieron los SHA-256 de `Hod2.exe`, `tex/common.bin` y `naomi_audio.bank` con la copia local. Se reconstruyó desde cero tras detectar objetos incrementales con dos tamaños distintos de `ExeImage`.

El 19 de noviembre, `widescreen_test` comprobó la proyección, la puntería y la geometría de los seis capítulos. Vulkan generó capturas a 960 × 540 y 1170 × 540 del mismo fotograma de la primera fase. En un replay de prueba, el caso base llegó a Game Over. Al pulsar Start durante la cuenta regresiva, el mismo replay siguió en la primera fase con el jugador 1 activo y tres vidas. La APK con los controles Android se compiló. La verificación física de esta versión quedó pendiente al estar el teléfono fuera de ADB.

Al reconectar el Galaxy S25 se instaló la versión panorámica y se inició la primera fase en **Ancho del teléfono**, a 2340 × 1080. Pasaron `gun_input_test`, `touch_guns_test` y las 396 comprobaciones de `pause_menu_test`. En la APK con iconos, se abrió el juego desde su nuevo icono en el lanzador de Samsung. Los controles exponen descripciones de accesibilidad y carecen de texto visible. Un toque en el cartucho devolvió la munición de cinco a seis balas. Dos toques en las mismas coordenadas pausaron y luego reanudaron la partida.

## Multijugador online

El Galaxy S25 recibió la lista del servidor y creó una sala por código. Antes de ajustar el redondeo, la conexión Android–Windows se desincronizó al comenzar Arcade. Al desactivar la fusión de multiplicaciones y sumas en Android, coincidieron los estados y las matrices de 2200 fotogramas en una repetición comparada con Windows. En una partida con el S25 como anfitrión y Windows x64 como invitado, ambos siguieron sincronizados por el relay hasta el fotograma 4000. Pasaron las 17 pruebas del servicio y los seis casos de conexión local, incluida la pérdida de los primeros mensajes del lobby.

Con la última APK, el S25 se unió como P2 tras dejar abierto el teclado del juego más de ocho segundos y tocar el código de cinco dígitos de una sala de Windows. Se comprobó la pausa de P2 y la reanudación desde el mismo icono. El cartucho apareció durante la partida online. El teléfono se desconectó de ADB antes de comprobar la reposición de munición y la cuenta regresiva online.

## Lanzador y pantalla completa

La APK del nuevo lanzador se instaló en el S25 y conservó la importación existente y la selección **Ancho del teléfono**. Android presentó las opciones del lanzador y se observó el acceso directo al menú online. En el juego a 2340 × 1080, la captura y el estado de la ventana confirmaron que las barras de estado y navegación estaban ocultas.

La misma APK se instaló en el Galaxy A55 (`SM-A556E`, Android API 36, ARM64). La primera importación empezó antes de terminar la transferencia y faltaba `tex`. Al repetirla con la transferencia completa, coincidieron los SHA-256 de los 2045 archivos en `h2evolved` y en la copia privada de la app. El juego inició con SDL_GPU y audio de baja latencia, entró online como P2 por conexión directa y mostró la primera fase con los controles táctiles.

La APK bilingüe compiló y pasó la verificación de firma. Se comprobaron los 40 recursos localizados y el filtro de registros pasó 11 casos de entrada, incluidos errores del motor, códigos, direcciones y rutas privadas.

En el S25, el lanzador cambió a inglés, conservó la selección al volver desde el juego y mostró en la vista previa el arranque del motor y los datos de Android.

Para Android 1.0 pasaron la compilación y la tarea `lintVitalRelease`. Se verificaron el ABI ARM64, la versión mínima de Android 10, la firma v3 y la alineación de 16 KiB. El inventario de la APK confirmó las bibliotecas, las licencias y el descriptor de escena, sin archivos del juego de PC. Con una carpeta que contenía solo `Hod2.exe` y las siete carpetas de datos, el cliente de escritorio de HOTD2RE renderizó el fotograma 2300 de la primera fase.
