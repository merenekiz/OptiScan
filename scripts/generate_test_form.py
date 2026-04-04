#!/usr/bin/env python3
"""
OptiScan — Test Formu ve QR Kod Oluşturucu

BubbleDetector.kt ile TAM UYUMLU form üretir.
Warp sonrası 800x1100'e normalize edildiğinde grid koordinatları eşleşir.

Kullanım:
    pip3 install qrcode pillow
    python3 scripts/generate_test_form.py
"""

import json
import os
import sys

try:
    import qrcode
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "qrcode", "pillow"])
    import qrcode
    from PIL import Image, ImageDraw, ImageFont

# ======== BubbleDetector.kt SABİTLERİ (DOKUNMA) ========
# Bu değerler BubbleDetector.kt companion object ile BİREBİR aynı olmalı
SHEET_WIDTH  = 800
SHEET_HEIGHT = 1100
GRID_START_Y     = 220     # px from top where bubbles start
GRID_LEFT_MARGIN = 50      # px from left
BUBBLE_ROW_HEIGHT = 28     # px per question row
BUBBLE_DIAMETER  = 22      # expected bubble diameter
OPTION_SPACING   = 52      # px between option columns (A-E)
NUM_OPTIONS = 5
COLUMN_SPLIT = 50
SECOND_COLUMN_X_OFFSET = 420
# ========================================================

# Baskı çözünürlüğü — 800x1100 doğrudan küçük olduğundan 2x ölçekle yazdır
PRINT_SCALE = 2     # 1600x2200 px => ~200 DPI A4 baskı
PW = SHEET_WIDTH  * PRINT_SCALE   # 1600
PH = SHEET_HEIGHT * PRINT_SCALE   # 2200
S  = PRINT_SCALE                   # kısaltma

# ======== SINAV AYARLARI ========
EXAM_CONFIG = {
    "examId": "MATH-2024-TEST",
    "questionCount": 20,
    "correctPoint": 5.0,
    "wrongPenalty": 1.25,
    "title": "Matematik Test Sinavi",
    "subject": "Matematik"
}
ANSWER_KEY = "ABCDEBCDAECBADEBACDE"
OPTIONS = ["A", "B", "C", "D", "E"]


