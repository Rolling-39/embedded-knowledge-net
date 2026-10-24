/* 验证「App 导出的备份 能被网页版导入」：
 * 把 app/build/test-exports/app-export.json 通过网页版真实 importJSON() 导入（mock FileReader），
 * 再用导入后的 localStorage 重新跑一遍网页版脚本，断言无异常且有效数据符合预期。
 * 用法：node tools/check-web-import.js [app导出文件路径] */
const fs = require('fs'), path = require('path'), vm = require('vm');
const ROOT = path.join(__dirname, '..');
const WEB = 'E:/AI_Coding_File/Learning_Guider/embed-kb.html';
const EXPORT = process.argv[2] || path.join(ROOT, 'app', 'build', 'test-exports', 'app-export.json');
const exportText = fs.readFileSync(EXPORT, 'utf8');

const src = fs.readFileSync(WEB, 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
function makeEl() {
  const el = {
    innerHTML: '', textContent: '', value: '', style: {}, dataset: {},
    classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
    setAttribute() {}, getAttribute() { return null; }, addEventListener() {}, focus() {},
    onclick: null, onchange: null, click() {},
    getBoundingClientRect() { return { left: 0, top: 0, width: 100, height: 100 }; },
    scrollLeft: 0, scrollTop: 0, setPointerCapture() {},
  };
  el.querySelectorAll = () => []; el.querySelector = () => makeEl(); return el;
}
const store = {};
const alerts = [];
const documentMock = {
  querySelectorAll: () => [], querySelector: () => makeEl(), getElementById: () => makeEl(),
  createElement: () => makeEl(), addEventListener() {},
  documentElement: { setAttribute() {}, getAttribute() { return null; }, removeAttribute() {} }, body: makeEl(),
};
class FakeFileReader { readAsText(f) { this.result = f.text; this.onload({ target: { result: f.text } }); } }
const ctx = {
  console, document: documentMock,
  localStorage: { getItem: k => (k in store ? store[k] : null), setItem: (k, v) => { store[k] = String(v); }, removeItem: k => { delete store[k]; } },
  window: { matchMedia: () => ({ matches: false }), scrollTo() {}, addEventListener() {} },
  alert: m => alerts.push(String(m)), confirm: () => true,
  Blob: class {}, URL: { createObjectURL: () => '', revokeObjectURL() {} },
  FileReader: FakeFileReader,
  setTimeout, clearTimeout, requestAnimationFrame: cb => cb(), cancelAnimationFrame() {}, performance: { now: () => Date.now() },
};
vm.createContext(ctx);
const probe = '\n;globalThis.__probe={skills:Object.keys(SKILLS).length,paths:PATHS.length,jobs:Object.keys(JOBS).length,resTotal:Object.values(RESOURCES).reduce((s,c)=>s+c.items.length,0)};';

// 第一遍：跑脚本（空数据）→ 调 importJSON 导入 App 导出
new vm.Script(src).runInContext(ctx);
ctx.importJSON({ text: exportText }, false);
const failMsgs = alerts.filter(a => a.includes('导入失败'));
if (failMsgs.length) { console.error('网页版导入失败:', failMsgs); process.exit(1); }

// 第二遍：用导入后的 localStorage 重新初始化（真实还原用户打开页面的路径）
const ctx2 = { ...ctx, document: documentMock, localStorage: ctx.localStorage, alert: m => alerts.push(String(m)), confirm: () => true };
vm.createContext(ctx2);
new vm.Script(src + probe).runInContext(ctx2);
const p = ctx2.__probe;
console.log('导入后重新加载，网页版有效数据:', JSON.stringify(p));
const expect = { skills: 81, paths: 15, jobs: 17 };
const ok = p.skills === expect.skills && p.paths === expect.paths && p.jobs === expect.jobs;
console.log(ok ? '✓ 网页版成功吃回 App 导出（技能/路径/岗位数量符合预期）' : `✗ 数量不符：期望 ${JSON.stringify(expect)}`);
const up = JSON.parse(store['wb_embed_upaths']);
console.log('覆盖记录穿透检查: path-mcu.skills =', JSON.stringify(up['path-mcu'] && up['path-mcu'].skills), '| path-fpga 墓碑 =', !!(up['path-fpga'] && up['path-fpga'].deleted));
const us = JSON.parse(store['wb_embed_uskills']);
console.log('技能覆盖穿透: c.name =', us.c && us.c.name, '| us1001 =', !!(us.us1001 && us.us1001.user), '| dc 墓碑 =', !!(us.dc && us.dc.deleted));
const sd = JSON.parse(store['wb_embed_skilldone']);
console.log('技能状态穿透检查: c =', JSON.stringify(sd.c));
if (!ok) process.exit(1);
