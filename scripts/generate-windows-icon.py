#!/usr/bin/env python3
"""Crée localement les icônes Windows de Kovelyx à partir de formes originales.

Aucune dépendance à une image, API ou marque Nuvio.
Dépendance de compilation : Pillow (voir le workflow Windows).
"""
from __future__ import annotations
from argparse import ArgumentParser
from pathlib import Path
from PIL import Image, ImageDraw
import math

SIZE = 1024

def gradient_masked(canvas, mask, start, end):
    grad = Image.new("RGBA", (SIZE,SIZE))
    px = grad.load()
    for y in range(SIZE):
        for x in range(SIZE):
            t = min(1.0,max(0.0,(x * .48 + y * .52) / SIZE))
            px[x,y] = tuple(int(start[i]*(1-t)+end[i]*t) for i in range(3))+(255,)
    grad.putalpha(mask)
    canvas.alpha_composite(grad)

def polygon_mask(points):
    mask = Image.new("L",(SIZE,SIZE),0)
    draw = ImageDraw.Draw(mask)
    draw.polygon([(int(x*2),int(y*2)) for x,y in points],fill=255)
    return mask

def create_icon():
    canvas = Image.new("RGBA",(SIZE,SIZE),(0,0,0,0))
    d = ImageDraw.Draw(canvas)
    d.rounded_rectangle([0,0,SIZE-1,SIZE-1],radius=232,fill=(14,12,28,255))
    d.ellipse([280,120,1000,950],fill=(35,22,66,255))
    # Icône en forme de K / lecture : formes conservées avec les proportions du SVG.
    stem=[(138,97),(118,99),(104,135),(104,371),(116,402),(139,416),(189,431),(189,126),(174,106)]
    top=[(188,232),(339,109),(364,98),(391,108),(411,125),(429,152),(410,180),(242,308),(188,283)]
    bottom=[(187,244),(411,344),(433,367),(418,397),(399,414),(369,427),(345,418),(188,334)]
    gradient_masked(canvas,polygon_mask(stem),(248,223,255),(103,67,243))
    gradient_masked(canvas,polygon_mask(top),(229,170,255),(135,70,247))
    gradient_masked(canvas,polygon_mask(bottom),(176,115,255),(78,58,206))
    d=ImageDraw.Draw(canvas)
    d.polygon([(x*2,y*2) for x,y in [(187,218),(270,264),(188,322)]],fill=(246,213,255,255))
    d.polygon([(x*2,y*2) for x,y in [(192,239),(241,266),(192,302)]],fill=(108,55,235,255))
    return canvas

def main():
    parser=ArgumentParser()
    parser.add_argument("--dest",required=True,help="Répertoire des icônes Compose Desktop")
    args=parser.parse_args()
    dest=Path(args.dest).resolve()
    dest.mkdir(parents=True,exist_ok=True)
    canvas=create_icon()
    png=dest/"kovelyx.png"
    ico=dest/"kovelyx.ico"
    canvas.resize((512,512),Image.Resampling.LANCZOS).save(png,format="PNG")
    canvas.save(ico,format="ICO",sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
    print(f"Kovelyx : {ico} ({ico.stat().st_size} octets), {png}")

if __name__=="__main__":
    main()
