# -*- coding: utf-8 -*-
import json
import requests
import traceback
from datetime import datetime, timedelta

def execute(params, session_dir):
    """
    符合 agent_master 标准体系的纯 API 热点抓取脚本。
    从 Java 端接收 playwrite 导出的 czgts_cookie，并发起高速抓取。
    """
    try:
        czgts_cookie = params.get("czgts_cookie", "")
        if not czgts_cookie:
            return {"success": False, "message": "Java 未传入 czgts_cookie(Playwright Persistent Context 尚未建立登录态)"}

        # ------------------- 映射查询条件 -------------------
        limit = int(params.get("limit", 50))
        offset = int(params.get("offset", 0))

        # 1. 媒体平台
        platform_str = params.get("mediaPlatform", "全部")
        if platform_str == "全部":
            platforms = [] # API 传空列表通常代表抓取全部平台
        else:
            platforms = [platform_str]

        # 2. 内容领域
        domain = params.get("domains", "")
        if domain and domain != "全部":
            categories = [d.strip() for d in domain.split(",") if d.strip()]
        else:
            categories = []

        # 3. 发布时间 (改进：增加容错和大小写支持)
        interval_str = str(params.get("publishTime", "1d")).lower()
        interval_hours = 24
        if "1h" in interval_str or "1小时" in interval_str: interval_hours = 1
        elif "3h" in interval_str or "3小时" in interval_str: interval_hours = 3
        elif "12h" in interval_str or "12小时" in interval_str: interval_hours = 12
        elif "1d" in interval_str or "1天" in interval_str: interval_hours = 24
        elif "2d" in interval_str or "2天" in interval_str: interval_hours = 48
        elif "7d" in interval_str or "7天" in interval_str: interval_hours = 168
        
        now = datetime.now()
        start_time = (now - timedelta(hours=interval_hours)).strftime("%Y-%m-%d %H:%M:%S")
        end_time = now.strftime("%Y-%m-%d %H:%M:%S")

        # 4. 内容排序
        # sortBy: 1:阅读/播放, 2:评论, 3:发布时间, 4:点赞
        sort_str = str(params.get("sort", "reads")).lower()
        sort_by = 1 
        if "time" in sort_str or "时间" in sort_str: sort_by = 3
        elif "comment" in sort_str or "评论" in sort_str: sort_by = 2
        elif "like" in sort_str or "赞" in sort_str: sort_by = 4

        # 5. 内容类型 & tab 映射
        tab_str = params.get("tab", "全网爆文")
        if "低粉" in tab_str or "popular" in tab_str:
            post_type = 3
        elif "热榜" in tab_str or "network" in tab_str:
            post_type = 1
        else:
            post_type = 2

        content_type = params.get("contentType", "全部")
        article_genres = []
        video_duration = ""
        
        if "视" in content_type:
            article_genres = ["视频"]
            video_duration = "0_600"
        elif "短图文" in content_type:
            article_genres = ["短图文"]
        elif "文章" in content_type:
            article_genres = ["文章"]
        elif "图文" in content_type:
            article_genres = ["图文"]
        else:
            article_genres = [] # 全部类型传空列表


        # 6. 关键词
        keyword = params.get("keyword", "")

        headers = {
            "Content-Type": "application/json",
            "Cookie": czgts_cookie,
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Origin": "https://www.czgts.cn",
            "Referer": "https://www.czgts.cn/v1/hots/popular"
        }

        # 构建完全匹配创作罐头 API 的 Payload 结构
        payload = {
            "limit": limit,
            "offset": offset,
            "postType": post_type,
            "platforms": platforms,
            "categories": categories,
            "sortBy": sort_by,
            "articleGenres": article_genres,
            "endTime": end_time,
            "startTime": start_time,
            "keyword": keyword,
            "searchId": "",
            "readLimits": "",
            "fansLimits": "",
            "videoDurationLimits": video_duration
        }

        # Debug print so we can see the payload in Java logs
        print(f"DEBUG: Sending Content Canned API Request with payload: {json.dumps(payload, ensure_ascii=False)}")

        url = "https://www.czgts.cn/muse/content/api/v1/hots/search?appVersion="
        response = requests.post(url, headers=headers, json=payload, timeout=15)
        response.raise_for_status()
        
        data = response.json()
        if data.get("code") == 0 or data.get("code") == 200:
            result_list = data.get("list", [])
            print(f"DEBUG: API returned {len(result_list)} items.")
            # 如果没有数据，打印一下请求的时间范围方便排查
            if not result_list:
                print(f"DEBUG: No items found between {start_time} and {end_time}")
            return {"success": True, "data": result_list}
        else:
            msg = data.get('msg', data.get('message', '未知错误'))
            print(f"DEBUG: API Error: {msg}")
            return {"success": False, "message": f"创作罐头API错误: {msg} 代码: {data.get('code')}"}

    except Exception as e:
        return {"success": False, "message": f"API 抓取异常: {str(e)}", "trace": traceback.format_exc()}

