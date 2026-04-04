#!/bin/bash
# Push test forms to Android emulator's gallery
set -e

ADB=~/Library/Android/sdk/platform-tools/adb
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
FORMS="$PROJECT_DIR/test_forms"

echo "Test formlarını emülatöre yükleniyor..."

# Wait for device
$ADB wait-for-device

# Push each form to emulator storage
for f in "$FORMS"/*.png; do
    fname=$(basename "$f")
    echo "  → $fname"
    $ADB push "$f" "/sdcard/Pictures/$fname"
done

# Trigger media scan so images appear in gallery
$ADB shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Pictures/"
# Also scan individual files
for f in "$FORMS"/*.png; do
    fname=$(basename "$f")
    $ADB shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Pictures/$fname" 2>/dev/null
done

echo ""
echo "Tamamlandı! Test formları emülatörün Galeri uygulamasında görünecek."
echo "OptiScan'da 'Galeriden Form Seç' butonuna basarak tarayabilirsin."
