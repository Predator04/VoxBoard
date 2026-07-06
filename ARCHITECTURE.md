# VoxBoard — Architecture & Implementation Plan

## Overview
Open-source Android keyboard built on FlorisBoard foundation. Privacy-first, deep theming, on-device Whisper voice input, swipe/glide typing, modern Material 3 design.

**Package:** `com.voxboard.keyboard`
**Min SDK:** 26 (Android 8.0)
**Target SDK:** 35 (Android 15)
**Language:** Kotlin (primary), Java (legacy from FlorisBoard)
**UI:** Jetpack Compose (new), XML Views (legacy, to migrate)
**License:** Apache 2.0

---

## Phase 0 — Foundation (Week 1)

### Day 1-2: Environment Setup
- [ ] Install Android Studio on Windows
- [ ] Install Android SDK 34+35, platform tools
- [ ] Create emulator (Pixel 8, API 34)
- [ ] Flutter/Kotlin Multiplatform? No — native Android

### Day 3-4: Fork & Strip FlorisBoard
- [ ] Clone FlorisBoard latest release tag (v0.5.2)
- [ ] Remove FlorisBoard branding/package name → `com.voxboard.keyboard`
- [ ] Strip unnecessary features: clipboard history (keep basic), extensions, backup system
- [ ] Keep: keyboard rendering, dictionary, prediction engine, gesture handling base, settings framework
- [ ] Rename app name, launcher icon, about page

### Day 5-7: First Buildable APK
- [ ] Get it compiling under `com.voxboard.keyboard`
- [ ] Generate debug signing key
- [ ] Install on emulator, verify keyboard functions
- [ ] Set up GitHub Actions CI (build + lint)

---

## Phase 1 — Theming Engine (Week 2-3)

### Core Theming (week 2)
- [ ] Theme data model: JSON schema with colors, fonts, key radius, shadow, backgrounds
- [ ] Theme manager class — load, apply, persist themes
- [ ] Compose theming layer over FlorisBoard's XML rendering
- [ ] Material You (Monet) — auto color from wallpaper
- [ ] Preset themes: Dark, Light, Neon, Cyberpunk, Minimal, Ocean, Sunset

### Background Support (week 2)
- [ ] Solid colors (any hex)
- [ ] Linear/radial gradients (2-4 stops)
- [ ] Image backgrounds (from gallery)
- [ ] Blur effect on background images
- [ ] Key transparency/opacity control

### Key Customization (week 3)
- [ ] Per-key text color
- [ ] Key background color
- [ ] Key font family (system + custom TTF)
- [ ] Key border radius (0px - round)
- [ ] Key shadow depth and color
- [ ] Key press animation: scale, color pop, ripple variants
- [ ] Key label font size

### Theme File System
- [ ] Export theme as .vboxtheme (zip of JSON + assets)
- [ ] Import theme from file
- [ ] Theme preview in settings
- [ ] Community theme store v1 — GitHub repo-based? Or simple JSON catalog serverless

---

## Phase 2 — Voice Input (Week 3-4)

### Day 1-2: SpeechRecognizer Integration
- [ ] Voice button on keyboard (microphone key)
- [ ] Android SpeechRecognizer integration (fallback)
- [ ] UI: mic button animates on listen, show transcription in overlay
- [ ] Insert transcribed text at cursor
- [ ] Language detection from current keyboard language

### Day 3-5: Whisper.cpp Integration
- [ ] Build Whisper.cpp for Android (ARM64, ARM32, x86_64)
- [ ] Download Tiny model (~75MB) on first use
- [ ] JNI bridge: Java → C++ Whisper
- [ ] Real-time audio capture from mic
- [ ] Whisper transcription callback → insert text
- [ ] Auto-punctuation (Whisper does this naturally)
- [ ] Batch mode: hold button, speak, release → transcribe

### Day 6-7: Voice Settings
- [ ] Toggle: SpeechRecognizer vs Whisper (on-device)
- [ ] Whisper model selection: Tiny (~75MB) vs Base (~150MB)
- [ ] Language selection for voice
- [ ] Push-to-talk button customization

---

## Phase 3 — Swipe/Gesture Typing (Week 4-6)

This is the hardest part. FlorisBoard doesn't have it.

### Approach
- [ ] Implement gesture trail rendering (canvas/Compose)
- [ ] Touch event interception for swipe (start on key, drag across keys)
- [ ] Key sequence recording: capture key path during swipe
- [ ] Path smoothing + bezier curve rendering

### Word Detection
- [ ] Dictionary lookup of key sequence
- [ ] Levenshtein distance for fuzzy matching
- [ ] Scoring algorithm: distance from key center, key press duration
- [ ] Top 3 suggestions in suggestion strip

