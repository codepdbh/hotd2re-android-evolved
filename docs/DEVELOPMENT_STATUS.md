# Estado del desarrollo

18 de noviembre de 2026. Auditoría de HOTD2RE en el SHA `90538bc7798fd2f020bfe117378d8a0e5692bcc4`.

Se completaron la auditoría, la compilación Win32 de referencia y una prueba gráfica independiente en un teléfono Android. La distribución local `hotd2re/` es el origen de datos. Las adaptaciones del motor están en la rama local `android-arm64` del checkout de referencia.

El diagnóstico independiente cumple **los niveles 1 a 3** de la escala de aceptación. Se generó una APK ARM64 firmada, se instaló e inició en el teléfono y se presentaron frames Vulkan con una escena sintética. El toque y el ciclo de superficie se ejercitaron en ese mismo diagnóstico. El render SDL_GPU también se verificó fuera de pantalla en Windows con lectura de píxeles. La auditoría de HOTD2RE registró el error de Clang en el objetivo `game` y la ausencia de SPIR-V para sus shaders. Los niveles de recursos, menús y gameplay del título quedan fuera del diagnóstico.

`probe.cpp` registra contactos por dispositivo y dedo. El tinte de dos dedos dura mientras ambos siguen activos y los contactos se descartan al perder el foco. La prueba instrumentada comprobó en el teléfono los píxeles durante el gesto, tras soltar un dedo y después de reanudar la actividad.

El último hito añadió un adaptador SAF de solo lectura en `StorageActivity.java`, `StorageTree.java` y `storage_adapter.cpp`. Se probó con `h2evolved` en el teléfono y conservó el permiso al volver a abrir la aplicación. [Guía del adaptador](ANDROID_STORAGE.md)

La APK del diagnóstico está en `android/app/build/outputs/apk/debug/app-debug.apk`. Su SHA-256 es `04B4A77E25922B93104BB63BEC59C4E55A0F17754B61DCB2936FA33A4107CD31`. [Guía de compilación del diagnóstico](ANDROID_BUILD.md)

## Port del motor existente

Se generó la APK ARM64 del port con el motor de HOTD2RE compilado para Android, el backend SDL_GPU en Vulkan y el importador SAF. El SHA-256 de `game-debug.apk` es `CED9056E28192EAC38E1E7F7D0CDD563B6251023A41D7699F0C609B6C0127EB1`. En escritorio, el mismo perfil Vulkan cargó los datos locales y renderizó el fotograma 2300 de la primera fase con 117 operaciones de dibujo, 8 sprites y 2 modelos de escenario. ADB perdió la conexión con el teléfono antes de instalar la APK del port. La ejecución en el dispositivo sigue pendiente. [Guía del port](ANDROID_GAME_PORT.md)

## Actualización del 19 de noviembre de 2026

El selector Android admite 4:3, 16:9 y el ancho del teléfono. La proyección y la puntería panorámicas pasaron las pruebas de escritorio en los seis capítulos. En el Galaxy S25 se inició la primera fase en **Ancho del teléfono**, con una superficie de 2340 × 1080. Un replay de referencia verificó que la acción Start devuelve al jugador 1 a la partida con tres vidas al activarse durante la cuenta regresiva.

La APK ARM64 incorpora controles táctiles con iconos para recargar, pausar, reanudar y continuar. También usa `icono.png` en el lanzador de Android. Su SHA-256 es `58F85D8EF6DE8F516C8E1D84F7E90B2403506C179A405128154BE6C48EC8596B`. La compilación se instaló en el Galaxy S25. Se comprobó el icono en el lanzador, la recarga y la pausa y reanudación desde el mismo punto de la pantalla.

La compilación online declara el permiso de red y ajusta el redondeo de Android para igualar la simulación de Windows. Los controles táctiles funcionan por medio de las entradas sincronizadas del jugador asignado al teléfono. El plazo para unirse a una sala empieza al enviar el código completo. El SHA-256 de esta APK es `A432BFC7694324189A0EB57FAC24BEC7E0E7D986D3ED26DF3EA926BCB1452030`.

La compilación con el nuevo lanzador incluye accesos para **Jugar** y **Online**, ajustes de formato y sonido y ayuda para compartir un código. El juego usa pantalla inmersiva. El SHA-256 de esta APK es `95630DEB86D6D731B71BBF8925158B5A1C52B0D757375925269FBDF98616A26B`.

La compilación posterior añade el selector persistente de idioma del lanzador y la vista previa de registros para crear un borrador en GitHub Issues. Su SHA-256 es `77286B94AB621D2D70B8835470B963DF8D785F00678727615FBD4C3EA44786A2`.

La versión pública Android 1.0 usa la compilación `Release` de ARM64. La APK firmada `HOTD2RE-Android-Evolved-v1.0-arm64.apk` tiene SHA-256 `AC1AABB33A88D82953C8CDA7B8A2F1A53F819394336E8206676C5FDBE705F1D8`. [GitHub Releases](https://github.com/codepdbh/hotd2re-android-evolved/releases/tag/v1.0)
