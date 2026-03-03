import sys
import os
import json

# 模拟 agent_master.py 的环境
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
SUB_SKILLS_DIR = os.path.join(CURRENT_DIR, "sub_skills")
if SUB_SKILLS_DIR not in sys.path:
    sys.path.insert(0, SUB_SKILLS_DIR)

print(f"Checking path: {SUB_SKILLS_DIR}")
print(f"Files in sub_skills: {os.listdir(SUB_SKILLS_DIR)}")

try:
    import baijiahao
    print("SUCCESS: baijiahao module imported successfully!")
    
    # 模拟执行
    params = {"title": "Test", "htmlContent": "Body"}
    session_dir = os.path.join(CURRENT_DIR, "sessions", "test_acc")
    
    # 注意：这里不真正运行 execute 因为它会启动浏览器，我们只检查函数是否存在
    if hasattr(baijiahao, 'execute'):
        print("SUCCESS: baijiahao.execute function found!")
    else:
        print("ERROR: baijiahao.execute function NOT found!")

except ImportError as e:
    print(f"FAILED: Could not import baijiahao. Error: {str(e)}")
except Exception as e:
    print(f"FAILED: Unexpected error: {str(e)}")
