/**
 * 前端真实 DOM 交互测试（jsdom）
 *
 * 与 frontend-smoke-test.js 的区别：那个只做静态断言，这个会真的把页面加载进 DOM、
 * 触发真实 click 事件，然后断言页面发出了什么请求、按钮怎么显示。
 *
 * 依赖 jsdom（故意不在项目内安装，避免给这个 Maven 工程引入 node_modules）：
 *   npm install jsdom --prefix D:\deepseek.harness\.frontend-test-tools
 * 运行：
 *   node frontend-dom-test.js            （会自动搜索常见位置的 jsdom）
 */
'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = __dirname;
const STATIC = path.join(ROOT, 'src', 'main', 'resources', 'static');
const CANDIDATES = [
    process.env.JSDOM_PATH,
    'D:\\deepseek.harness\\.frontend-test-tools\\node_modules\\jsdom',
    path.join(ROOT, '..', '.frontend-test-tools', 'node_modules', 'jsdom'),
    'jsdom'
].filter(Boolean);

let JSDOM = null;
let jsdomFrom = null;
for (const candidate of CANDIDATES) {
    try {
        ({ JSDOM } = require(candidate));
        jsdomFrom = candidate;
        break;
    } catch (e) {
        /* 尝试下一个位置 */
    }
}
if (!JSDOM) {
    console.log('SKIP: 未找到 jsdom，跳过 DOM 交互测试（安装方式见文件头注释）');
    process.exit(0);
}

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

/** 把页面加载进 jsdom，返回 {window, document, calls}；calls 记录页面发出的 API 请求 */
function loadPage(pageFile, orders, confirmAnswer) {
    let html = fs.readFileSync(path.join(STATIC, 'html', pageFile), 'utf8');
    const inline = [];
    html = html.replace(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g, (m, code) => {
        inline.push(code);
        return '';
    });
    // 去掉外链脚本：common.js 由测试自己注入（带桩），axios 用桩替代
    html = html.replace(/<script[^>]*\bsrc=[^>]*><\/script>/g, '');

    const dom = new JSDOM(html, { runScripts: 'outside-only', url: 'http://localhost:8082/html/' + pageFile });
    const win = dom.window;
    const calls = [];

    win.confirm = () => confirmAnswer !== false;
    win.alert = () => {};
    win.localStorage.setItem('user', JSON.stringify({ userId: 2, roleId: 1, userName: '张三', account: '3001' }));
    win.localStorage.setItem('accessToken', 'fake-token');

    // 只桩最底层的 axios：让 App.API.get/put/call 走 common.js 里的真实实现，
    // 这样被验证的包括 call() 的 {ok,data,message} 归一化逻辑，而不是我重写的替身。
    win.eval(`
        window.FormData = window.FormData || function () { this.append = function () {}; };
        window.axiosInstance = function (config) {
            window.__apiCalls.push({ method: (config.method || 'get').toUpperCase(), url: config.url, body: config.data });
            return Promise.resolve({ success: true, data: window.__orders || [], message: '' });
        };
        window.axios = { create: function () { return window.axiosInstance; } };
    `);
    win.__orders = orders.slice();
    win.__apiCalls = calls;

    // 注入真实的 common.js（它内部用的是 global.document / global.confirm）
    const commonSrc = fs.readFileSync(path.join(STATIC, 'js', 'common.js'), 'utf8');
    win.eval(`(function(){ var document = window.document; ${commonSrc} }).call(window)`);

    win.eval(inline.join('\n'));
    return { dom, win, doc: win.document, calls, App: win.App };
}

