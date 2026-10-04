"""
Sculpts Screech as smooth rock: every body part is a signed-distance shape (balls, capsules, ellipsoids blended together, roughened
like stone, with real holes carved into the chest and head and a small hot eyeball in the eye socket), turned into a triangle mesh by
surface nets. The parts stay separate (one per bone) so the creature can be posed rigidly, like loose pieces of rock.

The shape is read off one reference picture of the DOORS creature (front three-quarter view) and rebuilt by eye in our own
geometry; the back and sides are filled in by the same logic. Nothing is taken from the game's files.

Run:  python tools/screech/sculpt_screech.py [preview_dir]
Writes src/main/resources/assets/lotusblight/models/screech.bin          (triangles, one block per part)
       src/main/resources/assets/lotusblight/models/screech.skeleton.json (the bones: parent and pivot)
       src/main/resources/assets/lotusblight/textures/entity/screech.png, screech_glowing.png

Space: right-handed like the world, units are 1/16 of a block, y up, the character's FRONT is +z and its LEFT is +x.
"""
import json
import math
import os
import struct
import sys

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src/main/resources/assets/lotusblight')
BIN_OUT = os.path.join(RES, 'models/screech.bin')
SKEL_OUT = os.path.join(RES, 'models/screech.skeleton.json')
TEX_OUT = os.path.join(RES, 'textures/entity/screech.png')
GLOW_OUT = os.path.join(RES, 'textures/entity/screech_glowing.png')

S = 0.0583            # units per picture pixel: the picture's figure is ~720 px tall -> ~42 units


def P(x, y, z=0.0):
    """Reference-picture pixel (+ depth, forward positive) -> model units."""
    return np.array([(x - 400) * S, (730 - y) * S, z])


# ------------------------------------------------------------------------------------------------ distance shapes

def sphere(c, r):
    c = np.asarray(c, float)
    return lambda p: np.linalg.norm(p - c, axis=1) - r


def ellipsoid(c, radii, tilt=0.0, about=None):
    """An ellipsoid; `tilt` degrees clockwise seen from the front, about the point `about` (default its centre)."""
    c = np.asarray(c, float)
    radii = np.asarray(radii, float)
    pivot = c if about is None else np.asarray(about, float)
    a = math.radians(tilt)
    ca, sa = math.cos(a), math.sin(a)

    def f(p):
        q = p - pivot
        x = q[:, 0] * ca - q[:, 1] * sa                  # undo the clockwise tilt (viewed with +x to the right)
        y = q[:, 0] * sa + q[:, 1] * ca
        q2 = np.stack([x, y, q[:, 2]], axis=1) + pivot - c
        k = np.linalg.norm(q2 / radii, axis=1)
        return (k - 1.0) * radii.min()
    return f


def octagon(c, rx, ry, z_back, z_front, rot=0.0):
    """An octagonal prism along z (the opening of a hollow): rx, ry across, from z_back to z_front, turned by rot degrees."""
    c = np.asarray(c, float)
    a = math.radians(rot)
    ca, sa = math.cos(a), math.sin(a)
    k = math.cos(math.pi / 8)

    def f(p):
        x = (p[:, 0] - c[0]) * ca + (p[:, 1] - c[1]) * sa
        y = -(p[:, 0] - c[0]) * sa + (p[:, 1] - c[1]) * ca
        q = np.stack([x / rx, y / ry], axis=1)
        edge = np.zeros(len(p))
        for i in range(8):
            ang = i * math.pi / 4 + math.pi / 8
            edge = np.maximum(edge, q[:, 0] * math.cos(ang) + q[:, 1] * math.sin(ang))
        side = (edge - k) * min(rx, ry)
        zc = (z_back + z_front) / 2
        hz = (z_front - z_back) / 2
        dz = np.abs(p[:, 2] - zc) - hz
        outside = np.maximum(side, dz)
        return np.where(outside > 0, np.sqrt(np.maximum(side, 0) ** 2 + np.maximum(dz, 0) ** 2), outside)
    return f


def rbox(c, half, r):
    """A box with rounded edges: centre c, half sizes, corner radius r."""
    c, half = np.asarray(c, float), np.asarray(half, float)

    def f(p):
        q = np.abs(p - c) - (half - r)
        return np.linalg.norm(np.maximum(q, 0), axis=1) + np.minimum(np.max(q, axis=1), 0) - r
    return f


def capsule(a, b, ra, rb):
    a, b = np.asarray(a, float), np.asarray(b, float)
    ab = b - a
    l2 = float(ab @ ab)

    def f(p):
        t = np.clip(((p - a) @ ab) / l2, 0, 1)
        d = np.linalg.norm(p - (a + t[:, None] * ab), axis=1)
        return d - (ra + (rb - ra) * t)
    return f


