file(READ "${VERTEX}" vertex_bytes HEX)
file(READ "${FRAGMENT}" fragment_bytes HEX)
string(REGEX REPLACE "(..)" "0x\\1," vertex_bytes "${vertex_bytes}")
string(REGEX REPLACE "(..)" "0x\\1," fragment_bytes "${fragment_bytes}")
file(WRITE "${OUTPUT}" "#pragma once\n#include <SDL3/SDL_stdinc.h>\n"
    "alignas(4) inline constexpr Uint8 vertexShader[] = {${vertex_bytes}};\n"
    "alignas(4) inline constexpr Uint8 fragmentShader[] = {${fragment_bytes}};\n")
