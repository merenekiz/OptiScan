# OptiScan — Test Rehberi (Sıfırdan)

## ADIM 1: Android Studio Kur

### 1a. İndir ve Kur
```bash
# Homebrew ile (en kolay):
brew install --cask android-studio

# VEYA manual olarak:
# https://developer.android.com/studio adresinden indir
# .dmg dosyasını aç, Applications'a sürükle
```

### 1b. İlk Açılışta Android Studio Ayarları
1. Android Studio'yu aç
2. "Standard" kurulumu seç
3. SDK Components kurulumunu bekle (Android SDK 35, Build Tools, Emulator otomatik kurulur)
4. Finish'e tıkla

### 1c. PATH Ayarları (Terminal'den erişim için)
Şunu ~/.zshrc dosyana ekle:
```bash
export ANDROID_HOME=~/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/emulator
```
Sonra:
```bash
source ~/.zshrc
```

---

## ADIM 2: OpenCV SDK Kur

```bash
cd /Users/merenekiz/vscode/OptiScan
bash scripts/setup_opencv.sh
```

Bu script:
- OpenCV 4.10.0 Android SDK'yı indirir
- .so dosyalarını jniLibs/ klasörüne kopyalar
- Java API jar'ını libs/ klasörüne kopyalar

**Eğer script hata verirse manuel yap:**
```bash
# İndir
curl -L -o /tmp/opencv.zip https://github.com/opencv/opencv/releases/download/4.10.0/opencv-4.10.0-android-sdk.zip

# Aç
unzip /tmp/opencv.zip -d /tmp/opencv-sdk

# .so dosyalarını kopyala
cp /tmp/opencv-sdk/OpenCV-android-sdk/sdk/native/libs/arm64-v8a/libopencv_java4.so app/src/main/jniLibs/arm64-v8a/
cp /tmp/opencv-sdk/OpenCV-android-sdk/sdk/native/libs/armeabi-v7a/libopencv_java4.so app/src/main/jniLibs/armeabi-v7a/
cp /tmp/opencv-sdk/OpenCV-android-sdk/sdk/native/libs/x86_64/libopencv_java4.so app/src/main/jniLibs/x86_64/
```

---

## ADIM 3: Projeyi Android Studio'da Aç

1. Android Studio → File → Open
2. `/Users/merenekiz/vscode/OptiScan` klasörünü seç
3. "Trust Project" de
4. Gradle sync'i bekle (ilk seferde 5-10 dakika sürebilir)

### Olası Sorunlar:
- **"local.properties not found"**: Android Studio otomatik oluşturur, sorun değil
- **"SDK location not found"**: File → Project Structure → SDK Location'dan SDK yolunu göster
- **Gradle sync hatası**: File → Invalidate Caches → Restart

---

## ADIM 4: Test Cihazı Hazırla

### Seçenek A: Gerçek Android Telefon (ÖNERİLEN)
Kamera testi için gerçek cihaz şart.

1. Telefonda: Ayarlar → Telefon Hakkında → "Yapım Numarası"na 7 kez dokun
2. Geliştirici Seçenekleri açılır → "USB Hata Ayıklama"yı aç
3. USB kabloyla Mac'e bağla
4. Telefonda "Hata ayıklamaya izin ver" → Tamam

Test et:
```bash
adb devices
# Cihazın serial numarasını görmelisin
```

### Seçenek B: Emülatör (Sadece UI testi, kamera yok)
1. Android Studio → Tools → Device Manager
2. "Create Virtual Device"
3. Pixel 6 seç → Next
4. API 34 (Android 14) image'ı indir → Next → Finish
5. ▶ Play butonuyla başlat

---

## ADIM 5: Derle ve Yükle

### Android Studio'dan:
1. Üst menüden cihazını seç (telefon veya emülatör)
2. Yeşil ▶ "Run" butonuna bas
3. İlk derleme 3-5 dakika sürer
4. APK otomatik olarak cihaza yüklenir ve açılır

### Terminal'den:
```bash
cd /Users/merenekiz/vscode/OptiScan

# Derle
./gradlew assembleDebug

# APK konumu
ls -la app/build/outputs/apk/debug/app-debug.apk

# Cihaza yükle
adb install app/build/outputs/apk/debug/app-debug.apk

# Uygulamayı başlat
adb shell am start -n com.optiscan.debug/.MainActivity
```

---

## ADIM 6: Test Senaryoları

### Test 1: Uygulama Açılış
- [x] Uygulama crash olmadan açılıyor mu?
- [x] Ana ekran düzgün görünüyor mu?
- [x] "Sınavlar" ve "Tara" butonları çalışıyor mu?

