# -*- coding: utf-8 -*-
"""百家号：登录/发布/首页 URL、超时等配置。与头条页面结构不同，单独配置。"""

PUBLISH_URL = "https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1"
# 视频创作页（第一步上传，第二步选择封面）
VIDEO_EDIT_URL = "https://baijiahao.baidu.com/builder/rc/edit?type=videoV2&is_from_cms=1"
# 创作者首页（数据总览：累计投稿、总粉丝量、累计总收益等）
HOME_URL = "https://baijiahao.baidu.com/builder/rc/home"
LOGIN_WAIT_TIMEOUT_MS = 180000
