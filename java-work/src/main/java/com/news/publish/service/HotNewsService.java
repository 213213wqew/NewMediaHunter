package com.news.publish.service;

import com.news.publish.model.dto.HotNewsDto;
import java.util.List;

public interface HotNewsService {
    /**
     * 根据爱好或关键字抓取最新热点资讯
     * @param keyword 关键字
     * @return 资讯列表
     */
    List<HotNewsDto> fetchHotNewsByKeyword(String keyword);

    /**
     * 获取指定 URL 的文章正文内容
     * @param url 文章链接
     * @return 正文文本
     */
    String fetchArticleContent(String url);
}
