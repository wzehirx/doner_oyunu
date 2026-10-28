#!/usr/bin/env python3
"""Doner game - fully synthesized audio generator (deterministic).

Usage:  python3 tools/gen_audio.py
Outputs mono .ogg (Vorbis) into app/src/main/res/raw/ (falls back to 22.05 kHz
wav if ffmpeg/libvorbis is missing). Needs numpy only.
"""
import os, sys, zlib, shutil, subprocess, tempfile, wave
import numpy as np

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "raw")


def rng_for(name):
    return np.random.default_rng(zlib.crc32(name.encode()))


# ---------------------------------------------------------------- primitives
def T(dur):
    return np.arange(int(dur * SR)) / SR


def fft_filter(x, lo=None, hi=None, order=2):
    """Smooth zero-phase band filter (circular). lo=highpass edge, hi=lowpass edge."""
    n = len(x)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    g = np.ones_like(f)
    if hi:
        g *= 1 / (1 + (f / hi) ** (2 * order))
    if lo:
        ff = np.maximum(f, 1e-3)
        g *= 1 / (1 + (lo / ff) ** (2 * order))
    return np.fft.irfft(X * g, n)


def noise(rng, dur):
    return rng.standard_normal(int(dur * SR))


def adsr(n, a=0.005, r=0.05):
    e = np.ones(n)
    na, nr = max(1, int(a * SR)), max(1, int(r * SR))
    na, nr = min(na, n), min(nr, n)
    e[:na] *= np.linspace(0, 1, na) ** 1.5
    e[n - nr:] *= np.linspace(1, 0, nr) ** 1.5
    return e


def expdec(n, tau):
    return np.exp(-np.arange(n) / SR / tau)


def add(buf, sig, t0, gain=1.0, circular=False):
    i = int(t0 * SR)
    if circular:
        idx = (i + np.arange(len(sig))) % len(buf)
        np.add.at(buf, idx, sig * gain)
        return
    if i >= len(buf):
        return
    j = min(len(buf), i + len(sig))
    buf[i:j] += sig[:j - i] * gain


def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12)


# ------------------------------------------------------------- instruments
def pluck(freq, dur, decay=0.996, bright=0.5, rng=None, vel=1.0):
    """Karplus-Strong string (saz / tel), pitch-corrected by resampling."""
    rng = rng or np.random.default_rng(1)
    N = max(2, int(round(SR / freq - 0.5)))
    f_eff = SR / (N + 0.5)
    ratio = freq / f_eff
    n_src = int(dur * SR * ratio) + N + 4
    out = np.zeros(n_src + 1)
    ex = rng.uniform(-1, 1, N)
    # excitation lowpass => warmer pick
    k = 0.2 + 0.7 * (1 - bright)
    for i in range(1, N):
        ex[i] = ex[i] * (1 - k) + ex[i - 1] * k
    ex -= ex.mean()
    out[1:N + 1] = ex * vel
    a = 0.5
    d = decay ** (N / 100.0 * 1.0) if False else decay
    for i in range(N + 1, n_src + 1, N):
        j = min(i + N, n_src + 1)
        m = j - i
        out[i:j] = d * (a * out[i - N:i - N + m] + (1 - a) * out[i - N - 1:i - N - 1 + m])
    src = out[1:]
    idx = np.arange(int(dur * SR)) * ratio
    idx = idx[idx < len(src) - 2]
    i0 = idx.astype(int)
    fr = idx - i0
    y = src[i0] * (1 - fr) + src[i0 + 1] * fr
    y *= adsr(len(y), 0.002, min(0.05, dur / 2))
    return y


def fm_bell(freq, dur, ratio=3.5, index=2.0, tau=0.4, a=0.002, rng=None):
    t = T(dur)
    env = np.exp(-t / tau)
    mod = np.sin(2 * np.pi * freq * ratio * t) * index * np.exp(-t / (tau * 0.6))
    y = np.sin(2 * np.pi * freq * t + mod) * env
    y *= adsr(len(y), a, min(0.03, dur / 3))
    return y


def bell_partials(freq, dur, tau=0.5, parts=((1, 1.0), (2.01, 0.45), (2.76, 0.3), (4.1, 0.15), (5.4, 0.08))):
    t = T(dur)
    y = np.zeros_like(t)
    for k, (r, a) in enumerate(parts):
        y += a * np.sin(2 * np.pi * freq * r * t) * np.exp(-t / (tau / (1 + 0.6 * k)))
    return y * adsr(len(y), 0.001, 0.03)


def epiano(freq, dur, vel=1.0):
    t = T(dur)
    env = np.exp(-t / 0.7)
    mod = np.sin(2 * np.pi * freq * t) * 1.2 * np.exp(-t / 0.25) * vel
    y = np.sin(2 * np.pi * freq * t + mod) * env
    y += 0.25 * np.sin(2 * np.pi * freq * 2 * t) * np.exp(-t / 0.15)
    return y * adsr(len(y), 0.004, min(0.12, dur / 2))


