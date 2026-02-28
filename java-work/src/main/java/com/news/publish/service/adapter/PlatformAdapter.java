package com.news.publish.service.adapter;

import com.news.publish.model.entity.Account;
import com.news.publish.model.entity.Article;
import com.news.publish.model.entity.PublishTask;

/**
 * 平台适配器接口：定义所有发布渠道必须实现的标准行为
 */
public interface PlatformAdapter {

    /**
     * 获取平台唯一标识 (如 baijiahao, qiehao)
     */
    String getPlatformKey();

    /**
     * 第一步：上传单张图片/媒体到平台
     * @return 平台侧分配的 URL
     */
    String uploadSingleMedia(String mediaUrl, Account account);

    /**
     * 第二步：发布文章/稿件
     * @param content 洗涤后的 HTML 内容
     */
    void publishArticle(Article article, Account account, PublishTask task, String content);

    /**
     * 第三步：查询发布后的文章状态
     * 用于追踪审核是否通过、是否被拒稿等
     */
    void refreshArticleStatus(PublishTask task, Account account);

    /**
     * 发布视频内容
     */
    void publishVideo(Article article, Account account, PublishTask task, String videoUrl);
}
