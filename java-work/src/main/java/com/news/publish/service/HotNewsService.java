package com.news.publish.service;

import com.news.publish.model.dto.HotNewsDto;
import java.util.List;

public interface HotNewsService {
    /**
     * 根据爱好或关键字抓取最新热点资讯（支持与创作罐头页面一致的筛选条件）
     * @param keyword 关键字
     * @param tab 分类：network=全网热榜, article=全网爆文, popular=低粉爆款；为空时默认 popular
     * @param platform 媒体平台，如 今日头条、百家号
     * @param contentType 内容类型，如 短图文、文章、视频
     * @param domains 内容领域多选，逗号分隔，如 生活,影视
     * @param publishTime 发布时间，如 1d、3h、7d
     * @param sort 排序，如 reads、time、comments、likes
     * @return 资讯列表
     */
    List<HotNewsDto> fetchHotNewsByKeyword(String keyword, String tab, String platform, String contentType, String domains, String publishTime, String sort);

    /**
     * 获取指定 URL 的文章正文内容，支持平台和内容类型区分
     * @param url 文章链接
     * @param platform 平台标识
     * @param type 内容类型（短图文、文章）
     * @return 正文文本
     */
    String fetchArticleContent(String url, String platform, String type);

    /**
     * 获取指定 URL 的文章正文内容（旧版兼容）
     */
    String fetchArticleContent(String url);
}
