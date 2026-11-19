#include <SDL3/SDL.h>
#include <SDL3/SDL_main.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <memory>
#include <numbers>
#include <set>
#include <utility>

#include "probe_shaders.h"

namespace {

constexpr std::array<std::array<Uint8, 4>, 4> pattern{{
    {52, 197, 213, 255}, {241, 147, 75, 255},
    {99, 89, 201, 255}, {125, 207, 144, 255},
}};
constexpr std::array<float, 4> multitouchTint{0.80f, 1.0f, 0.45f, 1.0f};

bool ReportError(const char* operation) {
    SDL_LogError(SDL_LOG_CATEGORY_APPLICATION, "probe.error %s: %s", operation, SDL_GetError());
    return false;
}

struct Probe {
    SDL_Window* window = nullptr;
    SDL_GPUDevice* gpu = nullptr;
    SDL_GPUShader* vertex = nullptr;
    SDL_GPUShader* fragment = nullptr;
    SDL_GPUGraphicsPipeline* pipeline = nullptr;
    SDL_GPUTexture* image = nullptr;
    SDL_GPUSampler* sampler = nullptr;
    SDL_AudioStream* audio = nullptr;
    bool claimed = false;
    bool foreground = true;
    bool focused = true;
    bool confirmFrame = true;
    std::array<float, 4> tint{1, 1, 1, 1};
    std::set<std::pair<SDL_TouchID, SDL_FingerID>> contacts;

    ~Probe() {
        if (audio) SDL_DestroyAudioStream(audio);
        if (gpu) {
            if (pipeline) SDL_ReleaseGPUGraphicsPipeline(gpu, pipeline);
            if (sampler) SDL_ReleaseGPUSampler(gpu, sampler);
            if (image) SDL_ReleaseGPUTexture(gpu, image);
            if (vertex) SDL_ReleaseGPUShader(gpu, vertex);
            if (fragment) SDL_ReleaseGPUShader(gpu, fragment);
            if (claimed) SDL_ReleaseWindowFromGPUDevice(gpu, window);
            SDL_DestroyGPUDevice(gpu);
        }
        if (window) SDL_DestroyWindow(window);
        SDL_Quit();
    }

    bool Init() {
        if (!SDL_Init(SDL_INIT_VIDEO | SDL_INIT_GAMEPAD)) return ReportError("SDL_Init");
#ifdef SDL_PLATFORM_ANDROID
        window = SDL_CreateWindow("SDL Vulkan Probe", 960, 540, SDL_WINDOW_RESIZABLE);
        if (!window) return ReportError("SDL_CreateWindow");
#endif
        gpu = SDL_CreateGPUDevice(SDL_GPU_SHADERFORMAT_SPIRV, true, "vulkan");
        if (!gpu) return ReportError("SDL_CreateGPUDevice");
        if (window) {
            claimed = SDL_ClaimWindowForGPUDevice(gpu, window);
            if (!claimed) return ReportError("SDL_ClaimWindowForGPUDevice");
        }

        SDL_GPUShaderCreateInfo shader{};
        shader.format = SDL_GPU_SHADERFORMAT_SPIRV;
        shader.stage = SDL_GPU_SHADERSTAGE_VERTEX;
        shader.entrypoint = "vs_main";
        shader.code = vertexShader;
        shader.code_size = sizeof(vertexShader);
        vertex = SDL_CreateGPUShader(gpu, &shader);
        if (!vertex) return ReportError("vertex shader");

        shader.stage = SDL_GPU_SHADERSTAGE_FRAGMENT;
        shader.entrypoint = "ps_main";
        shader.code = fragmentShader;
        shader.code_size = sizeof(fragmentShader);
        shader.num_samplers = 1;
        shader.num_uniform_buffers = 1;
        fragment = SDL_CreateGPUShader(gpu, &shader);
        if (!fragment) return ReportError("fragment shader");

        SDL_GPUColorTargetDescription color{};
        color.format = window ? SDL_GetGPUSwapchainTextureFormat(gpu, window)
                              : SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
        SDL_GPUGraphicsPipelineCreateInfo info{};
        info.vertex_shader = vertex;
        info.fragment_shader = fragment;
        info.primitive_type = SDL_GPU_PRIMITIVETYPE_TRIANGLELIST;
        info.target_info.color_target_descriptions = &color;
        info.target_info.num_color_targets = 1;
        pipeline = SDL_CreateGPUGraphicsPipeline(gpu, &info);
        if (!pipeline) return ReportError("SDL_CreateGPUGraphicsPipeline");

        SDL_GPUSamplerCreateInfo sampling{};
        sampling.min_filter = SDL_GPU_FILTER_NEAREST;
        sampling.mag_filter = SDL_GPU_FILTER_NEAREST;
        sampler = SDL_CreateGPUSampler(gpu, &sampling);
        if (!sampler) return ReportError("SDL_CreateGPUSampler");
        if (!UploadPattern()) return false;

        SDL_Log("probe.ready driver=%s pointer_bits=%zu", SDL_GetGPUDeviceDriver(gpu), sizeof(void*) * 8);
        if (SDL_InitSubSystem(SDL_INIT_AUDIO)) {
            const SDL_AudioSpec spec{SDL_AUDIO_F32, 1, 48000};
            audio = SDL_OpenAudioDeviceStream(SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK, &spec, nullptr, nullptr);
            if (audio && SDL_ResumeAudioStreamDevice(audio)) {
                SDL_Log("probe.audio_ready driver=%s", SDL_GetCurrentAudioDriver());
            } else {
                ReportError("SDL audio stream");
                if (audio) SDL_DestroyAudioStream(audio);
                audio = nullptr;
            }
        } else {
            ReportError("SDL audio init");
        }
        return true;
    }

