// Portable test: run with  node site/tests/NAME  (needs Playwright; set PLAYWRIGHT_PATH if it is installed globally)
const REPO = require('path').resolve(__dirname, '..', '..');
const FIX = process.env.FIXTURES || require('path').join(require('os').tmpdir(), 'site-qa-fixtures');
const SHOTS = require('path').join(require('os').tmpdir(), 'site-qa-shots'); require('fs').mkdirSync(SHOTS, { recursive: true });
const fs = require('fs'), path = require('path');
const Gen = require(REPO + '/site/src/generator.js');
const S = REPO + '/site/src'; const SP = FIX;
const assets = { css: fs.readFileSync(S + '/style.css', 'utf8'), fonts: '', sprite: fs.readFileSync(S + '/sprite.html', 'utf8') };
const base = JSON.parse(fs.readFileSync(S + '/content.json', 'utf8'));
const map = (o, fs_, fa) => Array.isArray(o) ? fa(o.map(x => map(x, fs_, fa))) : (o && typeof o === 'object' ? Object.fromEntries(Object.entries(o).map(([k, v]) => [k, (k === 'icon' || k === 'slug' || k === 'file' || k === 'accent' || k === 'pastel' || k === 'sample' || k === 'home') ? v : map(v, fs_, fa)])) : (typeof o === 'string' ? fs_(o) : o));
const out = {};
function run(name, proj, dir) {
  const r = Gen.generate(proj, assets, {});
  fs.rmSync(dir, { recursive: true, force: true }); fs.mkdirSync(dir, { recursive: true });
  r.files.forEach(f => { fs.mkdirSync(path.dirname(path.join(dir, f.path)), { recursive: true }); fs.writeFileSync(path.join(dir, f.path), f.text || Buffer.from(f.base64, 'base64')); });
  const bad = r.files.filter(f => f.text && /undefined|\[object|NaN|>null</.test(f.text.replace(/<style>[\s\S]*?<\/style>/g, '').replace(/<script[\s\S]*?<\/script>/g, ''))).map(f => f.path);
  console.log(name, 'files', r.files.length, bad.length ? 'FOUND undefined/null text in: ' + bad : 'no undefined/null leaks');
  return bad.length;
}
let fails = 0;
// 1. hollow: every string empty, every array empty
const hollow = map(base, () => '', () => []);
hollow.services = base.services.map(sv => { const h = map(sv, () => '', () => []); ['slug','file','accent','pastel','icon'].forEach(k => h[k] = sv[k]); h.short = sv.short; return h; });
fails += run('hollow', hollow, SP + '/stress-hollow');
// 2. hostile: every string is a nasty payload
const nasty = `<img src=x onerror=window.__pwn=1>"'&<script>window.__pwn=2</script>\${1+1}{{7*7}}{%x%}`;
const hostile = map(base, () => nasty, a => a); hostile.services.forEach((s, i) => { ['slug', 'file', 'accent', 'pastel', 'icon'].forEach(k => s[k] = base.services[i][k]); s.testimonials.forEach(t => { t.sample = false; t.home = true; }); });
hostile.biz.phone = '052-1234567'; hostile.biz.siteUrl = 'javascript:alert(1)';
fails += run('hostile', hostile, SP + '/stress-hostile');
process.exit(fails ? 1 : 0);
