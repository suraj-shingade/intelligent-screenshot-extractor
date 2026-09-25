<p align="center">
  <h1 align="center">Intelligent Screenshot Extractor</h1>
  <p align="center">
    A smart desktop application that extracts <strong>unique</strong> frames from videos using perceptual hashing and AI-powered analysis.
  </p>
</p>

<p align="center">
  <a href="https://github.com/suraj-shingade/intelligent-screenshot-extractor/actions/workflows/ci.yml">
    <img src="https://github.com/suraj-shingade/intelligent-screenshot-extractor/actions/workflows/ci.yml/badge.svg" alt="CI Build">
  </a>
  <a href="https://github.com/suraj-shingade/intelligent-screenshot-extractor/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/suraj-shingade/intelligent-screenshot-extractor?color=blue" alt="License: MIT">
  </a>
  <img src="https://img.shields.io/badge/Java-22-orange?logo=openjdk&logoColor=white" alt="Java 22">
  <img src="https://img.shields.io/badge/Maven-3.9+-C71A36?logo=apachemaven&logoColor=white" alt="Maven 3.9+">
  <img src="https://img.shields.io/badge/UI-Swing%20%2B%20FlatLaf-4B8BBE" alt="Swing + FlatLaf">
  <a href="https://github.com/suraj-shingade/intelligent-screenshot-extractor/issues">
    <img src="https://img.shields.io/github/issues/suraj-shingade/intelligent-screenshot-extractor" alt="Issues">
  </a>
  <a href="https://github.com/suraj-shingade/intelligent-screenshot-extractor/stargazers">
    <img src="https://img.shields.io/github/stars/suraj-shingade/intelligent-screenshot-extractor?style=social" alt="Stars">
  </a>
</p>

---

## Table of Contents

