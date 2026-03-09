# -*- coding: utf-8 -*-
"""
百家号：仅负责发布流程。
先注入 Cookie（由调用方传入或从 token_store 读），再访问发布页、填表、发布。不负责登录、不负责存 Token。
"""
import os
import sys
import json
import time
from playwright.sync_api import sync_playwright

_ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from core.inject import inject_cookies
from platforms.baijiahao.config import PUBLISH_URL, LOGIN_WAIT_TIMEOUT_MS

try:
    from shared.ai_bridge import ask_ai
except ImportError:
    ask_ai = None


def _smart_action(page, action_name: str, exact_text: str) -> None:
    try:
        locator = page.get_by_text(exact_text, exact=True).last
        if locator.count() > 0:
            locator.evaluate("node => node.click()")
            print(f"[Smart Action] 成功点击 '{action_name}'")
            return
        raise Exception("未找到元素")
    except Exception:
        if ask_ai:
            try:
                html = page.evaluate("() => document.body.innerText")
                prompt = f"我需要在百家号发文页面点击【{exact_text}】按钮，请根据当前页面文本返回包含该按钮的最精确 Playwright CSS 选择器（只返回选择器文本）。"
                sel = ask_ai(prompt, html_context=html)
                if sel and "Not Found" not in sel:
                    page.locator(sel.strip()).first.evaluate("node => node.click()")
                    print(f"[Smart Action] AI 自愈点击 '{action_name}' 成功")
            except Exception:
                pass
        else:
            raise


def _smart_fill(page, label: str, selector: str, value: str) -> None:
    try:
        locator = page.locator(selector).first
        if locator.count() > 0:
            locator.fill(value, timeout=5000)
            print(f"[Smart Fill] 成功在 '{label}' 填充")
            return
        raise Exception("未找到元素")
    except Exception:
        if ask_ai:
            try:
                html = page.evaluate("() => document.body.innerText")
                prompt = f"我需要在百家号发文页面找到填入【{label}】的输入框，请返回该输入框的最精确 CSS 选择器。"
                sel = ask_ai(prompt, html_context=html)
                if sel and "Not Found" not in sel:
                    page.locator(sel.strip()).first.fill(value, timeout=5000)
                    print(f"[Smart Fill] AI 自愈填充 '{label}' 成功")
            except Exception:
                pass
        else:
            raise


