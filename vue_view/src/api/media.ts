import request from '../utils/request';
import type { MediaResource } from '../types';

/**
 * 获取素材列表
 */
export const getMediaList = (params?: { type?: string }): Promise<MediaResource[]> => {
    return request.get('/media/list', { params });
};

/**
 * 删除素材
 */
export const deleteMedia = (id: number): Promise<void> => {
    return request.delete(`/media/${id}`);
};