def smooth_union(ds, k):
    out = ds[0]
    for d in ds[1:]:
        h = np.clip(0.5 + 0.5 * (d - out) / k, 0, 1)
        out = d * (1 - h) + out * h - k * h * (1 - h)
    return out


def grain(p):
    """Fine stone grain, a few tenths of a unit."""
    out = np.zeros(len(p))
    for i, (f, a) in enumerate([(4.1, 0.12), (6.3, 0.07)]):
        k = _GR[i]
        out += a * np.sin(p @ (k * f) + i * 1.7)
    return out


_GR = [np.array([0.62, 0.52, 0.59]), np.array([-0.55, 0.64, 0.53])]


def smooth_max(a, b, k):
    h = np.clip(0.5 - 0.5 * (b - a) / k, 0, 1)
    return a * h + b * (1 - h) + k * h * (1 - h)


_rng = np.random.RandomState(11)
_WAVES = [(_rng.randn(3) / np.linalg.norm(_rng.randn(3)) * f, _rng.rand() * 6.28, a)
          for f, a in [(0.55, 0.28), (0.9, 0.2), (1.4, 0.13), (2.3, 0.08), (3.6, 0.05)] for _ in range(2)]


def rough(p):
    """Stone lumpiness: a few sine waves in random directions, a fraction of a unit tall."""
    out = np.zeros(len(p))
    for k, ph, amp in _WAVES:
        out += amp * np.sin(p @ k + ph)
    return out


class Part:
    def __init__(self, name, solids, k=1.1, holes=(), eyes=(), rough_amount=1.0, cores=(), lips=()):
        self.name, self.solids, self.k = name, solids, k
        self.holes, self.eyes, self.rough, self.cores, self.lips = list(holes), list(eyes), rough_amount, list(cores), list(lips)

    def field(self, p):
        body = smooth_union([s(p) for s in self.solids], self.k) + rough(p) * self.rough * 0.7 + grain(p) * 0.25 * self.rough
        lip_d = np.min([l(p) for l in self.lips], axis=0) if self.lips else None
        if lip_d is not None:
            body = np.minimum(body, lip_d)
        d = body
        tag = np.zeros(len(p), dtype=np.int8)          # 0 stone, 1 inside a hollow, 2 the eye
        depth = np.zeros(len(p))
        if self.holes:
            hole = np.min([h(p) for h in self.holes], axis=0)
            carved = smooth_max(body, -hole, 0.18)
            tag[-hole > body - 0.05] = 1
            depth = np.clip(-body / 2.6, 0, 1)
            d = carved
            # the pale lip round every opening, on the stone's face right next to it
            if lip_d is not None:
                tag[(lip_d <= body + 0.03) & (lip_d < 0.12) & (tag == 0) & (np.abs(carved) < 0.3)] = 4
        if self.eyes:
            eye = np.min([e(p) for e in self.eyes], axis=0)
            tag[eye < d] = 2
            d = np.minimum(d, eye)
        if self.cores:
            core = np.min([c(p) for c in self.cores], axis=0)
            tag[core < d] = 3
            d = np.minimum(d, core)
        return d, tag, depth

    def bounds(self, pad=3.0):
        # probe along a coarse grid to find where the shape is, so the sampling grid hugs it
        lo, hi = np.full(3, 1e9), np.full(3, -1e9)
        g = np.mgrid[-40:40:2.0, -5:50:2.0, -20:20:2.0].reshape(3, -1).T
        d, _, _ = self.field(g)
        pts = g[d < 3.0]
        lo, hi = pts.min(axis=0) - pad, pts.max(axis=0) + pad
        return lo, hi


# ------------------------------------------------------------------------------------------------ surface nets