    bool UploadPattern() {
        SDL_GPUTextureCreateInfo texture{};
        texture.type = SDL_GPU_TEXTURETYPE_2D;
        texture.format = SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
        texture.usage = SDL_GPU_TEXTUREUSAGE_SAMPLER;
        texture.width = texture.height = 2;
        texture.layer_count_or_depth = texture.num_levels = 1;
        image = SDL_CreateGPUTexture(gpu, &texture);
        if (!image) return ReportError("SDL_CreateGPUTexture");

        SDL_GPUTransferBufferCreateInfo transfer{};
        transfer.usage = SDL_GPU_TRANSFERBUFFERUSAGE_UPLOAD;
        transfer.size = sizeof(pattern);
        auto* staging = SDL_CreateGPUTransferBuffer(gpu, &transfer);
        if (!staging) return ReportError("SDL_CreateGPUTransferBuffer");
        void* bytes = SDL_MapGPUTransferBuffer(gpu, staging, false);
        if (!bytes) {
            ReportError("SDL_MapGPUTransferBuffer");
            SDL_ReleaseGPUTransferBuffer(gpu, staging);
            return false;
        }
        SDL_memcpy(bytes, pattern.data(), sizeof(pattern));
        SDL_UnmapGPUTransferBuffer(gpu, staging);

        auto* command = SDL_AcquireGPUCommandBuffer(gpu);
        if (!command) {
            ReportError("texture upload command buffer");
            SDL_ReleaseGPUTransferBuffer(gpu, staging);
            return false;
        }
        auto* copy = SDL_BeginGPUCopyPass(command);
        SDL_GPUTextureTransferInfo source{};
        source.transfer_buffer = staging;
        SDL_GPUTextureRegion destination{};
        destination.texture = image;
        destination.w = destination.h = 2;
        destination.d = 1;
        SDL_UploadToGPUTexture(copy, &source, &destination, false);
        SDL_EndGPUCopyPass(copy);
        const bool submitted = SDL_SubmitGPUCommandBuffer(command);
        SDL_ReleaseGPUTransferBuffer(gpu, staging);
        return submitted || ReportError("texture upload submit");
    }

    void Activate() {
        tint = tint[0] == 1.0f ? std::array<float, 4>{0.45f, 0.65f, 0.9f, 1.0f}
                              : std::array<float, 4>{1, 1, 1, 1};
        confirmFrame = true;
        if (!audio) return;
        constexpr float sampleRate = 48000.0f;
        std::array<float, 5760> tone;
        for (std::size_t index = 0; index < tone.size(); ++index) {
            const float time = static_cast<float>(index);
            const float envelope = std::min({time / 480.0f, (tone.size() - time) / 480.0f, 1.0f});
            tone[index] = 0.08f * envelope * std::sin(time * (2.0f * std::numbers::pi_v<float> * 523.25f / sampleRate));
        }
        if (SDL_ClearAudioStream(audio) && SDL_PutAudioStreamData(audio, tone.data(), sizeof(tone))) {
            SDL_Log("probe.audio_queued samples=%zu", tone.size());
        } else {
            ReportError("queue tone");
        }
    }

