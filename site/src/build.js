#!/usr/bin/env node
// בונה את האתר מ-content.json (או מקובץ גיבוי של העורך) ומדפיס אותו לתיקייה.
// שימוש: node site/src/build.js [--project קובץ.json] [--out תיקייה]
const fs = require('fs'), path = require('path');
const here = __dirname;
const SiteGen = require('./generator.js');
const args = process.argv.slice(2);
const arg = n => { const i = args.indexOf(n); return i >= 0 ? args[i + 1] : null; };
const projectFile = arg('--project') || path.join(here, 'content.json');
const outDir = arg('--out') || path.join(here, '..');
const project = JSON.parse(fs.readFileSync(projectFile, 'utf8'));
const assets = ['style.css', 'fonts.css', 'sprite.html'].reduce((a, f) => { a[f.split('.')[0] === 'style' ? 'css' : f.split('.')[0]] = fs.readFileSync(path.join(here, f), 'utf8'); return a; }, {});
const res = SiteGen.generate(project, assets, { preview: args.includes('--preview') });
fs.mkdirSync(outDir, { recursive: true });
for (const f of res.files) {
  const p = path.join(outDir, f.path);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  if (f.base64) fs.writeFileSync(p, Buffer.from(f.base64, 'base64')); else fs.writeFileSync(p, f.text, 'utf8');
  console.log('built', f.path, Math.round((f.text ? Buffer.byteLength(f.text) : f.base64.length * 0.75) / 1024) + ' KB');
}
if (args.includes('--issues')) res.issues.forEach(i => console.log(`[${i.level}] ${i.text}`));