def surface_nets(field, lo, hi, h):
    xs = np.arange(lo[0], hi[0] + h, h)
    ys = np.arange(lo[1], hi[1] + h, h)
    zs = np.arange(lo[2], hi[2] + h, h)
    nx, ny, nz = len(xs), len(ys), len(zs)
    gx, gy, gz = np.meshgrid(xs, ys, zs, indexing='ij')
    pts = np.stack([gx.ravel(), gy.ravel(), gz.ravel()], axis=1)
    vals = field(pts)[0].reshape(nx, ny, nz)

    def corner(dx, dy, dz):
        return vals[dx:nx - 1 + dx, dy:ny - 1 + dy, dz:nz - 1 + dz]

    base = np.stack(np.meshgrid(xs[:-1], ys[:-1], zs[:-1], indexing='ij'), axis=-1)
    acc = np.zeros(base.shape)
    cnt = np.zeros(base.shape[:3])
    corners = [(i & 1, (i >> 1) & 1, (i >> 2) & 1) for i in range(8)]
    for a in range(8):
        for b in range(a + 1, 8):
            ca, cb = corners[a], corners[b]
            if sum(abs(ca[k] - cb[k]) for k in range(3)) != 1:
                continue
            va, vb = corner(*ca), corner(*cb)
            cross = (va < 0) != (vb < 0)
            t = np.where(cross, va / np.where(cross, va - vb, 1.0), 0.0)
            pa = np.array(ca) * h
            pb = np.array(cb) * h
            pt = pa + (pb - pa) * t[..., None]
            acc += np.where(cross[..., None], pt, 0.0)
            cnt += cross
    mask = cnt > 0
    verts = (base + acc / np.maximum(cnt, 1)[..., None])[mask]
    index = np.full(mask.shape, -1, dtype=np.int64)
    index[mask] = np.arange(mask.sum())

    quads = []
    inside = vals < 0
    # edges along x, y, z: the four cells that share an edge make one quad
    for axis in range(3):
        sl_a = [slice(None)] * 3
        sl_b = [slice(None)] * 3
        sl_a[axis] = slice(0, -1)
        sl_b[axis] = slice(1, None)
        change = inside[tuple(sl_a)] != inside[tuple(sl_b)]
        ii = np.argwhere(change)
        for i in ii:
            i = list(i)
            others = [k for k in range(3) if k != axis]
            u, w = others
            if i[u] < 1 or i[w] < 1 or i[u] >= (nx, ny, nz)[u] - 1 or i[w] >= (nx, ny, nz)[w] - 1:
                continue
            cells = []
            for du, dw in ((-1, -1), (0, -1), (0, 0), (-1, 0)):
                c = list(i)
                c[u] += du
                c[w] += dw
                cells.append(tuple(c))
            ids = [index[c] if all(0 <= c[k] < index.shape[k] for k in range(3)) else -1 for c in cells]
            if min(ids) < 0:
                continue
            quads.append(ids)
    tris = []
    for q in quads:
        tris.append((q[0], q[1], q[2]))
        tris.append((q[0], q[2], q[3]))
    return verts, np.array(tris, dtype=np.int64)


def gradient(field, p, eps=0.05):
    out = np.zeros_like(p)
    for k in range(3):
        d = np.zeros(3)
        d[k] = eps
        out[:, k] = field(p + d)[0] - field(p - d)[0]
    n = np.linalg.norm(out, axis=1, keepdims=True)
    return out / np.maximum(n, 1e-9)


# ------------------------------------------------------------------------------------------------ the creature

def hollow(cx, cy, rx, ry, surface_z, depth=1.0, rot=None):
    """An octagonal opening sunk into a front surface at picture position (cx, cy), `depth` deep below surface_z."""
    c = P(cx, cy, surface_z)
    if rot is None:
        rot = (cx * 7 + cy * 3) % 45
    return octagon(c, rx * S, ry * S, surface_z - depth, surface_z + 4.0, rot)


def short(a, b, amount):
    """The end b of a limb pulled back along the limb by `amount`, so the next piece does not touch it."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    d = (b - a) / np.linalg.norm(b - a)
    return b - d * amount


def core(a, b, r=0.5):
    """A short rod (the rebar) across the joint at b (a is the far end of the limb piece), sunk in both pieces."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    d = (b - a) / np.linalg.norm(b - a)
    return capsule(b - d * 2.0, b + d * 0.6, r, r)


# The front reference: 770 px for the whole figure, centred on x = 305; units are 1/16 block.
S2 = 0.0545


def Q(x, y, z=0.0):
    return np.array([(x - 305) * S2, (790 - y) * S2, z])


def window(x, y, rx, ry, surface_z, rod=True):
    """An oval-ish opening in a limb with a rod of rebar across it. Returns (opening, rod or None)."""
    c = Q(x, y, surface_z)
    opening = octagon(c, rx * S2, ry * S2, surface_z - 1.4, surface_z + 3.0, 0)
    bar = capsule(c + np.array([0.0, ry * S2 * 1.3, -0.7]), c + np.array([0.0, -ry * S2 * 1.3, -0.7]), 0.45, 0.45) if rod else None
    return opening, bar


def finger(base, d, lens, radii, curl, scale):
    """A finger of stone segments from `base` along `d`, each segment bending further towards the front, with a knuckle at every joint and a sharp end."""
    shapes = []
    p = np.array(base, float)
    dirv = np.array(d, float)
    dirv /= np.linalg.norm(dirv)
    for i, ln in enumerate(lens):
        dirv = dirv + np.array([0.0, 0.0, curl * (i + 1)])
        dirv /= np.linalg.norm(dirv)
        q = p + dirv * ln * scale
        r0, r1 = radii[i] * scale, (radii[i + 1] if i + 1 < len(radii) else 0.2) * scale
        shapes.append(capsule(p, q, r0, r1))
        if i + 1 < len(lens):
            shapes.append(sphere(q, r1 * 1.18))                  # the knuckle
        p = q
    return shapes


