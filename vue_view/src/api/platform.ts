import request from '../utils/request';
import type { Platform } from '../types';

/**
 * 获取所有支持的平台列表
 */
export function getPlatformList() {
    return request.get<Platform[]>('/platform/list');
}
