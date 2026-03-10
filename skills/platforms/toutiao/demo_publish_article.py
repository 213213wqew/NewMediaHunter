# -*- coding: utf-8 -*-
"""
今日头条图文发布——独立演示脚本。
"""
import os
import sys

# 设置路径
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
SKILLS_DIR = os.path.normpath(os.path.join(CURRENT_DIR, "..", ".."))
if SKILLS_DIR not in sys.path:
    sys.path.insert(0, SKILLS_DIR)

from platforms.toutiao import publish

def demo():
    # 模拟参数
    params = {
        "title": "测试自动化上传图文 " + str(int(os.time() if hasattr(os, "time") else 123456)),
        "htmlContent": "<h1>测试正文</h1><p>这是一个通过 Playwright 自动化上传的演示图文。</p><p><img src='https://p3-toutiao.byteimg.com/img/pgc-image/7b0f16f861534b868e8e8ce168705f4c~tplv-tt-cs0:640:360.jpg' /></p>",
        "tags": "自动化,测试,今日头条",
        "summary": "图文发布自动化测试摘要",
        "isDraft": True,
        "platformSettings": {
            "toutiao": {
                "coverType": "none",
                "category": "科技",
                "tags": "自动化测试,今日头条,Playwright",
                "workStatements": ["个人观点，仅供参考", "引用AI"]
            }
        }
    }
    
    # 演示用的 session 目录 (使用临时或现有的)
    session_dir = os.path.join(SKILLS_DIR, "sessions", "toutiao", "1")
    
    # 演示用的 Cookie (留空则尝试使用 session 里的)
    cookie_json_str = "" 
    
    print(">>> 启动图文发布演示逻辑...")
    result = publish.run(params, session_dir, cookie_json_str)
    
    print(">>> 执行结果:", result)

if __name__ == "__main__":
    demo()