def hand(palm, side, scale=1.0):
    """A palm as big as the head with four long spiked fingers and a thumb. The fingers fan out and curl towards the front."""
    shapes = [ellipsoid(palm, (3.3 * scale, 3.5 * scale, 1.7 * scale))]
    for ang, lens in ((-42, (2.2, 1.9, 1.6)), (-15, (2.9, 2.3, 1.9)), (13, (3.0, 2.4, 1.9)), (40, (2.4, 2.0, 1.6))):
        a = math.radians(ang)
        base = palm + np.array([side * math.sin(a) * 3.2 * scale, -2.8 * scale, 0.2])
        d = np.array([side * math.sin(a) * 0.6, -math.cos(a), 0.2])
        shapes += finger(base, d, lens, (0.95, 0.8, 0.66, 0.1), 0.16, scale)
    tb = palm + np.array([-side * 3.0 * scale, -0.8, 0.6])
    shapes += finger(tb, np.array([-side * 0.5, -1.0, 0.5]), (1.8, 1.5, 1.3), (1.0, 0.85, 0.7, 0.1), 0.18, scale)
    return shapes


def build_parts():
    parts = []

    # --- pelvis
    parts.append(Part('waist', [rbox(Q(305, 428, -0.2), (3.7, 1.7, 2.3), 1.0)], rough_amount=0.6))

    # --- chest: a plate that narrows to the waist, with seven octagonal openings; knobs on the shoulders
    top, low = Q(305, 285, 0.0), Q(305, 365, 0.0)
    chest = [rbox(top, (5.5, 4.3, 2.8), 1.4), rbox(low, (3.9, 2.8, 2.5), 1.2),
             sphere(Q(205, 238, 0.0), 2.1), sphere(Q(405, 238, 0.0), 2.1), rbox(Q(305, 240, -0.5), (3.0, 2.0, 2.2), 1.0)]
    fz = 2.6
    LIPS_BODY = [(338, 276, 27, 32, fz), (275, 282, 14, 17, fz), (300, 345, 23, 27, fz - 0.1), (248, 250, 11, 13, fz), (370, 232, 11, 12, fz),
                 (250, 335, 10, 12, fz - 0.2), (352, 372, 12, 14, fz - 0.4)]
    holes = [hollow_q(338, 276, 27, 32, fz, 1.6), hollow_q(275, 282, 14, 17, fz, 1.4), hollow_q(300, 345, 23, 27, fz - 0.1, 1.6),
             hollow_q(248, 250, 11, 13, fz, 1.2), hollow_q(370, 232, 11, 12, fz, 1.2), hollow_q(250, 335, 10, 12, fz - 0.2, 1.0),
             hollow_q(352, 372, 12, 14, fz - 0.4, 1.2)]
    lips = [lip_q(*a) for a in LIPS_BODY]
    parts.append(Part('body', chest, k=1.3, holes=holes, rough_amount=0.45, lips=lips))

    # --- head: a wide flat dome, a hole for the eye with a hot white eye in it, and many small hollows
    head = [ellipsoid(Q(305, 95, 2.0), (6.5, 3.5, 4.9)), sphere(Q(305, 160, 0.5), 2.0)]
    hf = 2.0 + 4.9 - 0.4
    eye_hole = hollow_q(348, 72, 30, 33, hf, 1.9, rot=0)
    head_holes = [eye_hole, hollow_q(245, 62, 16, 17, hf, 1.4), hollow_q(278, 80, 14, 15, hf, 1.3), hollow_q(300, 48, 11, 12, hf - 0.2, 1.1),
                  hollow_q(262, 118, 22, 20, hf - 0.4, 1.4), hollow_q(318, 108, 14, 15, hf - 0.2, 1.2), hollow_q(215, 95, 9, 10, hf - 1.0, 1.0),
                  hollow_q(372, 112, 9, 10, hf - 0.7, 1.0), hollow_q(385, 82, 8, 9, hf - 1.0, 0.9), hollow_q(228, 70, 8, 9, hf - 1.0, 0.9)]
    eye_c = Q(348, 72, hf - 1.2)
    head_lips = [lip_q(*a) for a in ((348, 72, 30, 33, hf), (245, 62, 16, 17, hf), (278, 80, 14, 15, hf), (300, 48, 11, 12, hf - 0.2), (262, 118, 22, 20, hf - 0.4),
                                       (318, 108, 14, 15, hf - 0.2))]
    parts.append(Part('head', head, k=1.0, holes=head_holes, eyes=[sphere(eye_c, 1.0)], rough_amount=0.4, lips=head_lips))

    for side, name in ((-1, 'right'), (1, 'left')):
        sx = 305 + side * 100
        sh = Q(sx - side * 5, 250, 0.0)
        el = Q(305 + side * 160, 398, 1.4)
        wr = Q(305 + side * 128, 505, 5.8)
        # upper arm
        parts.append(Part(name + '_arm', [sphere(sh, 2.5), capsule(sh, short(sh, el, 0.9), 2.2, 1.8)], k=0.8, cores=[core(sh, el)], rough_amount=0.6))
        # forearm: the cuff at the elbow with its spike, rebar windows
        spike = capsule(el + np.array([side * 0.3, -0.2, -0.8]), el + np.array([side * 3.2, 0.9, -7.0]), 1.7, 0.1)
        w1, r1 = window(305 + side * 150, 450, 8, 22, 1.4, True)
        arm_holes = [w1]
        parts.append(Part(name + '_forearm', [sphere(el, 2.1), capsule(el, short(el, wr, 0.9), 1.8, 1.5), spike, capsule(el, el + np.array([0.0, -1.6, 0.0]), 2.4, 2.4)],
                          k=0.7, holes=arm_holes, cores=[core(el, wr), r1], rough_amount=0.6))
        palm = wr + np.array([side * 0.4, -3.0, 1.0])
        parts.append(Part(name + '_hand', hand(palm, side), k=0.12, rough_amount=0.25))
        # legs
        hp = Q(305 + side * 50, 428, 0.0)
        kn = Q(305 + side * 60, 600, 0.6)
        an = Q(305 + side * 52, 738, -0.4)
        w2, r2 = window(305 + side * 56, 520, 8, 22, 0.0, True)
        parts.append(Part(name + '_leg', [sphere(hp, 2.3), capsule(hp, short(hp, kn, 0.9), 1.9, 1.5)], k=0.7, holes=[w2], cores=[core(hp, kn), r2], rough_amount=0.6))
        w3, r3 = window(305 + side * 55, 668, 8, 22, 0.0, True)
        parts.append(Part(name + '_shin', [sphere(kn, 2.3), capsule(an - np.array([0, 1.6, 0]), an, 2.0, 2.0), capsule(kn, short(kn, an, 0.9), 1.5, 1.3)], k=0.7, holes=[w3], cores=[core(kn, an), r3], rough_amount=0.6))
        foot = [rbox(Q(305 + side * 52, 754, 1.2), (2.3, 2.2, 2.7), 0.9)]
        for dx in (-1, 0, 1):
            base = Q(305 + side * 52, 768, 3.2) + np.array([dx * 1.0, 0.0, 0.0])
            foot += finger(base, np.array([dx * 0.45, -0.15, 1.0]), (2.8, 2.6), (0.95, 0.75, 0.1), -0.06, 1.0)
        parts.append(Part(name + '_foot', foot, k=0.12, rough_amount=0.25))
    return parts