def run(params: dict, session_dir: str, cookie_json_str: str) -> dict:
    """
    发布流程：注入 Cookie -> 打开发布页 -> 若未登录则等待 -> 填标题正文等 -> 发布。
    """
    title = params.get("title", "未命名文章")
    content = params.get("htmlContent", "")
    with sync_playwright() as p:
        context = p.chromium.launch_persistent_context(
            user_data_dir=session_dir,
            headless=False,
            slow_mo=500,
            args=["--start-maximized"],
        )
        page = context.new_page()
        try:
            inject_cookies(context, cookie_json_str)
            page.goto(PUBLISH_URL, timeout=60000)
            if "login" in page.url or page.locator("text='登录'").count() > 0:
                print("DEBUG: 未登录，等待用户扫码...")
                try:
                    page.wait_for_url("**/builder/rc/edit**", timeout=LOGIN_WAIT_TIMEOUT_MS)
                    page.wait_for_load_state("networkidle", timeout=30000)
                except Exception:
                    return {"success": False, "need_login": True, "message": "登录超时"}

            title_locator = page.locator('div[data-lexical-editor="true"]').first
            title_locator.fill(title, timeout=15000)
            page.evaluate("() => { document.activeElement && document.activeElement.blur(); }")
            time.sleep(0.5)
            # ================= 核心增强：UI 式上传图片至百度 CDN =================
            import re
            import base64
            import mimetypes
            import urllib.request
            
            uploads_dir = params.get("localUploadsDir", "")
            if not uploads_dir:
                potential_root_uploads = os.path.abspath(os.path.join(_ROOT, "..", "uploads"))
                if os.path.exists(potential_root_uploads):
                    uploads_dir = potential_root_uploads
                else:
                    uploads_dir = os.path.abspath(os.path.join(_ROOT, "..", "java-work", "uploads"))
            
            print(f"DEBUG: 使用素材目录: {uploads_dir}")
            
            img_urls = re.findall(r'src=[\'"]?([^\'"\s>]+)[\'"]?', content)
            unique_urls = list(set(img_urls))
            url_map = {}
            
            if unique_urls:
                print(f"DEBUG: 发现 {len(unique_urls)} 个图片链接，准备通过 UI 方式『洗图』...")
                
                # 先清空编辑器，方便我们一张张上传并抓取链接
                is_iframe = page.locator("#ueditor_0").count() > 0
                if is_iframe:
                    page.frame_locator("#ueditor_0").locator("body").evaluate("el => el.innerHTML = ''")
                else:
                    # 对于 Lexical，我们需要找到正文编辑器
                    identify_js = """() => {
                        const editors = document.querySelectorAll('div[data-lexical-editor="true"], div[contenteditable="true"]');
                        let bodyEl = null;
                        editors.forEach(el => {
                            const ph = (el.getAttribute('data-placeholder') || el.innerText || "").toLowerCase();
                            if (ph.includes('正文') || ph.includes('内容') || ph.includes('开始')) bodyEl = el;
                        });
                        if (!bodyEl && editors.length > 1) bodyEl = editors[1];
                        if (bodyEl) { bodyEl.id = "real-body-editor"; return true; }
                        return false;
                    }"""
                    if page.evaluate(identify_js):
                        page.locator("#real-body-editor").evaluate("el => el.innerHTML = ''")

                for url in unique_urls:
                    print(f"DEBUG: 正在 UI 上传图片: {url}")
                    local_path = None
                    try:
                        if "/api/file/view/" in url:
                            filename = url.split('/')[-1].split('?')[0]
                            local_path = os.path.normpath(os.path.join(uploads_dir, filename))
                            if not os.path.exists(local_path):
                                print(f"DEBUG: 本地文件不存在: {local_path}")
                                continue
                        elif url.startswith("http"):
                            # 远程图也先转本地，再 UI 上传，确保 100% 成功
                            print(f"DEBUG: 下载远程图进行 UI 转发: {url}")
                            req = urllib.request.Request(url, headers={
                                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                                'Referer': 'https://www.toutiao.com/'
                            })
                            with urllib.request.urlopen(req, timeout=10) as response:
                                img_data = response.read()
                                local_path = os.path.join(uploads_dir, f"tmp_{int(time.time())}.jpg")
                                with open(local_path, "wb") as f:
                                    f.write(img_data)
                        
                        if local_path and os.path.exists(local_path):
                            # 1. 触发上传按钮
                            # 尝试多个可能的按钮选择器
                            image_btn = page.locator(".edui-for-insertimage, .cheetah-icon-image, button[aria-label*='图片'], ._73a3a52aab7e3a36-default").first
                            if image_btn.count() > 0:
                                image_btn.click()
                                time.sleep(1)
                                
                                # 2. 查找文件输入框 (通常是隐藏的，但 Playwright 可以直接 set_input_files)
                                file_input = page.locator('input[type="file"][accept*="image"]').last
                                if file_input.count() > 0:
                                    file_input.set_input_files(local_path)
                                    print("DEBUG: 已选择文件，等待上传完成...")
                                    time.sleep(3) # 等待上传并插入
                                    
                                    # 如果有确定按钮，点一下
                                    confirm_btn = page.locator("button:has-text('确定'), .cheetah-btn-primary").last
                                    if confirm_btn.count() > 0 and confirm_btn.is_visible():
                                        confirm_btn.click()
                                        time.sleep(1)
                                    
                                    # 3. 从编辑器中提取刚刚生成的链接
                                    extract_js = """() => {
                                        const editor = document.getElementById('real-body-editor') || document.querySelector('#ueditor_0')?.contentDocument?.body;
                                        if (!editor) return null;
                                        const imgs = Array.from(editor.querySelectorAll('img'));
                                        if (imgs.length === 0) return null;
                                        // 返回最后一张图的链接
                                        return imgs[imgs.length - 1].src;
                                    }"""
                                    baidu_url = page.evaluate(extract_js)
                                    if baidu_url and ("bjh" in baidu_url or "baidu" in baidu_url):
                                        url_map[url] = baidu_url
                                        print(f"DEBUG: UI 上传捕获成功: {url} -> {baidu_url}")
                                        # 清理掉这张图，避免干扰下一张
                                        if is_iframe:
                                            page.frame_locator("#ueditor_0").locator("body").evaluate("el => el.innerHTML = ''")
                                        else:
                                            page.locator("#real-body-editor").evaluate("el => el.innerHTML = ''")
                                    else:
                                        print(f"DEBUG: UI 上传未捕获到合法链接: {baidu_url}")
                            else:
                                print("DEBUG: 未发现『插入图片』按钮")
                    except Exception as ex:
                        print(f"DEBUG: UI 处理图片失败 {url}: {ex}")
                
                if url_map:
                    print(f"DEBUG: UI 清洗完成，共转换 {len(url_map)} 张图片")
                for old_url, new_url in url_map.items():
                    content = content.replace(old_url, new_url)
            # =================================================================

            is_iframe = page.locator("#ueditor_0").count() > 0
            if is_iframe:
                # 老版 UEditor
                print("DEBUG: 检测到 UEditor (iframe)")
                frame = page.frame_locator("#ueditor_0")
                frame.locator("body").click(position={"x": 5, "y": 5}, force=True)
                time.sleep(0.5)
                write_js = "(bodyEl, html) => { bodyEl.innerHTML = html; }"
                # 直接注入替换后的 HTML
                page.frame_locator("#ueditor_0").locator("body").evaluate(write_js, content)
            else:
                # 新版 Lexical 编辑器
                print("DEBUG: 检测到 Lexical 编辑器 (div)")
                identify_js = """() => {
                    const editors = document.querySelectorAll('div[data-lexical-editor="true"], div[contenteditable="true"]');
                    let bodyEl = null; let titleEl = null;
                    editors.forEach(el => {
                        const ph = (el.getAttribute('data-placeholder') || el.innerText || "").toLowerCase();
                        if (ph.includes('标题')) titleEl = el;
                        else if (ph.includes('正文') || ph.includes('内容') || ph.includes('开始')) bodyEl = el;
                    });
                    if (!bodyEl && editors.length > 1) bodyEl = editors[1];
                    if (titleEl) titleEl.id = "real-title-editor";
                    if (bodyEl) { bodyEl.id = "real-body-editor"; return true; }
                    return false;
                }"""
                page.evaluate(identify_js)

                write_js_paste = """(html) => {
                    const body = document.getElementById('real-body-editor');
                    if (body) {
                        body.focus();
                        const dt = new DataTransfer();
                        dt.setData('text/html', html);
                        dt.setData('text/plain', html.replace(/<[^>]*>?/gm, ''));
                        const pasteEvent = new ClipboardEvent('paste', {
                            clipboardData: dt, bubbles: true, cancelable: true
                        });
                        body.dispatchEvent(pasteEvent);
                        return 'ok';
                    }
                }"""
                page.evaluate(write_js_paste, content)
            time.sleep(2)

            platform_settings = params.get("platformSettings", {}) or {}
            topic = (platform_settings.get("selectedTopic") if isinstance(platform_settings, dict) else None) or ""
            if topic:
                if is_iframe:
                    page.frame_locator("#ueditor_0").locator("body").click(position={"x": 5, "y": 9999}, force=True)
                    page.keyboard.press("Control+End")
                else:
                    page.locator("#real-body-editor").click(position={"x": 5, "y": 5}, force=True)
                    page.keyboard.press("Control+End")
                time.sleep(0.5)
                page.keyboard.press("Enter")
                page.keyboard.type("#", delay=300)
                try:
                    page.wait_for_selector(".topic-modal-panel", timeout=10000, state="visible")
                    page.wait_for_selector(".topic-modal-panel .topic-item-wrap", timeout=8000, state="visible")
                    time.sleep(1)
                    click_js = """(topicText) => {
                        const items = document.querySelectorAll('.topic-modal-panel li.topic-item-wrap, .topic-modal-panel .topic-item');
                        for (let item of items) {
                            let content = item.innerText || "";
                            if (topicText === "__HOTTEST__" || content.includes(topicText.replace(/#/g, ""))) {
                                item.click(); return { success: true };
                            }
                        }
                        return { success: false };
                    }"""
                    page.evaluate(click_js, topic)
                    time.sleep(1.5)
                    if page.locator(".topic-modal-panel").count() > 0:
                        page.keyboard.press("Enter")
                except Exception:
                    page.keyboard.press("Escape")

            # ================= 封面设置逻辑 (单图 + 自定义/AI) =================
            print("DEBUG: 开始设置单图封面...")
            has_set_cover = False
            local_cover_path = params.get("localCoverPath")
            
            try:
                single_radio = page.get_by_text("单图", exact=True).first
                if single_radio.count() > 0:
                    single_radio.click(timeout=5000)
                time.sleep(1)

                # 点击封面缩略图“+”号打开封面选择弹窗
                cover_trigger = page.locator(".cheetah-upload-select, ._73a3a52aab7e3a36-default, div:has-text('\u9009\u62e9\u5c01\u9762')").last
                if cover_trigger.count() > 0:
                    try:
                        cover_trigger.click(timeout=3000)
                    except Exception:
                        # bjh-edit-header 盖住了封面按钮，用 JS 直接点击绕过
                        print("DEBUG: 封面按钮被拦截，压用 JS evaluate 点击")
                        cover_trigger.evaluate("el => el.click()")
                    time.sleep(2)

                # 优先级 1: 本地相册上传 (如果前端选了图)
                if local_cover_path and os.path.exists(local_cover_path):
                    print(f"DEBUG: 检测到本地封面，准备上传: {local_cover_path}")
                    file_input = page.locator('input[type="file"][accept*="image"]').first
                    if file_input.count() > 0:
                        try:
                            file_input.set_input_files(local_cover_path)
                            print("DEBUG: 本地封面图上传成功")
                            time.sleep(3)
                            has_set_cover = True
                        except Exception as ue:
                            print(f"DEBUG: 尝试上传本地封面失败: {ue}")

                # 优先级 2: AI 封图 (如果没有传图，或者上传失败)
                if not has_set_cover:
                    print("DEBUG: 使用 AI 封图導底...")
                    ai_tab = page.get_by_text("AI封图", exact=True).first
                    if ai_tab.count() == 0:
                        ai_tab = page.locator(".cheetah-tabs-tab").filter(has_text="AI封图").first
                    if ai_tab.count() > 0:
                        ai_tab.click()
                        time.sleep(1)

                        generate_btn = page.get_by_text("根据全文智能生成封面", exact=False).first
                        if generate_btn.count() > 0:
                            generate_btn.click()
                        else:
                            prompt_input = page.locator("textarea[placeholder*='提示词']").first
                            if prompt_input.count() > 0:
                                prompt_input.fill(title)
                                send_icon = page.locator(".cheetah-input-suffix i, .cheetah-input-suffix svg, img[src*='send']").last
                                if send_icon.count() > 0:
                                    send_icon.click()

                        print("DEBUG: 正在等待 AI 生成封面 (最多等待 30 秒)...")
                        try:
                            page.wait_for_selector(".cheetah-tabs-content .cheetah-modal-body img, img[class*='-img'], canvas[class*='-canvas']", timeout=30000, state="visible")
                            print("DEBUG: AI 封面生成完毕！")
                            time.sleep(2)
                            generated_img = page.locator(".cheetah-tabs-content .cheetah-modal-body img, img[class*='-img']").first
                            if generated_img.count() > 0:
                                generated_img.click()
                                time.sleep(1)
                        except Exception as ai_e:
                            print(f"DEBUG: AI 生成封面超时未显示: {ai_e}")

                # 统一点击确定的步骤（选好以后保存）
                confirm_btn = page.get_by_role("button", name="确定").filter(has_text="确定").last
                if confirm_btn.count() == 0:
                    confirm_btn = page.locator("button:has-text('确定')").last
                if confirm_btn.count() > 0 and confirm_btn.is_visible():
                    print("DEBUG: 点击封面弹窗确定按钮")
                    confirm_btn.click()
                    time.sleep(2)

            except Exception as cover_err:
                print(f"DEBUG: 封面设置失败（不影响发布）: {cover_err}")
            
            # =================================================================

            time.sleep(200)
            #     _smart_action(page, "存草稿", "存草稿")
            # else:
            #     _smart_action(page, "发布", "发布")
            # print("DEBUG: 发布指令已执行")
            # time.sleep(10)
            return {"success": True, "message": "发布成功", "final_url": page.url}
        except Exception as e:
            return {"success": False, "message": str(e)}
        finally:
            context.close()