### Integration
- [ ] Seamless mix: tap to type normally, swipe to gesture
- [ ] Correct detection of tap vs swipe (threshold-based)
- [ ] Visual feedback: trail line with color matching theme

---

## Phase 4 — Prediction Engine (Week 6-8)

### Dictionary
- [ ] Base dictionary: English + common words (~100K)
- [ ] User dictionary: words user types (learned)
- [ ] Multi-language dictionary support
- [ ] Dictionary format: compressed trie or bloom filter

### Next-Word Prediction
- [ ] Bigram model (pair frequency)
- [ ] Optional: small neural model via TensorFlow Lite (future)
- [ ] Context-aware: app-specific words (chat apps get casual words)

### Autocorrect
- [ ] Real-time correction as user types
- [ ] Fuzzy matching (edit distance ≤ 2)
- [ ] Space bar auto-correct
- [ ] Punctuation-aware

---

## Phase 5 — Polish & Release (Week 8-10)

### UI Polish
- [ ] Onboarding screens (first launch)
- [ ] Settings UI revamp (Material 3)
- [ ] Keyboard height slider
- [ ] Sound/haptic feedback
- [ ] Number row option
- [ ] Long-press secondary characters
- [ ] One-handed mode (left/right)

### Privacy & Compliance
- [ ] Privacy policy (no data collection — real)
- [ ] No internet permission by default
- [ ] All processing on-device
- [ ] Open source license in app

### Distribution
- [ ] GitHub Releases — APK + App Bundle
- [ ] Google Play Store listing (developer account needed)
- [ ] F-Droid metadata (auto-build)
- [ ] Website: voxboard.app (or voxboard.dev)

### Release
- [ ] v1.0-alpha — internal testing
- [ ] v1.0-beta — closed test group
- [ ] v1.0 — public release

---

## Technical Architecture

```
VoxBoard/
├── app/                    # Main application module
│   ├── src/main/
│   │   ├── java/com/voxboard/
│   │   │   ├── keyboard/    # Core keyboard logic
│   │   │   │   ├── VoxBoardService.kt    # InputMethodService
│   │   │   │   ├── KeyEventHandler.kt
│   │   │   │   ├── SwipeHandler.kt
│   │   │   │   ├── VoiceHandler.kt
│   │   │   │   └── PredictionEngine.kt
│   │   │   ├── theme/       # Theming engine
│   │   │   │   ├── ThemeManager.kt
│   │   │   │   ├── ThemeModel.kt
│   │   │   │   ├── ThemeRenderer.kt
│   │   │   │   └── MonetAdapter.kt
│   │   │   ├── voice/       # Voice input
│   │   │   │   ├── SpeechRecognition.kt    # Android STT
│   │   │   │   ├── WhisperBridge.kt        # JNI bridge
│   │   │   │   └── native/                  # C++ Whisper
│   │   │   ├── ui/          # Settings, theming UI
│   │   │   ├── dictionary/   # Dictionary + prediction
│   │   │   └── util/         # Helpers
│   │   ├── res/
│   │   └── jniLibs/         # Prebuilt Whisper .so files
│   └── build.gradle.kts
├── docs/                   # Documentation
├── themes/                 # Example theme files
├── scripts/                # Build scripts
├── .github/workflows/      # CI
└── README.md
```

## Libraries & Dependencies

| Library | Purpose | License |
|---------|---------|---------|
| Jetpack Compose (BOM) | Modern UI toolkit | Apache 2.0 |
| Material 3 (M3) | Design system | Apache 2.0 |
| AndroidX Preference | Settings framework | Apache 2.0 |
| Gson/Moshi | JSON parsing (themes) | Apache 2.0 |
| Whisper.cpp | On-device speech recognition | MIT |
| None for networking | — intentionally | — |
| Coroutines | Async voice processing | Apache 2.0 |

## Monetization (Open Source Model)

- **Core app:** Free, open source (Apache 2.0)
- **Donations:** GitHub Sponsors, Open Collective, Buy Me a Coffee
- **Paid themes:** Optional theme store with paid creator themes (95% to creator, 5% to project)
- **"Support the Dev" unlock:** Optional in-app purchase that's just a donation with a badge

No ads, no tracking, no data collection. Ever.

## Key Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Swipe typing is hard to get right | Start with simple algorithm, iterate based on feedback |
| Whisper model size (~75MB) | Download on first launch, stream from assets |
| FlorisBoard code is old (XML Views) | Migrate gradually, new features in Compose |
| Predictions won't match Gboard | Be honest about it, focus on privacy + theming as differentiators |
| Android Studio on Windows is slow | Set up build caching, use local SDK properly |