def lip_q(cx, cy, rx, ry, surface_z, rot=None):
    """The thin raised frame round an opening: an octagonal ring a little wider than the hole, standing 0.4 above the surface."""
    c = Q(cx, cy, surface_z)
    if rot is None:
        rot = (cx * 7 + cy * 3) % 45
    outer = octagon(c, rx * S2 + 0.5, ry * S2 + 0.5, surface_z - 0.7, surface_z + 0.42, rot)
    inner = octagon(c, rx * S2 - 0.05, ry * S2 - 0.05, surface_z - 1.0, surface_z + 1.0, rot)
    return lambda p: np.maximum(outer(p), -inner(p))


def hollow_q(cx, cy, rx, ry, surface_z, depth=1.0, rot=None):
    """An octagonal opening sunk into a front surface at reference-picture position (cx, cy) (the front-view picture)."""
    c = Q(cx, cy, surface_z)
    if rot is None:
        rot = (cx * 7 + cy * 3) % 45
    return octagon(c, rx * S2, ry * S2, surface_z - depth, surface_z + 4.0, rot)


SKELETON = {            # bone: (parent, pivot)
    'waist': (None, Q(305, 430, -0.2)),
    'body': ('waist', Q(305, 400, 0.0)),
    'head': ('body', Q(305, 180, 0.8)),
    'right_arm': ('body', Q(200, 250, 0.0)),
    'right_forearm': ('right_arm', Q(145, 398, 1.4)),
    'right_hand': ('right_forearm', Q(177, 505, 5.8)),
    'left_arm': ('body', Q(410, 250, 0.0)),
    'left_forearm': ('left_arm', Q(465, 398, 1.4)),
    'left_hand': ('left_forearm', Q(433, 505, 5.8)),
    'right_leg': ('waist', Q(255, 428, 0.0)),
    'right_shin': ('right_leg', Q(245, 600, 0.6)),
    'right_foot': ('right_shin', Q(253, 738, -0.4)),
    'left_leg': ('waist', Q(355, 428, 0.0)),
    'left_shin': ('left_leg', Q(365, 600, 0.6)),
    'left_foot': ('left_shin', Q(357, 738, -0.4)),
}

