// Generates postman/DevDocs-Story3-Metadata-Filters.postman_collection.json
// (Postman v2.1). Regenerate with: node postman/build-story3-collection.mjs
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const body = (query, extra = {}) => JSON.stringify({ query, topK: 5, ...extra }, null, 2);

// Test helpers are plain JS so the collection runs in Postman/Newman and in
// postman/run-collection.mjs without chai.
const helpers = `
const fail = (m) => { throw new Error(m); };
const json = () => pm.response.json();
const status = (code) => pm.test('HTTP ' + code, () => { if (pm.response.code !== code) fail('got ' + pm.response.code); });
const all = (label, pred) => pm.test(label, () => {
  const r = json().results; if (!Array.isArray(r) || r.length === 0) fail('expected at least one result');
  r.forEach((x, i) => { if (!pred(x)) fail('result #' + i + ' (chunk ' + x.id + ') violates filter'); });
});
const pageOf = (x) => x.pageNumber ?? (x.metadata && x.metadata.page && x.metadata.page.pdf_page_number);
`;

const item = (name, description, rawQuery, query, extraBody, tests) => {
  const queryParams = rawQuery.map(([key, value]) => ({ key, value }));
  const qs = rawQuery.map(([k, v]) => `${k}=${v}`).join('&');
  return {
    name,
    request: {
      description,
      method: 'POST',
      header: [{ key: 'Content-Type', value: 'application/json' }],
      body: { mode: 'raw', raw: body(query, extraBody), options: { raw: { language: 'json' } } },
      url: {
        raw: `{{baseUrl}}/api/query/retrieve${qs ? '?' + qs : ''}`,
        host: ['{{baseUrl}}'],
        path: ['api', 'query', 'retrieve'],
        query: queryParams,
      },
    },
    event: [{ listen: 'test', script: { type: 'text/javascript', exec: (helpers + tests).split('\n') } }],
  };
};