    void UpdateContact(const SDL_TouchFingerEvent& event) {
        const auto contact = std::pair{event.touchID, event.fingerID};
        if (event.type == SDL_EVENT_FINGER_DOWN) {
            if (!foreground || !focused || !contacts.insert(contact).second) return;
            SDL_Log("probe.touch down active=%zu x=%.3f y=%.3f", contacts.size(), event.x, event.y);
            if (contacts.size() == 1) Activate();
        } else {
            if (!contacts.erase(contact)) return;
            const char* action = event.type == SDL_EVENT_FINGER_CANCELED ? "cancel" : "up";
            SDL_Log("probe.touch %s active=%zu", action, contacts.size());
        }
        confirmFrame = true;
    }

    void ClearContacts(const char* reason) {
        if (contacts.empty()) return;
        SDL_Log("probe.touch reset reason=%s previous=%zu active=0", reason, contacts.size());
        contacts.clear();
        confirmFrame = true;
    }

    const std::array<float, 4>& FrameTint() const {
        return contacts.size() >= 2 ? multitouchTint : tint;
    }

    void Draw(SDL_GPUCommandBuffer* command, SDL_GPUTexture* output, Uint32 width, Uint32 height) {
        SDL_GPUColorTargetInfo target{};
        target.texture = output;
        target.clear_color = {0.035f, 0.045f, 0.063f, 1.0f};
        target.load_op = SDL_GPU_LOADOP_CLEAR;
        target.store_op = SDL_GPU_STOREOP_STORE;
        auto* pass = SDL_BeginGPURenderPass(command, &target, 1, nullptr);
        const float w = static_cast<float>(width);
        const float h = static_cast<float>(height);
        const float visibleWidth = std::min(w, h * (4.0f / 3.0f));
        const float visibleHeight = visibleWidth * 0.75f;
        const SDL_GPUViewport viewport{(w - visibleWidth) / 2, (h - visibleHeight) / 2,
                                       visibleWidth, visibleHeight, 0, 1};
        SDL_SetGPUViewport(pass, &viewport);
        SDL_BindGPUGraphicsPipeline(pass, pipeline);
        const SDL_GPUTextureSamplerBinding binding{image, sampler};
        SDL_BindGPUFragmentSamplers(pass, 0, &binding, 1);
        const auto& frameTint = FrameTint();
        SDL_PushGPUFragmentUniformData(command, 0, frameTint.data(), sizeof(frameTint));
        SDL_DrawGPUPrimitives(pass, 3, 1, 0, 0);
        SDL_EndGPURenderPass(pass);
    }

#ifdef SDL_PLATFORM_WINDOWS
    bool VerifyOffscreen() {
        constexpr Uint32 width = 96;
        constexpr Uint32 height = 54;
        SDL_GPUTextureCreateInfo targetInfo{};
        targetInfo.format = SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
        targetInfo.usage = SDL_GPU_TEXTUREUSAGE_COLOR_TARGET;
        targetInfo.width = width;
        targetInfo.height = height;
        targetInfo.layer_count_or_depth = targetInfo.num_levels = 1;
        const auto releaseTexture = [this](SDL_GPUTexture* ptr) { SDL_ReleaseGPUTexture(gpu, ptr); };
        const std::unique_ptr<SDL_GPUTexture, decltype(releaseTexture)> target{
            SDL_CreateGPUTexture(gpu, &targetInfo), releaseTexture};
        if (!target) return ReportError("offscreen target");

        SDL_GPUTransferBufferCreateInfo transferInfo{};
        transferInfo.usage = SDL_GPU_TRANSFERBUFFERUSAGE_DOWNLOAD;
        transferInfo.size = width * height * 4;
        const auto releaseTransfer = [this](SDL_GPUTransferBuffer* ptr) { SDL_ReleaseGPUTransferBuffer(gpu, ptr); };
        const std::unique_ptr<SDL_GPUTransferBuffer, decltype(releaseTransfer)> readback{
            SDL_CreateGPUTransferBuffer(gpu, &transferInfo), releaseTransfer};
        if (!readback) return ReportError("readback buffer");

        auto* command = SDL_AcquireGPUCommandBuffer(gpu);
        if (!command) return ReportError("offscreen command buffer");
        Draw(command, target.get(), width, height);
        auto* copy = SDL_BeginGPUCopyPass(command);
        SDL_GPUTextureRegion source{};
        source.texture = target.get();
        source.w = width;
        source.h = height;
        source.d = 1;
        SDL_GPUTextureTransferInfo destination{};
        destination.transfer_buffer = readback.get();
        SDL_DownloadFromGPUTexture(copy, &source, &destination);
        SDL_EndGPUCopyPass(copy);
        if (!SDL_SubmitGPUCommandBuffer(command) || !SDL_WaitForGPUIdle(gpu)) {
            return ReportError("offscreen frame");
        }

        const auto* pixels = static_cast<const Uint8*>(SDL_MapGPUTransferBuffer(gpu, readback.get(), false));
        if (!pixels) return ReportError("map readback");
        constexpr std::array offsets{(13 * width + 25) * 4, (13 * width + 70) * 4,
                                     (40 * width + 25) * 4, (40 * width + 70) * 4};
        const auto matches = [&](Uint32 offset, const auto& color, const auto& scale) {
            for (std::size_t channel = 0; channel < color.size(); ++channel) {
                const auto expected = std::lround(color[channel] * scale[channel]);
                if (std::abs(expected - pixels[offset + channel]) > 1) return false;
            }
            return true;
        };
        const bool hasPattern = std::ranges::all_of(pattern, [&](const auto& color) {
            return std::ranges::any_of(offsets, [&](Uint32 offset) { return matches(offset, color, tint); });
        });
        const bool hasBars = matches(0, std::array{9, 11, 16, 255}, std::array{1, 1, 1, 1});
        SDL_UnmapGPUTransferBuffer(gpu, readback.get());
        SDL_Log("probe.offscreen %ux%u tint=%.2f pattern=%s bars=%s", width, height, tint[0],
                hasPattern ? "ok" : "bad", hasBars ? "ok" : "bad");
        return hasPattern && hasBars;
    }
#else
    bool Render() {
        auto* command = SDL_AcquireGPUCommandBuffer(gpu);
        if (!command) return ReportError("frame command buffer");
        SDL_GPUTexture* backBuffer = nullptr;
        Uint32 width = 0;
        Uint32 height = 0;
        if (!SDL_WaitAndAcquireGPUSwapchainTexture(command, window, &backBuffer, &width, &height)) {
            ReportError("acquire swapchain");
            SDL_CancelGPUCommandBuffer(command);
            return false;
        }
        if (!backBuffer) {
            SDL_CancelGPUCommandBuffer(command);
            SDL_Delay(16);
            return true;
        }
        Draw(command, backBuffer, width, height);
        if (!SDL_SubmitGPUCommandBuffer(command)) return ReportError("submit frame");
        if (confirmFrame) {
            SDL_Log("probe.frame %ux%u tint=%.2f contacts=%zu", width, height, FrameTint()[0], contacts.size());
            confirmFrame = false;
        }
        return true;
    }
#endif
};

}  // namespace

