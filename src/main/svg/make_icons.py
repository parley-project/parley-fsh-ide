#!/usr/bin/env python3
"""Fsh Studio icon set, each drawn from what its button does.

Run from the parley-fsh-studio project folder:  python3 src/main/svg/make_icons.py
Writes each icon's SVG (32x32 viewBox) to src/main/svg and renders its 16 px png and
32 px @2x png into resources/img. Rendering uses cairosvg (pip install cairosvg) when
it is installed, otherwise the rsvg-convert command.

debug.svg is drawn by hand and isn't generated here; render it the same way
(16 px to resources/img/debug.png, 32 px to debug@2x.png).
"""
import os, shutil, subprocess, sys

SVG_DIR = "src/main/svg"
PNG_DIR = "resources/img"

BROWN = "#5a3d0a"
GREEN_EDGE = "#145a1e"
RED_EDGE = "#6e140e"
ICE_EDGE = "#1d4f86"
SOLID = {"url(#green)": "#3fb04d", "url(#red)": "#d9473a", "url(#ice)": "#5aa8ec", "url(#gold)": "#e0a82e"}

DEFS = """<defs>
  <linearGradient id="gold" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#f6d365"/><stop offset="0.55" stop-color="#e0a82e"/><stop offset="1" stop-color="#a8761a"/>
  </linearGradient>
  <linearGradient id="paleGold" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#fff3cf"/><stop offset="1" stop-color="#f1d58c"/>
  </linearGradient>
  <linearGradient id="green" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#5cd16a"/><stop offset="1" stop-color="#1f8a2e"/>
  </linearGradient>
  <linearGradient id="red" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#f07a68"/><stop offset="1" stop-color="#b3261e"/>
  </linearGradient>
  <linearGradient id="ice" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#9fd4ff"/><stop offset="1" stop-color="#3a8fd8"/>
  </linearGradient>
  <linearGradient id="term" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#4a3410"/><stop offset="1" stop-color="#2a1d06"/>
  </linearGradient>
</defs>"""


def svg(body):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32">{DEFS}{body}</svg>\n'


def stroke2(path, fill, edge, w=3.4, edge_w=6):
    """A line drawn with a dark edge under a colored stroke."""
    return (f'<path d="{path}" fill="none" stroke="{edge}" stroke-width="{edge_w}" stroke-linecap="round" stroke-linejoin="round"/>'
            f'<path d="{path}" fill="none" stroke="{fill}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/>')


def arrow(path, head, fill="url(#green)", edge=GREEN_EDGE):
    return stroke2(path, SOLID.get(fill, fill), edge) + \
        f'<polygon points="{head}" fill="{fill}" stroke="{edge}" stroke-width="1.6" stroke-linejoin="round"/>'


def box(x, y, w, h):
    """A function body: an open-topped gold box (the call the debugger can go into)."""
    return (f'<path d="M{x} {y} V{y+h-2} Q{x} {y+h} {x+2} {y+h} H{x+w-2} Q{x+w} {y+h} {x+w} {y+h-2} V{y}" '
            f'fill="url(#paleGold)" stroke="{BROWN}" stroke-width="2.2" stroke-linejoin="round"/>'
            f'<path d="M{x+3} {y+h-4} H{x+w-3}" stroke="#e0a82e" stroke-width="1.6" stroke-linecap="round"/>')


