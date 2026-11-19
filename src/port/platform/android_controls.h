#pragma once

// These bits also identify the actions in GameActivity.
enum AndroidControl : unsigned {
    kAndroidContinue = 1u << 0,
    kAndroidReload = 1u << 1,
    kAndroidEscape = 1u << 2,
};

void SetAndroidAvailableControls(unsigned controls);
unsigned TakeAndroidControlRequests();
