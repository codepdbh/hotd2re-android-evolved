# Pipeline HLSL y SPIR-V del diagnóstico

`native/android/probe.hlsl` contiene dos puntos de entrada. `vs_main` genera un triángulo y coordenadas UV a partir de `SV_VertexID`. `ps_main` toma una muestra de una textura RGBA8 de 2 × 2 y la multiplica por un vector de color. El código C++ fija un viewport 4:3 centrado en las dimensiones que devuelve el swapchain.

CMake ejecuta `glslc` con destino Vulkan 1.0. La opción `-fauto-combined-image-sampler` genera un descriptor combinado para `t0` y `s0`. `spirv-dis` muestra `DescriptorSet 2` y `Binding 0` para la textura, y `DescriptorSet 3`, `Binding 0`, offset 0, para el uniforme de fragmento. La etapa de vértice carece de recursos enlazados. `spirv-val` valida ambas etapas antes de generar la cabecera de bytes. [Convenciones de SDL_GPU](https://wiki.libsdl.org/SDL3/SDL_CreateGPUShader)

En ejecución, `probe.cpp` solicita explícitamente el driver `vulkan` y declara un sampler y un uniforme de fragmento. La actividad Android crea el pipeline con el formato del swapchain. La prueba Windows usa la misma pasada de renderizado con un destino RGBA8 y lee sus píxeles. Los módulos DXBC de la revisión auditada se describen en [la auditoría](ANDROID_PORT_AUDIT.md).