def pad(freq, dur):
    t = T(dur)
    y = np.zeros_like(t)
    for h, a in ((1, 1), (2, 0.5), (3, 0.25), (4, 0.12)):
        y += a * np.sin(2 * np.pi * freq * h * t + h)
    return y


def dum(rng, vel=1.0):
    t = T(0.35)
    f = 70 + 70 * np.exp(-t / 0.03)
    ph = 2 * np.pi * np.cumsum(f) / SR
    y = np.sin(ph) * np.exp(-t / 0.11)
    y += 0.25 * fft_filter(noise(rng, 0.35), 100, 900) * np.exp(-t / 0.01)
    return y * vel * adsr(len(y), 0.002, 0.04)


def tek(rng, vel=1.0, tone=620):
    t = T(0.14)
    n = fft_filter(noise(rng, 0.14), 1200, 4500) * np.exp(-t / 0.018)
    p = np.sin(2 * np.pi * tone * t) * np.exp(-t / 0.035) * 0.35
    return (0.55 * n + p) * vel * adsr(len(t), 0.001, 0.02)


def ka(rng, vel=1.0):
    t = T(0.1)
    n = fft_filter(noise(rng, 0.1), 700, 3000) * np.exp(-t / 0.014)
    p = np.sin(2 * np.pi * 420 * t) * np.exp(-t / 0.03) * 0.3
    return (0.4 * n + p) * vel * adsr(len(t), 0.001, 0.02)


def shaker(rng, vel=1.0, dur=0.12):
    t = T(dur)
    n = fft_filter(noise(rng, dur), 4000, 9000)
    e = np.minimum(1, t / 0.02) * np.exp(-t / 0.035)
    return n * e * vel * 0.4


# ------------------------------------------------------------------ reverb
def _comb(x, D, g):
    y = x.copy()
    for i in range(D, len(x), D):
        j = min(i + D, len(x))
        y[i:j] += g * y[i - D:i - D + (j - i)]
    return y


def _allpass(x, D, g):
    y = np.zeros_like(x)
    for i in range(0, len(x), D):
        j = min(i + D, len(x))
        m = j - i
        prev_x = x[i - D:i - D + m] if i >= D else 0
        prev_y = y[i - D:i - D + m] if i >= D else 0
        y[i:j] = -g * x[i:j] + prev_x + g * prev_y
    return y


def reverb(x, mix=0.2, size=1.0, damp=4500):
    ds = [int(SR * d * size) for d in (0.0297, 0.0371, 0.0411, 0.0437)]
    gs = [0.80, 0.78, 0.77, 0.75]
    w = sum(_comb(x, d, g) for d, g in zip(ds, gs)) / 4
    for d, g in ((int(SR * 0.005), 0.7), (int(SR * 0.0017), 0.7)):
        w = _allpass(w, d, g)
    w = fft_filter(w, 150, damp)
    return x * (1 - mix * 0.5) + w * mix


def reverb_loop(dry, mix=0.2, size=1.0, tail=3.0):
    """Reverb whose tail wraps to the start => seamless loop."""
    L = len(dry)
    padded = np.concatenate([dry, np.zeros(int(tail * SR))])
    wet = reverb(padded, mix, size)
    out = wet[:L].copy()
    rest = wet[L:]
    out[:len(rest)] += rest
    return out


# ------------------------------------------------------------------- output
def finalize_sfx(x, peak=0.66, fade_in=0.002, fade_out=0.01):
    x = x - np.mean(x)
    x = np.asarray(x, float)
    m = np.max(np.abs(x)) + 1e-9
    x = x / m * peak
    n = len(x)
    fi, fo = max(1, int(fade_in * SR)), max(1, int(fade_out * SR))
    x[:fi] *= np.linspace(0, 1, fi)
    x[n - fo:] *= np.linspace(1, 0, fo)
    return x


def finalize_music(x, rms_db=-14.0, peak=0.43):
    x = x - np.mean(x)
    r = np.sqrt(np.mean(x ** 2)) + 1e-9
    x = x * (10 ** (rms_db / 20) / r)
    # soft limiter keeps the peak under -3 dBFS without touching the body
    thr = 0.28
    ax = np.abs(x)
    over = ax > thr
    y = x.copy()
    y[over] = np.sign(x[over]) * (thr + (peak - thr) * np.tanh((ax[over] - thr) / (peak - thr)))
    return y


def to_int16(x):
    return (np.clip(x, -1, 1) * 32767).astype(np.int16)


def write_wav(path, x, sr):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(to_int16(x).tobytes())


HAVE_OGG = False


