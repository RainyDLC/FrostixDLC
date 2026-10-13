#!/usr/bin/env python3
"""Original RainyDLC VFX atlas, CC0. Reproducible RGBA PNG; no third-party art.
Four 256px tiles: irregular demonic stain, turbulent dust, ember, soft rim glow.
PNG is stored as base64 so the text-only GitHub MCP can commit it losslessly.
"""
from pathlib import Path
import base64, io, argparse
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
ROOT=Path(__file__).resolve().parents[1]
N=256
rng=np.random.default_rng(7732026)
y,x=np.mgrid[:N,:N];x=(x+.5-N/2)/(N/2);y=(y+.5-N/2)/(N/2)
r=np.sqrt(x*x+y*y);angle=np.arctan2(y,x)
noise=np.zeros((N,N),dtype=float)
for size,weight in [(4,.45),(8,.25),(16,.16),(32,.09),(64,.05)]:
    a=Image.fromarray((rng.random((size,size))*255).astype('uint8')).resize((N,N),Image.Resampling.BICUBIC)
    noise+=np.asarray(a)/255*weight

def rgba(red,green,blue,alpha):
    return Image.fromarray(np.stack([np.broadcast_to(red,(N,N)),np.broadcast_to(green,(N,N)),np.broadcast_to(blue,(N,N)),alpha],-1).clip(0,255).astype('uint8'))
# Asymmetric, ragged scorched stain — not a round portal, ring, or copied reference.
# Block-scale variation fits Minecraft, while fine veins suggest molten depth.
warpx=x+.10*np.sin(y*8)+.08*(noise-.5)
warpy=y+.06*np.sin(x*11)-.10*(noise-.5)
rr=np.sqrt(((warpx+.06)/.83)**2+((warpy+.025)/.76)**2)
ang=np.arctan2(warpy,warpx)
boundary=.84+.15*np.sin(ang*3+.6)+.085*np.sin(ang*7+1.9)+.07*np.sin(ang*11)+.30*(noise-.5)
mask=np.clip((boundary-rr)/.075,0,1)
# A few separated splash tongues and scorched islands, distributed unevenly.
for px,py,sx,sy in [(-.70,.38,.13,.16),(.66,-.34,.17,.10),(.39,.69,.11,.12),(-.44,-.64,.15,.11)]:
    island=np.exp(-(((x-px)/sx)**2+((y-py)/sy)**2))
    mask=np.maximum(mask,np.clip((island-.32)/.30,0,1))
veins=(1-np.abs(np.sin(x*16+y*10+noise*23)))**8
lava=np.clip((noise-.28)*1.8,0,1)
purple=np.clip((np.sin(x*6-y*9+noise*11)+.1)*.5,0,1)
red=12+lava*86+veins*121+purple*19
green=3+lava*9+veins*21+purple*3
blue=10+lava*16+veins*22+purple*57
stain=rgba(red,green,blue,mask*255)
# Preserve ragged pixel-scale edges without turning the patch into a geometrical decal.
stain=stain.resize((128,128),Image.Resampling.BOX).resize((N,N),Image.Resampling.NEAREST)
# Feathered fractal dust clouds; alpha removes rectangular billboard boundaries.
curl=np.sin(angle*4+r*18+noise*12)*.5+.5
edge=np.clip(1-r*r,0,1)**1.25
density=(.30+.70*noise)*edge*(.70+.30*curl)
dust=rgba(215+noise*40,203+noise*42,179+noise*49,density*255)
# Ember tile: hot core with a directional tail and physically smooth falloff.
core=np.exp(-((x/.09)**2+(y/.21)**2)*2)
halo=np.exp(-((x/.20)**2+(y/.43)**2)*2)
ember=rgba(255,80+core*168,18+core*196,np.maximum(core,halo*.55)*255)
# White glow carries vertex-tinted ember rings without introducing opaque edges.
glow=np.exp(-r*r*9);glow[r>.96]=0
glow=rgba(255,255,255,glow*255)
atlas=Image.new('RGBA',(512,512));atlas.paste(stain,(0,0));atlas.paste(dust,(256,0));atlas.paste(ember,(0,256));atlas.paste(glow,(256,256))
data=io.BytesIO();atlas.save(data,format='PNG',optimize=True)
path=ROOT/'RainyDLC/src/client/resources/assets/hatek_client/textures/killeffect/atlas.png.b64'
path.parent.mkdir(parents=True,exist_ok=True);path.write_text(base64.encodebytes(data.getvalue()).decode('ascii'))
# Optional local preview is not a shipped dependency.
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--preview',type=Path,help='Optional local PNG preview')
args=parser.parse_args()
if args.preview:
    args.preview.parent.mkdir(parents=True,exist_ok=True)
    atlas.save(args.preview)
print('Atlas:',atlas.size,'RGBA;',len(data.getvalue()),'PNG bytes')