# ------------------------------------------------------------------------------------------------ the texture atlas (256 x 256)
ROCK_T = (0, 0, 128, 128)        # stone, tiles
CLAW_T = (128, 0, 128, 128)      # paler stone for claws and spikes
LIP_T = (128, 128, 128, 128)       # the pale frame round an opening
CORE_T = (64, 128, 32, 128)      # the glowing core seen in the gap of a joint
HOLE_T = (0, 128, 32, 128)       # a hollow, top = rim, bottom = deep
EYE_T = (32, 128, 32, 128)       # the eye
PX_PER_UNIT = 3.2                # texels of stone per model unit


def periodic_noise(size, seed, beta=2.0):
    g = np.random.RandomState(seed)
    spec = np.fft.fft2(g.randn(size, size))
    fx = np.fft.fftfreq(size)[:, None]
    fy = np.fft.fftfreq(size)[None, :]
    r = np.sqrt(fx ** 2 + fy ** 2)
    r[0, 0] = 1
    spec = spec / r ** (beta / 2)
    spec[0, 0] = 0
    n = np.real(np.fft.ifft2(spec))
    return (n - n.min()) / (n.max() - n.min())


def stone_tile(size, seed, lo, hi):
    n = periodic_noise(size, seed, 3.0)
    fine = periodic_noise(size, seed + 1, 2.2)
    ridge = np.abs(periodic_noise(size, seed + 2, 3.6) - 0.5)                 # cracks follow the noise's middle line
    crack = np.exp(-(ridge / 0.016) ** 2)
    lo, hi = np.array(lo, float), np.array(hi, float)
    col = lo + (hi - lo) * (0.7 * n + 0.3 * fine)[..., None]
    col *= (1 - 0.55 * crack[..., None])
    col += (periodic_noise(size, seed + 5, 0.4)[..., None] - 0.5) * np.array([8, 8, 10])      # grit
    return np.clip(col, 0, 255).astype(np.uint8)


def make_textures():
    base = np.zeros((256, 256, 4), np.uint8)
    base[..., 3] = 255
    base[..., :3] = (70, 62, 84)
    glow = np.zeros((256, 256, 4), np.uint8)
    base[0:128, 0:128, :3] = stone_tile(128, 3, (40, 46, 60), (104, 112, 130))
    base[0:128, 128:256, :3] = stone_tile(128, 9, (58, 63, 78), (112, 117, 134))
    base[128:256, 128:256, :3] = stone_tile(128, 13, (120, 118, 134), (196, 192, 206))
    # a hollow: dark rim -> glowing deep
    for y in range(128):
        t = y / 127.0
        col = np.array([12, 8, 9]) * (1 - t) + np.array([150, 82, 28]) * t ** 1.6
        base[128 + y, 0:32, :3] = col
        g = max(0.0, (t - 0.45) / 0.55) ** 1.4
        glow[128 + y, 0:32] = [int(255 * g), int(140 * g), int(50 * g), 255]
    # a joint's core: a steady orange-red
    base[128:256, 64:96, :3] = (146, 96, 62)          # rebar
    glow[128:256, 64:96] = (0, 0, 0, 0)
    # the eye: dim red -> white hot
    for y in range(128):
        t = y / 127.0
        base[128 + y, 32:64, :3] = np.array([80, 20, 24]) * (1 - t) + np.array([255, 235, 230]) * t
        glow[128 + y, 32:64] = [int(255 * (0.4 + 0.6 * t)), int(150 * t), int(130 * t), 255]
    return Image.fromarray(base), Image.fromarray(glow).filter(ImageFilter.GaussianBlur(0.3))


# ------------------------------------------------------------------------------------------------ building the file

def region_uv(region, u, v, margin=2.0):
    x0, y0, w, h = region
    return x0 + margin + u * (w - 2 * margin), y0 + margin + v * (h - 2 * margin)


