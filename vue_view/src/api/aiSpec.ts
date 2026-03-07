import request from '../utils/request';

const BASE_URL = '/ai-spec';

export interface AiWritingSpecification {
    id?: number;
    name: string;
    category: 'GENERATION' | 'POLISH' | 'SUMMARY' | 'TAGS' | 'CATEGORY';
    promptContent: string;
    userId?: number;
    isDefault: boolean;
    isSystem?: boolean; // 新增：是否系统级别
    createdAt?: string;
    updatedAt?: string;
}

export const getSpecList = (category?: string) => {
    return request.get<AiWritingSpecification[]>(`${BASE_URL}/list`, { params: { category } });
};

export const saveSpec = (spec: AiWritingSpecification) => {
    return request.post<AiWritingSpecification>(`${BASE_URL}/save`, spec);
};

export const deleteSpec = (id: number) => {
    return request.delete(`${BASE_URL}/${id}`);
};

export const initDefaultSpecs = () => {
    return request.post(`${BASE_URL}/init-defaults`);
};

export const setDefaultSpec = (id: number) => {
    return request.post(`${BASE_URL}/set-default/${id}`);
};

// 预设接口由于已经在 list 里面合并，如果需要单独也可以再调
export const getSpecPresets = () => {
    return request.get(`${BASE_URL}/presets`);
};
