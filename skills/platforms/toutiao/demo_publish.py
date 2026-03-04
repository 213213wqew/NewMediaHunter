# -*- coding: utf-8 -*-
"""
今日头条视频发布——独立演示脚本。
运行此脚本将打开浏览器，注入 Cookie（如果有），并尝试上传演示视频。
"""
import os
import sys

# 设置路径
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
SKILLS_DIR = os.path.join(CURRENT_DIR, "..", "..")
if SKILLS_DIR not in sys.path:
    sys.path.insert(0, SKILLS_DIR)

from platforms.toutiao import publish_video

def demo():
    # 模拟参数
    params = {
        "title": "测试自动化上传视频",
        "videoPath": "uploads/08f7cd52-aa49-4843-bc9b-56700cf6c7b1.mp4", # 使用发现的视频文件
        "tags": "自动化,测试,今日头条",
        "summary": "这是一个通过 Playwright 自动化上传的演示视频。",
        "draft": True # 默认存草稿以免影响真实账号
    }
    
    # 演示用的 session 目录
    session_dir = os.path.join(SKILLS_DIR, "sessions", "toutiao", "demo_user")
    
    # 演示用的 Cookie (如果有请填入，没有则会跳转到登录页)
    cookie_json_str = "" 
    
    print(">>> 启动演示逻辑...")
    print(f">>> 视频文件: {params['videoPath']}")
    
    result = publish_video.run(params, session_dir, cookie_json_str)
    
    print(">>> 执行结果:", result)

if __name__ == "__main__":
    demo()
