#include "assets/asset_override.h"

#include <algorithm>
#include <atomic>
#include <cctype>
#include <cstdlib>
#include <filesystem>

namespace port {

namespace {

std::string& Dir() {
    static std::string dir;
    static bool init = false;
    if (!init) {
        init = true;
        if (const char* e = getenv("HOTD2_ASSET_DIR"); e && *e) dir = e;
    }
    return dir;
}

std::atomic<int> g_hits{ 0 };  // AssetStore's prefetch threads look overrides up too

#ifndef _WIN32
bool SameAssetName(const std::string& a, const std::string& b) {
    return std::equal(a.begin(), a.end(), b.begin(), b.end(), [](unsigned char x, unsigned char y) {
        return std::tolower(x) == std::tolower(y);
    });
}
#endif

}  // namespace

const std::string& AssetDir() { return Dir(); }

void SetAssetDir(const std::string& dir) {
    if (const char* e = getenv("HOTD2_ASSET_DIR"); e && *e) return;
    Dir() = dir;
}

std::string AssetOverride(const std::string& rel) {
    const std::string& dir = Dir();
    if (dir.empty()) return {};
    std::string r = rel;
    std::replace(r.begin(), r.end(), '\\', '/');
    std::filesystem::path p = std::filesystem::path(dir) / r;
    std::error_code ec;
    if (!std::filesystem::is_regular_file(p, ec)) return {};
    ++g_hits;
    return p.string();
}

int AssetOverrideHits() { return g_hits; }

std::string GamePath(const std::string& gameDir, const std::string& rel) {
    std::string p = gameDir;
    if (!p.empty() && p.back() != '/' && p.back() != '\\') p += '/';
    p += rel;
    std::replace(p.begin(), p.end(), '\\', '/');
#ifndef _WIN32
    // PC data contains mixed-case names and Windows resolves them case-insensitively.
    std::error_code ec;
    if (!std::filesystem::exists(p, ec)) {
        std::string relative = rel;
        std::replace(relative.begin(), relative.end(), '\\', '/');
        std::filesystem::path resolved = gameDir.empty() ? "." : gameDir;
        for (const auto& component : std::filesystem::path(relative)) {
            const auto candidate = resolved / component;
            if (std::filesystem::exists(candidate, ec)) {
                resolved = candidate;
                continue;
            }
            const std::filesystem::directory_iterator end;
            std::filesystem::directory_iterator entry(resolved, ec);
            for (; !ec && entry != end; entry.increment(ec)) {
                if (SameAssetName(entry->path().filename().string(), component.string())) break;
            }
            if (ec || entry == end) return p;
            resolved = entry->path();
        }
        return resolved.string();
    }
#endif
    return p;
}

std::string SoundPath(const std::string& gameDir, const std::string& rel) {
    if (std::string o = AssetOverride(rel); !o.empty()) return o;
    return GamePath(gameDir, rel);
}

}  // namespace port