const items = [
  item('01 Null filter → standard version-scoped flow',
    'No filter parameters: unchanged behaviour, versionId required, appliedFilter is null.',
    [], 'How do I configure the theme?', { versionId: '{{mkdocsVersionId}}' }, `
status(200);
pm.test('appliedFilter is null', () => { if (json().appliedFilter !== null) fail(JSON.stringify(json().appliedFilter)); });
all('all results in versionId', (x) => String(x.versionId) === pm.variables.get('mkdocsVersionId'));`),

  item('02 AND: source_type=WEB AND document_id in (Oracle PDF, MkDocs)',
    'Two conditions joined by AND (default filterMode).',
    [['filter', 'source_type:eq:WEB'], ['filter', 'document_id:in:{{oracleDocId}},{{mkdocsDocId}}']],
    'How do I configure the theme?', {}, `
status(200);
pm.test('mode AND with 2 conditions', () => { const f = json().appliedFilter; if (f.mode !== 'AND' || f.conditions.length !== 2) fail(JSON.stringify(f)); });
const ids = [pm.variables.get('oracleDocId'), pm.variables.get('mkdocsDocId')];
all('every result is WEB and in the document list', (x) => x.metadata.source_type === 'WEB' && ids.includes(String(x.documentId)));`),

  item('03 OR: document_id=ACL paper OR document_id=MkDocs',
    'filterMode=OR: a result may match either condition.',
    [['filter', 'document_id:eq:{{aclDocId}}'], ['filter', 'document_id:eq:{{mkdocsDocId}}'], ['filterMode', 'OR']],
    'How is the document text segmented?', {}, `
status(200);
pm.test('mode OR', () => { if (json().appliedFilter.mode !== 'OR') fail(); });
const ids = [pm.variables.get('aclDocId'), pm.variables.get('mkdocsDocId')];
all('every result matches one OR branch', (x) => ids.includes(String(x.documentId)));`),

  item('04 AND: STATIC PDFs from page 6 onward',
    'Numeric operator gte on the page field.',
    [['filter', 'source_type:eq:STATIC'], ['filter', 'page:gte:6']],
    'evaluation results', {}, `
status(200);
all('STATIC and page >= 6', (x) => x.metadata.source_type === 'STATIC' && pageOf(x) >= 6);`),

  item('05 page filter on a remote PDF (pdf_page_number) AND version',
    'page also matches remote PDF chunks (metadata.page.pdf_page_number); combined with versionId.',
    [['filter', 'page:lte:5']],
    'What will I build in this tutorial?', { versionId: '{{oracleVersionId}}' }, `
status(200);
all('Oracle version and page <= 5', (x) => String(x.versionId) === pm.variables.get('oracleVersionId') && pageOf(x) <= 5);`),

  item('06 fetch_date range AND source prefix',
    'Timestamp operator on metadata fetch_date plus a LIKE-escaped prefix on source.',
    [['filter', 'fetch_date:gte:2026-11-27T00:00:00Z'], ['filter', 'source:prefix:https://raw.githubusercontent.com/mkdocs/']],
    'deploy to GitHub Pages', {}, `
status(200);
all('fetched on/after 2026-11-27 and from mkdocs', (x) => x.metadata.fetch_date >= '2026-11-27T00:00:00Z' && x.sourceUri.startsWith('https://raw.githubusercontent.com/mkdocs/'));`),

  item('07 Invalid field → 400 listing valid fields',
    'Fields outside the whitelist are rejected before any database access.',
    [['filter', 'content:eq:password']],
    'anything', {}, `
status(400);
pm.test('lists valid fields', () => { const b = json(); if (!b.validFields || !b.validFields.source_type || !b.validFields.page) fail(JSON.stringify(b)); });
pm.test('names the bad field', () => { if (!json().details[0].includes("unknown field 'content'")) fail(json().details[0]); });`),

  item('08 Operator not allowed for field → 400',
    'gt is not whitelisted for source_type.',
    [['filter', 'source_type:gt:WEB'], ['filter', 'page:eq:zero']],
    'anything', {}, `
status(400);
pm.test('reports both problems', () => { if (json().details.length !== 2) fail(JSON.stringify(json().details)); });`),

  item('09 Bad filterMode → 400',
    'Only AND and OR are accepted.',
    [['filter', 'page:eq:1'], ['filterMode', 'XOR']],
    'anything', {}, `
status(400);
pm.test('mentions filterMode', () => { if (!json().details.some((d) => d.includes('filterMode'))) fail(); });`),

  item('10 SQL injection text stays a bound value',
    "The value is bound as a literal; it matches no version label and returns no rows.",
    [['filter', "version:eq:1' OR '1'='1"]],
    'anything', {}, `
status(200);
pm.test('no rows match the literal', () => { if (json().results.length !== 0) fail(json().results.length + ' rows'); });`),
];

const collection = {
  info: {
    name: 'DevDocs AI - Story 3 Task 1 - Safe metadata filtering',
    description: 'URL metadata filters on POST /api/query/retrieve (pre-retrieval-service).\n\n'
      + 'Syntax: repeated `filter=<field>:<operator>:<value>`, `filterMode=AND|OR` (default AND).\n'
      + 'Fields: source_type, document_id, version_id, version, is_current, source, page, fetch_date.\n'
      + 'Default IDs match the 2026-11-27 database (Story 1 and Story 2 ingestion); change the collection variables for another database.',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
  },
  variable: [
    { key: 'baseUrl', value: 'http://localhost:8084' },
    { key: 'aclDocId', value: '10' },
    { key: 'oracleDocId', value: '12' },
    { key: 'oracleVersionId', value: '11' },
    { key: 'mkdocsDocId', value: '13' },
    { key: 'mkdocsVersionId', value: '12' },
  ],
  item: items,
};

const out = fileURLToPath(new URL('./DevDocs-Story3-Metadata-Filters.postman_collection.json', import.meta.url));
writeFileSync(out, JSON.stringify(collection, null, 2) + '\n');
console.log('wrote', out);
