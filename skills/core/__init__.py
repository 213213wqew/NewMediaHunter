# -*- coding: utf-8 -*-
"""
与平台无关的公共能力：
- token_store: 按 平台+账号 存取 Token/Cookie 文件
- inject: 向浏览器上下文注入 Cookie
- browser: 创建持久化浏览器上下文
"""

from .token_store import load_token, save_token
from .inject import inject_cookies
from .browser import get_persistent_context

__all__ = ["load_token", "save_token", "inject_cookies", "get_persistent_context"]
