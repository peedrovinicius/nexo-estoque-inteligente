import fs from 'node:fs';
import pngjs from 'pngjs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const { PNG }=pngjs;
const here=dirname(fileURLToPath(import.meta.url));
const sourcePath=resolve(here,'../public/nexo-logo.png');
const outputPath=resolve(here,'../public/nexo-logo-hd.png');

const source=PNG.sync.read(fs.readFileSync(sourcePath));
const innerWidth=2046;
const targetWidth=2048;
const targetHeight=682;
const horizontal=new Float32Array(innerWidth*source.height*4);

function sinc(x){
  if(Math.abs(x)<1e-8) return 1;
  const p=Math.PI*x;
  return Math.sin(p)/p;
}
function lanczos(x,a=3){
  const ax=Math.abs(x);
  return ax<a?sinc(x)*sinc(x/a):0;
}
function contributors(inputSize,outputSize){
  const scale=outputSize/inputSize;
  const result=[];
  for(let o=0;o<outputSize;o++){
    const center=(o+0.5)/scale-0.5;
    const left=Math.floor(center)-2;
    const items=[];
    let total=0;
    for(let i=left;i<=left+5;i++){
      const clamped=Math.max(0,Math.min(inputSize-1,i));
      const w=lanczos(center-i,3);
      if(w!==0){items.push([clamped,w]);total+=w;}
    }
    result.push(items.map(([i,w])=>[i,w/total]));
  }
  return result;
}

const xContrib=contributors(source.width,innerWidth);
for(let y=0;y<source.height;y++){
  for(let x=0;x<innerWidth;x++){
    let a=0,r=0,g=0,b=0;
    for(const [sx,w] of xContrib[x]){
      const si=(y*source.width+sx)*4;
      const alpha=source.data[si+3]/255;
      a+=alpha*w;
      r+=source.data[si]*alpha*w;
      g+=source.data[si+1]*alpha*w;
      b+=source.data[si+2]*alpha*w;
    }
    const di=(y*innerWidth+x)*4;
    horizontal[di]=r;
    horizontal[di+1]=g;
    horizontal[di+2]=b;
    horizontal[di+3]=a;
  }
}

const output=new PNG({width:targetWidth,height:targetHeight,colorType:6});
output.data.fill(0);
const yContrib=contributors(source.height,targetHeight);

for(let y=0;y<targetHeight;y++){
  for(let x=0;x<innerWidth;x++){
    let a=0,r=0,g=0,b=0;
    for(const [sy,w] of yContrib[y]){
      const si=(sy*innerWidth+x)*4;
      r+=horizontal[si]*w;
      g+=horizontal[si+1]*w;
      b+=horizontal[si+2]*w;
      a+=horizontal[si+3]*w;
    }
    const dx=x+1;
    const di=(y*targetWidth+dx)*4;
    const alpha=Math.max(0,Math.min(1,a));
    output.data[di+3]=Math.round(alpha*255);
    if(alpha>1e-6){
      output.data[di]=Math.max(0,Math.min(255,Math.round(r/alpha)));
      output.data[di+1]=Math.max(0,Math.min(255,Math.round(g/alpha)));
      output.data[di+2]=Math.max(0,Math.min(255,Math.round(b/alpha)));
    }
  }
}

fs.writeFileSync(outputPath,PNG.sync.write(output,{colorType:6,inputColorType:6}));
console.log(`Nexo brand asset prepared: ${targetWidth}x${targetHeight}`);
