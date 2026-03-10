# -*- coding: utf-8 -*-
"""
仅负责：创建持久化浏览器上下文（按 session_dir 隔离，便于多账号）。
不负责登录、不负责注入、不负责存 Token。
"""
from playwright.sync_api import sync_playwright, BrowserContext, Page

# ========== 浏览器常驻唤醒参数 ==========
# 解决多窗口并行时：浏览器被遮挡 → Chrome 判定为 hidden → 平台禁用按钮/暂停上传
KEEP_AWAKE_ARGS = [
    "--disable-features=CalculateNativeWinOcclusion",  # 禁用 Windows 遮挡检测（关键！）
    "--disable-renderer-backgrounding",                 # 禁止渲染进程进入后台
    "--disable-background-timer-throttling",            # 禁止后台定时器节流
    "--disable-backgrounding-occluded-windows",         # 禁止被遮挡窗口降级
]

# 注入到每个页面的 JS：劫持 Visibility API，让平台网站永远认为页面可见
_KEEP_AWAKE_JS = """
Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
document.dispatchEvent(new Event('visibilitychange'));
"""


def inject_keep_awake(page: Page):
    """在页面中注入 visibilityState 劫持脚本，确保平台 JS 永远认为页面可见。"""
    try:
        page.evaluate(_KEEP_AWAKE_JS)
    except Exception:
        pass  # 页面尚未就绪时忽略


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
        args=["--start-maximized"] + KEEP_AWAKE_ARGS,
    )
    # 为所有已有和新建的页面自动注入唤醒脚本
    for pg in context.pages:
        inject_keep_awake(pg)
    context.on("page", inject_keep_awake)
    return p, context

