# OptiScan

Native Android OMR (Optical Mark Recognition) + OCR exam reader application. Scan printed answer sheets with your phone camera, automatically detect filled bubbles, read student information via OCR, and generate graded results with Excel export.

**Developer:** merenekiz

## Features

- **OMR Bubble Detection** — Detects filled bubbles on printed answer sheets using OpenCV adaptive thresholding
- **OCR Student Info** — Reads handwritten student name, number, and class via ML Kit Text Recognition
- **QR Code Support** — Auto-configure exam settings by scanning a QR code on the form
- **Auto Grading** — Calculates scores with configurable correct/wrong point values
- **PDF Form Generator** — Generate printable OMR answer sheets (A4) for any question count (1-100)
- **Excel Export** — Export results to `.xlsx` with per-student scores via Apache POI
- **Visual Feedback** — Color-coded overlay on scanned forms (green = correct, red = wrong)
- **Fully Offline** — All processing runs on-device, no internet required
- **Dynamic Layout** — Bubble grid auto-scales: single column for 1-30 questions, dual column for 31+

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Camera | CameraX |
| Image Processing | OpenCV 4.x (local module) |
| OCR | Google ML Kit Text Recognition (bundled) |
| QR Detection | Google ML Kit Barcode Scanning (bundled) |
| Database | Room |
| DI | Hilt |
| Excel Export | Apache POI 5.2.5 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |

## Project Structure

```
app/src/main/java/com/optiscan/
├── analysis/          # GradingEngine, scoring models
├── camera/            # CameraX manager
├── data/              # Room database, DAOs, entities, repositories
├── di/                # Hilt modules
├── export/            # ExcelExporter, FormPdfGenerator
├── ocr/               # ML Kit OCR processor
├── processing/        # BubbleDetector, PerspectiveTransformer, OmrProcessor
├── qr/                # QR/Barcode scanner
└── ui/
    ├── navigation/    # NavGraph, Screen routes
    ├── screens/       # Home, Exams, Camera, Results, Export
    └── theme/         # Colors, Typography, Theme
```

## How It Works

1. **Create Exam** — Set title, subject, question count, and answer key
2. **Generate Form** — Print the auto-generated OMR PDF form
3. **Scan** — Point the camera at a filled form or pick from gallery
4. **Process** — The pipeline runs: Perspective Correction → Bubble Detection → OCR → Grading
5. **Review** — See color-coded results with correct/wrong/empty breakdown
6. **Export** — Download results as Excel spreadsheet

## Installation (APK)

### Download

The pre-built APK is located at:

```
app/build/outputs/apk/debug/app-debug.apk
```

To build it yourself:

```bash
./gradlew assembleDebug
```

The output APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Install on Your Phone

#### Option 1: USB (adb)

```bash
# Connect phone via USB with USB Debugging enabled
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### Option 2: Direct Transfer

1. Transfer `app-debug.apk` to your phone (via USB file transfer, Google Drive, Telegram, etc.)
2. On your phone, go to **Settings > Security > Install Unknown Apps** and allow your file manager
3. Open the APK file on your phone and tap **Install**
4. Launch **OptiScan** from your app drawer

> **Note:** This is a debug build signed with a debug key. It works on any Android 8.0+ device but cannot be uploaded to Google Play. For a release build, configure signing in `app/build.gradle.kts`.

### Enable USB Debugging (Required for adb)

1. Go to **Settings > About Phone**
2. Tap **Build Number** 7 times until "You are now a developer" appears
3. Go to **Settings > Developer Options**
4. Enable **USB Debugging**
5. Connect phone to computer via USB
6. Accept the debugging authorization dialog on your phone

## Building from Source

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 35
- NDK (for OpenCV native libs)

### Steps

```bash
git clone https://github.com/merenekiz/OptiScan.git
cd OptiScan
./gradlew assembleDebug
```

The OpenCV module is included locally in the `opencv/` directory — no additional setup needed.

## Permissions

| Permission | Purpose |
|-----------|---------|
| `CAMERA` | Scan answer sheets in real-time |
| `READ_MEDIA_IMAGES` | Pick form images from gallery |
| `WRITE_EXTERNAL_STORAGE` (API ≤ 29) | Legacy storage access |

## Configuration

### Exam Setup

- **Question Count:** 1-100 (single column up to 30, dual column for 31+)
- **Correct Point:** Auto-calculated as 100 / question count
- **Wrong Penalty:** Configurable (0 = no penalty)
- **Answer Key:** Visual bubble selector (A-E per question)

### OMR Detection Parameters

Located in `BubbleDetector.kt`:

| Parameter | Value | Description |
|-----------|-------|-------------|
| `SHEET_WIDTH` | 800px | Warped sheet width |
| `SHEET_HEIGHT` | 1100px | Warped sheet height |
| `BUBBLE_DIAMETER` | 22px | Detection circle size |
| `FILL_THRESHOLD` | 0.40 | Min fill % to count as marked |
| `AMBIGUOUS_THRESHOLD` | 0.20 | Min fill % for uncertain mark |

## License

This project is proprietary software developed by merenekiz. All rights reserved.
