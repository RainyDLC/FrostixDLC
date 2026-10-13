#!/usr/bin/env python3
"""Optional real-driver regression for the shipped black-hole GLSL.
Requires moderngl, NumPy, Pillow; EGL/Mesa on headless Linux. This is not a game launch.
"""
from pathlib import Path
import argparse,struct
import numpy as np
from PIL import Image
import moderngl
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output',type=Path,default=ROOT/'RainyDLC/build/vfx-preview/blackhole-gl.png')
args=parser.parse_args()
ctx=moderngl.create_standalone_context(backend='egl',require=330)
shaders=ROOT/'RainyDLC/src/client/resources/assets/hatek/shaders/post/customsky'
programs={name:ctx.program(vertex_shader=(shaders/'customsky.vsh').read_text(),fragment_shader=(shaders/(name+'.fsh')).read_text())
          for name in ['blackhole','blackhole_resolve','bloom_down','composite']}
W,H=512,288
bh=np.array([.34,.62,.71]);bh/=np.linalg.norm(bh)
right=np.cross(bh,[0,1,0]);right/=np.linalg.norm(right);up=np.cross(right,bh)
view=np.eye(4);view[:3,:3]=np.array([right,up,-bh])
f=1/np.tan(np.deg2rad(70)/2);near,far=1024.,.05
proj=np.zeros((4,4));proj[0,0]=f/(W/H);proj[1,1]=f;proj[2,2]=(near+far)/(near-far);proj[2,3]=2*near*far/(near-far);proj[3,2]=-1
vp=proj@view
block=bytearray(224)
block[:64]=np.linalg.inv(vp).T.astype('f4').tobytes()
for offset,values in [(64,[4,W,H,1]),(80,[.58,.22,.9,0]),(96,[.58,.22,.9,1]),(112,[0,0,0,0]),(192,[1,1,.55,.7]),(208,[1,1,1,0])]:struct.pack_into('4f',block,offset,*values)
block[128:192]=vp.T.astype('f4').tobytes()
sky_uniform=ctx.buffer(block);bloom_uniform=ctx.buffer(reserve=16)
for name,p in programs.items():p['BloomParams' if name=='bloom_down' else 'SkyParams'].binding=0
sky_uniform.bind_to_uniform_block(0)
rng=np.random.default_rng(1592639215)
red=rng.integers(0,256,(256,256),dtype='uint8');green=np.roll(red,(17,37),(0,1));blue=rng.integers(0,256,(256,256),dtype='uint8')
noise=np.stack([red,green,blue,np.full_like(red,255)],-1)
noise_tex=ctx.texture((256,256),4,noise.tobytes());noise_tex.filter=(moderngl.LINEAR,moderngl.LINEAR)
noise_tex.repeat_x=noise_tex.repeat_y=True
resources=[noise_tex]
def target(width,height):
 tex=ctx.texture((width,height),4,dtype='f1');tex.filter=(moderngl.LINEAR,moderngl.LINEAR);tex.repeat_x=tex.repeat_y=False
 fbo=ctx.framebuffer(color_attachments=[tex]);resources.extend([tex,fbo]);return tex,fbo

def draw(name,fbo,bindings):
 fbo.use();ctx.viewport=(0,0,*fbo.size);p=programs[name]
 for unit,(sampler,tex) in enumerate(bindings.items()):p[sampler].value=unit;tex.use(unit)
 vao=ctx.vertex_array(p,[]);vao.render(mode=moderngl.TRIANGLES,vertices=6);vao.release()

half,hf=target(W//2,H//2);sky,sf=target(W,H);history,histf=target(W,H);histf.clear()
draw('blackhole',hf,{'NoiseTex':noise_tex})
draw('blackhole_resolve',sf,{'Current':half,'History':history})
bloom=[];source=sky;sw,sh=W//2,H//2
for level in range(6):
 tex,fbo=target(max(1,sw//2),max(1,sh//2))
 bloom_uniform.write(struct.pack('4f',1/sw,1/sh,0,1));bloom_uniform.bind_to_uniform_block(0)
 draw('bloom_down',fbo,{'Source':source});bloom.append(tex);source=tex;sw,sh=tex.size
sky_uniform.bind_to_uniform_block(0)
result,rf=target(W,H)
draw('composite',rf,{'Sky':sky,**{f'Bloom{i}':tex for i,tex in enumerate(bloom)},'NoiseTex':noise_tex})
rgba=np.frombuffer(rf.read(components=4,alignment=1),dtype='uint8').reshape(H,W,4)[::-1]
assert np.isfinite(rgba).all() and rgba[:,:,:3].max()>180,'No bright accretion light in actual GPU output'
assert (rgba[:,:,:3].mean(2)>25).sum()>1000,'Sky unexpectedly blank'
args.output.parent.mkdir(parents=True,exist_ok=True);Image.fromarray(rgba).save(args.output)
print('Black hole OpenGL:',ctx.info['GL_RENDERER'],'— four stages compiled, nonblank accretion image rendered')
for resource in reversed(resources):resource.release()
for p in programs.values():p.release()
sky_uniform.release();bloom_uniform.release();ctx.release()
