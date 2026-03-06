# -*- coding: utf-8 -*-
"""
统一入口：解析 stdin JSON，按 platform + account_id 确定 session_dir，分发给 platforms/{platform} 或兼容 sub_skills。
- sessions 与 tokens 按「平台 + 账号」划分：sessions/{platform}/{account_id}、tokens/{platform}/{account_id}.json
"""
import sys
import json
import os
import importlib

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
if CURRENT_DIR not in sys.path:
    sys.path.insert(0, CURRENT_DIR)

SUB_SKILLS_DIR = os.path.join(CURRENT_DIR, "sub_skills")
if SUB_SKILLS_DIR not in sys.path:
    sys.path.insert(0, SUB_SKILLS_DIR)


def run_master():
    try:
        sys.stdin.reconfigure(encoding="utf-8")
        input_data = sys.stdin.read()
        if not input_data:
            print(json.dumps({"success": False, "message": "No input data"}))
            return

        params = json.loads(input_data)
        command = params.get("command", "PUBLISH")
        platform = params.get("platform")
        account_id = str(params.get("account_id") or params.get("accountId") or "default_acc")

        # 按「平台 + 账号」分目录，便于多账号切换与维护
        session_dir = os.path.join(CURRENT_DIR, "sessions", platform or "default", account_id)
        os.makedirs(session_dir, exist_ok=True)

        sys.stdout.reconfigure(encoding="utf-8")
        print(f"DEBUG: Master 启动. command={command}, platform={platform}, account_id={account_id}, session_dir={session_dir}")

        result = None
        # 优先使用 platforms/{platform}（登录/存 Token/注入 已拆分）
        if platform:
            try:
                mod = importlib.import_module("platforms." + platform)
                if hasattr(mod, "execute"):
                    result = mod.execute(params, session_dir, CURRENT_DIR)
                    print(json.dumps(result, ensure_ascii=False))
                    return
            except ImportError:
                pass
        # 兼容：仍支持 sub_skills/{platform}.py（仅 execute(params, session_dir)）
        try:
            sub_skill = importlib.import_module(platform or "baijiahao")
            result = sub_skill.execute(params, session_dir)
            print(json.dumps(result, ensure_ascii=False))
        except ImportError:
            print(json.dumps({"success": False, "message": f"未找到平台技能: {platform}"}))
        except Exception as e:
            msg = str(e)
            if "Locked" in msg or "lock" in msg.lower():
                msg = f"子技能运行锁死（可能是其他实例正在占用该账号）: {msg}"
            else:
                msg = f"子技能执行异常: {msg}"
            print(json.dumps({"success": False, "message": msg}))

    except Exception as e:
        print(json.dumps({"success": False, "message": f"Master 入口解析错误 (stdin 可能非 JSON): {str(e)}"}))


if __name__ == "__main__":
    run_master()
