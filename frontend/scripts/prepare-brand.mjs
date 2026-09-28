import fs from 'node:fs';
import UPNG from 'upng-js';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here=dirname(fileURLToPath(import.meta.url));
const sourcePath=resolve(here,'../public/nexo-logo.png');
const outputPath=resolve(here,'../public/nexo-logo-hd.png');

const file=fs.readFileSync(sourcePath);
const inputBuffer=file.buffer.slice(file.byteOffset,file.byteOffset+file.byteLength);
const decoded=UPNG.decode(inputBuffer);
const sourceData=new Uint8Array(UPNG.toRGBA8(decoded)[0]);

const source={width:decoded.width,height:decoded.height,data:sourceData};
const targetWidth=2048;
const targetHeight=682;
const innerWidth=2046;
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
  const result=new Array(outputSize);
  for(let o=0;o<outputSize;o++){
    const center=(o+0.5)/scale-0.5;
    const first=Math.floor(center)-2;
    const items=[];
    let total=0;
    for(let i=first;i<=first+5;i++){
      const clamped=Math.max(0,Math.min(inputSize-1,i));
      const weight=lanczos(center-i,3);
      if(weight!==0){
        items.push([clamped,weight]);
        total+=weight;
      }
    }
    result[o]=items.map(([index,weight])=>[index,weight/total]);
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

const output=new Uint8Array(targetWidth*targetHeight*4);
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
    const alpha=Math.max(0,Math.min(1,a));
    const di=(y*targetWidth+x+1)*4;
    output[di+3]=Math.round(alpha*255);
    if(alpha>1e-6){
      output[di]=Math.max(0,Math.min(255,Math.round(r/alpha)));
      output[di+1]=Math.max(0,Math.min(255,Math.round(g/alpha)));
      output[di+2]=Math.max(0,Math.min(255,Math.round(b/alpha)));
    }
  }
}

const encoded=UPNG.encode([output.buffer],targetWidth,targetHeight,0);
fs.writeFileSync(outputPath,Buffer.from(encoded));
console.log(`Nexo brand asset prepared: ${targetWidth}x${targetHeight} from ${source.width}x${source.height}`);
