import axios from 'axios';
import { ElMessage } from 'element-plus';
import { clearCredentials, getBasicAuthorization } from '../auth/session';
export const api = axios.create({
    baseURL: '/api',
});
function adminLoginUrlPath() {
    const base = import.meta.env.BASE_URL || '/';
    const prefix = base.endsWith('/') ? base : `${base}/`;
    return `${prefix}login`;
}
/** 从 Spring / 通用 JSON 错误体提取可读文案（供拦截器与登录页共用） */
export function extractErrorBodyMessage(data, status) {
    if (data && typeof data === 'object') {
        const d = data;
        if (typeof d.error === 'string' && d.error.trim())
            return d.error.trim();
        if (typeof d.message === 'string' && d.message.trim())
            return d.message.trim();
        if (Array.isArray(d.errors)) {
            const parts = d.errors
                .map((e) => {
                if (e && typeof e === 'object') {
                    const o = e;
                    const df = o.defaultMessage;
                    if (typeof df === 'string')
                        return df;
                    const msg = o.message;
                    if (typeof msg === 'string')
                        return msg;
                }
                return String(e);
            })
                .filter(Boolean);
            if (parts.length)
                return parts.slice(0, 5).join('；');
        }
    }
    return `请求失败（${status}）`;
}
export function parseAxiosErrorMessage(err) {
    if (!axios.isAxiosError(err)) {
        return '请求失败，请稍后重试';
    }
    if (!err.response) {
        return '无法连接服务器，请确认后端已启动且网络正常';
    }
    return extractErrorBodyMessage(err.response.data, err.response.status);
}
api.interceptors.request.use((config) => {
    const auth = getBasicAuthorization();
    if (auth) {
        config.headers.Authorization = auth;
    }
    return config;
});
/** 相同文案在短时间内只 toast 一次，避免并行 403 等刷屏 */
let lastErrorToastKey = '';
let lastErrorToastAt = 0;
api.interceptors.response.use((res) => res, (err) => {
    if (axios.isAxiosError(err) && err.response?.status === 401) {
        clearCredentials();
        const loginPathname = new URL(adminLoginUrlPath(), window.location.origin).pathname;
        if (window.location.pathname !== loginPathname) {
            window.location.assign(adminLoginUrlPath());
        }
        return Promise.reject(err);
    }
    const msg = parseAxiosErrorMessage(err);
    const status = axios.isAxiosError(err) ? err.response?.status : undefined;
    const key = `${status ?? 'na'}:${msg}`;
    const now = Date.now();
    if (key !== lastErrorToastKey || now - lastErrorToastAt > 2800) {
        lastErrorToastKey = key;
        lastErrorToastAt = now;
        ElMessage.error(msg);
    }
    return Promise.reject(err);
});
