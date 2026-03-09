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

/** 筛选参数：与创作罐头页面一致，用于在目标页上自动点选后再抓取 */
export interface HotNewsFilter {
    tab?: string;
    platform?: string;
    contentType?: string;
    domains?: string[];  // 内容领域多选，如 ['生活', '影视']
    publishTime?: string;
    sort?: string;
}

/** tab: network=全网热榜, article=全网爆文, popular=低粉爆款；filter 会传给后端在页面上点选对应筛选项 */
export const fetchHotNews = (keyword: string, tab?: string, filter?: HotNewsFilter): Promise<HotNews[]> => {
    const params: Record<string, string> = { keyword };
    if (tab != null && tab !== '') params.tab = tab;
    if (filter?.platform) params.platform = filter.platform;
    if (filter?.contentType) params.contentType = filter.contentType;
    if (filter?.domains?.length) params.domains = filter.domains.join(',');
    if (filter?.publishTime) params.publishTime = filter.publishTime;
    if (filter?.sort) params.sort = filter.sort;
    return request.get('/hot-news/fetch', { params });
};

export const fetchArticleContent = (url: string): Promise<{ content: string, images?: string[] }> => {
    return request.get('/hot-news/article-content', { params: { url } });
};

export const generateArticle = (topic: string, outline: string, specId?: number | null): Promise<{ content: string, images?: string[] }> => {
    return request.post('/ai/generate-article', { topic, outline, specId });
};

export const suggestTitles = (title: string, content: string): Promise<string[]> => {
    return request.post('/ai/suggest-titles', { title, content });
};

export const matchImage = (keyword: string): Promise<{ url: string }> => {
    return request.post('/ai/match-image', { keyword });
};

export const polishArticle = (content: string, specId?: number | null): Promise<{ content: string, images?: string[] }> => {
    return request.post('/ai/polish', { content, specId });
};

export const suggestImages = (content: string): Promise<string[]> => {
    return request.post('/ai/suggest-images', { content });
};

export const syncPlatformTasks = (platKey: string): Promise<{ success: boolean; count: number; message?: string }> => {
    return request.post(`/ai/platforms/${platKey}/sync-tasks`);
};

export const getPlatformTasks = (platKey: string): Promise<any[]> => {
    return request.get(`/ai/platforms/${platKey}/tasks`);
};

export const matchHotTopics = (content: string, hotTopicsJson: string): Promise<string[]> => {
    return request.post('/ai/match-hot-topics', { content, hotTopicsJson });
};

export const generateImage = (prompt: string): Promise<{ url: string }> => {
    return request.post('/ai/generate-image', { prompt });
};
