#include "game/exe_image.h"

#include <array>
#include <chrono>
#include <cstdio>
#include <filesystem>
#include <fstream>
#include <limits>

int main() {
    std::array<uint8_t, 512> bytes{};
    auto put = [&](size_t offset, uint32_t value, size_t size) {
        for (size_t i = 0; i < size; ++i) bytes[offset + i] = static_cast<uint8_t>(value >> (8 * i));
    };
    put(0, 0x5a4d, 2);
    put(0x3c, 0x40, 4);
    put(0x40, 0x4550, 4);
    put(0x46, 1, 2);
    put(0x54, 0x20, 2);
    put(0x58, 0x10b, 2);
    put(0x74, 0x400000, 4);
    put(0x80, 8, 4);       // virtual size, including zero-filled tail
    put(0x84, 0x1000, 4);  // relative virtual address
    put(0x88, 4, 4);       // file size
    put(0x8c, 0x100, 4);   // file offset
    put(0x100, 0x12345678, 4);

    const auto id = std::chrono::steady_clock::now().time_since_epoch().count();
    const auto path = std::filesystem::temp_directory_path() / ("hotd2-pe32-" + std::to_string(id) + ".bin");
    struct Cleanup {
        const std::filesystem::path& path;
        ~Cleanup() { std::error_code ec; std::filesystem::remove(path, ec); }
    } cleanup{path};
    auto write = [&] {
        std::ofstream file(path, std::ios::binary);
        file.write(reinterpret_cast<const char*>(bytes.data()), bytes.size());
        return static_cast<bool>(file);
    };
    int failures = 0;
    auto check = [&](bool success, const char* name) {
        if (!success) {
            std::fprintf(stderr, "FAIL %s\n", name);
            ++failures;
        }
    };

    constexpr uint32_t va = 0x401000;
    game::ExeImage image;
    check(write() && image.Load(path.string()), "load PE32");
    check(image.U32(va) == 0x12345678 && image.U32(va + 4) == 0, "map data and zero tail");
    uint32_t value = 0;
    check(!image.Read(va + 5, &value, 4), "reject end of section overrun");
    check(!image.Read(va, &value, std::numeric_limits<size_t>::max()), "reject oversized read");
    put(0x3c, 0xfffffff0, 4);
    check(write() && !image.Load(path.string()) && image.U32(va) == 0x12345678, "reject overflowing PE offset");
    put(0x3c, 0x40, 4);
    put(0x46, 0xffff, 2);
    check(write() && !image.Load(path.string()), "reject truncated section table");
    put(0x46, 1, 2);
    put(0x58, 0x20b, 2);
    check(write() && !image.Load(path.string()), "reject PE32+");
    put(0x58, 0x10b, 2);
    put(0x84, 0x2000, 4);
    check(write() && image.Load(path.string()) && image.U32(va) == 0 && image.U32(va + 0x1000) == 0x12345678,
          "reload replaces section mapping");

    if (!failures) std::puts("exe_image_portability_test: all passed");
    return failures != 0;
}