ICONS = {
    # Run the script in fsh: a shell prompt, green chevron and gold cursor on a terminal tile
    "run": f'<rect x="2.5" y="4.5" width="27" height="23" rx="4" fill="url(#term)" stroke="url(#gold)" stroke-width="2.4"/>'
           + stroke2("M8 11 L14 16 L8 21", "#5cd16a", GREEN_EDGE, w=3.2, edge_w=5)
           + f'<rect x="16" y="19.5" width="9" height="3.2" rx="1" fill="url(#gold)"/>',

    # Resume: run on until the next breakpoint (green arrow travelling to a gold breakpoint)
    "resume": arrow("M2.5 16 H13", "12,9.5 20,16 12,22.5")
              + f'<circle cx="25.5" cy="16" r="5" fill="url(#gold)" stroke="{BROWN}" stroke-width="1.6"/>',

    # Suspend: pause the script on its next statement (pause bars resting on a code line)
    "suspend": f'<rect x="7" y="3" width="6.5" height="17" rx="1.6" fill="url(#gold)" stroke="{BROWN}" stroke-width="1.6"/>'
               f'<rect x="18.5" y="3" width="6.5" height="17" rx="1.6" fill="url(#gold)" stroke="{BROWN}" stroke-width="1.6"/>'
               f'<rect x="3" y="24" width="26" height="5" rx="2.5" fill="url(#paleGold)" stroke="{BROWN}" stroke-width="1.6"/>',

    # Terminate: end the script's process (power off)
    "terminate": stroke2("M10.3 9.2 A10 10 0 1 0 21.7 9.2", "#d9473a", RED_EDGE, w=3.6, edge_w=6.4)
                 + stroke2("M16 4 V15", "#d9473a", RED_EDGE, w=3.6, edge_w=6.4),

    # Step into: go down into the function called on this line
    "step_into": box(8, 15, 16, 14) + arrow("M16 2.5 V13", "10,12 22,12 16,20"),

    # Step over: run the call without going into it (arrow jumps over the function)
    "step_over": box(9, 18, 14, 11) + arrow("M3 20 Q3 6 16 6 Q25 6 27 12", "21.5,11 31,11 27,18.5"),

    # Step return: run until the current function returns (arrow climbs out of the function)
    "step_return": box(4, 14, 15, 15) + arrow("M11.5 24 V12 Q11.5 6 17.5 6 H21", "20,0.5 28.5,6 20,11.5"),

    # Breakpoint: a pin stuck in the line where the script will stop
    "breakpoint": f'<path d="M17 19 L9 29" stroke="{BROWN}" stroke-width="2.6" stroke-linecap="round"/>'
                  f'<path d="M12 12 L21.5 21.5 L24 16 L16 8 Z" fill="url(#gold)" stroke="{BROWN}" stroke-width="1.8" stroke-linejoin="round"/>'
                  f'<circle cx="21" cy="9.5" r="7" fill="url(#red)" stroke="{RED_EDGE}" stroke-width="1.8"/>'
                  f'<circle cx="19" cy="7.4" r="2" fill="#ffd9d3" opacity="0.9"/>',

    # Error on this line: red stop sign with an exclamation mark
    "error": f'<polygon points="10.6,2.5 21.4,2.5 29.5,10.6 29.5,21.4 21.4,29.5 10.6,29.5 2.5,21.4 2.5,10.6" '
             f'fill="url(#red)" stroke="{RED_EDGE}" stroke-width="2" stroke-linejoin="round"/>'
             f'<rect x="14" y="7" width="4" height="12" rx="2" fill="#ffffff"/><circle cx="16" cy="23.5" r="2.3" fill="#ffffff"/>',

    # Cancel: back out without saving (undo arrow)
    "cancel": arrow("M12 10 H20 Q27 10 27 17 Q27 25 19 25 H8", "13,3 3,10 13,17", fill="url(#red)", edge=RED_EDGE),
}


def render(svg_path, png_path, size):
    try:
        import cairosvg
        cairosvg.svg2png(url=svg_path, write_to=png_path, output_width=size, output_height=size)
    except ImportError:
        if not shutil.which("rsvg-convert"):
            sys.exit("Install cairosvg (pip install cairosvg) or rsvg-convert to render the icons")
        subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size), svg_path, "-o", png_path], check=True)


if __name__ == "__main__":
    for d in (SVG_DIR, PNG_DIR):
        if not os.path.isdir(d):
            sys.exit(f"{d} not found: run this from the parley-fsh-studio project folder")
    for name, body in ICONS.items():
        p = os.path.join(SVG_DIR, name + ".svg")
        with open(p, "w") as f:
            f.write(svg(body))
        render(p, os.path.join(PNG_DIR, name + ".png"), 16)
        render(p, os.path.join(PNG_DIR, name + "@2x.png"), 32)
    print("rendered " + " ".join(ICONS))
