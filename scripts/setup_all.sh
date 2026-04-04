#!/bin/bash
# ============================================================
# OptiScan — Tek Adımda Tam Kurulum
# Bu scripti çalıştır, gerisini o halleder.
# ============================================================
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()   { echo -e "${GREEN}[✓]${NC} $1"; }
warn()  { echo -e "${YELLOW}[!]${NC} $1"; }
err()   { echo -e "${RED}[✗]${NC} $1"; }
info()  { echo -e "${CYAN}[→]${NC} $1"; }

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

echo ""
echo "============================================"
echo "  OptiScan — Otomatik Kurulum"
echo "============================================"
echo ""

# ---- 1. Android Studio ----
echo "━━━ ADIM 1/5: Android Studio ━━━"
if [ -d "/Applications/Android Studio.app" ]; then
    log "Android Studio zaten kurulu"
else
    info "Android Studio kuruluyor (brew)..."
    if command -v brew &>/dev/null; then
        brew install --cask android-studio
        log "Android Studio kuruldu"
    else
        err "Homebrew bulunamadı. Manuel kur: https://developer.android.com/studio"
        echo "  brew install --cask android-studio"
        exit 1
    fi
fi

# ---- 2. Android SDK kontrol ----
echo ""
echo "━━━ ADIM 2/5: Android SDK ━━━"
SDK_DIR="$HOME/Library/Android/sdk"
if [ -d "$SDK_DIR/platforms" ]; then
    log "Android SDK bulundu: $SDK_DIR"
else
    warn "Android SDK henüz kurulmamış."
    echo ""
    echo "  Şunu yap:"
    echo "  1. Android Studio'yu aç (Applications'tan)"
    echo "  2. İlk kurulum sihirbazını tamamla (Standard seç)"
    echo "  3. SDK kurulumunu bekle"
    echo "  4. Bu scripti tekrar çalıştır"
    echo ""
    info "Android Studio açılıyor..."
    open -a "Android Studio" 2>/dev/null || true
    echo ""
    echo "SDK kurulduktan sonra tekrar çalıştır: bash scripts/setup_all.sh"
    exit 0
fi

# ---- 3. local.properties ----
echo ""
echo "━━━ ADIM 3/5: local.properties ━━━"
if [ ! -f "local.properties" ]; then
    echo "sdk.dir=$SDK_DIR" > local.properties
    log "local.properties oluşturuldu (sdk.dir=$SDK_DIR)"
else
    log "local.properties zaten var"
fi

# ---- 4. OpenCV SDK ----
echo ""
echo "━━━ ADIM 4/5: OpenCV Android SDK ━━━"
if [ -f "app/src/main/jniLibs/arm64-v8a/libopencv_java4.so" ]; then
    log "OpenCV .so dosyaları zaten mevcut"
else
    info "OpenCV 4.10.0 Android SDK indiriliyor..."
    OPENCV_VERSION="4.10.0"
    TMP="/tmp/optiscan-opencv"
    mkdir -p "$TMP"

    if [ ! -f "$TMP/opencv-${OPENCV_VERSION}-android-sdk.zip" ]; then
        curl -L --progress-bar -o "$TMP/opencv-${OPENCV_VERSION}-android-sdk.zip" \
            "https://github.com/opencv/opencv/releases/download/${OPENCV_VERSION}/opencv-${OPENCV_VERSION}-android-sdk.zip"
    fi

    info "Açılıyor..."
    unzip -qo "$TMP/opencv-${OPENCV_VERSION}-android-sdk.zip" -d "$TMP"

    NATIVE="$TMP/OpenCV-android-sdk/sdk/native/libs"

    mkdir -p app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}
    cp "$NATIVE/arm64-v8a/libopencv_java4.so"   app/src/main/jniLibs/arm64-v8a/
    cp "$NATIVE/armeabi-v7a/libopencv_java4.so" app/src/main/jniLibs/armeabi-v7a/
    cp "$NATIVE/x86_64/libopencv_java4.so"      app/src/main/jniLibs/x86_64/
    log "OpenCV .so dosyaları kopyalandı"

    # Java wrapper (OpenCV AAR veya jar)
    mkdir -p app/libs
    JAVA_DIR="$TMP/OpenCV-android-sdk/sdk/java"
    if [ -d "$JAVA_DIR" ]; then
        # AAR oluşturmaya gerek yok — build.gradle zaten fileTree ile libs/ tarar
        # Basit yol: java sources'ı jar'la
        info "OpenCV Java API jar oluşturuluyor..."
        mkdir -p /tmp/opencv-classes
        find "$JAVA_DIR/src" -name "*.java" > /tmp/opencv-sources.txt 2>/dev/null
        SRCCOUNT=$(wc -l < /tmp/opencv-sources.txt | tr -d ' ')
        if [ "$SRCCOUNT" -gt 0 ]; then
            javac -source 8 -target 8 -d /tmp/opencv-classes \
                  @/tmp/opencv-sources.txt 2>/dev/null || true
            jar cf app/libs/opencv-java.jar -C /tmp/opencv-classes . 2>/dev/null || true
            log "opencv-java.jar oluşturuldu (app/libs/)"
        else
            warn "OpenCV Java kaynak bulunamadı — build.gradle'daki OpenCV bağımlılığını kontrol et"
        fi
        rm -rf /tmp/opencv-classes /tmp/opencv-sources.txt
    fi
