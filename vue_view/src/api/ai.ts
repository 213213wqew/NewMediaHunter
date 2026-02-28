import request from '../utils/request';

export interface HotNews {
    id: string;
    title: string;
    summary: string;
    source: string;
    url: string;
    sourceUrl?: string;
    hotScore: number;
    publishTime: string;
    tags?: string[];
}

export const fetchHotNews = (keyword: string): Promise<HotNews[]> => {
    return request.get('/hot-news/fetch', { params: { keyword } });
};

export const fetchArticleContent = (url: string): Promise<{ content: string }> => {
    return request.get('/hot-news/article-content', { params: { url } });
};

export const generateArticle = (topic: string, outline: string): Promise<{ content: string }> => {
    return request.post('/ai/generate-article', { topic, outline });
};

export const suggestTitles = (title: string, content: string): Promise<string[]> => {
    return request.post('/ai/suggest-titles', { title, content });
};

export const matchImage = (keyword: string): Promise<{ url: string }> => {
    return request.post('/ai/match-image', { keyword });
};

export const polishArticle = (content: string): Promise<{ content: string }> => {
    return request.post('/ai/polish', { content });
};

export const suggestImages = (content: string): Promise<string[]> => {
    return request.post('/ai/suggest-images', { content });
};

export const fetchBaijiahaoTasks = (): Promise<{ result: string }> => {
    return request.get('/ai/baijiahao-tasks');
};
