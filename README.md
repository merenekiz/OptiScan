# OptiScan

Android tabanlı OMR (Optik İşaret Tanıma) + OCR sınav okuyucu uygulaması. Basılı cevap kağıtlarını telefon kamerasıyla tarayın, doldurulmuş baloncukları otomatik algılayın, OCR ile öğrenci bilgilerini okuyun ve notlandırılmış sonuçları Excel olarak dışa aktarın.

**Geliştiren:** merenekiz

> **APK İndir:** [`app/build/outputs/apk/debug/app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk)

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

## Teknoloji

| Katman | Teknoloji |
|--------|-----------|
| Dil | Kotlin |
| Arayüz | Jetpack Compose + Material3 |
| Kamera | CameraX |
| Görüntü İşleme | OpenCV 4.x (yerel modül) |
| OCR | Google ML Kit Text Recognition (gömülü) |
| QR Algılama | Google ML Kit Barcode Scanning (gömülü) |
| Veritabanı | Room |
| Bağımlılık Enjeksiyonu | Hilt |
| Excel Dışa Aktarma | Apache POI 5.2.5 |
| Min SDK | 26 (Android 8.0) |
| Hedef SDK | 35 |

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

### OMR Algılama Parametreleri

`BubbleDetector.kt` dosyasında bulunur:

| Parametre | Değer | Açıklama |
|-----------|-------|----------|
| `SHEET_WIDTH` | 800px | Düzeltilmiş form genişliği |
| `SHEET_HEIGHT` | 1100px | Düzeltilmiş form yüksekliği |
| `GRID_START_Y` | 240px | Baloncuk ızgarası başlangıç Y koordinatı |
| `BUBBLE_DIAMETER` | 22px | Algılama daire boyutu |
| `FILL_THRESHOLD` | 0.40 | İşaretli sayılması için min doluluk % |
| `AMBIGUOUS_THRESHOLD` | 0.20 | Belirsiz işaret için min doluluk % |

## Lisans

Bu proje merenekiz tarafından geliştirilen özel bir yazılımdır. Tüm hakları saklıdır.
