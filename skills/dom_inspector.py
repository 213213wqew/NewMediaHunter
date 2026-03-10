import os
import json
import sys
import time
from playwright.sync_api import sync_playwright

_skills_root = os.path.dirname(os.path.abspath(__file__))
if _skills_root not in sys.path:
    sys.path.insert(0, _skills_root)

from core.browser import KEEP_AWAKE_ARGS

def inspect_dom():
    session_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sessions", "default_acc")
    publish_url = "https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1"
    
    with sync_playwright() as p:
        context = p.chromium.launch_persistent_context(
            user_data_dir=session_dir,
            headless=True,
            args=['--start-maximized'] + KEEP_AWAKE_ARGS
        )
        page = context.new_page()
        try:
            print("Navigating to Baijiahao...")
            page.goto(publish_url, timeout=60000)
            
            print("Waiting for network idle...")
            # wait a bit for react/vue to render
            page.wait_for_timeout(5000)
            
            print("Dumping body innerHTML...")
            html = page.inner_html("body")
            
            dump_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dom_dump.html")
            with open(dump_file, "w", encoding="utf-8") as f:
                f.write(html)
            print(f"DOM dumped to {dump_file}")
            
            page.screenshot(path=os.path.join(session_dir, "inspect_bjh_editor.png"), full_page=True)

        except Exception as e:
            print(f"Error: {e}")
        finally:
            context.close()

if __name__ == "__main__":
    inspect_dom()
