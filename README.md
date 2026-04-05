# OptiScan

> **APK İndir:** [`app/build/outputs/apk/debug/app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk)

Android tabanlı OMR (Optik İşaret Tanıma) + OCR sınav okuyucu uygulaması. Basılı cevap kağıtlarını telefon kamerasıyla tarayın, doldurulmuş baloncukları otomatik algılayın, OCR ile öğrenci bilgilerini okuyun ve notlandırılmış sonuçları Excel olarak dışa aktarın.

**Geliştiren:** merenekiz

## Ne Yapıyor?

Basılı optik cevap kağıtlarını telefon kamerasıyla tarayıp, otomatik olarak okuyan ve notlandıran Android uygulaması.

### Pipeline (İşlem Akışı)

```
Kamera/Galeri → Perspektif Düzeltme → Baloncuk Algılama → OCR → Notlandırma → Sonuç
```

## Aktif Teknolojiler ve Kullanım Amaçları

| Teknoloji | Kullanım | Detay |
|-----------|----------|-------|
| **OpenCV 4.x** | Görüntü işleme | Perspektif düzeltme (warp), adaptif eşikleme (adaptive threshold), morfolojik işlemler (morphology). Kağıdı 800x1100px sabit boyuta dönüştürüyor |
| **ML Kit Text Recognition** | OCR | Ad Soyad, Öğrenci No, Şube bilgilerini formun üst kısmından okuyor. Cihaz üzerinde (on-device) çalışır, internet gerektirmez |
| **ML Kit Barcode Scanning** | QR Kod | Form üzerindeki QR kodu okuyarak sınav ayarlarını (soru sayısı, cevap anahtarı vb.) otomatik yapılandırıyor |
| **CameraX** | Kamera yönetimi | Önizleme + fotoğraf çekimi. Android'in modern kamera API'si |
| **Jetpack Compose + Material3** | Arayüz | Tamamen deklaratif UI, Material Design 3 teması |
| **Room** | Veritabanı | Sınav tanımları ve öğrenci sonuçlarını SQLite'da saklar |
| **Hilt (Dagger)** | Dependency Injection | Tüm bileşenler (processor, repository, viewmodel) otomatik enjekte edilir |
| **Apache POI 5.2.5** | Excel dışa aktarma | Sonuçları `.xlsx` formatında çıktı verir |
| **Android PDF API** | Form oluşturma | Yazdırılabilir A4 optik formları programatik olarak çiziyor |

## Algoritmalar

### 1. Perspektif Düzeltme (Warp)

- 4 köşe alignment marker'ı (siyah kareler) tespit edilir
- OpenCV `getPerspectiveTransform` + `warpPerspective` ile kağıt 800x1100px dikdörtgene dönüştürülür

### 2. Baloncuk Algılama (OMR)

- `adaptiveThreshold` (Gaussian, blockSize=15) ile binary görüntü elde edilir
- `morphologyEx` (MORPH_OPEN, ellipse kernel 3x3) ile gürültü temizlenir
- Her baloncuk pozisyonu grid layout'tan hesaplanır (sabit koordinat sistemi)
- Dairesel mask ile `bitwise_and` → dolu piksel oranı hesaplanır
- **Eşikler:** `≥0.40` = dolu (FILLED), `≥0.20` = belirsiz (AMBIGUOUS), `<0.20` = boş (EMPTY)
- 1-30 soru: tek sütun, 31-100 soru: çift sütun (otomatik)

### 3. OCR (Öğrenci Bilgisi)

- Warped görüntüden sabit koordinatlara göre 3 bölge crop edilir (Ad Soyad, No, Şube)
- ML Kit `TextRecognizer` ile metin çıkarılır
- Regex ile label prefix'leri temizlenir ("Ad Soyad:", "Öğrenci No:", "Şube:" gibi)

### 4. Notlandırma

- Yapılandırılabilir: doğru puanı, yanlış cezası
- `score = (doğru × doğruPuanı) - (yanlış × yanlışCezası)`
- Görsel feedback: doğru=yeşil, yanlış=kırmızı işaretleme warped bitmap üzerinde

## Mimari

- **MVVM** pattern (ViewModel + StateFlow + Compose)
- **Single Activity** + Compose Navigation
- **Singleton** servisler: OcrProcessor, BubbleDetector, QrScanner (Hilt @Singleton)
- **Repository** pattern: ExamRepository, StudentResultRepository
- Tamamen **offline** — tüm ML modelleri cihaza gömülü (bundled)

### Koordinat Sistemi

Tüm bileşenler (PDF oluşturucu, baloncuk algılayıcı, OCR) aynı 800x1100px koordinat uzayını paylaşır. Bu sayede form oluşturma ile okuma birebir eşleşir.

## Proje Yapısı

```
app/src/main/java/com/optiscan/
├── analysis/          # NotlandırmaMotoru, puanlama modelleri
├── camera/            # CameraX yöneticisi
├── data/              # Room veritabanı, DAO'lar, entity'ler, repository'ler
├── di/                # Hilt modülleri
├── export/            # Excel dışa aktarıcı, PDF form oluşturucu
├── ocr/               # ML Kit OCR işlemcisi
├── processing/        # BaloncukAlgılayıcı, PerspektifDönüştürücü, Omrİşlemci
├── qr/                # QR/Barkod tarayıcı
└── ui/
    ├── navigation/    # Navigasyon grafiği, ekran rotaları
    ├── screens/       # Ana Sayfa, Sınavlar, Kamera, Sonuçlar, Dışa Aktarma
    └── theme/         # Renkler, Tipografi, Tema
```

## Nasıl Çalışır

1. **Sınav Oluştur** — Başlık, ders, soru sayısı ve cevap anahtarını belirle
2. **Form Oluştur** — Otomatik oluşturulan OMR PDF formunu yazdır
3. **Tara** — Kamerayı doldurulan forma çevir veya galeriden seç
4. **İşle** — Pipeline çalışır: Perspektif Düzeltme → Baloncuk Algılama → OCR → Notlandırma
5. **İncele** — Renkli sonuçları doğru/yanlış/boş dağılımıyla gör
6. **Dışa Aktar** — Sonuçları Excel tablosu olarak indir

## Özellikler

- **OMR Baloncuk Algılama** — OpenCV adaptif eşikleme ile basılı cevap kağıtlarındaki doldurulmuş baloncukları algılar
- **OCR Öğrenci Bilgisi** — ML Kit ile ad soyad, öğrenci no ve şube bilgilerini okur
- **QR Kod Desteği** — Form üzerindeki QR kodu okuyarak sınav ayarlarını otomatik yapılandırır
- **Otomatik Notlandırma** — Yapılandırılabilir doğru/yanlış puan değerleriyle not hesaplar
- **PDF Form Oluşturucu** — Herhangi bir soru sayısı (1-100) için yazdırılabilir A4 optik form oluşturur
- **Excel Dışa Aktarma** — Apache POI ile öğrenci bazlı sonuçları `.xlsx` olarak dışa aktarır
- **Görsel Geri Bildirim** — Taranan formlarda renkli işaretleme (yeşil = doğru, kırmızı = yanlış)
- **Tamamen Çevrimdışı** — Tüm işlemler cihaz üzerinde çalışır, internet gerekmez
- **Dinamik Düzen** — Baloncuk ızgarası otomatik ölçeklenir: 1-30 soru tek sütun, 31+ çift sütun

## Kurulum (APK)

### APK Dosya Yolu

Derlenmiş APK dosyası şu konumdadır:

```
app/build/outputs/apk/debug/app-debug.apk
```

Kendiniz derlemek isterseniz:

```bash
./gradlew assembleDebug
```

Çıktı APK: `app/build/outputs/apk/debug/app-debug.apk`

### Telefona Yükleme

#### Yöntem 1: USB ile (adb)

```bash
# Telefonu USB ile bağlayın (USB Hata Ayıklama açık olmalı)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### Yöntem 2: Doğrudan Aktarma

1. `app-debug.apk` dosyasını telefonunuza aktarın (USB dosya aktarımı, Google Drive, Telegram vb.)
2. Telefonunuzda **Ayarlar > Güvenlik > Bilinmeyen Uygulamaları Yükle** bölümünden dosya yöneticinize izin verin
3. APK dosyasını telefonunuzda açın ve **Yükle** butonuna dokunun
4. Uygulama çekmecesinden **OptiScan**'i başlatın

> **Not:** Bu bir debug derlemesidir ve debug anahtarı ile imzalanmıştır. Android 8.0+ tüm cihazlarda çalışır ancak Google Play'e yüklenemez. Release derlemesi için `app/build.gradle.kts` dosyasında imzalama ayarlarını yapılandırın.

### USB Hata Ayıklama Nasıl Açılır (adb için gerekli)

1. **Ayarlar > Telefon Hakkında** bölümüne gidin
2. **Derleme Numarası**'na "Artık bir geliştiricisiniz" mesajı görünene kadar 7 kez dokunun
3. **Ayarlar > Geliştirici Seçenekleri** bölümüne gidin
4. **USB Hata Ayıklama**'yı etkinleştirin
5. Telefonu USB ile bilgisayara bağlayın
6. Telefonda çıkan hata ayıklama izni iletişim kutusunu kabul edin

## Kaynak Koddan Derleme

### Gereksinimler

- Android Studio Hedgehog (2023.1.1) veya üstü
- JDK 17
- Android SDK 35
- NDK (OpenCV native kütüphaneleri için)

### Adımlar

```bash
git clone https://github.com/merenekiz/OptiScan.git
cd OptiScan
./gradlew assembleDebug
```

OpenCV modülü `opencv/` dizininde yerel olarak dahildir — ek kurulum gerekmez.

## OMR Algılama Parametreleri

`BubbleDetector.kt` dosyasında bulunur:

| Parametre | Değer | Açıklama |
|-----------|-------|----------|
| `SHEET_WIDTH` | 800px | Düzeltilmiş form genişliği |
| `SHEET_HEIGHT` | 1100px | Düzeltilmiş form yüksekliği |
| `GRID_START_Y` | 240px | Baloncuk ızgarası başlangıç Y koordinatı |
| `BUBBLE_DIAMETER` | 22px | Algılama daire boyutu |
| `FILL_THRESHOLD` | 0.40 | İşaretli sayılması için min doluluk % |
| `AMBIGUOUS_THRESHOLD` | 0.20 | Belirsiz işaret için min doluluk % |

## İzinler

| İzin | Kullanım Amacı |
|------|----------------|
| `CAMERA` | Cevap kağıtlarını gerçek zamanlı tarama |
| `READ_MEDIA_IMAGES` | Galeriden form resmi seçme |
| `WRITE_EXTERNAL_STORAGE` (API ≤ 29) | Eski depolama erişimi |

## Yapılandırma

### Sınav Ayarları

- **Soru Sayısı:** 1-100 (30'a kadar tek sütun, 31+ çift sütun)
- **Doğru Puanı:** Otomatik hesaplanır (100 / soru sayısı)
- **Yanlış Cezası:** Ayarlanabilir (0 = ceza yok)
- **Cevap Anahtarı:** Görsel baloncuk seçici (her soru için A-E)

## Lisans

Bu proje merenekiz tarafından geliştirilen özel bir yazılımdır. Tüm hakları saklıdır.
