# Adaptador de almacenamiento Android

La APK incluye dos entradas en el lanzador. **Adaptador de almacenamiento** abre el selector de carpetas de Android. **SDL Vulkan Probe** abre la prueba gráfica.

1. Abre **Adaptador de almacenamiento** y pulsa **Elegir carpeta**.
2. En el selector de Android, abre **Memoria interna → h2evolved**. Pulsa **Usar esta carpeta** y confirma el permiso.
3. Escribe una ruta relativa, por ejemplo `Hod2.ini` o `cam/op_train.bin`, y pulsa **Comprobar lectura**. La pantalla muestra el tamaño, si el proveedor lo conoce, y los bytes leídos desde C++.

La aplicación conserva el permiso de lectura cuando el proveedor lo permite. El selector concede acceso al árbol elegido mediante SAF. La aplicación abre los documentos en modo `r`. [Acceso a carpetas y persistencia de permisos en Android](https://developer.android.com/training/data-storage/shared/documents-files)

## API para C++

`StorageTree.java` resuelve los componentes de una ruta relativa por nombre dentro de la carpeta elegida. Rechaza segmentos vacíos, `.` y `..`. `android_storage::OpenReadOnly` devuelve un descriptor POSIX del archivo. El llamador recibe el objeto Java `StorageTree` y la ruta como `jstring`, y cierra el descriptor cuando termina. Algunos proveedores entregan un flujo sin soporte de `lseek`. [ParcelFileDescriptor.detachFd](https://developer.android.com/reference/android/os/ParcelFileDescriptor#detachFd())

La actividad de diagnóstico lee una muestra de hasta 4096 bytes en un hilo de trabajo. Registra el tamaño y la huella FNV-1a de la muestra en Logcat bajo `StorageAdapter`. La carpeta permanece en la memoria interna del teléfono.

## Repetir la prueba con ADB

La prueba requiere la carpeta `h2evolved` en la memoria interna y las dos APK generadas por `-WithTests`. El teléfono debe estar autorizado, desbloqueado y con `h2evolved` visible en el selector de la memoria interna al solicitar acceso por primera vez.

```powershell
./scripts/build_android.ps1 -WithTests
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e mode storage -e folder h2evolved -e file Hod2.ini -e nested cam/op_train.bin dev.sdlprobe.android.test/dev.sdlprobe.android.ProbeTouchInstrumentation
```
