# -*- coding: utf-8 -*-
"""
今日头条：仅负责拉取创作者昨日数据（粉丝、阅读、收益）。
打开创作者主页/数据页，注入 Cookie 后解析页面上「昨日」相关数据。
"""
import json
import re
import sys
import os
import time

_ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from core.browser import get_persistent_context, KEEP_AWAKE_ARGS, inject_keep_awake
from core.inject import inject_cookies
from platforms.toutiao.config import LOGIN_WAIT_TIMEOUT_MS

# 创作者首页（登录后通常会展示粉丝数、总阅读、累计收益及昨日数据）
DASHBOARD_URL = "https://mp.toutiao.com/"


def run(session_dir: str, cookie_json_str: str, params: dict = None) -> dict:
    """
    打开首页，注入 Cookie，解析页面数据并返回。
    params.headless=True 时后台运行不弹窗。
    """
    opts = params or {}
    headless = bool(opts.get("headless", True))
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        context = p.chromium.launch_persistent_context(
            user_data_dir=session_dir,
            headless=headless,
            slow_mo=100 if headless else 300,
            args=(["--start-maximized"] if not headless else ["--no-sandbox", "--disable-dev-shm-usage"]) + KEEP_AWAKE_ARGS,
        )
        page = context.new_page()
        try:
            inject_keep_awake(page)
            if cookie_json_str and cookie_json_str.strip():
                inject_cookies(context, cookie_json_str)
            page.goto(DASHBOARD_URL, timeout=60000)
            if "/auth/page/login" in page.url:
                return {"success": False, "need_login": True, "message": "未登录或已过期，请重新绑定"}
            time.sleep(3)
            text = page.evaluate("() => document.body.innerText") or ""
            data = _parse_yesterday_from_text(text)
            totals = _parse_totals_from_text(text)
            data.update(totals)
            return {"success": True, "message": "已获取数据", "data": data}
        except Exception as e:
            return {"success": False, "message": str(e)}
        finally:
            context.close()


def _parse_yesterday_from_text(text: str) -> dict:
    """
    按上下文解析昨日数据，避免整页按出现顺序错配。
    页面结构：粉丝数/总阅读(播放)量/累计收益 各自一块，块内有「昨日 72↑」「昨日 62,307↑」「昨日无变化」等。
    """
    data = {"yesterdayFans": 0, "yesterdayReads": 0, "yesterdayRevenue": ""}
    num_re = r"昨日\s*([\d,.]+\d*)\s*↑?"

    # 昨日粉丝：在「粉丝数」之后的片段里取第一个「昨日 X」
    for head in ("粉丝数", "粉丝"):
        i = text.find(head)
        if i >= 0:
            block = text[i : i + 180]
            m = re.search(num_re, block)
            if m:
                try:
                    data["yesterdayFans"] = int(float(m.group(1).replace(",", "")))
                except ValueError:
                    pass
            break

    # 昨日阅读：在「总阅读(播放)量」或「总阅读」之后的片段里取第一个「昨日 X」
    for head in ("总阅读(播放)量", "总阅读", "阅读量"):
        i = text.find(head)
        if i >= 0:
            block = text[i : i + 200]
            m = re.search(num_re, block)
            if m:
                try:
                    data["yesterdayReads"] = int(float(m.group(1).replace(",", "")))
                except ValueError:
                    pass
            break

    # 昨日收益：在「累计收益」之后的片段里取「昨日 X」或「昨日无变化」
    i = text.find("累计收益")
    if i >= 0:
        block = text[i : i + 120]
        if "昨日无变化" in block:
            data["yesterdayRevenue"] = "0"
        else:
            m = re.search(num_re, block)
            if m:
                data["yesterdayRevenue"] = m.group(1).replace(",", "").strip()
    return data


def _parse_totals_from_text(text: str) -> dict:
    """从页面文本中解析总粉丝数、总阅读量、累计收益。"""
    out = {"totalFans": 0, "totalReads": 0, "totalRevenue": ""}
    # 粉丝数 后面最近的一个整数
    m = re.search(r"粉丝数\s*[^\d]*?(\d+)", text)
    if m:
        try:
            out["totalFans"] = int(m.group(1))
        except ValueError:
            pass
    # 总阅读(播放)量 后面最近的一个数字（可含逗号）
    m = re.search(r"总阅读\s*[^(]*\([^)]*\)\s*量\s*[^\d]*?([\d,]+)", text) or re.search(r"总阅读[^\d]*([\d,]+)", text)
    if m:
        try:
            out["totalReads"] = int(m.group(1).replace(",", ""))
        except ValueError:
            pass
    # 累计收益 后面 x.xx元
    m = re.search(r"累计收益\s*[^\d]*?([\d.]+)\s*元", text)
    if m:
        out["totalRevenue"] = m.group(1).strip() + "元"
    return out
