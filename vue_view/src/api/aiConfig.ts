import request from '../utils/request';

export interface AiConfigData {
    provider: string;
    baseUrl: string;
    apiKey: string;
    modelName: string;
}

/**
 * 获取当前用户的 AI 配置
 */
export function getAiConfig() {
    return request.get<AiConfigData>('/ai-config');
}

/**
 * 保存当前用户的 AI 配置
 */
export function saveAiConfig(config: AiConfigData) {
    return request.post<AiConfigData>('/ai-config', config);
}

/**
 * 测试 AI 配置是否可连通（使用临时配置，不落库）
 */
export function testAiConfig(config: AiConfigData) {
    return request.post<{ success: boolean; message: string }>('/ai-config/test', config);
}
