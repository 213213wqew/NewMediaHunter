# -*- coding: utf-8 -*-
"""
今日头条：仅负责发布流程（占位）。
先注入 Cookie，再访问发布页；正文/视频发布逻辑可在此扩展。
"""
import sys
import os

_ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from core.inject import inject_cookies
from platforms.toutiao.config import LOGIN_URL, LOGIN_WAIT_TIMEOUT_MS


def run(params: dict, session_dir: str, cookie_json_str: str) -> dict:
    """发布流程占位：当前仅支持 BIND_LOGIN，发布逻辑待扩展。"""
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        context = p.chromium.launch_persistent_context(
            user_data_dir=session_dir,
            headless=False,
            slow_mo=300,
            args=["--start-maximized"],
        )
        page = context.new_page()
        try:
            inject_cookies(context, cookie_json_str)
            page.goto(LOGIN_URL, timeout=60000)
            if "/auth/page/login" in page.url:
                print("DEBUG: 未登录，等待用户扫码...")
                try:
                    page.wait_for_function(
                        "() => !document.URL.includes('/auth/page/login')",
                        timeout=LOGIN_WAIT_TIMEOUT_MS,
                    )
                except Exception:
                    return {"success": False, "need_login": True, "message": "登录超时"}
            return {"success": True, "message": "今日头条发布流程占位，当前仅支持 BIND_LOGIN"}
        finally:
            context.close()
