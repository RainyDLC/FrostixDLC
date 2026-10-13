#!/usr/bin/env python3
"""Original RainyDLC VFX atlas, CC0. Reproducible RGBA PNG; no third-party art.
Four 256px tiles: aperture/sigil, turbulent dust, ember, soft rim glow.
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
# An opaque void with a molten, fissured stone rim. The surrounding runes are geometrical,
# not generated pseudo-text. Transparent outside the halo.
inner=1/(1+np.exp((r-.665)*110));rim=np.exp(-((r-.72)/.045)**2)
cracks=(np.sin(angle*17+r*39+noise*12)*.5+.5)**9
heat=rim*(.4+.6*cracks)
sigil=rgba(6+heat*246,2+heat*42,5+heat*13,np.maximum(inner*.99,rim*.94)*255)
d=ImageDraw.Draw(sigil)
# Double engraved rings and evenly spaced spear sigils.
for radius,width,color in [(104,1,(191,36,22,175)),(111,1,(126,20,21,140))]:
    d.ellipse((128-radius,128-radius,128+radius,128+radius),outline=color,width=width)
for i in range(12):
    a=i*np.pi/6
    def point(rad,offset=0):return (128+rad*np.cos(a+offset),128+rad*np.sin(a+offset))
    d.line([point(95),point(108),point(100,.045),point(108),point(100,-.045)],fill=(247,72,32,220),width=2)
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
atlas=Image.new('RGBA',(512,512));atlas.paste(sigil,(0,0));atlas.paste(dust,(256,0));atlas.paste(ember,(0,256));atlas.paste(glow,(256,256))
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
