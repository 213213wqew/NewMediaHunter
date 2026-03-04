# -*- coding: utf-8 -*-
"""
仅负责：按「平台 + 账号」读写 Token/Cookie 文件。
路径约定：{root}/tokens/{platform}/{account_id}.json
不处理登录、不处理注入，只做文件读写。
"""
import os
import json


def _dir(root: str, platform: str, account_id: str) -> str:
    d = os.path.join(root, "tokens", platform)
    os.makedirs(d, exist_ok=True)
    return d


def _path(root: str, platform: str, account_id: str) -> str:
    return os.path.join(_dir(root, platform, account_id), f"{account_id}.json")


def load_token(root: str, platform: str, account_id: str) -> dict | None:
    """读取 tokens/{platform}/{account_id}.json，返回 dict；不存在或无效则返回 None。"""
    p = _path(root, platform, account_id)
    if not os.path.isfile(p):
        return None
    try:
        with open(p, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None


def save_token(root: str, platform: str, account_id: str, data: dict) -> None:
    """写入 tokens/{platform}/{account_id}.json。data 通常含 cookieJson 等。"""
    _dir(root, platform, account_id)
    p = _path(root, platform, account_id)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=0)
