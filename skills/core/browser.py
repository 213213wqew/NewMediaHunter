# -*- coding: utf-8 -*-
"""
仅负责：创建持久化浏览器上下文（按 session_dir 隔离，便于多账号）。
不负责登录、不负责注入、不负责存 Token。
"""
from playwright.sync_api import sync_playwright, BrowserContext


def get_persistent_context(session_dir: str, headless: bool = False, slow_mo: int = 300):
    """
    启动带持久化用户数据的 Chromium 上下文。
    :param session_dir: 浏览器 Profile 目录，建议为 sessions/{platform}/{account_id}
    :param headless: 是否无头
    :param slow_mo: 操作延迟（毫秒）
    :return: (playwright_instance, context) 调用方负责 context 与 playwright 的关闭
    """
    import os
    os.makedirs(session_dir, exist_ok=True)
    p = sync_playwright().start()
    context = p.chromium.launch_persistent_context(
        user_data_dir=session_dir,
        headless=headless,
        slow_mo=slow_mo,
        args=["--start-maximized"],
    )
    return p, context
