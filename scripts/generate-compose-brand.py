#!/usr/bin/env python3
"""Generate original Kovelyx PC branding; independent of any upstream artwork."""
from __future__ import annotations
import argparse
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

SIZE = 1024
PALETTES = {
    'original': ((178, 119, 255), (100, 61, 220)),
    'arctic_blue': ((56, 206, 251), (46, 83, 231)),
    'emerald': ((75, 235, 148), (1, 167, 119)),
    'rose_gold': ((244, 103, 216), (255, 156, 122)),
    'copper': ((231, 97, 64), (255, 184, 91)),
    'graphite': ((244, 244, 249), (119, 125, 142)),
    'gold': ((255, 218, 134), (206, 136, 70)),
}

def blend(a, b, t):
    return tuple(round(x * (1.0 - t) + y * t) for x, y in zip(a, b))

def fill_shape(canvas, points, c1, c2):
    mask = Image.new('L', (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).polygon([(x*2,y*2) for x,y in points], fill=255)
    gradient = Image.new('RGBA',(SIZE,SIZE))
    d = ImageDraw.Draw(gradient)
    for y in range(SIZE):
        d.line((0,y,SIZE,y), fill=blend(c1,c2,y/(SIZE-1))+(255,))
    gradient.putalpha(mask)
    canvas.alpha_composite(gradient)

def make_icon(palette, *, transparent=False):
    start,end=palette
    image = Image.new('RGBA', (SIZE, SIZE), (0,0,0,0))
    d=ImageDraw.Draw(image)
    if not transparent:
        d.rounded_rectangle((0,0,SIZE-1,SIZE-1),radius=235,fill=(19,17,31,255))
        d.ellipse((250,95,1010,880),fill=blend((21,20,39),end,.15)+(255,))
    stem=[(138,97),(118,99),(104,135),(104,371),(116,402),(139,416),(189,431),(189,126),(174,106)]
    top=[(188,232),(339,109),(364,98),(391,108),(411,125),(429,152),(410,180),(242,308),(188,283)]
    bottom=[(187,244),(411,344),(433,367),(418,397),(399,414),(369,427),(345,418),(188,334)]
    fill_shape(image,stem,blend(start,(255,255,255),.27),end)
    fill_shape(image,top,start,blend(end,(0,0,0),.1))
    fill_shape(image,bottom,blend(start,(255,255,255),.13),end)
    d=ImageDraw.Draw(image)
    d.polygon([(x*2,y*2) for x,y in [(187,218),(270,264),(188,322)]],fill=blend(start,(255,255,255),.75)+(255,))
    d.polygon([(x*2,y*2) for x,y in [(192,239),(241,266),(192,302)]],fill=end+(255,))
    return image.resize((512,512),Image.Resampling.LANCZOS)

def load_font(size):
    for path in ('DejaVuSans-Bold.ttf','/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf','C:/Windows/Fonts/arialbd.ttf'):
        try: return ImageFont.truetype(path,size)
        except OSError: pass
    raise RuntimeError('A TrueType font is required for Kovelyx wordmarks')

def wordmark(palette, *, gold=False):
    image=Image.new('RGBA',(1220,250),(0,0,0,0))
    icon=make_icon(palette,transparent=True).resize((224,224),Image.Resampling.LANCZOS)
    image.alpha_composite(icon,(12,13))
    draw=ImageDraw.Draw(image)
    font=load_font(132)
    ink=(244,226,179,255) if gold else (251,249,255,255)
    draw.text((247,123),'KOVELYX',font=font,fill=ink,anchor='lm',stroke_width=0)
    # Center the visible ink, not the asymmetric transparent source canvas.
    bounds = image.getchannel('A').getbbox()
    if bounds:
        ink = image.crop(bounds)
        image = Image.new('RGBA', image.size, (0, 0, 0, 0))
        image.alpha_composite(ink, ((image.width-ink.width)//2, (image.height-ink.height)//2))
    return image

def splash_icon(palette):
    """Place the visible K inside Android's centered, circular splash safe area."""
    icon = make_icon(palette, transparent=True)
    ink = icon.crop(icon.getchannel('A').getbbox())
    diameter = 512 * 2 / 3
    scale = diameter / (ink.width ** 2 + ink.height ** 2) ** .5
    ink = ink.resize((round(ink.width * scale), round(ink.height * scale)), Image.Resampling.LANCZOS)
    image = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
    image.alpha_composite(ink, ((512 - ink.width) // 2, (512 - ink.height) // 2))
    return image


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--project',default='apps/desktop')
    args=ap.parse_args()
    project=Path(args.project).resolve()
    tv_drawable = project / 'app/src/main/res/drawable'
    if tv_drawable.is_dir():
        for key in ('original', 'arctic_blue', 'emerald', 'rose_gold', 'graphite', 'gold'):
            suffix = {'original': '', 'emerald': '_jade'}.get(key, '_' + key)
            wordmark(PALETTES[key], gold=key == 'gold').save(tv_drawable / ('app_logo_wordmark' + suffix + '.png'))
        splash_icon(PALETTES['original']).save(tv_drawable / 'app_logo_mark.png')
        # Keep the repository presentation aligned with the installed TV branding.
        brand = project / 'assets/brand'
        if brand.is_dir():
            wordmark(PALETTES['original']).save(brand / 'app_logo_wordmark.png')
        return
    native_splash = project / 'composeApp/src/androidMain/res/drawable-nodpi'
    if (native_splash / 'ic_splash_logo.png').exists():
        for key in ('original', 'arctic_blue', 'emerald', 'rose_gold', 'copper', 'graphite'):
            suffix = '' if key == 'original' else '_' + key
            splash_icon(PALETTES[key]).save(native_splash / ('ic_splash_logo' + suffix + '.png'))
    common=project/'composeApp/src/commonMain/composeResources/drawable'
    desktop=project/'composeApp/src/desktopMain/resources/icons'
    common.mkdir(parents=True,exist_ok=True)
    desktop.mkdir(parents=True,exist_ok=True)
    for key,palette in PALETTES.items():
        if key == 'gold':
            wordmark(palette,gold=True).save(common/'app_logo_wordmark_gold.png')
            continue
        opaque=make_icon(palette)
        transparent=make_icon(palette,transparent=True)
        opaque.save(common/f'app_icon_{key}.png')
        transparent.save(common/f'app_icon_{key}_transparent.png')
        wordmark(palette).save(common/f'app_logo_wordmark_{key}.png')
        transparent.save(desktop/f'app-icon-{key}-transparent.png')
        transparent.save(desktop/f'app-icon-{key}-transparent.ico',format='ICO',sizes=[(16,16),(32,32),(48,48),(128,128),(256,256)])
        if key == 'original':
            wordmark(palette).save(common/'app_logo_wordmark.png')
            opaque.save(desktop/'kovelyx.png')
            opaque.save(desktop/'kovelyx.ico',format='ICO',sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
    print('Generated six authentic Kovelyx icons, transparent variants and seven wordmarks.')

if __name__ == '__main__': main()
