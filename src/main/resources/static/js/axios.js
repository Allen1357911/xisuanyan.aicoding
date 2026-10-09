// 引入axios库
// 注意：需要在HTML中先引入axios库，然后再引入此文件

// 创建axios实例
const axiosInstance = axios.create({
    baseURL: '', // 基础URL
    timeout: 10000, // 请求超时时间
    headers: {
        'Content-Type': 'application/json'
    }
});

// 清除登录状态并回到登录页
function clearAuthAndGoLogin() {
    localStorage.removeItem('user');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    // 已经在登录页时不再重复跳转，避免页面反复刷新
    if (!window.location.pathname.endsWith('login.html')) {
        window.location.href = 'login.html';
    }
}

// 请求拦截器
axiosInstance.interceptors.request.use(
    config => {
        // 从localStorage中获取access token
        const accessToken = localStorage.getItem('accessToken');
        if (accessToken) {
            // 将token添加到请求头
            config.headers.Authorization = `Bearer ${accessToken}`;
        }
        return config;
    },
    error => {
        // 处理请求错误
        return Promise.reject(error);
    }
);

// 响应拦截器
axiosInstance.interceptors.response.use(
    response => {
        // 直接返回响应数据
        return response.data;
    },
    error => {
        const config = error.config || {};
        const requestUrl = config.url || '';
        const requestMethod = (config.method || 'get').toLowerCase();

        // 登录/注册/刷新令牌自身的失败不再触发刷新，避免401死循环
        const isAuthEndpoint = requestUrl.includes('/users/login')
            || requestUrl.includes('/users/refresh-token')
            || (requestUrl === '/users' && requestMethod === 'post');

        if (error.response) {
            // 服务器返回错误状态码
            switch (error.response.status) {
                case 401:
                    // 未授权：尝试刷新token后重试一次
                    if (isAuthEndpoint || config._retried) {
                        clearAuthAndGoLogin();
                        return Promise.reject(error.response.data || { success: false, message: '登录已过期，请重新登录' });
                    }
                    return refreshAccessToken().then(newAccessToken => {
                        // 重新发起请求，并标记已重试过，防止无限重试
                        config._retried = true;
                        config.headers = config.headers || {};
                        config.headers.Authorization = `Bearer ${newAccessToken}`;
                        return axiosInstance(config);
                    }).catch(() => {
                        // 刷新token失败，跳转到登录页面
                        clearAuthAndGoLogin();
                        return Promise.reject(error.response.data || { success: false, message: '登录已过期，请重新登录' });
                    });
                case 403:
                    // 权限不足：不跳登录页，交给调用方提示，避免误踢出登录
                    return Promise.reject(error.response.data || { success: false, message: '权限不足' });
                default:
                    // 其他错误，直接返回错误信息
                    return Promise.reject(error.response.data);
            }
        } else if (error.request) {
            // 请求已发送但没有收到响应
            return Promise.reject({ success: false, message: '网络错误，请检查网络连接' });
        } else {
            // 请求配置错误
            return Promise.reject({ success: false, message: error.message });
        }
    }
);

// 刷新access token的函数
function refreshAccessToken() {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
        return Promise.reject('No refresh token found');
    }
    // 使用不带拦截器的原始 axios，避免刷新接口自身报 401 时再次进入刷新逻辑
    return axios.post('/users/refresh-token', { refreshToken: refreshToken })
        .then(response => {
            const data = response.data || {};
            if (data.success && data.accessToken) {
                // 更新localStorage中的access token
                localStorage.setItem('accessToken', data.accessToken);
                return data.accessToken;
            }
            return Promise.reject('Access token refresh failed');
        });
}

// 导出axios实例
window.axiosInstance = axiosInstance;