def make_part_mesh(part, res):
    lo, hi = part.bounds()
    verts, tris = surface_nets(part.field, lo, hi, res)
    if len(tris) == 0:
        return None
    d, tag, depth = part.field(verts)
    normal = gradient(part.field, verts)
    out = []
    for t in tris:
        a, b, c = verts[t[0]], verts[t[1]], verts[t[2]]
        fn = np.cross(b - a, c - a)
        nn = (normal[t[0]] + normal[t[1]] + normal[t[2]])
        if fn @ nn < 0:
            t = (t[0], t[2], t[1])
        out.append(t)
    tris = np.array(out)
    # per-triangle kind and UVs (vertices are unwelded, three per triangle)
    records = []
    for t in tris:
        idx = [t[0], t[1], t[2]]
        tags = tag[idx]
        kind = 3 if (tags == 3).any() else (2 if (tags == 2).any() else (1 if (tags == 1).sum() >= 2 else (4 if (tags == 4).sum() >= 2 else 0)))
        claw = part.name.endswith('hand') or part.name.endswith('foot') or part.name == 'right_forearm' and verts[idx][:, 0].mean() < -5.0
        pts = verts[idx]
        nrm = normal[idx]
        centroid = pts.mean(axis=0)
        n = np.abs(nrm.mean(axis=0))
        axis = int(np.argmax(n))
        uv_axes = [(1, 2), (0, 2), (0, 1)][axis]
        uvs = []
        for k in range(3):
            if kind == 1:
                uvs.append(region_uv(HOLE_T, 0.5, float(depth[idx[k]])))
            elif kind == 3:
                uvs.append(region_uv(CORE_T, 0.5, 0.5))
            elif kind == 2:
                dist = np.clip(np.linalg.norm(pts[k] - centroid) * 0.0 + (1.0 - float(depth[idx[k]])), 0, 1)
                uvs.append(region_uv(EYE_T, 0.5, 0.5 + 0.5 * (pts[k][2] - centroid[2]) * 0.0 + 0.4))
            else:
                region = LIP_T if kind == 4 else (CLAW_T if claw else ROCK_T)
                cu, cv = centroid[uv_axes[0]] * PX_PER_UNIT, centroid[uv_axes[1]] * PX_PER_UNIT
                span = region[2] - 12
                # keep the whole triangle inside the tile: wrap its centroid, keep its own offsets
                wu, wv = (cu % span) + 6, (cv % span) + 6
                u = wu + (pts[k][uv_axes[0]] - centroid[uv_axes[0]]) * PX_PER_UNIT
                v = wv + (pts[k][uv_axes[1]] - centroid[uv_axes[1]]) * PX_PER_UNIT
                uvs.append((region[0] + u, region[1] + v))
        for k in range(3):
            records.append((pts[k], nrm[k], uvs[k]))
    return records, len(tris)


def write_bin(path, meshes):
    with open(path, 'wb') as f:
        f.write(b'SCRM')
        f.write(struct.pack('<II', 1, len(meshes)))
        for name, (records, ntris) in meshes.items():
            nb = name.encode('utf-8')
            f.write(struct.pack('<B', len(nb)))
            f.write(nb)
            f.write(struct.pack('<I', ntris))
            for pos, nrm, uv in records:
                f.write(struct.pack('<8f', pos[0], pos[1], pos[2], nrm[0], nrm[1], nrm[2], uv[0] / 256.0, uv[1] / 256.0))


def write_skeleton(path):
    data = {'units_per_block': 16, 'bones': []}
    for name, (parent, pivot) in SKELETON.items():
        data['bones'].append({'name': name, 'parent': parent, 'pivot': [round(float(v), 3) for v in pivot]})
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)


# ------------------------------------------------------------------------------------------------ preview

