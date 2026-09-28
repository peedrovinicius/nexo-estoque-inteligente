import sharp from 'sharp';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here=dirname(fileURLToPath(import.meta.url));
const source=resolve(here,'../public/nexo-logo.png');
const output=resolve(here,'../public/nexo-logo-hd.png');

await sharp(source)
  .resize({height:682,withoutEnlargement:false,kernel:sharp.kernel.lanczos3})
  .png({compressionLevel:9,palette:false})
  .toFile(output);

console.log('Nexo brand asset prepared:', output);
