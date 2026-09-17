# -*- coding: utf-8 -*-
"""Detect icon-sized blobs (filters out text labels and palette swatches) in the
AI-generated sheet by alpha, in reading order, for manual name verification."""
import numpy as np
from PIL import Image
from scipy import ndimage
import sys

path = sys.argv[1]
im = Image.open(path).convert("RGBA")
arr = np.array(im)
alpha = arr[:, :, 3]

mask = alpha > 60
labeled, n = ndimage.label(mask, structure=np.ones((3, 3)))
objs = ndimage.find_objects(labeled)

icon_boxes = []
for sl in objs:
    if sl is None:
        continue
    y0, y1 = sl[0].start, sl[0].stop
    x0, x1 = sl[1].start, sl[1].stop
    w, h = x1 - x0, y1 - y0
    if h < 45 or w < 45:  # drop text labels / thin dividers / palette swatch rows
        continue
    if y0 > 780:  # palette section
        continue
    icon_boxes.append((x0, y0, x1, y1))

# Merge boxes that are heavily overlapping/nested (icon glow sometimes splits into 2 blobs)
def overlaps(a, b):
    ax0, ay0, ax1, ay1 = a
    bx0, by0, bx1, by1 = b
    ix0, iy0 = max(ax0, bx0), max(ay0, by0)
    ix1, iy1 = min(ax1, bx1), min(ay1, by1)
    if ix0 >= ix1 or iy0 >= iy1:
        return False
    inter = (ix1 - ix0) * (iy1 - iy0)
    a_area = (ax1 - ax0) * (ay1 - ay0)
    b_area = (bx1 - bx0) * (by1 - by0)
    return inter > 0.3 * min(a_area, b_area)

merged = []
used = [False] * len(icon_boxes)
for i, b in enumerate(icon_boxes):
    if used[i]:
        continue
    x0, y0, x1, y1 = b
    for j in range(i + 1, len(icon_boxes)):
        if used[j]:
            continue
        if overlaps(b, icon_boxes[j]):
            ox0, oy0, ox1, oy1 = icon_boxes[j]
            x0, y0, x1, y1 = min(x0, ox0), min(y0, oy0), max(x1, ox1), max(y1, oy1)
            used[j] = True
    merged.append((x0, y0, x1, y1))

# Row bucket by y-center with 60px tolerance, then sort by x within row
merged.sort(key=lambda b: (b[1] + b[3]) / 2)
rows = []
for b in merged:
    cy = (b[1] + b[3]) / 2
    placed = False
    for row in rows:
        if abs(row[0] - cy) < 50:
            row[1].append(b)
            placed = True
            break
    if not placed:
        rows.append([cy, [b]])
rows.sort(key=lambda r: r[0])

idx = 0
for row_cy, row_boxes in rows:
    row_boxes.sort(key=lambda b: b[0])
    for b in row_boxes:
        print(idx, "box", b, "size", b[2] - b[0], b[3] - b[1])
        idx += 1
    print("--- row end, y~", row_cy)
