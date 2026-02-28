-- =====================================================
-- 多平台内容发布系统 - 数据库初始化脚本
-- 数据库：MySQL 8.0+
-- 字符集：utf8mb4
-- 创建时间：2026-02-22
-- =====================================================

CREATE DATABASE IF NOT EXISTS `publish_center` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `publish_center`;

SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 1. 平台信息表
-- 存储所有支持的第三方发布平台基本信息
-- =====================================================
DROP TABLE IF EXISTS `platform`;
CREATE TABLE `platform` (
    `id`            BIGINT          PRIMARY KEY AUTO_INCREMENT,
    `platform_key`  VARCHAR(50)     NOT NULL UNIQUE COMMENT '平台标识符',
    `platform_name` VARCHAR(100)    NOT NULL COMMENT '平台展示名称',
    `official_url`  VARCHAR(500)    DEFAULT NULL COMMENT '官方地址',
    `config_schema` TEXT            DEFAULT NULL COMMENT '动态配置项定义(JSON)',
    `create_time`   DATETIME        DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布平台配置表';

-- =====================================================
-- 1.5. 系统用户表
-- 存储多租户系统的用户及角色
-- =====================================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id`            BIGINT          PRIMARY KEY AUTO_INCREMENT,
    `username`      VARCHAR(50)     NOT NULL UNIQUE COMMENT '登录用户名',
    `password`      VARCHAR(100)    NOT NULL COMMENT '登录密码',
    `role`          VARCHAR(20)     NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN 或 USER',
    `create_time`   DATETIME        DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- =====================================================
-- 2. 账号信息表
-- 存储用户在各平台绑定的账号及授权凭证
-- =====================================================
DROP TABLE IF EXISTS `account`;
CREATE TABLE `account` (
    `id`                BIGINT          PRIMARY KEY AUTO_INCREMENT,
    `user_id`           BIGINT          NOT NULL COMMENT '所属用户 ID',
    `platform_id`       BIGINT          NOT NULL COMMENT '所属平台 ID',
    `account_name`      VARCHAR(100)    NOT NULL COMMENT '账号自定义名称/昵称',
    `app_id`            VARCHAR(200)    DEFAULT NULL COMMENT 'OAuth2 AppID',
    `app_secret`        VARCHAR(500)    DEFAULT NULL COMMENT 'OAuth2 AppSecret（加密存储）',
    `access_token`      TEXT            DEFAULT NULL COMMENT '访问令牌（加密存储）',
    `refresh_token`     TEXT            DEFAULT NULL COMMENT '刷新令牌（加密存储）',
    `token_expires_at`  DATETIME        DEFAULT NULL COMMENT 'AccessToken 过期时间',
    `cookie_data`       TEXT            DEFAULT NULL COMMENT 'Cookie 数据（非 API 接入的平台）',
    `open_id`           VARCHAR(200)    DEFAULT NULL COMMENT '平台侧用户身份标识',
    `status`            TINYINT         DEFAULT 1 COMMENT '账号状态: 1-正常, 0-禁用',
    `create_time`       DATETIME        DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`platform_id`) REFERENCES `platform`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方平台账号表';

-- =====================================================
-- 3. 文章稿件表
-- 存储待发布的原始文章内容
-- =====================================================
DROP TABLE IF EXISTS `article`;
CREATE TABLE `article` (
    `id`            BIGINT          PRIMARY KEY AUTO_INCREMENT,
    `user_id`       BIGINT          NOT NULL COMMENT '所属用户 ID',
    `title`         VARCHAR(500)    NOT NULL COMMENT '文章标题',
    `content`       LONGTEXT        NOT NULL COMMENT '文章正文（HTML 或 Markdown 格式）',
    `content_type`  VARCHAR(20)     DEFAULT 'html' COMMENT '正文格式: html, markdown',
    `author`        VARCHAR(100)    DEFAULT NULL COMMENT '作者名称',
    `summary`       VARCHAR(1000)   DEFAULT NULL COMMENT '文章摘要',
    `cover_image`   VARCHAR(500)    DEFAULT NULL COMMENT '封面图片地址',
    `category`      VARCHAR(100)    DEFAULT NULL COMMENT '内容分类',
    `tags`          VARCHAR(500)    DEFAULT NULL COMMENT '标签（逗号分隔）',
    `status`        TINYINT         DEFAULT 0 COMMENT '稿件状态: 0-草稿, 1-就绪, 2-已归档',
    `create_time`   DATETIME        DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章稿件表';

-- =====================================================
-- 4. 媒体资源表
-- 追踪文章中图片/视频在各平台的上传状态
-- =====================================================
DROP TABLE IF EXISTS `media_resource`;
CREATE TABLE `media_resource` (
    `id`                    BIGINT          PRIMARY KEY AUTO_INCREMENT,
    `user_id`               BIGINT          NOT NULL COMMENT '所属用户 ID',
    `article_id`            BIGINT          NOT NULL COMMENT '关联文章 ID',
    `original_url`          VARCHAR(1000)   NOT NULL COMMENT '原始媒体文件地址',
    `file_type`             VARCHAR(20)     DEFAULT 'image' COMMENT '文件类型: image, video',
    `platform_id`           BIGINT          DEFAULT NULL COMMENT '已上传到的平台 ID',
    `platform_media_id`     VARCHAR(500)    DEFAULT NULL COMMENT '平台侧媒体 ID（用于引用）',
    `platform_media_url`    VARCHAR(1000)   DEFAULT NULL COMMENT '平台侧媒体访问地址',
    `upload_status`         TINYINT         DEFAULT 0 COMMENT '上传状态: 0-未上传, 1-已上传, 2-失败',
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`article_id`) REFERENCES `article`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`platform_id`) REFERENCES `platform`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体资源洗涤表';

-- =====================================================
-- 5. 发布任务表
-- 记录每次文章分发到具体账号的执行任务
-- =====================================================
DROP TABLE IF EXISTS `publish_task`;
CREATE TABLE `publish_task` (
    `id`                    BIGINT          PRIMARY KEY AUTO_INCREMENT,
    `user_id`               BIGINT          NOT NULL COMMENT '所属用户 ID',
    `article_id`            BIGINT          NOT NULL COMMENT '关联的文章 ID',
    `account_id`            BIGINT          NOT NULL COMMENT '关联的账号 ID',
    `publish_status`        TINYINT         DEFAULT 0 COMMENT '发布状态: 0-待处理, 1-排队中, 2-发布中, 3-成功, 4-失败, 5-人工审核中',
    `platform_article_id`   VARCHAR(500)    DEFAULT NULL COMMENT '平台侧文章唯一 ID',
    `platform_article_url`  VARCHAR(1000)   DEFAULT NULL COMMENT '平台侧文章访问 URL',
    `retry_count`           INT             DEFAULT 0 COMMENT '重试次数',
    `error_message`         TEXT            DEFAULT NULL COMMENT '失败原因详细信息',
    `publish_time`          DATETIME        DEFAULT NULL COMMENT '实际发布成功时间',
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP,
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (`article_id`) REFERENCES `article`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`account_id`) REFERENCES `account`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容发布分发任务表';

-- =====================================================
-- 初始化数据：平台配置
-- =====================================================
INSERT INTO `platform` (`platform_key`, `platform_name`, `official_url`) VALUES
('baijiahao',   '百度百家号',   'https://baijiahao.baidu.com/'),
('qiehao',      '腾讯企鹅号',   'https://om.qq.com/'),
('toutiao',     '今日头条号',   'https://mp.toutiao.com/'),
('sina',        '新浪看点',     'https://mp.sina.com.cn/'),
('sohu',        '搜狐号',       'https://mp.sohu.com/'),
('netease',     '网易号',       'https://dy.163.com/'),
('dayuhao',     '阿里大鱼号',   'https://mp.dayu.com/');

-- =====================================================
-- 初始化数据：系统用户
-- 为了保障系统一开始就能登录，预置初始员工
-- =====================================================
INSERT INTO `sys_user` (`username`, `password`, `role`) VALUES
('admin', '123456', 'ADMIN'),
('user1', '123456', 'USER');

-- =====================================================
-- 7. 用户 AI 模型配置表
-- 每位用户独立保存自己的大模型接口信息，互不干扰
-- =====================================================
DROP TABLE IF EXISTS `ai_config`;
CREATE TABLE `ai_config` (
    `id`          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL UNIQUE          COMMENT '所属用户 ID（一用户一条）',
    `provider`    VARCHAR(50)  DEFAULT NULL             COMMENT '提供商：openai/gemini/claude/qianwen/zhipu/deepseek/ollama/custom',
    `base_url`    VARCHAR(255) DEFAULT NULL             COMMENT '接口基地址，如 https://api.openai.com/v1',
    `api_key`     VARCHAR(512) DEFAULT NULL             COMMENT 'API Key',
    `model_name`  VARCHAR(100) DEFAULT NULL             COMMENT '模型名称，如 gpt-4o',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户 AI 模型配置表';