def export(name, x, music=False):
    tmp = tempfile.mkdtemp()
    wav = os.path.join(tmp, name + ".wav")
    write_wav(wav, x, SR)
    os.makedirs(OUT, exist_ok=True)
    if HAVE_OGG:
        dst = os.path.join(OUT, name + ".ogg")
        q = "2" if music else "4"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1",
                        "-c:a", "libvorbis", "-q:a", q, dst], check=True)
    else:
        sr2 = 16000 if music else 22050
        n2 = int(len(x) * sr2 / SR)
        y = np.interp(np.arange(n2) * SR / sr2, np.arange(len(x)), x)
        dst = os.path.join(OUT, name + ".wav")
        write_wav(dst, y, sr2)
    shutil.rmtree(tmp)
    return dst


# ======================================================================= SFX
def sfx_customer_arrive(r):
    L = 1.6
    b = np.zeros(int(L * SR))
    for t0, f, g in ((0.0, 1760, 1.0), (0.17, 1320, 0.85), (0.5, 1760, 0.4)):
        add(b, bell_partials(f, 1.2, tau=0.35), t0, g)
    b = fft_filter(b, 300, 6000)
    return finalize_sfx(reverb(np.concatenate([b, np.zeros(SR)]), 0.18)[:int(L * SR) + SR // 3], fade_out=0.15)


def sfx_order_new(r):
    b = np.zeros(int(1.3 * SR))
    add(b, fm_bell(659.25, 0.9, 2.0, 1.5, 0.30), 0.0, 1.0)
    add(b, fm_bell(880.0, 1.0, 2.0, 1.5, 0.35), 0.17, 0.9)
    b = fft_filter(b, 200, 5000)
    return finalize_sfx(reverb(b, 0.15), fade_out=0.08)


def sfx_slice(r):
    L = 0.7
    b = np.zeros(int(L * SR))
    for k, t0 in enumerate((0.0, 0.17, 0.34)):
        d = 0.16
        t = T(d)
        n = fft_filter(noise(r, d), 700 + 200 * k, 5500)
        # whoosh with rasp
        e = np.sin(np.pi * np.minimum(1, t / d) ** 0.7) ** 2
        n = n * e * (1 + 0.5 * np.sin(2 * np.pi * 90 * t))
        thunk = np.sin(2 * np.pi * (180 - 60 * t / d) * t) * np.exp(-t / 0.03) * 0.5
        add(b, n + thunk, t0, 0.9 - 0.1 * k)
    return finalize_sfx(b, fade_out=0.06)


def sfx_bread_warm(r):
    L = 1.3
    n = fft_filter(noise(r, L), 1800, 6500)
    n *= (0.6 + 0.4 * fft_filter(noise(r, L), None, 30)) ** 2 * 3
    t = T(L)
    for _ in range(30):
        p = r.uniform(0.05, L - 0.05)
        add(n, fft_filter(noise(r, 0.012), 2500, 7000) * np.exp(-T(0.012) / 0.003) * 3, p, r.uniform(0.3, 1))
    n *= np.sin(np.pi * t / L) ** 0.8
    n += 0.15 * fft_filter(noise(r, L), 80, 300) * np.sin(np.pi * t / L)
    return finalize_sfx(n, peak=0.6, fade_in=0.05, fade_out=0.1)


def sfx_sauce(r):
    L = 0.9
    t = T(L)
    fc = 500 + 900 * np.sin(np.pi * t / L) ** 1.5
    n = noise(r, L)
    lo = fft_filter(n, 250, 900)
    hi = fft_filter(n, 900, 2600)
    mixv = np.clip((fc - 500) / 900, 0, 1)
    y = lo * (1 - mixv) + hi * mixv
    y *= np.sin(np.pi * t / L) ** 0.7 * (0.75 + 0.25 * np.sin(2 * np.pi * 14 * t))
    for _ in range(5):
        p = r.uniform(0.3, 0.8)
        d = 0.05
        tt = T(d)
        f0 = r.uniform(300, 500)
        add(y, np.sin(2 * np.pi * (f0 + 900 * tt) * tt) * np.exp(-tt / 0.015) * 0.5, p, 1)
    y += 0.4 * np.sin(2 * np.pi * 120 * t) * np.sin(np.pi * t / L)
    return finalize_sfx(y, fade_in=0.04, fade_out=0.12)


def sfx_wrap(r):
    L = 0.9
    y = np.zeros(int(L * SR))
    env = np.sin(np.pi * np.linspace(0, 1, len(y))) ** 1.2
    for _ in range(90):
        p = r.uniform(0, L - 0.03) ** 1.0
        d = r.uniform(0.004, 0.02)
        c = fft_filter(noise(r, d), r.uniform(1500, 3500), r.uniform(5000, 9000)) * np.exp(-T(d) / (d / 3))
        add(y, c * 4, p, r.uniform(0.2, 1.0) * env[int(p * SR)])
    y += 0.25 * fft_filter(noise(r, L), 1500, 5000) * env * (0.5 + 0.5 * np.sin(2 * np.pi * 7 * T(L)))
    return finalize_sfx(y, peak=0.6, fade_in=0.03, fade_out=0.1)


def sfx_grill_sizzle(r):
    L = 1.5
    n = int(L * SR)
    base = fft_filter(noise(r, L), 2500, 8000)  # circular filter => loops
    base *= 0.5 + 0.5 * np.clip(1 + 0.3 * fft_filter(noise(r, L), None, 15), 0.2, 2)
    y = base * 0.5
    for _ in range(160):
        p = r.uniform(0, L)
        d = r.uniform(0.004, 0.015)
        c = fft_filter(noise(r, d), 3000, 9000) * np.exp(-T(d) / (d / 3))
        add(y, c * 5, p, r.uniform(0.2, 1.0), circular=True)
    y += 0.2 * fft_filter(noise(r, L), 200, 900)
    y = y - y.mean()
    # crossfade the ends so the loop point is click-free
    cf = int(0.05 * SR)
    w = np.linspace(0, 1, cf)
    y2 = y.copy()
    y2[:cf] = y[:cf] * w + y[n - cf:] * (1 - w)
    y2 = y2[:n - cf]
    y2 = y2 / np.max(np.abs(y2)) * 0.5
    return y2  # no fades: loop


def sfx_pour(r):
    L = 1.3
    t = T(L)
    n = fft_filter(noise(r, L), 300, 3500) * 0.5
    n *= 0.6 + 0.4 * fft_filter(noise(r, L), None, 25) * 4
    y = n
    rise = 350 + 500 * (t / L) ** 1.3
    ph = 2 * np.pi * np.cumsum(rise * (1 + 0.15 * fft_filter(noise(r, L), None, 20) * 6)) / SR
    y += 0.25 * np.sin(ph)
    for _ in range(40):
        p = r.uniform(0.0, L - 0.08)
        d = 0.06
        tt = T(d)
        f0 = r.uniform(400, 1100) * (0.8 + 0.6 * p / L)
        add(y, np.sin(2 * np.pi * (f0 + 1500 * tt) * tt) * np.exp(-tt / 0.012), p, r.uniform(0.1, 0.35))
    y *= np.minimum(1, t / 0.12) * np.minimum(1, (L - t) / 0.2)
    return finalize_sfx(y, peak=0.6, fade_in=0.03, fade_out=0.15)


def sfx_step_done(r):
    b = fm_bell(880.0, 1.0, 2.0, 1.2, 0.28)
    b = fft_filter(b, 200, 5000)
    return finalize_sfx(reverb(np.concatenate([b, np.zeros(SR // 3)]), 0.12), fade_out=0.08)


def coin_hit(f, dur=0.6):
    t = T(dur)
    y = (np.sin(2 * np.pi * f * t) + 0.6 * np.sin(2 * np.pi * f * 1.504 * t)
         + 0.3 * np.sin(2 * np.pi * f * 2.42 * t)) * np.exp(-t / 0.12)
    return y * adsr(len(y), 0.001, 0.05)


def sfx_deliver(r):
    b = np.zeros(int(1.6 * SR))
    add(b, fm_bell(783.99, 1.2, 2.0, 1.2, 0.35), 0.0, 0.9)
    add(b, fm_bell(1046.5, 1.3, 2.0, 1.2, 0.4), 0.14, 0.8)
    for k, t0 in enumerate((0.3, 0.42, 0.52, 0.66)):
        add(b, coin_hit(r.choice([2093, 2349, 2637, 3136]) * 0.9, 0.5), t0, 0.35)
    b = fft_filter(b, 250, 6500)
    return finalize_sfx(reverb(b, 0.14), fade_out=0.15)


def sfx_coin(r):
    b = np.zeros(int(0.8 * SR))
    add(b, coin_hit(1760, 0.6), 0.0, 0.8)
    add(b, coin_hit(2349, 0.6), 0.07, 0.9)
    b = fft_filter(b, 400, 6500)
    return finalize_sfx(b, fade_out=0.1)


def sfx_error(r):
    L = 0.42
    t = T(L)
    f = 215 - 45 * (t / L) ** 0.8
    ph = 2 * np.pi * np.cumsum(f * (1 + 0.01 * np.sin(2 * np.pi * 6 * t))) / SR
    y = np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.25 * np.sin(3 * ph)
    y = fft_filter(y, 100, 900)
    y *= np.minimum(1, t / 0.03) * np.exp(-t / 0.3)
    return finalize_sfx(y, peak=0.62, fade_out=0.08)


def sfx_warning(r):
    L = 0.9
    b = np.zeros(int(L * SR))
    for t0, f in ((0.0, 392.0), (0.34, 369.99), (0.0, 0)):
        if not f:
            continue
        t = T(0.32)
        y = (np.sin(2 * np.pi * f * t) + 0.4 * np.sin(4 * np.pi * f * t)) * (0.8 + 0.2 * np.sin(2 * np.pi * 9 * t))
        y *= adsr(len(t), 0.02, 0.12) * np.exp(-t / 0.35)
        add(b, y, t0)
    b = fft_filter(b, 150, 2500)
    return finalize_sfx(reverb(b, 0.12), peak=0.6, fade_out=0.1)


def marimba(f, dur=0.5):
    t = T(dur)
    y = np.sin(2 * np.pi * f * t) * np.exp(-t / 0.22)
    y += 0.35 * np.sin(2 * np.pi * f * 4 * t) * np.exp(-t / 0.05)
    return y * adsr(len(y), 0.001, 0.04)


def sfx_customer_happy(r):
    b = np.zeros(int(1.2 * SR))
    for k, m in enumerate((72, 76, 79, 84)):
        add(b, marimba(midi(m), 0.6), 0.09 * k, 1.0 - 0.05 * k)
    b = fft_filter(b, 200, 5500)
    return finalize_sfx(reverb(b, 0.14), fade_out=0.1)


def sfx_customer_angry(r):
    L = 0.75
    t = T(L)
    f = 330 * (0.5) ** (t / L * 1.0)
    ph = 2 * np.pi * np.cumsum(f * (1 + 0.012 * np.sin(2 * np.pi * 5.5 * t))) / SR
    y = np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.3 * np.sin(3 * ph)
    y *= 0.8 + 0.2 * np.sin(2 * np.pi * 28 * t)
    y = fft_filter(y, 90, 1000)
    y *= np.minimum(1, t / 0.04) * np.exp(-t / 0.5)
    return finalize_sfx(y, peak=0.62, fade_out=0.15)


def sfx_star_up(r):
    b = np.zeros(int(1.5 * SR))
    for k, m in enumerate((79, 84, 88)):
        add(b, fm_bell(midi(m), 1.0, 2.0, 1.0, 0.4), 0.11 * k, 0.9)
    add(b, fft_filter(noise(r, 0.6), 4000, 8000) * np.exp(-T(0.6) / 0.2) * 0.04, 0.2)
    b = fft_filter(b, 250, 7000)
    return finalize_sfx(reverb(b, 0.18), fade_out=0.2)


def sfx_star_down(r):
    b = np.zeros(int(1.2 * SR))
    for k, m in enumerate((72, 67)):
        add(b, fm_bell(midi(m), 0.9, 2.0, 0.8, 0.3), 0.2 * k, 0.9)
    b = fft_filter(b, 150, 3500)
    return finalize_sfx(reverb(b, 0.12), peak=0.6, fade_out=0.15)


D3 = 146.83


def saz_chord(b, t0, midis, spread=0.035, dur=1.2, decay=0.997, rng=None, gain=1.0):
    for k, m in enumerate(midis):
        add(b, pluck(midi(m), dur, decay, 0.55, rng), t0 + spread * k, gain)


def sfx_day_start(r):
    L = 2.4
    b = np.zeros(int(L * SR))
    add(b, dum(r), 0.0, 0.8)
    saz_chord(b, 0.05, (62, 66, 69), 0.03, 1.2, rng=r, gain=0.7)
    for k, m in enumerate((69, 70, 69, 66, 69, 74)):
        add(b, pluck(midi(m), 0.9, 0.9965, 0.6, r), 0.35 + 0.14 * k, 0.9)
    add(b, tek(r), 0.35, 0.5)
    add(b, tek(r), 0.77, 0.5)
    add(b, fm_bell(midi(86), 1.3, 2.0, 1.0, 0.5), 1.25, 0.35)
    b = fft_filter(b, 70, 6000)
    return finalize_sfx(reverb(b, 0.2), fade_out=0.25)


def sfx_day_end(r):
    L = 2.6
    b = np.zeros(int(L * SR))
    for k, m in enumerate((74, 72, 70, 69, 66, 62)):
        add(b, pluck(midi(m), 1.2, 0.9968, 0.45, r), 0.0 + 0.24 * k, 0.9)
    saz_chord(b, 1.5, (50, 57, 62, 66), 0.05, 1.1, rng=r, gain=0.6)
    b = fft_filter(b, 60, 4500)
    return finalize_sfx(reverb(b, 0.25), fade_out=0.3)


def sfx_buy(r):
    L = 1.3
    b = np.zeros(int(L * SR))
    c = fft_filter(noise(r, 0.05), 900, 5000) * np.exp(-T(0.05) / 0.01)
    add(b, c, 0.0, 0.9)
    add(b, dum(r) * 0.5, 0.0, 0.6)
    add(b, c, 0.1, 0.5)
    add(b, bell_partials(2093, 1.0, tau=0.5, parts=((1, 1), (2.76, 0.5), (5.4, 0.2))), 0.18, 0.7)
    add(b, bell_partials(2637, 1.0, tau=0.5, parts=((1, 1), (2.76, 0.5), (5.4, 0.2))), 0.22, 0.6)
    b = fft_filter(b, 100, 6500)
    return finalize_sfx(reverb(b, 0.1), fade_out=0.12)


def sfx_tap(r):
    t = T(0.09)
    f = 520 - 200 * t / 0.09
    y = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.02)
    y += 0.15 * fft_filter(noise(r, 0.09), 800, 3000) * np.exp(-t / 0.004)
    return finalize_sfx(y, peak=0.45, fade_in=0.003, fade_out=0.02)


def sfx_tick(r):
    t = T(0.06)
    y = np.sin(2 * np.pi * 1150 * t) * np.exp(-t / 0.008)
    y += 0.5 * np.sin(2 * np.pi * 1730 * t) * np.exp(-t / 0.005)
    return finalize_sfx(y, peak=0.25, fade_in=0.002, fade_out=0.015)


# ====================================================================== MUSIC
HICAZ = [0, 1, 4, 5, 7, 8, 10]  # D hicaz: D Eb F# G A Bb C
D4 = 62


def deg(i, root=D4):
    return root + HICAZ[i % 7] + 12 * (i // 7)


def melody_line(rng, bars, slots, density, center=7, span=5, cadence=(0, 4, 7)):
    """Generate per-bar list of (slot, degree, length_slots)."""
    phrase = []
    cur = center
    for b in range(bars):
        pat = []
        s = 0
        while s < slots:
            if rng.random() < density or s == 0:
                ln = int(rng.choice([1, 1, 2, 2, 3, 4]))
                ln = min(ln, slots - s)
                step = int(rng.choice([-2, -1, -1, 0, 1, 1, 2]))
                pull = np.sign(center - cur)
                if abs(cur - center) > span:
                    step = int(pull) * abs(step or 1)
                cur += step
                pat.append((s, cur, ln))
                s += ln
            else:
                s += 1
        if b == bars - 1:  # cadence
            pat[-1] = (pat[-1][0], center if rng.random() < 0.6 else center - 3, max(pat[-1][2], 2))
            cur = pat[-1][1]
        phrase.append(pat)
    return phrase


def build_music(name, bpm, bars, style, seed):
    r = np.random.default_rng(seed)
    beat = 60.0 / bpm
    bar = beat * 4
    L = bar * bars
    n = int(round(L * SR))
    buf = np.zeros(n)
    perc = np.zeros(n)

    def put(sig, t, g=1.0, target=buf):
        add(target, sig, t % L, g, circular=True)

    # chord roots per 2-bar group (hicaz harmony)
    prog = [(50, (62, 66, 69)), (55, (62, 67, 70)), (51, (63, 67, 70)), (50, (62, 66, 69))]

    # ---- pad / drone (LFO periods divide the loop => seamless)
    droneb = np.zeros(n)
    t_full = np.arange(n) / SR
    base = D3
    d = sum(a * np.sin(2 * np.pi * base * h * t_full + h) for h, a in ((1, 1), (2, 0.4), (3, 0.18)))
    # 3-4 slow swells
    sw = 0.55 + 0.45 * np.sin(2 * np.pi * t_full / (L / 4) - np.pi / 2)
    fifth = np.sin(2 * np.pi * base * 1.5 * t_full)  # tonic+5th
    drone_gain = {"menu": 0.10, "calm": 0.06, "busy": 0.05, "shop": 0.04}[style]
    droneb += (d + 0.3 * fifth * sw) * drone_gain * (0.6 + 0.4 * sw)
    # quantise drone frequencies so they loop exactly
    # (D3 is not a whole number of cycles per loop, so crossfade the seam)
    cf = int(0.25 * SR)
    w = np.linspace(0, 1, cf)
    seam = droneb[:cf] * w + 0.0
    wrap_part = np.zeros(cf)
    ext = sum(a * np.sin(2 * np.pi * base * h * (np.arange(n, n + cf) / SR) + h) for h, a in ((1, 1), (2, 0.4), (3, 0.18)))
    ext += 0.3 * np.sin(2 * np.pi * base * 1.5 * (np.arange(n, n + cf) / SR)) * 0.55
    ext *= drone_gain * 0.8
    droneb[:cf] = droneb[:cf] * w + ext * (1 - w)
    buf += droneb

    if style == "menu":
        slots = 8
        sl = beat / 2
        # arpeggio bed: soft tremolo-like pairs
        for b in range(bars):
            ch = prog[(b // 2) % 4][1]
            root = prog[(b // 2) % 4][0]
            put(pluck(midi(root - 12), bar * 0.9, 0.9985, 0.3, r), b * bar, 0.55)
            for k in (0, 3, 5):
                m = ch[(k // 2) % 3] + (12 if k > 3 else 0)
                put(pluck(midi(m), 1.6, 0.9975, 0.4, r), b * bar + k * sl, 0.28)
        mel = []
        A = melody_line(r, 4, slots, 0.45, center=7)
        A2 = melody_line(r, 4, slots, 0.5, center=7)
        B = melody_line(r, 4, slots, 0.5, center=9, span=4)
        form = [A, A, B, A2]
        total = []
        while len(total) < bars:
            for ph in form:
                total += ph
        for b in range(bars):
            for s, dg, ln in total[b]:
                dur = min(3.0, ln * sl * 1.8)
                put(pluck(midi(deg(dg)), dur, 0.9985, 0.5, r), b * bar + s * sl, 0.8)
                if ln >= 3 and r.random() < 0.7:  # saz ornament (mordent)
                    put(pluck(midi(deg(dg + 1)), 0.4, 0.99, 0.5, r), b * bar + s * sl + sl * ln * 0.6, 0.3)
        # soft frame drum
        for b in range(bars):
            for s, kind in ((0, "d"), (4, "d"), (6, "t")):
                if b % 4 == 3 and s == 6:
                    continue
                sig = dum(r, 0.55) if kind == "d" else tek(r, 0.3)
                put(sig, b * bar + s * sl, 0.5, perc)
        wetmix, rv = 0.28, 1.2

    elif style == "calm":
        sl = beat / 2
        slots = 8
        arp_shape = [0, 1, 2, 1, 0, 1, 2, 1]
        for b in range(bars):
            root, ch = prog[(b // 2) % 4]
            for s in range(8):
                if s % 8 in (7,) and r.random() < 0.4:
                    continue
                m = ch[arp_shape[s]] + (12 if s in (2, 6) else 0)
                put(pluck(midi(m), 0.7, 0.9955, 0.5, r), b * bar + s * sl, 0.38 if s % 2 == 0 else 0.26)
            put(pluck(midi(root - 12), 0.9, 0.998, 0.25, r), b * bar, 0.6)
            put(pluck(midi(root - 12), 0.7, 0.998, 0.25, r), b * bar + 4 * sl, 0.45)
        A = melody_line(r, 4, slots, 0.5, center=9)
        B = melody_line(r, 4, slots, 0.55, center=11, span=4)
        A2 = melody_line(r, 4, slots, 0.5, center=9)
        total = []
        while len(total) < bars:
            total += A + A + B + A2
        for b in range(bars):
            for s, dg, ln in total[b]:
                put(pluck(midi(deg(dg)), min(2.0, ln * sl * 1.6), 0.9975, 0.7, r), b * bar + s * sl, 0.85)
        # darbuka: 8-step 'dum . tek . . dum tek .' with ghost ka
        pat = {0: ("d", 1), 2: ("t", .7), 3: ("k", .35), 4: ("d", .55), 5: ("d", .7), 6: ("t", .6), 7: ("k", .3)}
        for b in range(bars):
            for s, (kd, v) in pat.items():
                if b % 4 == 3 and s >= 6:
                    continue
                sig = dum(r, v) if kd == "d" else tek(r, v) if kd == "t" else ka(r, v)
                put(sig, b * bar + s * sl, 0.55, perc)
        wetmix, rv = 0.2, 1.0

    elif style == "busy":
        sl = beat / 4
        for b in range(bars):
            root, ch = prog[(b // 2) % 4]
            for s in range(16):
                if s % 4 == 3 and r.random() < 0.5:
                    continue
                k = [0, 1, 2, 1][s % 4]
                m = ch[k] + (12 if (s // 4) % 2 else 0)
                put(pluck(midi(m), 0.4, 0.994, 0.6, r), b * bar + s * sl, 0.33 if s % 4 else 0.45)
            for q in (0, 6, 8, 12):
                put(pluck(midi(root - 12), 0.4, 0.997, 0.3, r), b * bar + q * sl, 0.55)
        A = melody_line(r, 4, 8, 0.7, center=9)
        B = melody_line(r, 4, 8, 0.75, center=12, span=4)
        A2 = melody_line(r, 4, 8, 0.7, center=9)
        total = []
        while len(total) < bars:
            total += A + A2 + B + A
        for b in range(bars):
            for s, dg, ln in total[b]:
                put(pluck(midi(deg(dg)), min(1.2, ln * beat / 2 * 1.4), 0.9965, 0.75, r), b * bar + s * beat / 2, 0.8)
        # def/darbuka driving 16-step
        pat = {0: ("d", 1), 2: ("k", .3), 3: ("t", .6), 4: ("t", .5), 6: ("d", .6), 7: ("k", .3), 8: ("d", .9),
               10: ("t", .55), 11: ("k", .3), 12: ("t", .6), 14: ("d", .6), 15: ("k", .35)}
        for b in range(bars):
            for s, (kd, v) in pat.items():
                if b % 4 == 3 and s >= 14:
                    continue
                sig = dum(r, v) if kd == "d" else tek(r, v) if kd == "t" else ka(r, v)
                put(sig, b * bar + s * sl, 0.55, perc)
            for s in range(0, 16, 2):
                put(shaker(r, 0.5 if s % 4 == 0 else 0.3), b * bar + s * sl, 0.25, perc)
        wetmix, rv = 0.15, 0.9

    else:  # shop: jazz-flavoured, swung
        sw = 0.62
        sl = beat / 2
        chords = [(50, (60, 64, 69, 72)), (55, (58, 62, 65, 69)), (51, (58, 62, 67, 70)), (45, (55, 60, 64, 67))]
        # (root bass midi, upper voicing) -> D7-ish, Gm7, Ebmaj7, A7-ish
        def sw_t(b, s):
            return b * bar + (s // 2) * beat + (0 if s % 2 == 0 else sw * beat)
        for b in range(bars):
            root, vo = chords[b % 4]
            # comping on 2 & 'and' of 3
            for s, g in ((2, 0.5), (5, 0.4)):
                for m in vo:
                    put(epiano(midi(m), 0.6, 0.8), sw_t(b, s) + r.uniform(0, 0.012), 0.12 * g)
            # walking bass
            walk = [root - 12, root - 12 + 7, root - 12 + 4 if b % 4 != 1 else root - 12 + 3, root - 12 + 7 + (2 if b % 2 else 0)]
            for q in range(4):
                put(pluck(midi(walk[q]), 0.5, 0.997, 0.2, r), b * bar + q * beat, 0.6)
            # brushes
            for s in range(8):
                put(shaker(r, 0.5 if s % 2 == 0 else 0.25, 0.1), sw_t(b, s), 0.16, perc)
            put(tek(r, 0.35, 480), b * bar + beat, 0.28, perc)
            put(tek(r, 0.35, 480), b * bar + 3 * beat, 0.28, perc)
            put(dum(r, 0.4), b * bar, 0.3, perc)
        A = melody_line(r, 4, 8, 0.5, center=9)
        B = melody_line(r, 4, 8, 0.55, center=10, span=4)
        total = []
        while len(total) < bars:
            total += A + A + B + A
        for b in range(bars):
            for s, dg, ln in total[b]:
                put(pluck(midi(deg(dg)), min(1.5, ln * beat / 2 * 1.5), 0.997, 0.5, r), sw_t(b, s), 0.7)
        wetmix, rv = 0.22, 1.0

    dry = fft_filter(buf, 60, 5500) + fft_filter(perc, 50, 7000)
    wet = reverb_loop(dry, wetmix, rv)
    # tiny edge fades are NOT applied: loop must be seamless (reverb tail wraps)
    return finalize_music(wet)


MUSIC = {
    "music_menu": dict(bpm=72, bars=14, style="menu", seed=11),
    "music_day_calm": dict(bpm=100, bars=20, style="calm", seed=22),
    "music_day_busy": dict(bpm=130, bars=28, style="busy", seed=33),
    "music_shop": dict(bpm=92, bars=18, style="shop", seed=44),
}

SFX = {k: v for k, v in dict(
    sfx_customer_arrive=sfx_customer_arrive, sfx_order_new=sfx_order_new, sfx_slice=sfx_slice,
    sfx_bread_warm=sfx_bread_warm, sfx_sauce=sfx_sauce, sfx_wrap=sfx_wrap, sfx_grill_sizzle=sfx_grill_sizzle,
    sfx_pour=sfx_pour, sfx_step_done=sfx_step_done, sfx_deliver=sfx_deliver, sfx_coin=sfx_coin,
    sfx_error=sfx_error, sfx_warning=sfx_warning, sfx_customer_happy=sfx_customer_happy,
    sfx_customer_angry=sfx_customer_angry, sfx_star_up=sfx_star_up, sfx_star_down=sfx_star_down,
    sfx_day_start=sfx_day_start, sfx_day_end=sfx_day_end, sfx_buy=sfx_buy, sfx_tap=sfx_tap,
    sfx_tick=sfx_tick).items()}


def have_vorbis():
    if not shutil.which("ffmpeg"):
        return False
    try:
        o = subprocess.run(["ffmpeg", "-hide_banner", "-encoders"], capture_output=True, text=True).stdout
        return "libvorbis" in o
    except Exception:
        return False


def main():
    global HAVE_OGG
    HAVE_OGG = have_vorbis()
    only = set(sys.argv[1:])
    os.makedirs(OUT, exist_ok=True)
    made = []
    for name, fn in SFX.items():
        if only and name not in only:
            continue
        x = fn(rng_for(name))
        made.append((name, export(name, x), len(x) / SR, x))
        print("ok", name, flush=True)
    for name, p in MUSIC.items():
        if only and name not in only:
            continue
        x = build_music(name, **p)
        made.append((name, export(name, x, True), len(x) / SR, x))
        print("ok", name, flush=True)

    print("\n%-22s %-5s %7s %9s %9s %9s" % ("file", "fmt", "sec", "KB", "peak dB", "rms dB"))
    tot = 0
    for name, path, dur, x in made:
        sz = os.path.getsize(path)
        tot += sz
        pk = 20 * np.log10(np.max(np.abs(x)) + 1e-12)
        rms = 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12)
        print("%-22s %-5s %7.2f %9.1f %9.1f %9.1f" % (name, os.path.splitext(path)[1][1:], dur, sz / 1024, pk, rms))
    print("total raw size: %.2f MB" % (tot / 1048576))


if __name__ == "__main__":
    main()
