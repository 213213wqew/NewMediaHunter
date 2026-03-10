/**
 * 平台信息定义
 */
export interface Platform {
    id: number;
    platformKey: string;
    platformName: string;
}

/**
 * 账号信息定义
 */
export interface Account {
    id: number;
    platformId: number;
    accountName: string;
    appId?: string;
    appSecret?: string;
    cookieData?: string;
    status: number; // 1-正常, 0-禁用
}

/**
 * 文章稿件定义
 */
export interface Article {
    id?: number;
    title: string;
    content: string;
    contentType?: string;
    author?: string;
    summary?: string;
    coverImage?: string;
    /** 视频地址（contentType=video 时必填） */
    videoUrl?: string;
    category?: string;
    tags?: string;
    platformSettings?: string; // 各平台独立设置的 JSON 字符串
    status?: number;
    createTime?: string;
}

export interface MediaResource {
    id?: number;
    articleId?: number;
    originalUrl: string;
    fileType: string;
    platformMediaId?: string;
    platformMediaUrl: string;
    accountId?: number;
    uploadStatus?: number;
    createTime?: string;
}

/**
 * 发布任务定义
 */
export interface PublishTask {
    id: number;
    articleId: number;
    articleTitle?: string;
    accountId: number;
    publishStatus: number; // 0-待处理, 1-排号中, 2-发布中, 3-成功, 4-失败
    platformArticleUrl?: string;
    errorMessage?: string;
    createTime: string;
    scheduledTime?: string;
    batchId?: number;
}

export interface ChartData {
    name: string;
    value: number;
}

export interface PublishStats {
    totalAccounts: number;
    totalArticles: number;
    totalTasks: number;
    successRate: number;
    seriesData: ChartData[];
    platformData: ChartData[];
}

export interface SensitiveWord {
    id: number;
    word: string;
    category?: string;
    createTime?: string;
}

export interface PublishLog {
    id: number;
    taskId: number;
    logLevel: string;
    message: string;
    requestData?: string;
    responseData?: string;
    httpStatus?: number;
    stackTrace?: string;
    createTime: string;
}
