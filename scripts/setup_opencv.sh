#!/bin/bash
# OptiScan — OpenCV Android SDK Kurulum Scripti
# Bu scripti proje kök dizininden çalıştırın: bash scripts/setup_opencv.sh

set -e

OPENCV_VERSION="4.10.0"
OPENCV_ZIP="opencv-${OPENCV_VERSION}-android-sdk.zip"
OPENCV_URL="https://github.com/opencv/opencv/releases/download/${OPENCV_VERSION}/${OPENCV_ZIP}"
TMP_DIR="/tmp/optiscan-opencv"
SDK_ROOT="${TMP_DIR}/OpenCV-android-sdk"

echo "==> OpenCV ${OPENCV_VERSION} Android SDK indiriliyor..."
mkdir -p "${TMP_DIR}"

if [ ! -f "${TMP_DIR}/${OPENCV_ZIP}" ]; then
    curl -L -o "${TMP_DIR}/${OPENCV_ZIP}" "${OPENCV_URL}"
else
    echo "==> Zaten indirilmiş, atlıyorum."
fi

echo "==> Açılıyor..."
unzip -q "${TMP_DIR}/${OPENCV_ZIP}" -d "${TMP_DIR}"

echo "==> .so dosyaları kopyalanıyor..."
mkdir -p app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}

NATIVE_LIBS="${SDK_ROOT}/sdk/native/libs"
cp "${NATIVE_LIBS}/arm64-v8a/libopencv_java4.so"   app/src/main/jniLibs/arm64-v8a/
cp "${NATIVE_LIBS}/armeabi-v7a/libopencv_java4.so" app/src/main/jniLibs/armeabi-v7a/
cp "${NATIVE_LIBS}/x86_64/libopencv_java4.so"      app/src/main/jniLibs/x86_64/

echo "==> Java API jar'ı kopyalanıyor..."
mkdir -p app/libs
JAVA_BIN="${SDK_ROOT}/sdk/java/bin"
if [ -f "${JAVA_BIN}/classes.jar" ]; then
    cp "${JAVA_BIN}/classes.jar" app/libs/opencv-java.jar
    echo "==> opencv-java.jar kopyalandı."
else
    # Build the JAR from sources if not present
    echo "==> Kaynak dosyalarından JAR oluşturuluyor (bu biraz sürebilir)..."
    JAVA_SRC="${SDK_ROOT}/sdk/java/src"
    mkdir -p /tmp/opencv-classes
    find "${JAVA_SRC}" -name "*.java" > /tmp/opencv-sources.txt
    javac -source 8 -target 8 -d /tmp/opencv-classes @/tmp/opencv-sources.txt 2>/dev/null || true
    jar cf app/libs/opencv-java.jar -C /tmp/opencv-classes . 2>/dev/null || true
    echo "==> JAR oluşturuldu (varsa)."
fi

echo ""
echo "==> OpenCV kurulumu tamamlandı!"
echo ""
echo "Şimdi projeyi derleyebilirsiniz:"
echo "  ./gradlew assembleDebug"
