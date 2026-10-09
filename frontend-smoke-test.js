/**
 * 前端冒烟测试（无需浏览器）
 *
 * 能验证的：状态徽章映射、页面脚本里"按钮出现条件 / 提交的状态值 / 确认文案分支"。
 * 不能验证的：真实 DOM 点击、真实浏览器里的接口调用（本机无浏览器环境，见提交说明的已知限制）。
 *
 * 运行：node frontend-smoke-test.js   （工作目录 = 项目根目录）
 */
'use strict';

const fs = require('fs');
const path = require('path');

const STATIC = path.join(__dirname, 'src', 'main', 'resources', 'static');
const html = (name) => fs.readFileSync(path.join(STATIC, 'html', name), 'utf8');

let pass = 0;
let fail = 0;
const results = [];

function check(name, actual, expected) {
    const ok = String(actual) === String(expected);
    if (ok) {
        pass++;
    } else {
        fail++;
    }
    results.push(`  ${ok ? 'PASS' : 'FAIL'}  ${name}  -> ${actual}${ok ? '' : ` (expect ${expected})`}`);
}

// ---------- 1. 真实加载 common.js，验证状态徽章映射 ----------
global.document = {
    body: { dataset: {} },
    title: 'test',
    addEventListener() {},
    getElementById: () => null,
    querySelectorAll: () => []
};
global.localStorage = { getItem: () => null, setItem() {}, removeItem() {} };
global.window = global;
require(path.join(STATIC, 'js', 'common.js'));

const App = global.App;
check('common.js 暴露 App', typeof App, 'object');
check('App.UI 存在', typeof App.UI, 'object');
check('App.UI.stamp 存在', typeof App.UI.stamp, 'function');

const stampOf = (status) => App.UI.stamp(status, true);
const classOf = (status) => {
    const m = /bp-stamp--([a-z]+)/.exec(stampOf(status));
    return m ? m[1] : '(none)';
};
check('待处理 -> pending', classOf('待处理'), 'pending');
check('维修中 -> doing', classOf('维修中'), 'doing');
check('待确认 -> wait（新增状态必须有自己的徽章）', classOf('待确认'), 'wait');
check('已完成 -> done', classOf('已完成'), 'done');
check('已取消 -> cancel', classOf('已取消'), 'cancel');

// ---------- 2. 维修端页面 ----------
const repair = html('repair-orders.html');
check('维修端提交的状态是 待确认', /status:\s*'待确认'/.test(repair), 'true');
check('维修端不再提交 已完成', /status:\s*'已完成'/.test(repair), 'false');
check('维修端只在维修中给出提交按钮', /canSubmitFinish\s*=\s*order\.orderStatus === '维修中'/.test(repair), 'true');
check('维修端按钮文案为 提交完工', /提交完工/.test(repair), 'true');
check('维修端有 待确认 筛选芯片', /data-status="待确认"/.test(repair), 'true');

// 用真实 App.UI.confirm 验证页面的确认文案是二选一分支
const repairConfirmSrc = repair.match(/if \(!App\.UI\.confirm\(([^)]*)\)\)/);
check('维修端确认弹窗存在', Boolean(repairConfirmSrc), 'true');
if (repairConfirmSrc) {
    const text = repairConfirmSrc[1];
    check('维修端确认文案提到验收', /验收/.test(text), 'true');
}

// ---------- 3. 学生端页面（把 render 逻辑抽出来真跑一遍） ----------
const student = html('student-orders.html');
check('学生端 canConfirm 条件是 待确认', /canConfirm\s*=\s*status === '待确认'/.test(student), 'true');
check('学生端 canCancel 只允许 待处理/维修中', /canCancel\s*=\s*status === '待处理' \|\| status === '维修中'/.test(student), 'true');
check('学生端确认按钮发 已完成', /data-status-set="已完成"/.test(student), 'true');
check('学生端确认按钮文案为 确认验收', /确认验收/.test(student), 'true');
check('学生端有 待确认 筛选芯片', /data-status="待确认"/.test(student), 'true');

// 从页面脚本里抽出 canCancel / canConfirm 两行，按不同状态真跑一遍
const canCancelExpr = /var canCancel = ([^;]+);/.exec(student)[1];
const canConfirmExpr = /var canConfirm = ([^;]+);/.exec(student)[1];
const evaluate = (expr, status) => Function('status', `return (${expr});`)(status);

const cases = [
    ['待处理', true, false],
    ['维修中', true, false],
    ['待确认', false, true],
    ['已完成', false, false],
    ['已取消', false, false]
];
for (const [status, cancel, confirm] of cases) {
    check(`学生端 ${status} -> 可取消=${cancel}`, evaluate(canCancelExpr, status), String(cancel));
    check(`学生端 ${status} -> 可确认=${confirm}`, evaluate(canConfirmExpr, status), String(confirm));
}

// ---------- 4. 管理端筛选参数（缺了会退化成只发「待处理」） ----------
const admin = html('admin-orders.html');
check('管理端状态下拉含 待确认', /'待处理', '维修中', '待确认', '已完成', '已取消'/.test(admin), 'true');
check('管理端筛选参数含 待确认', /'待确认': '待确认'/.test(admin), 'true');

// ---------- 5. CSS 与 JS 的徽章契约闭合 ----------
const css = fs.readFileSync(path.join(STATIC, 'css', 'app.css'), 'utf8');
check('app.css 定义了 .bp-stamp--wait', /\.bp-stamp--wait\b/.test(css), 'true');
check('app.css 定义了 --st-wait-bg', /--st-wait-bg/.test(css), 'true');

console.log(results.join('\n'));
console.log(`\nRESULT pass=${pass} fail=${fail}`);
process.exit(fail === 0 ? 0 : 1);