int main(int, char**) {
    static_assert(sizeof(void*) == 8);
    SDL_SetHint(SDL_HINT_TOUCH_MOUSE_EVENTS, "0");
#ifdef SDL_PLATFORM_ANDROID
    SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft LandscapeRight");
#endif
    Probe probe;
    if (!probe.Init()) return 1;

#ifdef SDL_PLATFORM_WINDOWS
    if (!probe.VerifyOffscreen()) return 1;
    probe.Activate();
    return probe.VerifyOffscreen() ? 0 : 1;
#else
    while (true) {
        SDL_Event event;
        while (SDL_PollEvent(&event)) {
            switch (event.type) {
            case SDL_EVENT_QUIT:
            case SDL_EVENT_WINDOW_CLOSE_REQUESTED:
                return 0;
            // SDL dispatches app lifecycle events to watchers. These companion
            // window events are queued for SDL_PollEvent on Android.
            case SDL_EVENT_WINDOW_MINIMIZED:
                if (probe.foreground) SDL_Log("probe.background");
                probe.foreground = false;
                probe.ClearContacts("background");
                if (probe.audio) SDL_ClearAudioStream(probe.audio);
                break;
            case SDL_EVENT_WINDOW_RESTORED:
                SDL_Log("probe.foreground");
                probe.foreground = true;
                probe.confirmFrame = true;
                break;
            case SDL_EVENT_WINDOW_FOCUS_LOST:
                probe.focused = false;
                probe.ClearContacts("focus");
                if (probe.audio) SDL_ClearAudioStream(probe.audio);
                break;
            case SDL_EVENT_WINDOW_FOCUS_GAINED:
                probe.focused = true;
                break;
            case SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED:
                probe.confirmFrame = true;
                break;
            case SDL_EVENT_FINGER_DOWN:
            case SDL_EVENT_FINGER_UP:
            case SDL_EVENT_FINGER_CANCELED:
                probe.UpdateContact(event.tfinger);
                break;
            case SDL_EVENT_GAMEPAD_BUTTON_DOWN:
                SDL_Log("probe.gamepad button=%u", static_cast<unsigned>(event.gbutton.button));
                probe.Activate();
                break;
            }
        }
        if (probe.foreground && probe.confirmFrame) {
            if (!probe.Render()) return 1;
        } else {
            SDL_Delay(16);
        }
    }
#endif
}
