import request from '../utils/request';
import type { PublishTask } from '../types';

/**
 * 提交发布任务接口（单条）
 */
export function submitPublishTask(data: { articleId: number; accountIds: number[]; scheduledTime?: string }) {
    return request.post<PublishTask[]>('/publish/submit', data);
}

/**
 * 视频批量发布：多视频按账号轮询分配，后端按账号串行、最多 9 账号并发
 */
export function submitVideoBatch(data: { articleIds: number[]; accountIds: number[]; scheduledTime?: string }) {
    return request.post<PublishTask[]>('/publish/submit-batch', data);
}

/**
 * 获取所有任务列表
 */
export function getTaskList() {
    return request.get<PublishTask[]>('/publish/tasks');
}

/**
 * 获取单个任务状态
 */
export function getTaskStatus(taskId: number) {
    return request.get<PublishTask>(`/publish/tasks/${taskId}`);
}

/**
 * 获取发布统计数据
 */
export function getPublishStats() {
    return request.get<import('../types').PublishStats>('/publish/stats');
}
/**
 * 获取所有敏感词
 */
export function getSensitiveWords() {
    return request.get<import('../types').SensitiveWord[]>('/compliance/words');
}

/**
 * 添加敏感词
 */
export function addSensitiveWord(data: { word: string; category?: string }) {
    return request.post<import('../types').SensitiveWord>('/compliance/words', data);
}

/**
 * 删除敏感词
 */
export function deleteSensitiveWord(id: number) {
    return request.delete(`/compliance/words/${id}`);
}

/**
 * 获取任务详情日志
 */
export function getTaskLogs(taskId: number) {
    return request.get<import('../types').PublishLog[]>(`/publish/tasks/${taskId}/logs`);
}
/**
 * 获取 AI 摘要
 */
export function getAiSummary(content: string) {
    return request.post<string>('/ai/summary', { content });
}

/**
 * 获取 AI 推荐标题
 */
export function getAiSuggestedTitles(title: string, content: string) {
    return request.post<string[]>('/ai/suggest-titles', { title, content });
}

/**
 * 获取 AI 推荐标签
 */
export function getAiTags(content: string) {
    return request.post<string[]>('/ai/extract-tags', { content });
}

/**
 * 获取 AI 推荐分类
 */
export function getAiCategory(content: string, categories: string) {
    return request.post<string>('/ai/extract-category', { content, categories });
}
