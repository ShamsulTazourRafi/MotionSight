# MotionSight

MotionSight is a native Android R&D prototype that renders moving regions in white over a dim grayscale live camera view. It is optimized for the Pixel 7 and supports four detection profiles:

- Tripod + Crawling
- Tripod + Flying
- Handheld + Crawling
- Handheld + Flying

The prototype detects motion, not insects. It does not save or upload camera frames.

## Implemented

- Kotlin and Jetpack Compose application targeting API 36 with API 26 minimum support.
- Camera-only permission flow, first-use education, session setup, limitations, and recoverable error UI.
- Main camera composition based on Figma node `1:2`, including the exact exported Handheld and Tripod icons and local Poppins font.
- OpenGL ES two-texture renderer that combines the dim grayscale camera plane and accepted white motion mask in a fragment shader.
- CameraX `ImageAnalysis` using `YUV_420_888` and `KEEP_ONLY_LATEST`.
- OpenCV 4.14 fixed pipeline using MOG2, three-frame differencing, morphology, component filtering, and broad illumination suppression.
- Experimental handheld pipeline using feature tracking, RANSAC affine alignment, valid-border masking, residual motion, and unstable-frame rejection.
- Sensitivity, minimum region size, persistence, learning rate, stability, torch, zoom, and background-reset controls.
- FPS, processing latency, actual resolution, motion percentage, stabilization confidence, rejected frames, and rolling latency diagnostics.
- Automatic analysis rebind from a 1280 × 720 request to 960 × 540 after sustained over-budget processing.
- Battery-aware 15–20 FPS analysis scheduling, 960-pixel processing width, single-pass rendering, and reusable GPU upload buffers.
- Profile persistence, camera lifecycle cleanup, and temporal reset behavior.

## Build

Open the project in Android Studio or run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The Pixel 7 (`arm64-v8a`) debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The project intentionally packages only `arm64-v8a` native libraries. This
keeps local test APKs compact and matches the primary Pixel 7 target. To run
on an x86/x86_64 emulator, add that emulator ABI to `ndk.abiFilters` or use a
physical ARM64 device.

Release builds enable R8 code shrinking and resource shrinking. Store
distribution should use an Android App Bundle so each device receives only
its required native ABI.

Optimized artifacts:

```text
app/build/outputs/apk/debug/app-debug.apk              # installable ARM64 debug build
app/build/outputs/apk/release/app-release-unsigned.apk # shrunk, requires release signing
app/build/outputs/bundle/release/app-release.aab       # store bundle, requires release signing
```

## Operation

- Tap the live image to open controls.
- Long-press the live image to open diagnostics.
- Tap **CRAWLING** or **FLYING** at the top-left of the view to switch profiles.
- Tap the FPS readout to inspect diagnostics.
- Tap the Handheld or Tripod icon to change capture mode.
- Long-press the mode capsule to open controls.

For best results, mount the phone, use strong steady lighting, move close to the target, and use a plain background.

## Validation status

The project compiles and the included unit tests pass. Camera behavior and the PRD's recall, false-highlight, endurance, memory, and thermal targets still require measurement on a physical Pixel 7 with a labeled test set.

See [prd.md](prd.md), [architecture.md](architecture.md), and [appflow.md](appflow.md) for the full product and engineering specification.
