import axios from 'axios';
import type { AxiosResponse } from 'axios';

const request = axios.create({
    baseURL: '/api',
    timeout: 60000,
});

// 请求拦截器
request.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// 响应拦截器
request.interceptors.response.use(
    (response: AxiosResponse) => {
        return response.data;
    },
    (error) => {
        if (error.response && error.response.status === 401) {
            localStorage.removeItem('token');
            localStorage.removeItem('username');
            localStorage.removeItem('role');
            window.location.href = '/login';
        }
        console.error('API Error:', error.response?.data || error.message);
        return Promise.reject(error);
    }
);

// 导出包装后的请求方法，以正确处理类型
export default {
    get: <T = any>(url: string, config?: any): Promise<T> => request.get(url, config) as any,
    post: <T = any>(url: string, data?: any, config?: any): Promise<T> => request.post(url, data, config) as any,
    put: <T = any>(url: string, data?: any, config?: any): Promise<T> => request.put(url, data, config) as any,
    delete: <T = any>(url: string, config?: any): Promise<T> => request.delete(url, config) as any,
};
