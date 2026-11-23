// SDL loads its Vulkan library by path and looks up vkGetInstanceProcAddr there.
// A custom driver is reached through a separate libvulkan instance opened by
// libadrenotools, so this library forwards SDL's lookups to that instance.

#include <stddef.h>

typedef void (*hotd2_vk_void_function)(void);
typedef hotd2_vk_void_function (*hotd2_vk_get_instance_proc_addr)(void* instance, const char* name);

static hotd2_vk_get_instance_proc_addr forward;

__attribute__((visibility("default"))) void hotd2_vulkan_shim_set(hotd2_vk_get_instance_proc_addr target) {
    forward = target;
}

__attribute__((visibility("default"))) hotd2_vk_void_function vkGetInstanceProcAddr(void* instance, const char* name) {
    return forward ? forward(instance, name) : NULL;
}
