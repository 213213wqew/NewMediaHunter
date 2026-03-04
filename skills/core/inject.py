# -*- coding: utf-8 -*-
"""
仅负责：把已有 Cookie（JSON 字符串或 list）注入到 Playwright 的 browser context。
不负责登录、不负责存 Token，只做注入。
"""
import json


def inject_cookies(context, cookie_json_str: str) -> bool:
    """
    向 context 注入 Cookie。
    :param context: Playwright 的 BrowserContext
    :param cookie_json_str: Cookie 列表的 JSON 字符串（与 context.add_cookies 格式一致）
    :return: 是否注入成功
    """
    if not cookie_json_str or not cookie_json_str.strip():
        return True
    try:
        cookies = json.loads(cookie_json_str)
        if isinstance(cookies, list):
            context.add_cookies(cookies)
        else:
            context.add_cookies([cookies])
        return True
    except Exception:
        return False