def get_fonts():
    """Platform bağımsız font yükle"""
    paths = [
        "/System/Library/Fonts/Helvetica.ttc",
        "/System/Library/Fonts/SFNSText.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for p in paths:
        if os.path.exists(p):
            return (
                ImageFont.truetype(p, 22 * S),
                ImageFont.truetype(p, 14 * S),
                ImageFont.truetype(p, 11 * S),
                ImageFont.truetype(p, 8 * S),
            )
    d = ImageFont.load_default()
    return d, d, d, d


def bubble_center(q: int, opt: int) -> tuple:
    """BubbleDetector.kt ile BİREBİR aynı hesaplama.
    Döndürür: (cx, cy) warp-space 800x1100 koordinatında."""
    row = q % COLUMN_SPLIT
    is_second = q >= COLUMN_SPLIT
    x_base = SECOND_COLUMN_X_OFFSET if is_second else GRID_LEFT_MARGIN
    cy = GRID_START_Y + row * BUBBLE_ROW_HEIGHT + BUBBLE_ROW_HEIGHT // 2
    cx = x_base + opt * OPTION_SPACING + BUBBLE_DIAMETER // 2
    return cx, cy


def create_qr_image(config: dict, size: int = 140) -> Image.Image:
    """QR kod oluştur (PIL Image)"""
    qr = qrcode.QRCode(version=2, error_correction=qrcode.constants.ERROR_CORRECT_M,
                        box_size=6, border=2)
    qr.add_data(json.dumps(config, ensure_ascii=False))
    qr.make(fit=True)
    return qr.make_image(fill_color="black", back_color="white").resize((size, size))


def draw_form_skeleton(draw: ImageDraw.Draw, config: dict, fonts: tuple,
                       student_name: str = "", student_no: str = "", student_class: str = ""):
    """Form iskeletini çiz: alignment marker, başlık, bilgi alanı"""
    f_large, f_med, f_small, f_tiny = fonts
    M = 30 * S  # margin

    # ---- Alignment Markers (4 köşe — siyah kareler) ----
    ms = 20 * S
    draw.rectangle([M, M, M + ms, M + ms], fill="black")
    draw.rectangle([PW - M - ms, M, PW - M, M + ms], fill="black")
    draw.rectangle([M, PH - M - ms, M + ms, PH - M], fill="black")
    draw.rectangle([PW - M - ms, PH - M - ms, PW - M, PH - M], fill="black")

    # ---- Başlık ----
    draw.text((PW // 2, M + 50 * S), config.get("title", "Sinav"),
              fill="black", font=f_large, anchor="mm")

    # ---- Öğrenci bilgi alanı ----
    iy = M + 80 * S
    name_text = f"Ad Soyad: {student_name}" if student_name else "Ad Soyad: ______________________"
    no_text = f"Ogrenci No: {student_no}" if student_no else "Ogrenci No: ____________"
    cls_text = f"Sinif: {student_class}" if student_class else "Sinif: ________"
    draw.text((M + 10 * S, iy), name_text, fill="black", font=f_med)
    draw.text((M + 10 * S, iy + 24 * S), no_text, fill="black", font=f_med)
    draw.text((PW // 2, iy + 24 * S), cls_text, fill="black", font=f_med)

    # ---- Sınav bilgi çizgisi ----
    by = iy + 55 * S
    info = f"Soru:{config['questionCount']}  D:+{config['correctPoint']}  Y:-{config['wrongPenalty']}  ID:{config['examId']}"
    draw.rectangle([M + 10 * S, by, PW - M - 10 * S, by + 18 * S], outline="gray")
    draw.text((PW // 2, by + 9 * S), info, fill="black", font=f_small, anchor="mm")

    # ---- Alt bilgi ----
    draw.text((PW // 2, PH - M + 5 * S), "OptiScan Test Formu", fill="gray", font=f_small, anchor="mm")


def draw_bubble_grid(draw: ImageDraw.Draw, config: dict, fonts: tuple,
                     answers: str = ""):
    """Baloncuk grid çiz — BubbleDetector koordinatlarıyla birebir uyumlu"""
    _, _, f_small, f_tiny = fonts
    qcount = config["questionCount"]

    # Sütun başlıkları
    for opt_idx, opt_label in enumerate(OPTIONS):
        cx, _ = bubble_center(0, opt_idx)
        draw.text((cx * S, (GRID_START_Y - 15) * S), opt_label, fill="black", font=f_small, anchor="mm")

    for q in range(qcount):
        cx0, cy = bubble_center(q, 0)
        # Soru numarası
        draw.text(((cx0 - 22) * S, cy * S), f"{q+1}.", fill="black", font=f_small, anchor="rm")

        student_ans = answers[q].upper() if q < len(answers) and answers[q].strip() else ""

        for opt_idx, opt_label in enumerate(OPTIONS):
            cx, cy = bubble_center(q, opt_idx)
            r = (BUBBLE_DIAMETER // 2) * S
            px, py = cx * S, cy * S
            bbox = [px - r, py - r, px + r, py + r]

            if opt_label == student_ans:
                # Doldurulmuş
                draw.ellipse(bbox, fill="#1a1a1a", outline="black", width=2)
            else:
                # Boş
                draw.ellipse(bbox, outline="black", width=2)
                draw.text((px, py), opt_label, fill="#aaaaaa", font=f_tiny, anchor="mm")


def create_form(config: dict, answers: str = "",
                student_name: str = "", student_no: str = "", student_class: str = "",
                filename: str = "form.png", add_qr: bool = True) -> Image.Image:
    """Tam form üret"""
    img = Image.new("RGB", (PW, PH), "white")
    draw = ImageDraw.Draw(img)
    fonts = get_fonts()

    draw_form_skeleton(draw, config, fonts, student_name, student_no, student_class)
    draw_bubble_grid(draw, config, fonts, answers)

    # QR kod (sağ üst)
    if add_qr:
        qr = create_qr_image(config, 80 * S)
        img.paste(qr, (PW - 30 * S - 80 * S, 35 * S))

    img.save(filename, dpi=(200, 200))
    return img


def score_report(config, answer_key, student_answers, student_name):
    """Beklenen skor hesapla ve yazdır"""
    qc = config["questionCount"]
    correct = wrong = empty = 0
    for i in range(qc):
        key = answer_key[i].upper() if i < len(answer_key) else ""
        ans = student_answers[i].upper() if i < len(student_answers) and student_answers[i].strip() else ""
        if not ans:
            empty += 1
        elif ans == key:
            correct += 1
        else:
            wrong += 1
    score = max(0, correct * config["correctPoint"] - wrong * config["wrongPenalty"])
    print(f"  {student_name}: D={correct} Y={wrong} B={empty} → Puan={score:.1f}")
    return correct, wrong, empty, score


# ===== ANA PROGRAM =====
if __name__ == "__main__":
    out = "/Users/merenekiz/vscode/OptiScan/test_forms"
    os.makedirs(out, exist_ok=True)

    print("=" * 60)
    print("OptiScan Test Form Oluşturucu (BubbleDetector Uyumlu)")
    print(f"Grid: {SHEET_WIDTH}x{SHEET_HEIGHT}, Baskı: {PW}x{PH} (2x)")
    print("=" * 60)

    # 1. Boş form (yazıcıdan çıkart, elle doldur)
    print("\n[1] Boş form...")
    create_form(EXAM_CONFIG, filename=f"{out}/bos_form.png")
    print(f"  → {out}/bos_form.png")

    # 2. Tam doğru
    print("\n[2] Tam doğru öğrenci...")
    create_form(EXAM_CONFIG, answers=ANSWER_KEY,
                student_name="Ayse Kaya", student_no="2024001", student_class="10-A",
                filename=f"{out}/tam_dogru.png")
    score_report(EXAM_CONFIG, ANSWER_KEY, ANSWER_KEY, "Ayse Kaya")

    # 3. Karışık (6-10 arası yanlış)
    mixed = "ABCDEAAAAACBADEBACDE"
    print("\n[3] Karışık cevaplı...")
    create_form(EXAM_CONFIG, answers=mixed,
                student_name="Mehmet Demir", student_no="2024002", student_class="10-A",
                filename=f"{out}/karisik.png")
    score_report(EXAM_CONFIG, ANSWER_KEY, mixed, "Mehmet Demir")

    # 4. Yarısı boş
    half = "ABCDE               "
    print("\n[4] Yarısı boş...")
    create_form(EXAM_CONFIG, answers=half,
                student_name="Zeynep Yildiz", student_no="2024003", student_class="10-B",
                filename=f"{out}/yarisibos.png")
    score_report(EXAM_CONFIG, ANSWER_KEY, half, "Zeynep Yildiz")

    # 5. Hepsi yanlış
    alwrong = "EDCBAEDCBAEDCBAEDCBA"
    print("\n[5] Hepsi yanlış...")
    create_form(EXAM_CONFIG, answers=alwrong,
                student_name="Can Ozturk", student_no="2024004", student_class="10-A",
                filename=f"{out}/hepsyanlis.png")
    score_report(EXAM_CONFIG, ANSWER_KEY, alwrong, "Can Ozturk")

    # 6. Ayrı QR kod (büyük, telefon ekranından göstermek için)
    print("\n[6] Ayrı QR kod...")
    qr = create_qr_image(EXAM_CONFIG, 400)
    qr.save(f"{out}/sinav_qr.png")
    print(f"  → {out}/sinav_qr.png")
    print(f"  QR İçerik: {json.dumps(EXAM_CONFIG, ensure_ascii=False)}")

    print("\n" + "=" * 60)
    print(f"Tüm dosyalar: {out}/")
    print()
    print("TEST TALİMATLARI:")
    print("  1. Boş formu A4 yazdır → elle doldur → OptiScan ile tara")
    print("  2. Dolu formları ekrandan göster → OptiScan ile tara")
    print("  3. sinav_qr.png'yi ekrandan göster → QR okuma testi")
    print("  4. Beklenen puanları yukarıdaki çıktıyla karşılaştır")
    print("=" * 60)
