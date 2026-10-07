# MusicBabe 🎵

<p align="center">
  <strong>Next-Gen Android Music Player</strong><br>
  Crafted with Jetpack Compose, Material 3 Expressive, and Core Motion Physics.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-11%2B-green?style=for-the-badge&logo=android" alt="Android 11+">
  <img src="https://img.shields.io/badge/Kotlin-100%25-purple?style=for-the-badge&logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Compose-Material%203%20Expressive-blue?style=for-the-badge&logo=jetpackcompose" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/Performance-120%20FPS%20Zero--Jank-orange?style=for-the-badge" alt="120 FPS">
</p>

---

## ✨ Highlights

### ⚡ 120 FPS Zero-Jank Architecture
- **Phase Confinement**: High-frequency progression states, bottom-sheet drag offsets, and scrolling values are isolated strictly to layout (`.offset { ... }`) and draw (`graphicsLayer { ... }`) phases.
- **Compose Strong Skipping**: Domain models and UI state holders are configured with explicit stability annotations, skipping redundant recompositions during scrolling and sheet gestures.
- **Linear Sheet Expansion**: Seamless transition between MiniPlayer and FullPlayer powered entirely by hardware-accelerated layer interpolation.

### 🎨 Material 3 Expressive & Monet
- **Dynamic Cover Art Palette**: Accurate color extraction mapping into full Material 3 roles (`primaryContainer`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`).
- **Smooth Palette Transitions**: 500ms continuous color transitions (`LinearOutSlowInEasing`) across the interface as tracks change.
- **Expressive Shapes**: Pill contours (`CircleShape`, `28.dp`), tonal surface layering, and soft elevation styling.

### 🌊 Core Motion Physics (Spring Specs)
- **Interactive Micro-Bounces**: Play/Pause, Shuffle, Repeat, and Favorite buttons react with an organic spring pop (`dampingRatio = MediumBouncy, stiffness = Low`) down to `0.94f` scale.
- **Harmonic Wavy Slider**: Audio wave dynamically animates during playback and smoothly flattens into a line via spring motion upon pausing or seeking. Contact thumb expands to `1.25f` during scrubbing.
- **AOSP Shared Axis Transitions**: Natural, critically-damped spring navigation (`NoBouncy, StiffnessMediumLow`) between root and detail screens.
- **Tactile Haptic Feedback**: Light, modern micro-vibrations (`TextHandleMove`) on seekbar scrub steps, carousel snapping, and quick toggles.

---

## 🛠️ Tech Stack
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 Expressive APIs
- **Audio Engine**: [AndroidX Media3 (ExoPlayer)](https://developer.android.com/media/media3)
- **Dependency Injection**: [Hilt](https://dagger.dev/hilt/)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
- **Database & Storage**: [Room](https://developer.android.com/training/data-storage/room) & DataStore

---

## 🚀 Building & Running

### Requirements
- Android Studio Ladybug (2024.2.1) or newer
- JDK 21
- Android SDK 35+ (Min SDK: Android 11 / API 30)

### Quick Build
```bash
./gradlew assembleDebug
```

---

## 📄 License
This project is licensed under the [MIT License](LICENSE).
