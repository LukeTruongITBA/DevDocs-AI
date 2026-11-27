// Minimal dependency-free runner for this repo's Postman collections.
// Supports collection variables, raw JSON bodies and plain-JS pm.test scripts.
// Usage: node postman/run-collection.mjs <collection.json> [baseUrl]
import { readFileSync } from 'node:fs';

const [file, baseUrlOverride] = process.argv.slice(2);
const collection = JSON.parse(readFileSync(file, 'utf8'));
const vars = Object.fromEntries((collection.variable || []).map((v) => [v.key, v.value]));
if (baseUrlOverride) vars.baseUrl = baseUrlOverride;
const sub = (s) => s.replace(/\{\{(\w+)\}\}/g, (_, k) => vars[k] ?? '');

let passed = 0;
let failed = 0;
for (const item of collection.item) {
  const req = item.request;
  const url = new URL(sub(req.url.raw.split('?')[0]));
  for (const { key, value } of req.url.query || []) url.searchParams.append(key, sub(value));
  const started = performance.now();
  const res = await fetch(url, {
    method: req.method,
    headers: Object.fromEntries(req.header.map((h) => [h.key, h.value])),
    body: req.body ? sub(req.body.raw) : undefined,
  });
  const text = await res.text();
  const ms = Math.round(performance.now() - started);
  const results = [];
  const pm = {
    response: { code: res.status, json: () => JSON.parse(text), text: () => text },
    variables: { get: (k) => vars[k] },
    test: (name, fn) => {
      try { fn(); results.push(['PASS', name]); } catch (e) { results.push(['FAIL', name + ': ' + e.message]); }
    },
  };
  const script = (item.event || []).find((e) => e.listen === 'test');
  if (script) new Function('pm', script.script.exec.join('\n'))(pm);
  console.log(`${item.name}  [HTTP ${res.status}, ${ms} ms]`);
  for (const [status, name] of results) {
    console.log(`   ${status}  ${name}`);
    status === 'PASS' ? passed++ : failed++;
  }
}
console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
