import request from '../utils/request';
import type { Account } from '../types';

export interface AccountStats {
    accountId: number;
    totalFans?: number;
    totalReads?: number;
    totalRevenue?: string;
    yesterdayFans?: number;
    yesterdayReads?: number;
    yesterdayRevenue?: string;
    updatedAt?: string;
}

/**
 * 获取所有账号列表（来自本地文件）
 */
export function getAccountList() {
    return request.get<Account[]>('/account/list');
}

/**
 * 获取各账号昨日数据（粉丝、阅读、收益）
 */
export function getAccountStats() {
    return request.get<Record<string, AccountStats>>('/account/stats');
}

/**
 * 更新数据：拉取选中账号昨日数据并保存到本地。不传 accountIds 则更新全部。
 */
export function refreshAccountStats(accountIds?: number[]) {
    const body = accountIds != null && accountIds.length > 0 ? { accountIds } : {};
    return request.post<Record<string, AccountStats>>('/account/refresh-stats', body, { timeout: 300000 });
}

/**
 * 开始绑定：仅传平台+账号名称，后端弹出登录页，登录成功后 Token 存本地文件
 */
export function bindStart(params: { platformKey: string; accountName: string }) {
    return request.post<Account>('/account/bind-start', params, { timeout: 200000 });
}

/**
 * 删除账号（从本地文件移除）
 */
export function deleteAccount(id: number) {
    return request.delete(`/account/${id}`);
}
