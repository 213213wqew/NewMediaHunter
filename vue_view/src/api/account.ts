import request from '../utils/request';
import type { Account } from '../types';

/**
 * 获取所有账号列表
 */
export function getAccountList() {
    return request.get<Account[]>('/account/list');
}

/**
 * 保存账号信息
 */
export function saveAccount(account: Partial<Account>) {
    return request.post<Account>('/account/save', account);
}

/**
 * 删除账号
 */
export function deleteAccount(id: number) {
    return request.delete(`/account/${id}`);
}
