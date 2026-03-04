# -*- coding: utf-8 -*-
"""
百家号：拉取创作者数据。页面与头条不同，使用 builder/rc/home 数据总览（累计投稿、总粉丝量、累计总收益等）。
"""
import re
import sys
import os
import time

_ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from core.inject import inject_cookies
from platforms.baijiahao.config import HOME_URL


def _parse_wan(s: str) -> int:
    """解析 5.48万 或 54800 为整数。"""
    s = (s or "").replace(",", "").strip()
    if not s:
        return 0
    if "万" in s:
        try:
            return int(float(s.replace("万", "").strip()) * 10000)
        except ValueError:
            return 0
    try:
        return int(float(s))
    except ValueError:
        return 0


def run(session_dir: str, cookie_json_str: str, params: dict = None) -> dict:
    opts = params or {}
    headless = bool(opts.get("headless", True))
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        context = p.chromium.launch_persistent_context(
            user_data_dir=session_dir,
            headless=headless,
            slow_mo=100 if headless else 300,
            args=["--start-maximized"] if not headless else ["--no-sandbox", "--disable-dev-shm-usage"],
        )
        page = context.new_page()
        try:
            if cookie_json_str and cookie_json_str.strip():
                inject_cookies(context, cookie_json_str)
            page.goto(HOME_URL, timeout=60000)
            if "login" in page.url or ("baijiahao.baidu.com" in page.url and "/builder/rc/" not in page.url):
                return {"success": False, "need_login": True, "message": "未登录或已过期，请重新绑定"}
            time.sleep(3)
            text = page.evaluate("() => document.body.innerText") or ""
            data = _parse_yesterday_from_text(text)
            data.update(_parse_totals_from_text(text))
            return {"success": True, "message": "已获取数据", "data": data}
        except Exception as e:
            return {"success": False, "message": str(e)}
        finally:
            context.close()


def _parse_yesterday_from_text(text: str) -> dict:
    """百家号昨日多为「昨日 +21」格式。"""
    data = {"yesterdayFans": 0, "yesterdayReads": 0, "yesterdayRevenue": ""}
    # 昨日 +21 或 昨日 4↑ 或 昨日 +5.37万
    yesterday_match = re.findall(r"昨日\s*\+?\s*([\d,.]+\d*)\s*万?\s*↑?", text)
    if len(yesterday_match) >= 1:
        try:
            data["yesterdayFans"] = int(float(yesterday_match[0].replace(",", "")))
        except ValueError:
            pass
    if len(yesterday_match) >= 2:
        data["yesterdayReads"] = _parse_wan(yesterday_match[1])
    if len(yesterday_match) >= 3:
        try:
            data["yesterdayRevenue"] = yesterday_match[2].replace(",", "").strip()
        except IndexError:
            pass
    return data


def _parse_totals_from_text(text: str) -> dict:
    """百家号数据总览：总粉丝量、累计阅读(播放)量、累计总收益。"""
    out = {"totalFans": 0, "totalReads": 0, "totalRevenue": ""}
    m = re.search(r"总粉丝量\s*[^\d]*?([\d,.\d万]+)", text) or re.search(r"粉丝数\s*[^\d]*?(\d+)", text)
    if m:
        try:
            out["totalFans"] = _parse_wan(m.group(1))
        except (ValueError, IndexError):
            pass
    m = re.search(r"累计阅读\s*[^(]*\([^)]*\)\s*量\s*[^\d]*?([\d,.\d万]+)", text) or re.search(r"累计阅读[^\d]*([\d,.\d万]+)", text)
    if m:
        out["totalReads"] = _parse_wan(m.group(1))
    m = re.search(r"累计总收益\s*[^\d]*?([\d.]+)", text) or re.search(r"累计收益\s*[^\d]*?([\d.]+)\s*元?", text)
    if m:
        out["totalRevenue"] = m.group(1).strip() + "元"
    return out
