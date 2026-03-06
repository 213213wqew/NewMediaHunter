# -*- coding: utf-8 -*-
"""
多账号浏览器窗口排版：根据 session_dir 计算窗口大小与位置，3 列网格，最多 9 个不重叠。
"""
import os


def get_window_layout(session_dir: str):
    """
    按 session_dir 中的账号序号计算窗口位置，3 列 x 3 行网格排版。
    :param session_dir: 如 sessions/toutiao/3，末尾数字为账号 ID，用于槽位
    :return: (width, height, x, y) 供 Chromium --window-size 与 --window-position
    """
    try:
        base = os.path.basename(session_dir.rstrip(os.sep))
        slot = int(base) % 9 if base.isdigit() else abs(hash(session_dir)) % 9
    except Exception:
        slot = 0
    # 为了防止在大窗口尺寸下 (如 1024x768) 窗口超出屏幕底部
    # 我们改用“错位叠加”的排版方式，让它们都保持在顶部区域
    w, h = 1024, 768
    # 每增加一个账号，向右和向下偏移固定的像素（如 40px）
    offset = 40
    x = 50 + slot * offset
    y = 50 + slot * offset
    return w, h, x, y
