cmake_minimum_required(VERSION 3.30)
project(sdl_vulkan_probe LANGUAGES C CXX)

if(ANDROID AND NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "Build this probe with the Android NDK for arm64-v8a.")
endif()
if(NOT ANDROID AND NOT (WIN32 AND CMAKE_SIZEOF_VOID_P EQUAL 8))
    message(FATAL_ERROR "The probe supports Android ARM64 and offscreen testing on Windows x64.")
endif()

set(SDL_SHARED ON CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_TEST_LIBRARY OFF CACHE BOOL "" FORCE)
set(SDL_INSTALL OFF CACHE BOOL "" FORCE)
set(SDL_VULKAN ON CACHE BOOL "" FORCE)
add_subdirectory("${SDL_SOURCE_DIR}" sdl EXCLUDE_FROM_ALL)

if(ANDROID)
    file(GLOB shader_tool_dirs LIST_DIRECTORIES true "${CMAKE_ANDROID_NDK}/shader-tools/*")
    find_program(GLSLC glslc PATHS ${shader_tool_dirs} NO_DEFAULT_PATH REQUIRED)
    find_program(SPIRV_VAL spirv-val PATHS ${shader_tool_dirs} NO_DEFAULT_PATH REQUIRED)
else()
    find_program(GLSLC glslc REQUIRED)
    find_program(SPIRV_VAL spirv-val REQUIRED)
endif()
set(shader "${CMAKE_CURRENT_SOURCE_DIR}/probe.hlsl")
set(vertex "${CMAKE_CURRENT_BINARY_DIR}/probe.vert.spv")
set(fragment "${CMAKE_CURRENT_BINARY_DIR}/probe.frag.spv")
set(embedded "${CMAKE_CURRENT_BINARY_DIR}/probe_shaders.h")
set(embed_script "${CMAKE_CURRENT_SOURCE_DIR}/embed_shaders.cmake")

add_custom_command(
    OUTPUT "${embedded}"
    BYPRODUCTS "${vertex}" "${fragment}"
    COMMAND "${GLSLC}" -x hlsl --target-env=vulkan1.0 -fshader-stage=vert -fentry-point=vs_main
        "${shader}" -o "${vertex}"
    COMMAND "${GLSLC}" -x hlsl --target-env=vulkan1.0 -fshader-stage=frag -fentry-point=ps_main
        -fauto-combined-image-sampler "${shader}" -o "${fragment}"
    COMMAND "${SPIRV_VAL}" --target-env vulkan1.0 "${vertex}"
    COMMAND "${SPIRV_VAL}" --target-env vulkan1.0 "${fragment}"
    COMMAND "${CMAKE_COMMAND}" "-DVERTEX=${vertex}" "-DFRAGMENT=${fragment}"
        "-DOUTPUT=${embedded}" -P "${embed_script}"
    DEPENDS "${shader}" "${embed_script}"
    COMMENT "Compiling and validating the Vulkan probe shaders"
    VERBATIM)

if(ANDROID)
    add_library(main SHARED probe.cpp storage_adapter.cpp "${embedded}")
else()
    add_executable(main probe.cpp "${embedded}")
endif()
target_compile_features(main PRIVATE cxx_std_20)
target_include_directories(main PRIVATE "${CMAKE_CURRENT_BINARY_DIR}")
target_link_libraries(main PRIVATE SDL3::SDL3)
if(MSVC)
    target_compile_options(main PRIVATE /W4)
else()
    target_compile_options(main PRIVATE -Wall -Wextra -Wpedantic)
endif()
