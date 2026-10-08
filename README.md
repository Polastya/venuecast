# VenueCast V4

VenueCast V4 is a development prototype for **wired Android Auto phone-screen projection to a car-app surface**. It is intended for personal/device testing, not as a Play Store production app.

## What is included

- Phone-side `MediaProjection` screen capture.
- Foreground `mediaProjection` service for Android 14+ compatibility.
- Android Auto `CarAppService` with a projected surface.
- Surface lifecycle bridge so Android Auto and the phone-side mirror can connect in either order.
- 720p / 30 FPS and 1080p / 30 FPS output targets.
- Optional Android Accessibility gesture executor for future compatible input transport.
- Car-speed permission and safety state. Visual output is withheld until speed data is available and is paused when the car is detected moving.
- GitHub Actions workflow that builds a debug APK automatically in the cloud.

## Important technical limitation

The Android Auto surface API is a rendering surface; it is not a generic raw-touch pipe. V4 therefore includes the phone-side Accessibility gesture executor as a separate component, but does **not** claim that Venue touch coordinates can be injected into arbitrary phone apps through the public Car App API.

DRM-protected apps may also reject or blank screen-capture output. That must be tested per app/device; no DRM bypass is included.

## Build without Android Studio

1. Create an empty GitHub repository named `VenueCast`.
2. Upload this entire project folder to the repository.
3. Open the repository's **Actions** tab.
4. Run **Build VenueCast APK** manually if it did not run automatically.
5. Open the completed workflow run and download the **VenueCast-V4-debug-apk** artifact.
6. Extract the ZIP and install `app-debug.apk` on the Android phone.

## Phone setup

1. Install VenueCast.
2. Connect the phone to the Venue by USB and make sure Android Auto starts normally.
3. Open VenueCast on the phone.
4. In the car, open VenueCast and use **ENABLE SAFETY** while parked.
5. Back on the phone, tap **START MIRROR** and approve Android's screen-capture prompt.
6. The car surface should change to the mirrored phone display when the car-speed API reports the vehicle is stationary.

## Debugging

The first test should be a simple, non-DRM screen such as the phone home screen or a local test image. Do not use protected streaming services as the first compatibility test.

## Version scope

V4 combines the previous milestone goals into one codebase. It does not bypass Android Auto safety or DRM restrictions, and it should be treated as experimental until tested on the exact Venue/Android Auto/phone combination.
