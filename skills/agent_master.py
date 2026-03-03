import sys
import json
import os
import importlib

# 动态添加 sub_skills 路径
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
SUB_SKILLS_DIR = os.path.join(CURRENT_DIR, "sub_skills")
if SUB_SKILLS_DIR not in sys.path:
    sys.path.insert(0, SUB_SKILLS_DIR)

print(f"DEBUG: sys.path = {sys.path}")
print(f"DEBUG: sub_skills content = {os.listdir(SUB_SKILLS_DIR)}")

def run_master():
    """
    Master Agent 入口：解析指令并分发给对应的 Sub-Skill
    """
    try:
        # 强制标准输入使用 UTF-8 解码，防止从 Java 接收 JSON 时出现中文乱码 (GBK -> UTF-8)
        sys.stdin.reconfigure(encoding='utf-8')
        input_data = sys.stdin.read()
        if not input_data:
            print(json.dumps({"success": False, "message": "No input data"}))
            return

        params = json.loads(input_data)
        command = params.get("command", "PUBLISH")
        platform = params.get("platform")
        # 兼容处理 account_id 或 accountId，并强制转为字符串以防路径拼接错误
        account_id = str(params.get("account_id", params.get("accountId", "default_acc")))
        
        # 确定 Session 路径 (Skill 内部持久化)
        session_dir = os.path.join(CURRENT_DIR, "sessions", account_id)
        if not os.path.exists(session_dir):
            os.makedirs(session_dir)

        # 确保输出编码为 UTF-8
        sys.stdout.reconfigure(encoding='utf-8')
        
        print(f"DEBUG: Master Agent 启动. 指令: {command}, 平台: {platform}, Session: {session_dir}")

        # 动态加载分项技能逻辑
        try:
            sub_skill = importlib.import_module(platform)
            # 执行分项逻辑
            print(f"DEBUG: 正在调用子技能: {platform}.execute()")
            result = sub_skill.execute(params, session_dir)
            # 必须保证 JSON 是最后一行输出，不能在它之后 print 其他内容
            print(json.dumps(result, ensure_ascii=False))
        except ImportError:
            print(json.dumps({"success": False, "message": f"未找到平台技能逻辑: {platform}"}))
        except Exception as e:
            print(json.dumps({"success": False, "message": f"执行异常: {str(e)}"}))

    except Exception as e:
        print(json.dumps({"success": False, "message": f"Master 解析错误: {str(e)}"}))

if __name__ == "__main__":
    run_master()
