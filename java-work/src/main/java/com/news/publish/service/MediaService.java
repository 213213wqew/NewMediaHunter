package com.news.publish.service;

import com.news.publish.model.entity.Account;
import com.news.publish.model.entity.Article;

import java.util.Map;

/**
 * 媒体洗涤服务 - 负责同步文章中的图片/视频到目标平台
 */
public interface MediaService {

    /**
     * 内容洗涤：提取图片 -> 上传平台 -> 替换链接
     * @param article 原始文章
     * @param account 目标账号
     * @return 洗涤后的 HTML 内容
     */
    String cleanContent(Article article, Account account);
}
