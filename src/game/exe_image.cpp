#include "exe_image.h"

#include <algorithm>
#include <bit>
#include <cstring>
#include <fstream>
#include <limits>
#include <utility>

namespace game {

namespace {
constexpr uint32_t kNameTable = 0x004D1410;   // char* per texture file
constexpr uint32_t kDescTable = 0x0055B9B8;   // descriptor array pointer per file
constexpr uint32_t kCountTable = 0x0055C0A8;  // s16 count per file
static_assert(std::endian::native == std::endian::little);
}  // namespace

bool ExeImage::Load(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return false;
    std::vector<uint8_t> file(std::istreambuf_iterator<char>(f), {});
    if (file.size() < 0x200 || file[0] != 'M' || file[1] != 'Z') return false;
    uint32_t headerOffset;
    memcpy(&headerOffset, file.data() + 0x3C, 4);
    const size_t pe = headerOffset;
    if (pe < 0x40 || pe > file.size() - 24 || memcmp(file.data() + pe, "PE\0\0", 4) != 0) return false;
    uint16_t nSections, optSize, magic;
    memcpy(&nSections, file.data() + pe + 6, 2);
    memcpy(&optSize, file.data() + pe + 20, 2);
    const size_t optional = pe + 24;
    if (!nSections || optSize < 32 || optSize > file.size() - optional) return false;
    memcpy(&magic, file.data() + optional, 2);
    if (magic != 0x10B) return false;  // original PC executable is PE32
    uint32_t imageBase;
    memcpy(&imageBase, file.data() + optional + 28, 4);
    const size_t sec = optional + optSize;
    if (nSections > (file.size() - sec) / 40) return false;

    std::vector<Section> sections;
    sections.reserve(nSections);
    for (size_t i = 0; i < nSections; ++i) {
        const uint8_t* s = file.data() + sec + i * 40;
        Section x{};
        memcpy(&x.vsize, s + 8, 4);
        memcpy(&x.va, s + 12, 4);
        memcpy(&x.rawSize, s + 16, 4);
        memcpy(&x.rawOffset, s + 20, 4);
        if (x.va > std::numeric_limits<uint32_t>::max() - imageBase) return false;
        if (x.rawSize && (x.rawOffset > file.size() || x.rawSize > file.size() - x.rawOffset)) return false;
        x.va += imageBase;
        sections.push_back(x);
    }
    file_ = std::move(file);
    sections_ = std::move(sections);
    return true;
}

bool ExeImage::Read(uint32_t va, void* out, size_t size) const {
    for (const Section& s : sections_) {
        if (va < s.va) continue;
        const uint32_t rel = va - s.va;
        const uint32_t extent = std::max(s.vsize, s.rawSize);
        if (rel > extent || size > extent - rel) continue;
        const size_t raw = rel < s.rawSize ? std::min(size, static_cast<size_t>(s.rawSize - rel)) : 0;
        auto* bytes = static_cast<uint8_t*>(out);
        if (raw) memcpy(bytes, file_.data() + static_cast<size_t>(s.rawOffset) + rel, raw);
        if (size > raw) memset(bytes + raw, 0, size - raw);
        return true;
    }
    return false;
}

uint32_t ExeImage::U32(uint32_t va) const {
    uint32_t v = 0;
    Read(va, &v, 4);
    return v;
}

int16_t ExeImage::S16(uint32_t va) const {
    int16_t v = 0;
    Read(va, &v, 2);
    return v;
}

std::string ExeImage::CStr(uint32_t va, size_t maxLen) const {
    std::string s;
    for (size_t i = 0; i < maxLen; ++i) {
        char c;
        if (i > std::numeric_limits<uint32_t>::max() - va) return {};
        if (!Read(va + static_cast<uint32_t>(i), &c, 1)) return {};
        if (c == 0) return s;
        if (c < 0x20 || c > 0x7E) return {};
        s += c;
    }
    return {};
}

bool ExeImage::InData(uint32_t va) const {
    return va >= 0x004C4000 && va < 0x009CC000;
}

std::vector<TextureFileEntry> ListTextureFiles(const ExeImage& exe) {
    std::vector<TextureFileEntry> out;
    int misses = 0;
    for (int n = 0; misses < 8; ++n) {
        const std::string name = exe.CStr(exe.U32(kNameTable + n * 4));
        if (name.size() > 4 && name.compare(name.size() - 4, 4, ".bin") == 0) {
            misses = 0;
            const uint32_t desc = exe.U32(kDescTable + n * 4);
            const int count = exe.S16(kCountTable + n * 2);
            if (exe.InData(desc) && count > 0 && count <= 4096) {
                out.push_back({ n, name, desc, count });
            }
        } else {
            ++misses;
        }
    }
    return out;
}

std::vector<std::array<uint16_t, 16>> LoadPaletteBanks(const ExeImage& exe) {
    constexpr uint32_t kPaletteTable = 0x0057A010;  // entries: s16 ?, s16 count (0x10), u32 colours*, s32 n
    std::vector<std::array<uint16_t, 16>> banks;
    for (;;) {
        const uint32_t e = kPaletteTable + 2 + static_cast<uint32_t>(banks.size()) * 12;
        const int16_t count = exe.S16(e);
        const uint32_t ptr = exe.U32(e + 2);
        int32_t n = 0;
        exe.Read(e + 6, &n, 4);
        if (count != 0x10 || n < 1 || !exe.InData(ptr)) break;
        std::array<uint16_t, 16> bank{};
        exe.Read(ptr, bank.data(), 32);
        banks.push_back(bank);
    }
    return banks;
}

TextureDesc ReadTextureDesc(const ExeImage& exe, const TextureFileEntry& f, int i) {
    TextureDesc d;
    uint8_t raw[16];
    exe.Read(f.descTable + i * 16, raw, 16);
    memcpy(&d.width, raw + 0, 2);
    memcpy(&d.height, raw + 2, 2);
    memcpy(&d.format, raw + 4, 4);
    memcpy(&d.offset, raw + 8, 4);
    memcpy(&d.id, raw + 12, 4);
    return d;
}

}  // namespace game
