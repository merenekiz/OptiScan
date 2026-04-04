#!/usr/bin/env python3
"""
Generate filled test form PNGs matching BubbleDetector's exact coordinate system.
Output: 800×1100 images with filled bubbles that OptiScan can process.

Usage: python3 generate_test_forms.py
"""

from PIL import Image, ImageDraw, ImageFont
import random
import os

# === BubbleDetector constants (must match Kotlin exactly) ===
SHEET_WIDTH = 800
SHEET_HEIGHT = 1100
GRID_START_Y = 240
GRID_MARGIN_X = 30
GRID_BOTTOM_MARGIN = 30
NUM_OPTIONS = 5
BUBBLE_DIAMETER = 22
MAX_SINGLE_COL_ROWS = 30

OPTIONS = ["A", "B", "C", "D", "E"]


def compute_layout(question_count):
    """Mirror of BubbleDetector.computeLayout()"""
    safe_count = max(question_count, 1)
    avail_h = SHEET_HEIGHT - GRID_START_Y - GRID_BOTTOM_MARGIN  # 870
    avail_w = SHEET_WIDTH - 2 * GRID_MARGIN_X  # 740

    use_second_col = safe_count > MAX_SINGLE_COL_ROWS

    if use_second_col:
        col1_count = (safe_count + 1) // 2
        col2_count = safe_count - col1_count
    else:
        col1_count = safe_count
        col2_count = 0

    max_rows = max(col1_count, col2_count)
    row_height = min(avail_h // max_rows, 36)

    if use_second_col:
        col_gap = 40
        col_width = (avail_w - col_gap) // 2
        q_num_width = 28
        bubble_area = col_width - q_num_width
        opt_spacing = bubble_area // NUM_OPTIONS

        col1_x = GRID_MARGIN_X + q_num_width
        col2_x = GRID_MARGIN_X + col_width + col_gap + q_num_width
    else:
        q_num_width = 28
        bubble_area = avail_w - q_num_width
        opt_spacing = bubble_area // NUM_OPTIONS

        col1_x = GRID_MARGIN_X + q_num_width
        col2_x = 0

    return {
        "use_second_col": use_second_col,
        "col1_count": col1_count,
        "col2_count": col2_count,
        "row_height": row_height,
        "opt_spacing": opt_spacing,
        "col1_x": col1_x,
        "col2_x": col2_x,
        "q_num_width": q_num_width,
    }


def draw_form(draw, question_count, exam_title, exam_id):
    """Draw the form template (same as FormPdfGenerator)."""
    # White background
    draw.rectangle([0, 0, SHEET_WIDTH, SHEET_HEIGHT], fill="white")

    # Border
    draw.rectangle([0, 0, SHEET_WIDTH - 1, SHEET_HEIGHT - 1], outline="#CCCCCC", width=1)

    # Corner alignment markers
    s = 14
    m = 6
    for x, y in [(m, m), (SHEET_WIDTH - m - s, m), (m, SHEET_HEIGHT - m - s), (SHEET_WIDTH - m - s, SHEET_HEIGHT - m - s)]:
        draw.rectangle([x, y, x + s, y + s], fill="black")

    # Header
    try:
        title_font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 20)
        sub_font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 12)
        label_font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 14)
        num_font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 9)
        small_font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 8)
        header_font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 9)
    except:
        title_font = ImageFont.load_default()
        sub_font = title_font
        label_font = title_font
        num_font = title_font
        small_font = title_font
        header_font = title_font

    # Title
    draw.text((SHEET_WIDTH // 2, 35), exam_title, fill="black", font=title_font, anchor="mt")

    # Subtitle
    correct_pt = 100.0 / question_count
    draw.text((SHEET_WIDTH // 2, 57), f"Ders: Test  |  {question_count} Soru  |  Her dogru: {correct_pt:.2f} puan",
              fill="#444444", font=sub_font, anchor="mt")

    margin = GRID_MARGIN_X
    end_x = SHEET_WIDTH - margin

    # Student info boxes (bigger: 30px height each)
    draw.text((margin, 88), "Ad Soyad:", fill="black", font=label_font)
    draw.rectangle([margin + 80, 76, end_x, 106], outline="black", width=1)

    mid_x = margin + int((end_x - margin) * 0.6)
    draw.text((margin, 128), "Ogrenci No:", fill="black", font=label_font)
    draw.rectangle([margin + 90, 116, mid_x - 10, 146], outline="black", width=1)
    draw.text((mid_x, 128), "Sube:", fill="black", font=label_font)
    draw.rectangle([mid_x + 40, 116, end_x, 146], outline="black", width=1)

    # Separator
    draw.line([margin, 165, end_x, 165], fill="#CCCCCC", width=1)

    # Instructions
    draw.text((margin, 172), "Her soru icin yalnizca bir secenek isaretleyiniz.", fill="#888888", font=small_font)

    # Bubble grid
    layout = compute_layout(question_count)
    r = BUBBLE_DIAMETER // 2

    # Column 1 headers
    for opt in range(NUM_OPTIONS):
        cx = layout["col1_x"] + opt * layout["opt_spacing"] + r
        draw.text((cx, GRID_START_Y - 12), OPTIONS[opt], fill="black", font=header_font, anchor="mt")

    # Column 2 headers
    if layout["use_second_col"]:
        for opt in range(NUM_OPTIONS):
            cx = layout["col2_x"] + opt * layout["opt_spacing"] + r
            draw.text((cx, GRID_START_Y - 12), OPTIONS[opt], fill="black", font=header_font, anchor="mt")

    # Column 1 bubbles
    for q in range(layout["col1_count"]):
        cy = GRID_START_Y + q * layout["row_height"] + layout["row_height"] // 2
        # Question number
        q_num_x = GRID_MARGIN_X + layout["q_num_width"] - 4
        draw.text((q_num_x, cy), f"{q + 1}.", fill="black", font=num_font, anchor="rm")
        for opt in range(NUM_OPTIONS):
            cx = layout["col1_x"] + opt * layout["opt_spacing"] + r
            draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline="black", width=1)
            draw.text((cx, cy), OPTIONS[opt], fill="#AAAAAA", font=small_font, anchor="mm")

    # Column 2 bubbles
    if layout["use_second_col"]:
        for q in range(layout["col2_count"]):
            global_q = layout["col1_count"] + q
            cy = GRID_START_Y + q * layout["row_height"] + layout["row_height"] // 2
            q_num_x = GRID_MARGIN_X + (SHEET_WIDTH - 2 * GRID_MARGIN_X - 40) // 2 + 40 + layout["q_num_width"] - 4
            # Use col2 q_num position
            draw.text((layout["col2_x"] - 4, cy), f"{global_q + 1}.", fill="black", font=num_font, anchor="rm")
            for opt in range(NUM_OPTIONS):
                cx = layout["col2_x"] + opt * layout["opt_spacing"] + r
                draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline="black", width=1)
                draw.text((cx, cy), OPTIONS[opt], fill="#AAAAAA", font=small_font, anchor="mm")

    # Footer
    draw.text((SHEET_WIDTH // 2, SHEET_HEIGHT - 15), f"OptiScan by merenekiz — {exam_id}",
              fill="#888888", font=small_font, anchor="mt")


def fill_bubble(draw, cx, cy, r):
    """Fill a bubble with dark ink (simulating student marking)."""
    # Draw filled circle
    draw.ellipse([cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1], fill="#1A1A1A")


def write_student_info(draw, name, number, sube):
    """Write student info in the boxes (simulating handwriting)."""
    try:
        hand_font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 20)
    except:
        hand_font = ImageFont.load_default()

    margin = GRID_MARGIN_X
    # Name - inside the box area (box is y=76-106, 30px height)
    draw.text((margin + 85, 80), name, fill="black", font=hand_font)
    # Number - inside the box area (box is y=116-146, 30px height)
    draw.text((margin + 95, 120), number, fill="black", font=hand_font)
    # Sube
    mid_x = margin + int((SHEET_WIDTH - margin - margin) * 0.6)
    draw.text((mid_x + 45, 120), sube, fill="black", font=hand_font)


def generate_test_form(question_count, answers, student_name, student_no, sube, exam_title, exam_id, filename):
    """Generate a filled test form PNG."""
    img = Image.new("RGB", (SHEET_WIDTH, SHEET_HEIGHT), "white")
    draw = ImageDraw.Draw(img)

    # Draw base form
    draw_form(draw, question_count, exam_title, exam_id)

    # Write student info
    write_student_info(draw, student_name, student_no, sube)

    # Fill answer bubbles
    layout = compute_layout(question_count)
    r = BUBBLE_DIAMETER // 2

    for q_idx, answer in enumerate(answers):
        if answer == "":
            continue  # leave empty
        opt_idx = OPTIONS.index(answer)

        if q_idx < layout["col1_count"]:
            # Column 1
            cy = GRID_START_Y + q_idx * layout["row_height"] + layout["row_height"] // 2
            cx = layout["col1_x"] + opt_idx * layout["opt_spacing"] + r
        else:
            # Column 2
            q_in_col2 = q_idx - layout["col1_count"]
            cy = GRID_START_Y + q_in_col2 * layout["row_height"] + layout["row_height"] // 2
            cx = layout["col2_x"] + opt_idx * layout["opt_spacing"] + r

        fill_bubble(draw, cx, cy, r)

    img.save(filename, "PNG")
    print(f"  Saved: {filename}")
    return img


def main():
    output_dir = "/Users/merenekiz/vscode/OptiScan/test_forms"
    os.makedirs(output_dir, exist_ok=True)

    random.seed(42)

    # ===== 20-question test form =====
    print("\n--- 20 Soruluk Form ---")
    answers_20 = []
    answer_key_20 = []
    for i in range(20):
        correct = random.choice(OPTIONS)
        answer_key_20.append(correct)
        # Student gets ~60% correct, ~30% wrong, ~10% empty
        r = random.random()
        if r < 0.60:
            answers_20.append(correct)  # correct
        elif r < 0.90:
            wrong = random.choice([o for o in OPTIONS if o != correct])
            answers_20.append(wrong)  # wrong
        else:
            answers_20.append("")  # empty

    generate_test_form(
        question_count=20,
        answers=answers_20,
        student_name="Ahmet Yilmaz",
        student_no="2024001",
        sube="10-A",
        exam_title="Matematik Sinavi",
        exam_id=1001,
        filename=os.path.join(output_dir, "test_20q_filled.png")
    )
    print(f"  Answer key: {answer_key_20}")
    print(f"  Student answers: {answers_20}")

    # ===== 25-question test form =====
    print("\n--- 25 Soruluk Form ---")
    answers_25 = []
    answer_key_25 = []
    for i in range(25):
        correct = random.choice(OPTIONS)
        answer_key_25.append(correct)
        r = random.random()
        if r < 0.55:
            answers_25.append(correct)
        elif r < 0.85:
            wrong = random.choice([o for o in OPTIONS if o != correct])
            answers_25.append(wrong)
        else:
            answers_25.append("")

    generate_test_form(
        question_count=25,
        answers=answers_25,
        student_name="Elif Demir",
        student_no="2024015",
        sube="11-B",
        exam_title="Fen Bilgisi Sinavi",
        exam_id=1002,
        filename=os.path.join(output_dir, "test_25q_filled.png")
    )
    print(f"  Answer key: {answer_key_25}")
    print(f"  Student answers: {answers_25}")

    # ===== 35-question test form (dual column) =====
    print("\n--- 35 Soruluk Form (Cift Sutun) ---")
    answers_35 = []
    answer_key_35 = []
    for i in range(35):
        correct = random.choice(OPTIONS)
        answer_key_35.append(correct)
        r = random.random()
        if r < 0.50:
            answers_35.append(correct)
        elif r < 0.85:
            wrong = random.choice([o for o in OPTIONS if o != correct])
            answers_35.append(wrong)
        else:
            answers_35.append("")

    generate_test_form(
        question_count=35,
        answers=answers_35,
        student_name="Mehmet Kaya",
        student_no="2024042",
        sube="12-C",
        exam_title="Tarih Sinavi",
        exam_id=1003,
        filename=os.path.join(output_dir, "test_35q_filled.png")
    )
    print(f"  Answer key: {answer_key_35}")
    print(f"  Student answers: {answers_35}")

    print(f"\nAll forms saved to: {output_dir}")
    print("\nAnswer keys (for creating exams in app):")
    print(f"  20q: {','.join(answer_key_20)}")
    print(f"  25q: {','.join(answer_key_25)}")
    print(f"  35q: {','.join(answer_key_35)}")


if __name__ == "__main__":
    main()
