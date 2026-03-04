# -*- coding: utf-8 -*-
"""
百家号：仅负责登录流程。页面与头条不同，登录成功后可能跳转到 /builder/rc/home 或 /builder/rc/edit。
"""
import json
import sys
import os

_ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from core.browser import get_persistent_context
from platforms.baijiahao.config import PUBLISH_URL, LOGIN_WAIT_TIMEOUT_MS


def _is_baijiahao_logged_in(url: str) -> bool:
    """百家号登录成功：进入 builder/rc 下的首页或编辑页即可。"""
    return "baijiahao.baidu.com" in url and "/builder/rc/" in url and "login" not in url


def run(session_dir: str) -> dict:
    """
    仅登录：打开发布页，若在登录页则等待；成功后可能到 /builder/rc/home 或 /builder/rc/edit，任一即视为成功并取 Cookie 返回。
    """
    pw, context = get_persistent_context(session_dir, headless=False, slow_mo=500)
    page = context.new_page()
    try:
        page.goto(PUBLISH_URL, timeout=60000)
        if "login" in page.url or page.locator("text='登录'").count() > 0:
            print("DEBUG: 百家号未登录，等待用户扫码/登录...")
            try:
                # 百家号登录后可能跳转到 home 或 edit，任一即退出
                page.wait_for_function(
                    "() => document.URL.includes('baijiahao.baidu.com') && document.URL.includes('/builder/rc/') && !document.URL.includes('login')",
                    timeout=LOGIN_WAIT_TIMEOUT_MS,
                )
                print("DEBUG: 百家号登录成功，已进入创作者页")
            except Exception:
                return {"success": False, "need_login": True, "message": "登录超时"}
        page.wait_for_load_state("networkidle", timeout=30000)
        cookies = context.cookies()
        cookie_json = json.dumps(cookies, ensure_ascii=False)
        return {"success": True, "message": "登录成功", "data": {"cookieJson": cookie_json}}
    finally:
        context.close()
        pw.stop()

