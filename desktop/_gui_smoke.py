"""Temporary GUI smoke test with screenshots (not shipped)."""
import sys, time
from pathlib import Path
import tkinter as tk
from PIL import ImageGrab

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
import gui  # noqa: E402


def pump(root, seconds, condition=None):
    end = time.time() + seconds
    while time.time() < end:
        root.update()
        if condition and condition():
            return True
        time.sleep(0.03)
    return condition() if condition else True


def shoot(root, name):
    root.lift()
    root.attributes("-topmost", True)
    pump(root, 0.7)
    x, y = root.winfo_rootx(), root.winfo_rooty()
    w, h = root.winfo_width(), root.winfo_height()
    image = ImageGrab.grab(bbox=(x, y, x + w, y + h))
    target = ROOT.parent / "docs-tmp" / name
    target.parent.mkdir(exist_ok=True)
    image.save(target)
    print("saved", target, image.size)


root = tk.Tk()
app = gui.CubeProbeApp(root)
pump(root, 1.0)
shoot(root, "desktop-home.png")

app.mode = "java"
app._update_mode_styles()
app.entry.delete(0, "end")
app.entry.insert(0, "127.0.0.1:25565")
app.start_query()
ok = pump(root, 20, lambda: not app.busy)
pump(root, 1.0)
print("java busy:", app.busy, "status:", bool(app.last_status), "ok:", ok)
shoot(root, "desktop-java.png")

app.mode = "bedrock"
app._update_mode_styles()
app.entry.delete(0, "end")
app.entry.insert(0, "127.0.0.1:19132")
app.start_query()
ok = pump(root, 20, lambda: not app.busy)
pump(root, 1.0)
print("bedrock busy:", app.busy, "status:", bool(app.last_status), "ok:", ok)
shoot(root, "desktop-bedrock.png")

root.destroy()
