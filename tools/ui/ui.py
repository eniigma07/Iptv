"""Sterowanie aplikacją na emulatorze przez adb + uiautomator (zrzuty ekranu do przeglądu)."""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

OUT = "shots"
os.makedirs(OUT, exist_ok=True)

def sh(cmd, check=False):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return r.stdout

def dump():
    for _ in range(3):
        sh("adb shell uiautomator dump /sdcard/ui.xml")
        x = sh("adb shell cat /sdcard/ui.xml")
        if x.strip().startswith("<?xml"):
            try:
                return ET.fromstring(x)
            except ET.ParseError:
                pass
        time.sleep(1)
    return None

def nodes(root):
    return root.iter("node") if root is not None else []

def center(b):
    a = list(map(int, re.findall(r"\d+", b)))
    return (a[0] + a[2]) // 2, (a[1] + a[3]) // 2

def dismiss_system(root):
    for n in nodes(root):
        t = n.get("text") or ""
        if t in ("Wait", "Czekaj", "Close app"):
            x, y = center(n.get("bounds"))
            sh(f"adb shell input tap {x} {y}")
            time.sleep(1)
            return True
    return False

def find(text, exact=False):
    root = dump()
    if dismiss_system(root):
        root = dump()
    for n in nodes(root):
        for attr in ("text", "content-desc"):
            v = n.get(attr) or ""
            if (v == text) if exact else (text in v):
                return n
    return None

def wait(text, timeout=60):
    t0 = time.time()
    while time.time() - t0 < timeout:
        n = find(text)
        if n is not None:
            return n
        time.sleep(2)
    print(f"!! nie znaleziono: {text}")
    return None

def tap(text, timeout=30, exact=False):
    t0 = time.time()
    while time.time() - t0 < timeout:
        n = find(text, exact)
        if n is not None:
            x, y = center(n.get("bounds"))
            sh(f"adb shell input tap {x} {y}")
            print(f"tap '{text}' @ {x},{y}")
            time.sleep(1.5)
            return True
        time.sleep(2)
    print(f"!! brak elementu do kliknięcia: {text}")
    return False

def tap_xy(x, y):
    sh(f"adb shell input tap {x} {y}")
    time.sleep(1.2)

def back():
    sh("adb shell input keyevent 4"); time.sleep(1.5)

def swipe_up():
    sh("adb shell input swipe 540 1800 540 700 400"); time.sleep(1.2)

def text_in(s):
    sh(f"adb shell input text '{s}'"); time.sleep(0.8)

def shot(name):
    dismiss_system(dump())
    sh(f"adb exec-out screencap -p > {OUT}/{name}.png")
    print(f"zrzut {name}")
