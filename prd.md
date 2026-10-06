# MotionSight Product Requirements Document

## 1. Document Control

| Field | Value |
|---|---|
| Product | MotionSight |
| Platform | Native Android |
| Product stage | R&D prototype / feasibility validation |
| Primary test device | Google Pixel 7, rear main camera |
| Minimum OS | Android 8.0 (API 26) |
| Document status | Draft for screen design and implementation |
| Version | 1.0 |

### Visual source of truth

The main-screen visual reference is Figma node `1:2` in [MotionSight](https://www.figma.com/design/2i1RDquvk1Qjwts2bZuNbg/MotionSight?node-id=1-2). It is the canonical design for `S-04 Live Motion Camera`. All other screens must extend its visual language: near-black surfaces, restrained monochrome content, generous spacing, large rounded camera geometry, minimal chrome, and floating pill-shaped controls.

When written requirements and visual styling appear to conflict, this PRD governs behavior, accessibility, states, and required capabilities; the Figma reference governs visual direction and the main-screen composition. Any necessary deviation must be documented rather than silently introduced.

## 2. Product Summary

MotionSight is an on-device Android camera prototype that makes movement easier to see. The live camera view is rendered in dim grayscale while regions classified as moving are rendered white.

Version 1 detects motion only. It does not identify insects, determine species, or distinguish insects from other moving objects such as dust, shadows, foliage, people, or screen flicker.

The product is designed to answer one research question:

> Can a Pixel 7 highlight small crawling and flying insects in real time with useful accuracy and acceptable performance, particularly when the phone is fixed?

## 3. Problem Statement

Small insects can be difficult to notice in a normal camera preview because of their size, camouflage, speed, and visually complex backgrounds. Motion contrast can make them more visible, but apparent motion can also be caused by camera movement, parallax, autofocus, exposure changes, shadows, and lighting flicker.

The prototype must provide a fast, legible motion-enhanced view while exposing enough controls and diagnostics to compare detection approaches under repeatable conditions.

## 4. Goals

### 4.1 Product goals

- Highlight moving regions in white over a dim grayscale live camera view.
- Support fixed-camera and experimental handheld operation.
- Provide crawling and flying presets suited to different motion characteristics.
- Keep camera capture, analysis, rendering, and diagnostics responsive during continuous use.
- Give testers direct access to the controls required to tune and compare detection behavior.
- Collect or expose sufficient measurements to determine whether semantic insect classification is justified as a next phase.
- Perform all image processing on the device without saving or transmitting camera media.

### 4.2 Research goals

- Compare frame differencing, adaptive background subtraction, combined temporal detection, and stabilized residual motion.
- Measure detection quality, false motion, latency, dropped frames, thermal behavior, and stability.
- Establish practical operating limits for fixed and handheld capture.
- Identify the winning configuration for each supported mode and preset.

## 5. Non-goals

- Insect recognition, species classification, or object labeling.
- A guarantee that every white region represents an insect.
- Reliable detection of stationary insects.
- Cloud processing, accounts, synchronization, sharing, or remote APIs.
- Photo or video capture, media storage, galleries, or export.
- Support for every Android device or camera lens during the R&D phase.
- Production analytics, advertising, subscriptions, or monetization.
- Background monitoring while the app is not visible.

## 6. Target Users

### 6.1 Primary: R&D tester

A technical or field tester evaluating whether motion contrast can reveal small insects. The tester needs quick mode switching, tunable detection parameters, clear performance diagnostics, and repeatable test behavior.

### 6.2 Secondary: Observer

A person using the camera to visually locate movement. The observer needs a simple live view, a clear explanation of what white regions mean, and warnings when the result is unreliable.

## 7. Core Concepts

### 7.1 Capture mode

- **Fixed:** The phone is mounted or held still. This is the primary and highest-confidence mode.
- **Handheld:** The phone moves slowly and smoothly. Frames are aligned before residual motion is calculated. This mode is experimental and intentionally less sensitive.

### 7.2 Target preset

- **Crawling:** Favors persistence, slow movement, and slower background adaptation.
- **Flying:** Favors brief movement, smaller regions, short persistence, strong lighting, and reduced exposure time where supported.

### 7.3 Detection profile

The active experience is one of four combinations:

1. Fixed + Crawling
2. Fixed + Flying
3. Handheld + Crawling
4. Handheld + Flying

## 8. User Stories

- As a first-time user, I want to understand that the app highlights motion rather than identifying insects so that I interpret the view correctly.
- As a user, I want to grant camera permission in context so that I understand why it is required.
- As a tester, I want to choose whether the camera is fixed or handheld so that the correct processing pipeline is used.
- As a tester, I want to choose crawling or flying behavior so that detection timing matches the target.
- As an observer, I want moving areas to appear clearly in white so that subtle movement is easy to notice.
- As a tester, I want to tune sensitivity and minimum object size so that I can trade recall against noise.
- As a tester, I want to reset the learned background so that I can recover quickly after changing the scene.
- As a user, I want zoom and torch controls so that I can improve subject size and lighting.
- As a tester, I want FPS, processing time, actual analysis resolution, and motion percentage so that I can judge system performance.
- As a user, I want a camera-instability warning so that I know when handheld output is not trustworthy.
- As a privacy-conscious user, I want assurance that no camera images or video are saved or uploaded.

## 9. Functional Requirements

### 9.1 First launch and permission

| ID | Requirement | Priority | Acceptance criteria |
|---|---|---|---|
| FR-001 | Explain the prototype's purpose and limitations before entering the camera. | Must | Copy states that white means detected motion, not a confirmed insect, and that stationary insects are not detected. |
| FR-002 | Request only Android camera permission. | Must | The app requests no storage, microphone, location, or network-related runtime permission. |
| FR-003 | Explain on-device privacy. | Must | The permission experience states that frames are processed on-device and are not saved. |
| FR-004 | Handle permission denial. | Must | A denied state explains the dependency, supports retry, and offers a route to system settings after permanent denial. |

### 9.2 Session setup

| ID | Requirement | Priority | Acceptance criteria |
|---|---|---|---|
| FR-010 | Let the user select Fixed or Handheld mode. | Must | Exactly one capture mode is active before a session begins. |
| FR-011 | Let the user select Crawling or Flying preset. | Must | Exactly one target preset is active before a session begins. |
| FR-012 | Communicate mode-specific guidance. | Must | Fixed recommends mounting the phone; Handheld recommends slow, smooth movement and is labeled experimental. |
| FR-013 | Preserve the most recent selection during the current app installation. | Should | Returning users see their previous mode and preset preselected. |

### 9.3 Live motion view

| ID | Requirement | Priority | Acceptance criteria |
|---|---|---|---|
| FR-020 | Show a live rear-main-camera preview in dim grayscale. | Must | The preview remains visible and responsive during analysis. |
| FR-021 | Render accepted motion-mask pixels in white. | Must | Motion overlay remains spatially aligned with the preview. |
| FR-022 | Warm up and initialize the detection model. | Must | A visible preparing state is shown until temporal state is ready; uninitialized output is not presented as valid detection. |
| FR-023 | Display the active capture mode and target preset. | Must | Both are visible or reachable without leaving the live view. |
| FR-024 | Allow the user to switch mode or preset. | Must | A switch resets temporal state and clearly re-enters the preparing state. |
| FR-025 | Suppress invalid full-frame motion events. | Must | Exposure flashes and rejected stabilization frames do not whiten most of the preview as if valid. |
| FR-026 | Warn when the camera is unstable. | Must | Low stabilization confidence, rapid movement, or strong parallax produces a prominent warning and rejected/degraded output. |
| FR-027 | Reset temporal state after invalidating camera changes. | Must | Rotation, lens change, zoom change, app resume, or a large scene transition triggers a reset. |

### 9.4 Controls

| ID | Requirement | Priority | Acceptance criteria |
|---|---|---|---|
| FR-030 | Provide a sensitivity control. | Must | Changes affect subsequent motion detection and expose a clear current value. |
| FR-031 | Provide a minimum-object-size control. | Must | Regions smaller than the chosen threshold are excluded from the rendered mask. |
| FR-032 | Provide a background reset action. | Must | Reset clears temporal models and shows the preparing state before valid output resumes. |
| FR-033 | Provide torch control when supported. | Must | State is clear; unsupported or unavailable states are disabled with an explanation. |
| FR-034 | Provide zoom control within the selected camera's supported range. | Must | Changing zoom resets temporal state and the shown zoom value matches the applied value. |
| FR-035 | Show current motion percentage. | Must | The value represents the percentage of accepted analysis pixels marked as moving. |
| FR-036 | Provide advanced motion settings for R&D use. | Should | Persistence, learning rate, and instability threshold can be viewed and adjusted within safe ranges. |
| FR-037 | Restore preset defaults. | Should | One action restores all tunable motion settings for the active profile. |

### 9.5 Diagnostics

| ID | Requirement | Priority | Acceptance criteria |
|---|---|---|---|
| FR-040 | Show current analysis FPS. | Must | Display updates during a live session without materially affecting performance. |
| FR-041 | Show processing duration. | Must | Display includes a rolling or recent per-frame processing time in milliseconds. |
| FR-042 | Show the actual selected analysis resolution. | Must | The app reports the resolution delivered by CameraX, not merely the requested value. |
| FR-043 | Show stabilization confidence in handheld mode. | Should | The diagnostic is available in the expanded diagnostics view. |
| FR-044 | Show degraded-performance state. | Must | Resolution fallback or sustained frame-processing pressure is visible to the tester. |
| FR-045 | Make diagnostics collapsible. | Should | The observer can preserve an uncluttered camera view while the tester can inspect detail. |

### 9.6 Information and safety messaging

| ID | Requirement | Priority | Acceptance criteria |
|---|---|---|---|
| FR-050 | Provide a concise How It Works and Limitations view. | Should | It covers false positives, stationary targets, fixed-camera preference, lighting, distance, blur, and handheld limitations. |
| FR-051 | Keep claims calibrated. | Must | UI never calls a detected region an insect or presents a species result. |

## 10. User Experience Requirements

- Camera imagery is the dominant surface; controls must not unnecessarily obscure the target area.
- The main live-camera screen must reproduce the composition and design language of Figma node `1:2` in the MotionSight file.
- The primary surface remains deliberately sparse. Secondary camera controls, tuning, warnings, and diagnostics should appear contextually or through progressive disclosure rather than permanently surrounding the preview.
- White is reserved for detected motion in the camera image. UI controls must remain distinguishable without being confused with detections.
- Instability, initialization, permission, and unsupported-camera states must never be represented only by color.
- Primary controls must be usable one-handed in portrait orientation on a Pixel 7.
- The live view must support portrait and landscape rotation; rotation resets temporal detection state.
- Advanced R&D controls and dense diagnostics should be progressively disclosed.
- A user must be able to start a configured session from launch in no more than three intentional actions after permission has been granted.
- Labels should use plain language. Technical terms such as MOG2, RANSAC, and morphology belong in diagnostics or documentation, not the primary observer flow.

The canonical screens and transitions are defined in [appflow.md](appflow.md).

## 11. Non-functional Requirements

### 11.1 Performance

- Request 1280 × 720 analysis at 30 FPS.
- Use backpressure strategy `KEEP_ONLY_LATEST`.
- Automatically fall back to a requested 960 × 540 analysis size when processing repeatedly exceeds 33 ms.
- Median processing duration target: below 33 ms.
- 95th-percentile processing duration target: below 50 ms.
- Maintain a responsive preview during a continuous 10-minute run.
- Avoid unbounded memory growth.
- Surface dropped-frame and thermal degradation in validation results.

### 11.2 Privacy and security

- Process frames entirely on-device.
- Do not save images, video, or motion masks.
- Do not transmit camera frames or derived visual data.
- Do not require a user account.
- Request only the camera runtime permission.
- Do not add analytics or crash uploads to the R&D build unless separately approved and documented.

### 11.3 Compatibility

- Primary validation target: Pixel 7 rear main camera.
- Minimum supported platform: Android 8/API 26.
- The app must inspect and report the actual CameraX-selected resolution.
- Unsupported torch, exposure, frame rate, or resolution behavior must degrade gracefully.

### 11.4 Reliability

- Analyzer failures must not block camera cleanup or leave the app in a frozen session.
- Returning from background must restart or rebind the camera safely and reset temporal state.
- Camera disconnect, initialization failure, and unavailable-camera cases require recoverable error states.

## 12. Detection Behavior Requirements

### 12.1 Fixed mode

1. Warm up the camera.
2. Initialize an adaptive MOG2 background model.
3. Combine the MOG2 foreground result with short-term three-frame differencing.
4. Apply noise filtering and morphological cleanup.
5. Filter connected components by minimum size.
6. Detect and suppress full-frame exposure or illumination flashes.
7. Produce the final motion mask and metrics.

### 12.2 Handheld mode

1. Detect background features in consecutive frames.
2. Estimate global movement using RANSAC.
3. Align the frames.
4. Exclude warped or invalid border pixels.
5. Calculate residual motion.
6. Reject frames with inadequate alignment confidence.
7. Prefer an instability warning over a misleading near-full-frame mask.

### 12.3 Preset behavior

- Crawling uses longer persistence and slower background learning.
- Flying uses shorter persistence, accepts smaller regions, recommends brighter lighting, and uses reduced exposure time where Camera2 interoperability permits it.
- Exact defaults are configuration values to be calibrated during validation, not hard-coded product claims.

## 13. Success Metrics and Go/No-go Criteria

| Area | Target |
|---|---|
| Fixed + Crawling | At least 90% event recall for insects 10+ pixels across |
| Fixed + Flying | At least 80% event recall when visible for at least two frames and approximately 6+ pixels across |
| Handheld modes | At least 70% event recall during slow, smooth movement |
| Handheld false highlighting | Fewer than 10% of pixels falsely white in accepted frames |
| Median processing | Below 33 ms |
| P95 processing | Below 50 ms |
| Endurance | Responsive continuous preview for 10 minutes with no unbounded memory growth |

Supporting measures:

- Event recall.
- False events per minute.
- Percentage of falsely highlighted pixels.
- Processing latency distribution.
- Analysis FPS and dropped-frame rate.
- Stabilization rejection rate.
- Device temperature and thermal throttling behavior.

## 14. Validation Plan

Create a labeled Pixel 7 test set spanning:

- Crawling and flying insects.
- Fixed and handheld capture.
- Indoor and outdoor light.
- Textured and plain backgrounds.
- Shadows, foliage, screen flicker, and empty scenes.
- Close and distant targets, including known difficult cases.

Run simple frame differencing, MOG2, combined temporal detection, and stabilized residual motion against identical clips or repeatable scenarios. Report results by profile rather than as a single aggregate score.

## 15. Deliverables

- Installable Android R&D build.
- Source code with separated capture, detection, stabilization, rendering, settings, and diagnostics responsibilities.
- Reproducible test set and evaluation procedure.
- Feasibility report containing:
  - Winning configuration by mode.
  - Results against each go/no-go target.
  - Representative screenshots.
  - False-positive and failure cases.
  - Latency, dropped-frame, memory, and thermal measurements.
  - Recommendation on whether to proceed to insect classification.

## 16. Release Scope

### R&D Version 1

- Camera permission and education.
- Four motion profiles.
- Real-time grayscale preview and white motion mask.
- Essential and advanced tuning controls.
- Diagnostics and instability feedback.
- On-device-only, non-recording behavior.
- Pixel 7 validation report.

### Potential follow-up, contingent on feasibility

- Region-of-interest tracking.
- Insect-versus-non-insect classification operating on detected regions.
- Broader device compatibility.
- Guided calibration and simplified consumer controls.
- Optional research-session recording or metric export, subject to a separate privacy review.

## 17. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Camera motion overwhelms target motion | High | Treat handheld as experimental; stabilize, reject low-confidence frames, and show an instability warning. |
| Slow targets are absorbed into the background | High | Combine adaptive background subtraction with short-term frame differencing and crawling persistence. |
| Exposure/autofocus changes create full-frame motion | High | Detect broad illumination changes and suppress invalid masks. |
| Target is too small or blurred | High | Encourage fixed capture, close focus, zoom, strong lighting, and plain backgrounds. |
| Processing exceeds the frame budget | High | Keep only the latest frame, reuse buffers, measure each stage, and fall back in resolution. |
| UI overstates certainty | Medium | Use “motion” language consistently and display limitations before first use. |
| Device-specific camera behavior | Medium | Validate on Pixel 7, inspect actual capabilities and resolution, and degrade unsupported controls gracefully. |
| Thermal throttling during long sessions | Medium | Track sustained performance, minimize allocations, and surface degraded state in diagnostics. |

## 18. Assumptions and Open Decisions

### Assumptions

- “Bug” includes visible crawling insects and small flying insects.
- The initial build is an internal or limited R&D distribution.
- Pixel 7 portrait use is the primary design target, with landscape supported.
- Default settings will be calibrated through testing rather than selected solely from theory.

### Open decisions

- Exact default value and allowed range for each motion setting.
- Duration and visual treatment of camera warm-up/background learning.
- Whether the R&D build needs local numeric-result export; it is excluded unless explicitly added.
- Whether resolution fallback can be applied without rebinding on the selected CameraX version or requires a controlled camera rebind.
- Whether manual/limited auto-exposure control is stable enough to enable for the flying preset.

## 19. References

- [CameraX ImageAnalysis](https://developer.android.com/media/camera/camerax/analyze)
- [CameraX resolution behavior](https://developer.android.com/reference/androidx/camera/core/ImageAnalysis.Builder)
- [OpenCV background subtraction](https://docs.opencv.org/5.0/tutorials/others/background_subtraction.html)
- [OpenCV optical flow](https://docs.opencv.org/4.x/d4/dee/tutorial_optical_flow.html)
- [Motion-informed insect detection study](https://pmc.ncbi.nlm.nih.gov/articles/PMC10459366/)