fi

# ---- 5. Gradle Wrapper ----
echo ""
echo "━━━ ADIM 5/5: Gradle Wrapper ━━━"
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    log "Gradle wrapper jar mevcut"
else
    info "Gradle wrapper indiriliyor..."
    GRADLE_VER="8.7"
    WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
    mkdir -p gradle/wrapper

    # gradle-wrapper.jar'ı doğrudan Gradle dağıtımından çıkart
    GRADLE_DIST="/tmp/gradle-${GRADLE_VER}-bin.zip"
    if [ ! -f "$GRADLE_DIST" ]; then
        curl -L --progress-bar -o "$GRADLE_DIST" \
            "https://services.gradle.org/distributions/gradle-${GRADLE_VER}-bin.zip"
    fi
    # Sadece wrapper jar'ı çıkart
    unzip -qjo "$GRADLE_DIST" "gradle-${GRADLE_VER}/lib/gradle-wrapper-*.jar" -d /tmp/ 2>/dev/null
    FOUND_JAR=$(ls /tmp/gradle-wrapper-*.jar 2>/dev/null | head -1)
    if [ -n "$FOUND_JAR" ]; then
        cp "$FOUND_JAR" "$WRAPPER_JAR"
        log "gradle-wrapper.jar hazır"
    else
        # Alternatif: doğrudan URL'den
        curl -L --progress-bar -o "$WRAPPER_JAR" \
            "https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VER}/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null || true
        if [ -f "$WRAPPER_JAR" ] && [ -s "$WRAPPER_JAR" ]; then
            log "gradle-wrapper.jar indirildi"
        else
            warn "gradle-wrapper.jar indirilemedi. Android Studio açınca otomatik oluşur."
        fi
    fi
fi

# ---- PATH hatırlatma ----
echo ""
echo "━━━ SONUÇ ━━━"
echo ""
if ! command -v adb &>/dev/null; then
    warn "adb PATH'te yok. ~/.zshrc'ye ekle:"
    echo ""
    echo "  export ANDROID_HOME=~/Library/Android/sdk"
    echo "  export PATH=\$PATH:\$ANDROID_HOME/platform-tools"
    echo ""
fi

# ---- Özet ----
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
log "Kurulum tamamlandı!"
echo ""
echo "  Sonraki adımlar:"
echo ""
echo "  ${CYAN}Yöntem A — Android Studio ile:${NC}"
echo "    1. Android Studio → File → Open → $PROJECT_DIR"
echo "    2. Gradle sync bekle"
echo "    3. Telefonu USB ile bağla (USB Debug açık)"
echo "    4. ▶ Run"
echo ""
echo "  ${CYAN}Yöntem B — Terminal ile:${NC}"
echo "    cd $PROJECT_DIR"
echo "    ./gradlew assembleDebug"
echo "    adb install app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "  ${CYAN}Test formları:${NC}"
echo "    $PROJECT_DIR/test_forms/"
echo "    bos_form.png    — yazdır, elle doldur"
echo "    tam_dogru.png   — ekrandan tarat (100 puan beklenir)"
echo "    karisik.png     — ekrandan tarat (75 puan beklenir)"
echo "    sinav_qr.png    — ekrandan göster (QR test)"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
