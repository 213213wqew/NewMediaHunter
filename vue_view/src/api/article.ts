import request from '../utils/request';
import type { Article } from '../types';

/**
 * 获取文章列表
 */
export function getArticleList() {
    return request.get<Article[]>('/article/list');
}

/**
 * 保存文章
 */
export function saveArticle(article: Partial<Article>) {
    return request.post<Article>('/article/save', article);
}

/**
 * 获取文章详情
 */
export function getArticle(id: number) {
    return request.get<Article>(`/article/${id}`);
}

/**
 * 删除文章
 */
export function deleteArticle(id: number) {
    return request.delete(`/article/${id}`);
}
