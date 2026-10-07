#!/usr/bin/env node
// בונה את קובץ העורך היחיד (editor/site-editor.html) מכל החלקים.
const fs = require('fs'), path = require('path');
const src = __dirname, ed = path.join(__dirname, '..', '..', 'editor');
const rd = (d, f) => fs.readFileSync(path.join(d, f), 'utf8');
const LS = new RegExp(String.fromCharCode(0x2028), 'g'), PS = new RegExp(String.fromCharCode(0x2029), 'g');
const j = o => JSON.stringify(o).replace(/</g, '\\u003c').replace(LS, '\\u2028').replace(PS, '\\u2029');
const assets = { css: rd(src, 'style.css'), fonts: rd(src, 'fonts.css'), sprite: rd(src, 'sprite.html') };
const defaults = JSON.parse(rd(src, 'content.json'));
const gen = rd(src, 'generator.js').replace(/<\/script/gi, '<\\/script');
const js = rd(path.join(ed, 'src'), 'editor.js').replace('/*ASSETS*/null', j(assets)).replace('/*DEFAULTS*/null', j(defaults));
let html = rd(path.join(ed, 'src'), 'editor.html');
html = html.replace('/*FONTS*/', () => rd(src, 'fonts.css')).replace('/*EDITORCSS*/', () => rd(path.join(ed, 'src'), 'editor.css'))
  .replace('/*SCRIPTS*/', () => gen + '\n' + js.replace(/<\/script/gi, '<\\/script'));
fs.writeFileSync(path.join(ed, 'site-editor.html'), html, 'utf8');
console.log('built editor/site-editor.html', Math.round(Buffer.byteLength(html) / 1024) + ' KB');
