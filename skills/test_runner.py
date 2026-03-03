import subprocess
import json
import os

def test_execution():
    input_data = {
        "command": "PUBLISH",
        "platform": "baijiahao",
        "title": "真正执行验证文章",
        "htmlContent": "<p>这是自动化测试内容</p>",
        "accountId": "acc_verified_01"
    }
    
    # 路径
    master_script = 'e:\\java-Project\\新闻发布程序\\skills\\agent_master.py'
    
    print(f"Testing Master Agent: {master_script}")
    
    try:
        process = subprocess.Popen(
            ['py', master_script],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding='gbk', # Use gbk to handle Windows console output safely, or errors='replace'
            errors='replace'
        )
        stdout, stderr = process.communicate(input=json.dumps(input_data))
        
        print("\n--- MASTER AGENT STDOUT ---")
        print(stdout)
        
        if stderr:
            print("\n--- MASTER AGENT STDERR ---")
            print(stderr)
            
    except Exception as e:
        print(f"Test Execution Failed: {str(e)}")

if __name__ == "__main__":
    test_execution()