- [About](#about)
- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Requirements](#requirements)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Adding a Custom AI Analyzer](#adding-a-custom-ai-analyzer)
- [Project Structure](#project-structure)
- [Contributing](#contributing)
- [License](#license)
- [Author](#author)

---

## About

**Intelligent Screenshot Extractor** is a desktop application built with Java Swing that intelligently extracts unique frames from video files. Instead of blindly saving every frame, it uses a **64-bit perceptual hash (DCT pHash)** with a sliding-window Hamming-distance comparator to detect and discard near-duplicate frames -- saving you time and disk space.

The application features a clean hexagonal architecture with first-class extension points for plugging in AI-driven frame analysis via the Deep Java Library (DJL).

---

## Features

- **Multi-Video Batch Processing** -- Bounded executor with per-job pause, resume, and cancel controls.
- **Visual Uniqueness Filter** -- 64-bit perceptual hashing (DCT pHash) with sliding-window Hamming-distance comparison. Three sensitivity presets: `Strict`, `Balanced`, `Permissive`.
- **SSIM Second Opinion** -- Frames whose hash distance lands just past the accept threshold get re-checked structurally. A high SSIM score means the hash was about to let a duplicate through, so the frame is rejected after all. Confident accepts and rejects skip the check, so it costs almost nothing.
- **Three Sampling Strategies** -- Every N frames, every N seconds, or **scene-change detection**: consecutive frames are compared on a luma grid and a shot boundary is reported when the picture actually cuts. A minimum-interval guard keeps cross-fades and camera flashes from reporting a burst of adjacent cuts.
- **Working AI Analysis** -- Two DJL model-zoo providers ship ready to use. `resnet` classifies each accepted frame and writes searchable tags; `ssd` detects objects and writes labelled bounding boxes. Both load lazily on the first analysed frame, and a model that cannot be loaded degrades to empty metadata instead of failing the job. Disabled by default, because enabling it downloads model weights once.
- **Pluggable AI Pipeline** -- `FrameAnalyzer` SPI discovered at runtime via `ServiceLoader` and registered through Guice multibinding. Adding a model means implementing one interface and naming it in configuration.
- **Modern Swing UI** -- FlatLaf theme support (Light / Dark / IntelliJ), MigLayout, HiDPI-aware rendering, drag-and-drop of files or whole folders, live thumbnail gallery, tailing log console with level filtering, and a status bar.
- **Externalised Configuration** -- HOCON via Typesafe Config. All UI strings externalised through `ResourceBundle`. Zero magic strings in business logic.
- **Structured Logging** -- SLF4J + Logback with per-job `MDC` correlation IDs for easy tracing.
- **Multiple Output Formats** -- PNG, JPEG, and WEBP with configurable quality and optional resize. A missing WEBP codec falls back to PNG rather than failing the job.
- **Distribution Ready** -- Fat-JAR and `jpackage` native-installer Maven profiles for easy distribution.

---

## Architecture

The application follows a **hexagonal (ports & adapters) architecture** with clearly separated layers:

```
ui (Swing / FlatLaf)
    |
    v   Observer: ProgressEvent
application (VideoProcessingService, JobQueueService)
    |
    v
core (domain, framework-free)
    - pipeline:  FrameStage chain (Chain of Responsibility)
    - dedup:     ImageHasher, UniquenessFilter, SsimRefiner (Strategy)
    - sampling:  FrameSampler, SceneChangeDetector
    - ai:        FrameAnalyzer SPI (extension point)
    |
    v
infrastructure (adapters)
    - JavaCvFrameExtractor  (FFmpeg via JavaCV)
    - FileSystemFrameWriter (PNG / JPEG / WEBP)
    - JsonMetadataWriter    (per-frame sidecar)
    - DJL model zoo         (classification / object detection)
```

**Design Patterns Applied:**

| Pattern | Where |
|---|---|
| Chain of Responsibility | Frame processing pipeline |
| Strategy | `ImageHasher`, `SamplingStrategy` |
| Template Method | `AbstractZooModelProvider` (lazy load, per-thread predictors) |
| Observer | `ProgressListener` / `ProgressEvent` |
| Builder | `ExtractionRequest` |
| Factory | `FrameExtractorFactory` |
| SPI / ServiceLoader | `FrameAnalyzer`, `ModelProvider` |
| Dependency Injection | Guice `AppModule` |

---

## Tech Stack

| Category | Technology |
|---|---|
| Language | Java 22 |
| Build Tool | Maven 3.9+ |
| UI Framework | Swing + FlatLaf + MigLayout |
| Video Decoding | JavaCV (FFmpeg + OpenCV) |
| AI / ML | Deep Java Library (DJL) |
| Dependency Injection | Google Guice |
| Configuration | Typesafe Config (HOCON) |
| Serialization | Jackson |
| Logging | SLF4J + Logback |
| Testing | JUnit 5, AssertJ, Mockito |

---

## Requirements

| Requirement | Version |
|---|---|
| JDK | **22** or higher |
| Maven | **3.9+** |
| FFmpeg | Bundled via `javacv-platform` (no manual install) |

---

## Getting Started

### Clone the Repository

```bash
git clone https://github.com/suraj-shingade/intelligent-screenshot-extractor.git
cd intelligent-screenshot-extractor
```

### Build & Test

```bash
# Compile and run all tests (unit + integration)
mvn clean verify
```

### Run the Application

```bash
# Run directly from Maven
mvn exec:java
```

### Build a Fat JAR

```bash
mvn -Pfatjar package
java -jar target/video-to-image-1.0.0-SNAPSHOT-all.jar
```

### Build a Native Installer

```bash
# Produces .dmg (macOS) / .exe (Windows) / .deb (Linux)
mvn -Pjpackage install
```

---

## Configuration

All defaults are defined in [`src/main/resources/application.conf`](src/main/resources/application.conf) (HOCON format).
Override any setting via `-Dkey=value` JVM properties or by placing an external `application.conf` on the classpath.

| Key | Purpose | Default |
|---|---|---|
| `extraction.uniqueness.preset` | Sensitivity: `STRICT` / `BALANCED` / `PERMISSIVE` | `BALANCED` |
| `extraction.uniqueness.thresholds.*` | Hamming-distance threshold per preset | 3 / 5 / 10 |
| `extraction.uniqueness.windowSize` | Sliding-window size for comparison | 32 |
| `extraction.uniqueness.ssim.enabled` | Structural second opinion on borderline frames | `true` |
| `extraction.uniqueness.ssim.borderlineBand` | Bits past the preset threshold still treated as borderline | 5 |
| `extraction.uniqueness.ssim.threshold` | SSIM score at which two frames count as the same picture | 0.92 |
| `extraction.sampling.strategy` | `INTERVAL_FRAMES` / `INTERVAL_SECONDS` / `SCENE_CHANGE` | `INTERVAL_SECONDS` |
| `extraction.sampling.sceneChange.threshold` | Normalised luma difference that marks a shot boundary | 0.12 |
| `extraction.sampling.sceneChange.minIntervalSeconds` | Shortest gap between two reported cuts | 0.5 |
| `extraction.output.format` | Output format: `PNG` / `JPEG` / `WEBP` | `PNG` |
| `extraction.output.jpegQuality` | JPEG quality (0.0 - 1.0) | 0.92 |
| `concurrency.maxParallelJobs` | Max concurrent video jobs | 2 |
| `ai.djl.enabled` | Enable DJL AI analyzer (downloads weights on first use) | `false` |
| `ai.djl.modelProvider` | `resnet` / `ssd` / `placeholder` | `resnet` |
| `ai.djl.minConfidence` | Predictions weaker than this are discarded | 0.25 |
| `ai.djl.maxResults` | Most labels or objects recorded per frame | 5 |
| `ui.theme` | UI theme selector | `FLATLAF_INTELLIJ_DARK` |

### Turning on AI analysis

```hocon
ai.djl {
    enabled       = true
    modelProvider = "resnet"   # or "ssd" for object detection
}
```

The first analysed frame downloads model weights (a few hundred MB) and caches
them in the DJL cache directory; later runs work offline. Nothing is downloaded
at startup, and nothing is downloaded at all unless a job runs with an analyzer
selected. If the download or the native engine is unavailable, the failure is
logged once and analysis is skipped for the rest of the run -- frame extraction
carries on unaffected.

Analyzers are also selectable per job in the **AI Analyzers** list on the
configuration panel.

---

## Adding a Custom AI Analyzer

The application provides an SPI extension point for plugging in custom AI models.
See [`src/main/java/com/srj/videotoimage/core/ai/README.md`](src/main/java/com/srj/videotoimage/core/ai/README.md) for a detailed guide.

**Quick steps:**

1. Implement `FrameAnalyzer` (or implement `ModelProvider` to reuse the DJL wiring).
2. Register it in `META-INF/services/` under the corresponding SPI file.
3. Enable it via `application.conf`.

No other code changes required -- the pipeline discovers and integrates it automatically via Guice's `Multibinder<FrameAnalyzer>`.

---

## Project Structure

```
src/main/java/com/srj/videotoimage/
  VideoToImageApplication.java         Entry point
  bootstrap/                           Guice DI wiring
  config/                              Typed Typesafe Config wrapper
  application/                         Services (VideoProcessingService, JobQueueService)
  core/
    pipeline/                          Chain of Responsibility + stages
    dedup/                             pHash, sliding-window store, UniquenessFilter, SSIM
    sampling/                          FrameSampler, SceneChangeDetector
    ai/                                FrameAnalyzer SPI + DJL model-zoo providers
    model/                             Domain records (Frame, VideoSource, etc.)
  infrastructure/
    extractor/                         JavaCV / FFmpeg adapter
    persistence/                       Filesystem + JSON sidecar writers
  event/                               Progress event model
  exception/                           Custom exception hierarchy
  ui/
    panels/                            ConfigPanel, GalleryPanel, JobQueuePanel, LogPanel
    components/                        ThumbnailRenderer, UiLogAppender, VideoDropTarget
    dialog/                            AboutDialog
    model/                             UI table/list models
    theme/                             FlatLaf ThemeManager
    i18n/                              Internationalisation (Messages)

src/main/resources/
  application.conf                     HOCON configuration
  messages.properties                  UI strings
  logback.xml                          Logging configuration
  META-INF/services/                   SPI registrations

src/test/java/                         JUnit 5 tests
  testsupport/                         Synthetic decoder, image fixtures, polling helper
```

---

## Contributing

Contributions are welcome! Please read the [Contributing Guidelines](CONTRIBUTING.md) before submitting a pull request.

---

## License

This project is licensed under the **MIT License** -- see the [LICENSE](LICENSE) file for details.

---

## Author

**Suraj Shingade**

- GitHub: [@suraj-shingade](https://github.com/suraj-shingade)

---

<p align="center">
  Made with Java and passion for clean code.
</p>
