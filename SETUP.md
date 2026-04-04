# OptiScan — Kurulum ve Derleme Rehberi

## 1. Gereksinimler
- Android Studio Hedgehog (2023.1.1) veya üzeri
- Android SDK API 26-35
- JDK 17
- OpenCV Android SDK 4.10.0

## 2. OpenCV Kurulumu (ZORUNLU)

OpenCV Maven Central'da bulunmuyor; manuel kurulum gerekiyor.

### İndir ve Kur:
```bash
# OpenCV 4.10.0 Android SDK indir
curl -L -o opencv-android.zip https://github.com/opencv/opencv/releases/download/4.10.0/opencv-4.10.0-android-sdk.zip
unzip opencv-android.zip -d /tmp/opencv-sdk
```

### .so Dosyalarını Kopyala:
```bash
SDK_PATH="/tmp/opencv-sdk/OpenCV-android-sdk/sdk/native/libs"

cp "$SDK_PATH/arm64-v8a/libopencv_java4.so"   app/src/main/jniLibs/arm64-v8a/
cp "$SDK_PATH/armeabi-v7a/libopencv_java4.so" app/src/main/jniLibs/armeabi-v7a/
cp "$SDK_PATH/x86_64/libopencv_java4.so"      app/src/main/jniLibs/x86_64/
```

### Java API'yi Kopyala:
```bash
# OpenCV Java classes jar'ı app/libs/ içine kopyala
cp /tmp/opencv-sdk/OpenCV-android-sdk/sdk/java/bin/classes.jar app/libs/opencv-java.jar

# Alternatif: OpenCV AAR kullan
# AAR dosyasını sdk/ klasöründen app/libs/ içine kopyala
```

## 3. local.properties Oluştur
```
sdk.dir=/Users/KULLANICI_ADI/Library/Android/sdk
```

## 4. Derleme
```bash
./gradlew assembleDebug
# APK konumu: app/build/outputs/apk/debug/app-debug.apk
```

## 5. Test Cihazına Yükle
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 6. QR Kod Formatı

Sınav formuna şu JSON formatında QR kod ekleyin:
```json
{
  "examId": "TURKCE-2024-01",
  "questionCount": 40,
  "correctPoint": 2.5,
  "wrongPenalty": 0.833,
  "title": "Türkçe Sınavı",
  "subject": "Türkçe"
}
```

## 7. Form Tasarımı

Optik form şu şartları sağlamalı:
- Dört köşede hizalama işareti (koyu daire veya kare)
- Üst bölümde: Ad Soyad, Öğrenci No, Sınıf
- Baloncuk grid: 5 seçenek (A-E), sütun genişliği ~52px (800px normalleştirilmiş)
- 50 soru 1. sütun, 51-100. sorular 2. sütun (iki sütunlu layout)

## 8. Baloncuk Grid Kalibrasyonu

Eğer formunuz farklı boyuttaysa `BubbleDetector.kt` içindeki sabitleri ayarlayın:
- `GRID_START_Y`: Baloncukların başladığı y konumu
- `BUBBLE_ROW_HEIGHT`: Satır yüksekliği  
- `OPTION_SPACING`: A-E seçenekleri arası mesafe
- `GRID_LEFT_MARGIN`: Sol kenar boşluğu
- `SECOND_COLUMN_X_OFFSET`: İkinci sütun başlangıcı

## 9. Proguard

Release derlemesi için `proguard-rules.pro` zaten yapılandırıldı.
Apache POI için `-dontwarn` kuralları dahil.

## 10. APK İmzalama

```bash
./gradlew assembleRelease
# Sonra keystore ile imzala:
jarsigner -keystore your.keystore app-release-unsigned.apk alias_name
```
