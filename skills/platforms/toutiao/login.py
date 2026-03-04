# -*- coding: utf-8 -*-
"""
今日头条：仅负责登录流程。
打开登录页，等待用户扫码/登录，成功后取 Cookie 返回。不写 Token、不注入。
"""
import json
import sys
import os
import time

_ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from core.browser import get_persistent_context
from platforms.toutiao.config import LOGIN_URL, LOGIN_WAIT_TIMEOUT_MS


def run(session_dir: str) -> dict:
    """
    仅登录：打开登录页 https://mp.toutiao.com/auth/page/login，等待离开后返回 Cookie。
    :return: {"success": bool, "message": str, "data": {"cookieJson": str}}
    """
    pw, context = get_persistent_context(session_dir, headless=False, slow_mo=300)
    page = context.new_page()
    try:
        page.goto(LOGIN_URL, timeout=60000)
        print("DEBUG: 已打开今日头条登录页，请扫码或完成登录...")
        try:
            page.wait_for_function(
                "() => !document.URL.includes('/auth/page/login')",
                timeout=LOGIN_WAIT_TIMEOUT_MS,
            )
        except Exception:
            return {"success": False, "need_login": True, "message": "等待登录超时，请重试"}
        time.sleep(2)
        cookies = context.cookies()
        if not cookies:
            return {"success": False, "message": "未获取到 Cookie，请确认已登录成功"}
        cookie_json = json.dumps(cookies, ensure_ascii=False)
        return {"success": True, "message": "登录成功，已获取 Cookie", "data": {"cookieJson": cookie_json}}
    finally:
        context.close()
        pw.stop()
