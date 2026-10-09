/* ============================================================
   宿舍报修系统 · 前端公共运行时
   依赖：js/axios.min.js（本地运行时）、js/axios.js（实例与拦截器）
   提供：会话管理、接口封装、UI 组件片段、页面外壳（顶栏 + 侧栏导航）
   ============================================================ */

(function (global) {
    'use strict';

    var ROLE_NAMES = { 1: '学生', 2: '管理员', 3: '维修人员' };

    var STATUS_CLASS = {
        '待处理': 'pending',
        '维修中': 'doing',
        '待确认': 'wait',
        '已完成': 'done',
        '已取消': 'cancel'
    };

    /* 各角色的侧栏导航 */
    var NAV = {
        1: [
            { href: 'student-index.html', label: '总览' },
            { href: 'student-dorm.html', label: '我的宿舍' },
            { href: 'student-submit.html', label: '提交报修' },
            { href: 'student-orders.html', label: '我的报修' }
        ],
        2: [
            { href: 'admin.html', label: '总览' },
            { href: 'admin-orders.html', label: '报修工单' },
            { href: 'admin-dormitories.html', label: '宿舍管理' },
            { href: 'admin-students.html', label: '学生管理' },
            { href: 'admin-repairmen.html', label: '维修人员' }
        ],
        3: [
            { href: 'repair-accept.html', label: '待接工单' },
            { href: 'repair-orders.html', label: '我的任务' }
        ]
    };

    /* ---------------- 会话 ---------------- */

    var Session = {
        getUser: function () {
            try {
                return JSON.parse(localStorage.getItem('user'));
            } catch (e) {
                return null;
            }
        },
        userId: function () {
            var u = this.getUser();
            return u ? u.userId : null;
        },
        roleId: function () {
            var u = this.getUser();
            return u ? u.roleId : null;
        },
        roleName: function () {
            return ROLE_NAMES[this.roleId()] || '未知角色';
        },
        token: function () {
            return localStorage.getItem('accessToken');
        },
        clear: function () {
            localStorage.removeItem('user');
            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
        },
        logout: function () {
            this.clear();
            location.href = 'login.html';
        },
        /* 未登录直接跳登录页，返回当前用户 */
        requireLogin: function () {
            var user = this.getUser();
            if (!user || !this.token()) {
                location.href = 'login.html';
                return null;
            }
            return user;
        }
    };

    /* ---------------- 接口封装 ----------------
       axios.js 里的响应拦截器已经把响应体解包（返回 {success,message,data}），
       失败时 reject 的也是响应体，所以这里统一用 call() 拿到 {ok, data, message}。 */

    var API = {
        get: function (url, params) {
            return axiosInstance({ url: url, method: 'get', params: params });
        },
        post: function (url, data) {
            return axiosInstance({ url: url, method: 'post', data: data });
        },
        put: function (url, data) {
            return axiosInstance({ url: url, method: 'put', data: data });
        },
        del: function (url) {
            return axiosInstance({ url: url, method: 'delete' });
        },
        upload: function (url, file) {
            var form = new FormData();
            form.append('file', file);
            return axiosInstance({
                url: url,
                method: 'post',
                data: form,
                headers: { 'Content-Type': 'multipart/form-data' }
            });
        },
        /* 统一收口错误：任何接口调用都拿到 {ok, data, message} */
        call: function (promise) {
            return promise.then(function (body) {
                return {
                    ok: !!(body && body.success),
                    data: body ? body.data : null,
                    message: body ? body.message : '',
                    body: body
                };
            }).catch(function (err) {
                return {
                    ok: false,
                    data: null,
                    message: (err && err.message) || '请求失败，请稍后重试',
                    body: err
                };
            });
        }
    };

    /* ---------------- UI 片段 ---------------- */

    var UI = {
        /* 所有来自接口的文本都要过一遍，避免把 HTML 当内容渲染 */
        esc: function (value) {
            if (value === null || value === undefined) {
                return '';
            }
            return String(value)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#39;');
        },

        statusClass: function (status) {
            return STATUS_CLASS[status] || 'pending';
        },

        /* 盖章式状态徽章 */
        stamp: function (status, animate) {
            var cls = this.statusClass(status);
            return '<span class="bp-stamp bp-stamp--' + cls + (animate ? ' bp-stamp--animate' : '') + '">'
                + this.esc(status || '未知') + '</span>';
        },

        /* LocalDateTime 字符串直接截取，避免 new Date() 带来时区偏移 */
        time: function (value) {
            if (!value) {
                return '—';
            }
            var m = String(value).match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/);
            return m ? (m[1] + '-' + m[2] + '-' + m[3] + ' ' + m[4] + ':' + m[5]) : String(value);
        },

        date: function (value) {
            var m = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})/);
            return m ? (m[1] + '-' + m[2] + '-' + m[3]) : '—';
        },

        /* 顶部提示条 */
        alert: function (el, message, type) {
            if (!el) {
                return;
            }
            el.className = 'bp-alert bp-alert--' + (type || 'info') + ' is-on';
            el.textContent = message;
            if (type === 'ok') {
                clearTimeout(el._bpTimer);
                el._bpTimer = setTimeout(function () {
                    el.classList.remove('is-on');
                }, 3200);
            }
        },

        clearAlert: function (el) {
            if (el) {
                el.classList.remove('is-on');
            }
        },

        empty: function (title, hint) {
            return '<div class="bp-empty"><div class="bp-empty__big">' + this.esc(title) + '</div>'
                + (hint ? '<div>' + this.esc(hint) + '</div>' : '') + '</div>';
        },

        loading: function (text) {
            return '<div class="bp-loading">' + this.esc(text || '加载中') + '</div>';
        },

        /* 渲染一组卡片，自动处理空态与错峰入场 */
        list: function (container, items, build) {
            if (!container) {
                return;
            }
            if (!items || !items.length) {
                container.innerHTML = this.empty('暂无记录', '换个筛选条件试试');
                return;
            }
            container.innerHTML = items.map(build).join('');
        },

        confirm: function (message) {
            return global.confirm(message);
        }
    };

    /* ---------------- 页面外壳 ---------------- */

    function currentPage() {
        var parts = location.pathname.split('/');
        return parts[parts.length - 1] || 'index.html';
    }

    function pad(n) {
        return n < 10 ? '0' + n : '' + n;
    }

    function clockText() {
        var d = new Date();
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
            + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    }

    function topTemplate(user) {
        var title = document.body.dataset.title || document.title || '宿舍报修系统';
        var kicker = document.body.dataset.kicker || '';
        return ''
            + '<div class="bp-top__title">'
            + '  <h1>' + UI.esc(title) + '</h1>'
            + (kicker ? '  <span class="bp-top__kicker">' + UI.esc(kicker) + '</span>' : '')
            + '</div>'
            + '<div class="bp-user">'
            + '  <div class="bp-user__meta">'
            + '    <div class="bp-user__name">' + UI.esc(user.userName || user.account) + '</div>'
            + '    <div class="bp-user__role">' + UI.esc(Session.roleName()) + ' · #' + UI.esc(user.userId) + '</div>'
            + '  </div>'
            + '  <span class="bp-top__kicker" id="bpClock">' + clockText() + '</span>'
            + '  <button class="bp-btn bp-btn--ghost bp-btn--sm" data-action="logout" type="button">退出</button>'
            + '</div>';
    }

    function railTemplate(user) {
        var items = NAV[user.roleId] || [];
        var page = currentPage();
        return '<div class="bp-rail__label">工作台 / ' + UI.esc(Session.roleName()) + '</div>'
            + items.map(function (item) {
                var current = item.href === page ? ' aria-current="page"' : '';
                return '<a href="' + item.href + '"' + current + '>' + UI.esc(item.label) + '</a>';
            }).join('');
    }

    var Shell = {
        /* 在受保护的页面上调用：校验登录、渲染顶栏与导航，返回当前用户 */
        mount: function () {
            var user = Session.requireLogin();
            if (!user) {
                return null;
            }
            var top = document.getElementById('bpTop');
            var rail = document.getElementById('bpRail');
            if (top) {
                top.innerHTML = topTemplate(user);
            }
            if (rail) {
                rail.innerHTML = railTemplate(user);
            }
            var clock = document.getElementById('bpClock');
            if (clock) {
                setInterval(function () {
                    clock.textContent = clockText();
                }, 30000);
            }
            return user;
        },

        /* 页脚图纸标题栏 */
        titleblock: function (extra) {
            var user = Session.getUser() || {};
            var rows = [
                ['SYSTEM', 'DormRepairSystem'],
                ['ROLE', Session.roleName()],
                ['USER', user.account || '—'],
                ['PAGE', currentPage()],
                ['DATE', clockText()]
            ].concat(extra || []);
            return '<dl class="bp-titleblock">' + rows.map(function (r) {
                return '<div><dt>' + UI.esc(r[0]) + '</dt><dd>' + UI.esc(r[1]) + '</dd></div>';
            }).join('') + '</dl>';
        }
    };

    /* 全局事件委托：退出按钮 */
    document.addEventListener('click', function (e) {
        if (e.target.closest && e.target.closest('[data-action="logout"]')) {
            Session.logout();
        }
    });

    global.App = {
        Session: Session,
        API: API,
        UI: UI,
        Shell: Shell,
        ROLE_NAMES: ROLE_NAMES
    };
})(window);