### Test 2: Sınav Oluşturma
1. Ana ekran → "Sınavlar" → "Sınav Oluştur"
2. Şu bilgileri gir:
   - Sınav Adı: `Test Sınavı`
   - Ders: `Matematik`
   - Soru Sayısı: `10`
   - Doğru Puan: `10`
   - Yanlış Ceza: `2.5`
   - Cevap Anahtarı: `ABCDABCDAB`
3. "Kaydet" → Sınav listesinde görünmeli

### Test 3: Kamera ve Tarama
1. Sınav kartında "Tara" butonuna bas
2. Kamera izni iste → İzin ver
3. Kamera preview açılmalı
4. Köşe işaretçileri (mavi çerçeve) görünmeli

### Test 4: QR Kod Testi
Bu QR kodu oluştur (herhangi bir QR generator ile):
```json
{"examId":"TEST-001","questionCount":10,"correctPoint":10,"wrongPenalty":2.5,"title":"QR Test","subject":"Matematik"}
```

QR oluşturmak için: https://www.qr-code-generator.com/
- "Text" seç, yukarıdaki JSON'ı yapıştır, QR'ı indir veya ekrandan göster
- Telefon kamerasını QR koda tut
- Yeşil banner: "QR okundu: TEST-001 | 10 soru" görünmeli

### Test 5: Optik Form Tarama
**Basit bir test formu oluştur:**
1. A4 kağıda şunu çiz:
   - Üst kısım: "Ad: Test Öğrenci", "No: 1234567"
   - 10 satır, her satırda 5 yuvarlak (A B C D E)
   - Bazı yuvarlakları kalemle karala
2. Formu kameraya göster → Çek butonuna bas
3. Sonuç ekranı:
   - Doğru/Yanlış/Boş sayıları
   - Puan hesaplaması
   - Yeşil/kırmızı işaretlemeler

### Test 6: Sonuçlar
1. Sınav → "Sonuçlar" → Taranan öğrenci listesi
2. Her kartta: isim, puan, D/Y/B sayıları
3. Sil butonu çalışıyor mu?

### Test 7: Excel Dışa Aktarma
1. Sonuçlar ekranında 📥 ikonuna bas
2. "Excel Raporu Oluştur" → "Paylaş"
3. Excel dosyası açılabilir mi?
4. Doğru/Yanlış hücreleri renkli mi?

---

## ADIM 7: Zor Koşul Testleri

### Kötü Aydınlatma
- Karanlık ortamda tarama yap → Flaş otomatik açılmalı
- Gölgeli ortamda → Adaptive threshold bu durumu yönetmeli

### Eğik Form
- Formu 15-20 derece eğik tut → Perspektif düzeltme çalışmalı
- 45 derece eğik → Fallback modu devreye girmeli

### Eksik İşaretleme
- Hafif karalanmış yuvarlak → Ambiguous olarak algılanmalı
- Çift işaretleme → Boş sayılmalı
- Hiç işaretleme yok → Boş

### Bozuk QR
- Yarısı kapalı QR → Hata banner'ı göstermeli, crash olmamalı
- Geçersiz JSON QR → Sessizce yok sayılmalı

---

## ADIM 8: Logları İzle

```bash
# Tüm OptiScan logları
adb logcat | grep -E "OptiScan|OmrProcessor|BubbleDetector|OcrProcessor|QrScanner|CameraManager|PerspectiveTransformer"

# Sadece hatalar
adb logcat *:E | grep optiscan

# Crash logları
adb logcat --buffer=crash
```

---

## Hızlı Sorun Giderme

| Sorun | Çözüm |
|---|---|
| Gradle sync başarısız | File → Invalidate Caches, sonra Sync |
| "SDK not found" | local.properties dosyasında sdk.dir ayarla |
| OpenCV crash | scripts/setup_opencv.sh tekrar çalıştır |
| Kamera siyah | Uygulama izinlerinden kamerayı aç |
| APK yüklenmiyor | `adb uninstall com.optiscan.debug` sonra tekrar yükle |
| Emülatörde kamera yok | Gerçek cihaz kullan |
| Excel açılmıyor | Apache POI çakışması → logcat kontrol et |

---

## Önemli Test Formu Boyutları

Optik formun doğru taranması için:
- A4 boyutunda form kullan
- 4 köşede belirgin siyah işaretler olmalı (alignment marker)
- Baloncuklar ~5mm çapında, aralarında eşit boşluk
- Header bölgesinde yazılı bilgiler (Ad, No, Sınıf)

**Not:** BubbleDetector'daki grid parametreleri kendi form tasarımınıza göre ayarlanmalıdır.
`BubbleDetector.kt` içindeki sabitleri formunuza uygun değiştirin:
```
GRID_START_Y = 220    → Baloncukların y başlangıcı
BUBBLE_ROW_HEIGHT = 28 → Satır aralığı  
OPTION_SPACING = 52    → A-E arası mesafe
GRID_LEFT_MARGIN = 50  → Sol boşluk
```
