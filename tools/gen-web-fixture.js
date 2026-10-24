/* 生成「真实网页版导出」测试夹具：
 * 在 node vm 里运行真正的 embed-kb.html 脚本（预置用户定制 localStorage），
 * 调用其 exportJSON 捕获输出 → app/src/test/resources/web-export-sample.json
 * 用法：node tools/gen-web-fixture.js */
const fs = require('fs'), path = require('path'), vm = require('vm');
const ROOT = path.join(__dirname, '..');
const WEB = 'E:/AI_Coding_File/Learning_Guider/embed-kb.html';

const h = fs.readFileSync(WEB, 'utf8');
const src = h.match(/<script>([\s\S]*?)<\/script>/)[1];

/* ---- 预置用户数据（模拟真实使用痕迹：部分覆盖/自定义/墓碑/进度） ---- */
const seed = {
  'wb_embed_uskills': {
    c: { name: 'C语言(定制版)', cat: '语言基础', diff: 2, content: '按自己的节奏过了一遍', user: true },
    us1001: { name: '我的独门技能', cat: '系统', diff: 3, content: 'x', role: 'x', hard: 'x', key: 'x', res: ['r1'], pre: ['c'], user: true },
    dc: { name: '数字电路基础', cat: '硬件', diff: 3, deleted: true },
  },
  'wb_embed_upaths': {
    'path-mcu': { id: 'path-mcu', name: 'MCU软件工程师', job: 'job-mcu', period: '3-6个月', color: '#10b981', skills: ['c', 'rust', 'stm32', 'gpio', 'uart'], user: true },
    'path-fpga': { id: 'path-fpga', name: 'FPGA工程师', job: 'job-fpga', period: '6-12个月', color: '#b91c1c', skills: ['c', 'dc', 'verilog'], deleted: true },
  },
  'wb_embed_ujobs': {
    'job-x1': { id: 'job-x1', name: '自定义岗位', level: '进阶', diff: 3, demand: 3, salary: '¥20-40K', color: '#059669', tiers: [{ t: '初级', s: '¥20-25K', d: '入门' }], work: '做东西', companies: ['某公司'], review: '自定义', projects: ['项目A'], user: true },
  },
  'wb_embed_ures': [{ id: 'ur2001', cat: 'books', n: '我的私藏书', d: '自定义备注' }],
  'wb_embed_skilldone': {
    c: { st: 'done', note: '基础打完', updated: 1700000000000, startDate: '2026-01-05', goalDate: '2026-06-01' },
    rust: { st: 'learning', note: '进行中', updated: 1700000000000, startDate: '2026-02-01', goalDate: '' },
  },
  'wb_embed_jobstate': { 'job-mcu': { st: 'learning', updated: 1700000000000 } },
  'wb_embed_progress': [{ jobId: 'job-mcu', mastery: 35, done: false, note: '冲鸭' }],
  'wb_embed_meta': {},
};
const store = {};
Object.keys(seed).forEach(k => { store[k] = JSON.stringify(seed[k]); });

/* ---- 浏览器环境 mock ---- */
function makeEl() {
  const el = {
    innerHTML: '', textContent: '', value: '', style: {}, dataset: {},
    classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
    setAttribute() {}, getAttribute() { return null; }, addEventListener() {}, focus() {},
    onclick: null, onchange: null, click() {},
    getBoundingClientRect() { return { left: 0, top: 0, width: 100, height: 100 }; },
    scrollLeft: 0, scrollTop: 0, setPointerCapture() {},
  };
  el.querySelectorAll = () => []; el.querySelector = () => makeEl();
  el.href = ''; el.download = '';
  return el;
}
let capturedExport = null;
class FakeBlob { constructor(parts) { capturedExport = parts.join(''); } }
const documentMock = {
  querySelectorAll: () => [], querySelector: () => makeEl(), getElementById: () => makeEl(),
  createElement: () => makeEl(), addEventListener() {},
  documentElement: { setAttribute() {}, getAttribute() { return null; }, removeAttribute() {} },
  body: makeEl(),
};
const ctx = {
  console, document: documentMock,
  localStorage: { getItem: k => (k in store ? store[k] : null), setItem: (k, v) => { store[k] = String(v); }, removeItem: k => { delete store[k]; } },
  window: { matchMedia: () => ({ matches: false }), scrollTo() {}, addEventListener() {} },
  alert() {}, confirm() { return true; },
  Blob: FakeBlob,
  URL: { createObjectURL: () => 'blob:fake', revokeObjectURL() {} },
  setTimeout, clearTimeout, requestAnimationFrame: cb => cb(), cancelAnimationFrame() {}, performance: { now: () => Date.now() },
};
vm.createContext(ctx);
const probe = '\n;globalThis.__probe={skills:Object.keys(SKILLS).length,paths:PATHS.length,jobs:Object.keys(JOBS).length,resTotal:Object.values(RESOURCES).reduce((s,c)=>s+c.items.length,0)};';
try {
  new vm.Script(src + probe).runInContext(ctx);
  ctx.exportJSON();
} catch (e) {
  console.error('网页版仿真失败:', e.stack ? e.stack.split('\n').slice(0, 4).join('\n') : e);
  process.exit(1);
}
if (!capturedExport) { console.error('未捕获到导出内容'); process.exit(1); }

const target = path.join(ROOT, 'app', 'src', 'test', 'resources', 'web-export-sample.json');
fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, capturedExport, 'utf8');
console.log('夹具已生成 →', target);
console.log('网页版有效数据（预期口径）:', JSON.stringify(ctx.__probe));
console.log('导出 type:', JSON.parse(capturedExport).type);
