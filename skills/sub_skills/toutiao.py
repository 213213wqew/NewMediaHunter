# -*- coding: utf-8 -*-
"""
今日头条号 Skill：支持扫码登录绑定、发布等。
- BIND_LOGIN：打开创作者平台登录页，等待用户扫码/登录完成后提取 Cookie 返回，供账号绑定保存。
- PUBLISH：使用已保存的 Cookie 进行发文（与 baijiahao 类似流程）。
"""
import os
import sys
import json
import time
from playwright.sync_api import sync_playwright

# 可选：shared 路径（若有 ai_bridge 等）
_shared_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "shared")
if os.path.isdir(_shared_path) and _shared_path not in sys.path:
    sys.path.insert(0, _shared_path)

# 确保能导入 core 模块
_skills_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _skills_root not in sys.path:
    sys.path.insert(0, _skills_root)

from core.browser import KEEP_AWAKE_ARGS, inject_keep_awake


def execute(params, session_dir):
    """
    今日头条自动化逻辑
    params: command=BIND_LOGIN 时仅做登录并返回 cookieJson；command=PUBLISH 时发文。
    session_dir: 浏览器用户数据目录，按账号隔离，实现多账号与“下次直接使用”的持久登录。
    """
    command = params.get("command", "PUBLISH")
    login_url = "https://mp.toutiao.com/login/"
    # 登录成功后会跳转到创作首页等，不再包含 login
    success_url_pattern = "mp.toutiao.com"
    cookie_json_str = params.get("cookieJson", "")

    print(f"DEBUG: 今日头条 Skill 启动. command={command}, session_dir={session_dir}")

    with sync_playwright() as p:
        context = p.chromium.launch_persistent_context(
            user_data_dir=session_dir,
            headless=False,
            slow_mo=300,
            args=["--start-maximized"] + KEEP_AWAKE_ARGS,
        )
        page = context.new_page()
        inject_keep_awake(page)

        try:
            if command == "BIND_LOGIN":
                # 仅做扫码/登录，不发文
                return _do_bind_login(context, page, login_url)
            else:
                # PUBLISH：注入已有 Cookie 后发文（与百家号类似）
                if cookie_json_str:
                    try:
                        cookies = json.loads(cookie_json_str)
                        context.add_cookies(cookies)
                    except Exception as e:
                        print(f"DEBUG: Cookie 注入失败: {e}")
                page.goto(login_url, timeout=60000)
                # 若仍在登录页则等待用户登录
                if "login" in page.url or page.locator("text=登录").count() > 0:
                    print("DEBUG: 检测到未登录，等待用户扫码/登录 (超时 180 秒)...")
                    try:
                        page.wait_for_url("**/mp.toutiao.com/**", timeout=180000)
                        # 排除 login 子路径
                        for _ in range(180):
                            if "login" not in page.url:
                                break
                            time.sleep(1)
                        if "login" in page.url:
                            return {"success": False, "need_login": True, "message": "登录超时"}
                        print("DEBUG: 登录成功")
                    except Exception:
                        return {"success": False, "need_login": True, "message": "登录超时"}
                # TODO: 此处可扩展今日头条的 PUBLISH 正文填充、发布逻辑
                return {"success": True, "message": "今日头条发布流程占位，当前仅支持 BIND_LOGIN"}
        finally:
            context.close()


def _do_bind_login(context, page, login_url):
    """打开登录页，等待用户完成扫码/登录，提取 Cookie 并返回。"""
    page.goto(login_url, timeout=60000)
    print("DEBUG: 已打开今日头条登录页，请使用手机扫码或完成登录...")

    # 等待离开登录页（登录成功后一般会跳转到 / 或 /creator 等）
    try:
        # 最多等 3 分钟，轮询直到当前 URL 不再包含 /login
        page.wait_for_function(
            "() => !document.URL.includes('/login')",
            timeout=180000,
        )
    except Exception:
        return {"success": False, "need_login": True, "message": "等待登录超时，请重试"}

    time.sleep(2)
    cookies = context.cookies()
    if not cookies:
        return {"success": False, "message": "未获取到 Cookie，请确认已登录成功"}

    # 返回与后端 SkillExecutionResult.data 一致，供前端从 result.data.data.cookieJson 读取
    cookie_json = json.dumps(cookies, ensure_ascii=False)
    return {
        "success": True,
        "message": "登录成功，已获取 Cookie",
        "data": {"cookieJson": cookie_json},
    }
