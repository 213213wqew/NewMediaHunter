# -*- coding: utf-8 -*-
"""
百家号技能入口：只做分发。
- BIND_LOGIN -> login.py（仅登录，返回 Cookie）
- PUBLISH -> 先取 Token（params 或 token_store），再 inject，再 publish.py（仅发布）
"""
import os
import sys

_ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from core.token_store import load_token, save_token
from platforms.baijiahao import login as login_mod
from platforms.baijiahao import publish as publish_mod
from platforms.baijiahao import fetch_stats as fetch_stats_mod

PLATFORM = "baijiahao"


def execute(params: dict, session_dir: str, root: str) -> dict:
    command = params.get("command", "PUBLISH")
    account_id = str(params.get("account_id") or params.get("accountId") or "default_acc")

    if command == "BIND_LOGIN":
        out = login_mod.run(session_dir)
        if out.get("success") and out.get("data", {}).get("cookieJson"):
            save_token(root, PLATFORM, account_id, {"cookieJson": out["data"]["cookieJson"]})
        return out

    if command == "FETCH_STATS":
        cookie_json_str = params.get("cookieJson") or ""
        if not cookie_json_str:
            stored = load_token(root, PLATFORM, account_id)
            if stored and isinstance(stored, dict):
                cookie_json_str = stored.get("cookieJson") or ""
        return fetch_stats_mod.run(session_dir, cookie_json_str or "", params)

    cookie_json_str = params.get("cookieJson") or ""
    if not cookie_json_str:
        stored = load_token(root, PLATFORM, account_id)
        if stored and isinstance(stored, dict):
            cookie_json_str = stored.get("cookieJson") or ""

    # 视频发布：走独立模块（若有）；否则返回暂未实现
    if command == "PUBLISH_VIDEO":
        try:
            from platforms.baijiahao import publish_video as publish_video_mod
            return publish_video_mod.run(params, session_dir, cookie_json_str or "")
        except ImportError:
            return {"success": False, "message": "百家号视频发布功能开发中，请先使用图文发布"}

    return publish_mod.run(params, session_dir, cookie_json_str or "")