def euler(rx, ry, rz):
    """Rotation matrix for degrees about x, then y, then z (right-handed, y up)."""
    ax, ay, az = (math.radians(v) for v in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mz @ my @ mx


def pose_meshes(meshes, pose):
    """The parts moved by a pose {bone: (rx, ry, rz) degrees}: each bone turns about its pivot, carrying everything below it."""
    world = {}

    def matrix(name):
        if name in world:
            return world[name]
        parent, pivot = SKELETON[name]
        pivot = np.asarray(pivot, float)
        rot = euler(*pose.get(name, (0, 0, 0)))
        local = np.eye(4)
        local[:3, :3] = rot
        local[:3, 3] = pivot - rot @ pivot
        world[name] = (matrix(parent) if parent else np.eye(4)) @ local
        return world[name]

    out = {}
    for name, (records, n) in meshes.items():
        m = matrix(name)
        moved = []
        for pos, nrm, uv in records:
            moved.append((m[:3, :3] @ np.asarray(pos) + m[:3, 3], m[:3, :3] @ np.asarray(nrm), uv))
        out[name] = (moved, n)
    return out


COVER_FACE = {                     # both hands up in front of the face, fingers up and curled towards it
    'left_arm': (-92, -44, 0), 'left_forearm': (-66, -10, 0), 'left_hand': (-8, 0, 0),
    'right_arm': (-92, 44, 0), 'right_forearm': (-66, 10, 0), 'right_hand': (-8, 0, 0),
    'head': (6, 0, 0),
}


def preview(meshes, base, out_dir, in_reference_space=False, name_suffix=''):
    os.makedirs(out_dir, exist_ok=True)
    tex = np.asarray(base.convert('RGB'), dtype=float)
    W, H = 560, 660
    scale = 12.0
    ox, oy = W / 2, H - 50
    views = (('front', 0), ('three', 35), ('side', 90), ('back', 180))
    if in_reference_space:
        W, H, scale, ox, oy = 823, 743, 1.0 / S, 400.0, 730.0
        views = (('refspace', 0),)
    for name, yaw in views:
        img = np.zeros((H, W, 3))
        img[:] = (22, 12, 16)
        zbuf = np.full((H, W), 1e9)
        ca, sa = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
        for _, (records, ntris) in meshes.items():
            for ti in range(ntris):
                tri = records[ti * 3: ti * 3 + 3]
                pts, nrms = [], []
                for pos, nrm, uv in tri:
                    # the viewer looks at the character's front (+z) from the front, so its left (+x) is on the viewer's right
                    x, y, z = pos
                    xr = x * ca + z * sa
                    zr = -x * sa + z * ca                  # larger = nearer the viewer
                    pts.append((ox + xr * scale, oy - y * scale, -zr))
                    nx = nrm[0] * ca + nrm[2] * sa
                    nz = -nrm[0] * sa + nrm[2] * ca
                    nrms.append((nx, nrm[1], nz))
                (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = pts
                den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
                if abs(den) < 1e-9:
                    continue
                minx, maxx = int(max(0, math.floor(min(x0, x1, x2)))), int(min(W - 1, math.ceil(max(x0, x1, x2))))
                miny, maxy = int(max(0, math.floor(min(y0, y1, y2)))), int(min(H - 1, math.ceil(max(y0, y1, y2))))
                if maxx < minx or maxy < miny:
                    continue
                gx, gy = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
                l0 = ((y1 - y2) * (gx - x2) + (x2 - x1) * (gy - y2)) / den
                l1 = ((y2 - y0) * (gx - x2) + (x0 - x2) * (gy - y2)) / den
                l2 = 1 - l0 - l1
                inside = (l0 >= -0.001) & (l1 >= -0.001) & (l2 >= -0.001)
                if not inside.any():
                    continue
                zz = l0 * z0 + l1 * z1 + l2 * z2
                sub = zbuf[miny:maxy + 1, minx:maxx + 1]
                win = inside & (zz < sub)
                if not win.any():
                    continue
                u = (l0 * tri[0][2][0] + l1 * tri[1][2][0] + l2 * tri[2][2][0])
                v = (l0 * tri[0][2][1] + l1 * tri[1][2][1] + l2 * tri[2][2][1])
                colour = tex[np.clip(v.astype(int), 0, 255), np.clip(u.astype(int), 0, 255)]
                nx = l0 * nrms[0][0] + l1 * nrms[1][0] + l2 * nrms[2][0]
                ny = l0 * nrms[0][1] + l1 * nrms[1][1] + l2 * nrms[2][1]
                nz = l0 * nrms[0][2] + l1 * nrms[1][2] + l2 * nrms[2][2]
                ln = np.maximum(np.sqrt(nx ** 2 + ny ** 2 + nz ** 2), 1e-6)
                nx, ny, nz = nx / ln, ny / ln, nz / ln
                diffuse = np.clip(nz * 0.7 + ny * 0.4 + nx * 0.2, 0, 1)
                rim = np.clip(nx * 0.9 + 0.2, 0, 1) ** 2                     # red light from the side, as in the picture
                lit = colour * (0.40 + 0.75 * diffuse)[..., None] + rim[..., None] * np.array([70, 8, 16])
                sub[win] = zz[win]
                region = img[miny:maxy + 1, minx:maxx + 1]
                region[win] = lit[win]
        Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(os.path.join(out_dir, f'screech_{name}{name_suffix}.png'))


def build_meshes(scale):
    """All parts meshed; `scale` multiplies the grid step (1 = the fine mesh used for pictures)."""
    meshes = {}
    total = 0
    for part in build_parts():
        small = part.name.endswith('hand') or part.name.endswith('foot')
        fine = part.name in ('body', 'head')
        step = (0.55 if small else (0.27 if fine else 0.6)) * scale
        made = make_part_mesh(part, step)
        if made is None:
            print('empty part', part.name)
            continue
        meshes[part.name] = made
        total += made[1]
    return meshes, total


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    game = '--game' in sys.argv
    base, glow = make_textures()
    os.makedirs(os.path.dirname(TEX_OUT), exist_ok=True)
    base.save(TEX_OUT)
    glow.save(GLOW_OUT)
    write_skeleton(SKEL_OUT)
    if game:
        for scale, path in ((1.7, BIN_OUT), (3.0, BIN_OUT.replace('.bin', '_far.bin'))):
            meshes, total = build_meshes(scale)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            write_bin(path, meshes)
            print(f'{os.path.basename(path)}: {total} triangles, {os.path.getsize(path) / 1024:.0f} KB')
        return
    meshes, total = build_meshes(1.0)
    print(f'{total} triangles (fine mesh, for pictures only)')
    if args:
        preview(meshes, base, args[0])
        preview(meshes, base, args[0], in_reference_space=True)
        covered = pose_meshes(meshes, COVER_FACE)
        preview(covered, base, args[0], name_suffix='_cover')
        base.resize((512, 512), Image.NEAREST).save(os.path.join(args[0], 'texture_big.png'))


if __name__ == '__main__':
    main()
