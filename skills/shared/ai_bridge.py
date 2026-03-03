import urllib.request
import json
import time

def ask_ai(prompt: str, html_context: str = "", retries: int = 2) -> str:
    """
    通用的大模型求助底座 (The Host/Agent Base Bridge)
    
    此脚本无需任何第三方包（如 requests, openai 等），保证极 致的便携性。
    它的唯一作用是向宿主程序（Java 后端）发送求助请求，由宿主分配 AI 算力和验证。
    
    :param prompt: 你的问题，如 "原先的选择器失效了，请帮我在 html 中找 '发布' 按钮的新 css 选择器"
    :param html_context: 案发现场的精简 HTML 文本
    :param retries: 失败重试次数
    :return: AI 的纯文本回答（如返回的也是 JSON，需业务层自行 parse）
    """
    url = "http://127.0.0.1:28080/api/internal/ask-ai" 
    # 注意：Java应用如果跑在8080，这里端口需要一致。如果是 vue_view 的 28080 也可以走代理转发。
    # 统一起见，打向 Java 真实服务端口 28080 或 8080
    
    # 构建请求 payload
    payload = {
        "prompt": prompt,
        "htmlContext": html_context
    }
    
    data = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                result_json = response.read().decode('utf-8')
                result_dict = json.loads(result_json)
                if result_dict.get("success"):
                    return result_dict.get("answer", "")
                else:
                    print(f"[AI Bridge] 宿主返回错误: {result_dict.get('message')}")
                    
        except Exception as e:
            print(f"[AI Bridge] 向宿主请求大模型援助失败 (尝试 {attempt+1}/{retries}): {str(e)}")
            time.sleep(2)
            
    return ""