// ---------- 学生端：待确认工单点「确认验收」 ----------
const pendingOrder = {
    orderId: 2, userId: 2, repairmanId: 4, deviceType: '电灯', problemDesc: '灯不亮',
    orderStatus: '待确认', building: '1栋', roomNum: '502',
    createTime: '2024-03-10T20:30:00', updateTime: '2024-03-12T10:05:00'
};
const student = loadPage('student-orders.html', [pendingOrder], true);
// 等 loadOrders() 的 Promise 链跑完
setTimeout(() => {
    try {
        const confirmBtn = student.doc.querySelector('[data-status-set="已完成"]');
        check('待确认工单渲染出「确认验收」按钮', Boolean(confirmBtn), 'true');
        check('按钮文案', confirmBtn && confirmBtn.textContent, '确认验收');
        check('待确认工单不再显示「取消报修」', Boolean(student.doc.querySelector('[data-status-set="已取消"]')), 'false');
        check('徽章是 wait 样式', Boolean(student.doc.querySelector('.bp-stamp--wait')), 'true');

        confirmBtn.dispatchEvent(new student.win.Event('click', { bubbles: true }));

        const put = student.calls.filter((c) => c.method === 'PUT').pop();
        check('点击后发出 PUT 请求', Boolean(put), 'true');
        check('请求路径带订单号', put && put.url, '/repair-orders/2/status');
        check('提交的状态是 已完成', put && put.body && put.body.status, '已完成');
    } catch (e) {
        fail++;
        results.push(`  FAIL  学生端用例异常: ${e.message}`);
    }

    // ---------- 学生端：维修中工单不应出现确认按钮 ----------
    const repairing = loadPage('student-orders.html', [Object.assign({}, pendingOrder, { orderStatus: '维修中' })], true);
    setTimeout(() => {
        try {
            check('维修中工单没有「确认验收」按钮',
                Boolean(repairing.doc.querySelector('[data-status-set="已完成"]')), 'false');
            check('维修中工单有「取消报修」按钮',
                Boolean(repairing.doc.querySelector('[data-status-set="已取消"]')), 'true');

            // ---------- 学生端：用户取消确认弹窗时不应发请求 ----------
            const declined = loadPage('student-orders.html', [Object.assign({}, pendingOrder, { orderStatus: '待处理' })], false);
            setTimeout(() => {
                try {
                    const cancelBtn = declined.doc.querySelector('[data-status-set="已取消"]');
                    cancelBtn.dispatchEvent(new declined.win.Event('click', { bubbles: true }));
                    check('用户点了取消后不发任何写请求',
                        declined.calls.filter((c) => c.method === 'PUT' || c.method === 'POST').length, '0');

                    // ---------- 维修端：维修中工单点「提交完工」 ----------
                    const repairPage = loadPage('repair-orders.html', [Object.assign({}, pendingOrder, { orderStatus: '维修中' })], true);
                    setTimeout(() => {
                        try {
                            const finishBtn = repairPage.doc.querySelector('[data-finish]');
                            check('维修端渲染出「提交完工」按钮', Boolean(finishBtn), 'true');
                            check('维修端按钮文案', finishBtn && finishBtn.textContent, '提交完工');
                            finishBtn.dispatchEvent(new repairPage.win.Event('click', { bubbles: true }));
                            const put = repairPage.calls.filter((c) => c.method === 'PUT').pop();
                            check('维修端请求路径', put && put.url, '/repair-orders/2/status');
                            check('维修端提交的状态是 待确认', put && put.body && put.body.status, '待确认');

                            // 待确认的工单，维修端不应再给动作按钮
                            const waiting = loadPage('repair-orders.html', [pendingOrder], true);
                            setTimeout(() => {
                                check('待确认工单在维修端没有「提交完工」按钮',
                                    Boolean(waiting.doc.querySelector('[data-finish]')), 'false');

                                console.log(`jsdom from: ${jsdomFrom}`);
                                console.log(results.join('\n'));
                                console.log(`\nRESULT pass=${pass} fail=${fail}`);
                                process.exit(fail === 0 ? 0 : 1);
                            }, 30);
                        } catch (e) {
                            fail++;
                            results.push(`  FAIL  维修端用例异常: ${e.message}`);
                            console.log(results.join('\n'));
                            process.exit(1);
                        }
                    }, 30);
                } catch (e) {
                    fail++;
                    results.push(`  FAIL  取消弹窗用例异常: ${e.message}`);
                    console.log(results.join('\n'));
                    process.exit(1);
                }
            }, 30);
        } catch (e) {
            fail++;
            results.push(`  FAIL  维修中用例异常: ${e.message}`);
            console.log(results.join('\n'));
            process.exit(1);
        }
    }, 30);
}, 30);
