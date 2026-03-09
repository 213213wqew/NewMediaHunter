import os
import sys
import json
import time
from playwright.sync_api import sync_playwright

# 确保能正常导入 shared.ai_bridge
_shared_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "shared")
if _shared_path not in sys.path:
    sys.path.insert(0, _shared_path)
from ai_bridge import ask_ai

def smart_action(page, action_name, exact_text):
    """带 AI 自愈能力的点击动作"""
    try:
        locator = page.get_by_text(exact_text, exact=True).last
        if locator.count() > 0:
            locator.evaluate("node => node.click()")
            print(f"[Smart Action] 成功点击 '{action_name}'")
            return
        else:
            raise Exception("常规选择器未找到元素")
    except Exception as e:
        print(f"[Smart Action] '{action_name}' 常规点击失败，触发 AI自愈中...")
        try:
            html = page.evaluate("() => document.body.innerText")
            prompt = f"我需要在百家号发文页面点击【{exact_text}】按钮，但原本的方法失效了。请根据当前页面文本给我返回包含该按钮的最精确的 Playwright CSS选择器（只需返回文本，例如 'button.cheetah-btn'）。"
            new_selector = ask_ai(prompt, html_context=html)
            
            if new_selector and "Not Found" not in new_selector:
                print(f"[Smart Action] AI 提供最新选择器: {new_selector}，正在尝试执行...")
                page.locator(new_selector.strip()).first.evaluate("node => node.click()")
                print(f"[Smart Action] AI 自愈执行 '{action_name}' 成功！")
            else:
                print(f"[Smart Action] AI 暂时也无法在当前页面定位到 '{action_name}'。")
        except Exception as ai_e:
            print(f"[Smart Action] AI 自愈流程也发生异常: {ai_e}")

def smart_fill(page, label, selector, value):
    """带 AI 自愈能力的输入框填充动作"""
    try:
        locator = page.locator(selector).first
        if locator.count() > 0:
            locator.fill(value, timeout=5000)
            print(f"[Smart Fill] 成功在 '{label}' 填充了值")
            return
        else:
            raise Exception("常规选择器未找到元素")
    except Exception:
        print(f"[Smart Fill] '{label}' 常规填充失败，触发 AI自愈中...")
        try:
            html = page.evaluate("() => document.body.innerText")
            prompt = f"我需要在百家号发文页面找到填入【{label}】的输入框。之前的选择器是 {selector}。请给我返回该输入框的最精确 CSS 选择器。"
            new_selector = ask_ai(prompt, html_context=html)
            if new_selector and "Not Found" not in new_selector:
                page.locator(new_selector.strip()).first.fill(value, timeout=5000)
                print(f"[Smart Fill] AI 自愈填充 '{label}' 成功！")
        except Exception as ai_e:
            print(f"[Smart Fill] AI 自愈流程发生异常: {ai_e}")

def execute(params, session_dir):
    """
    百家号自动化逻辑 - 真实执行版本
    params: {title, htmlContent, category, ...}
    session_dir: 浏览器用户数据目录 (持久化 Session)
    """
    title = params.get("title", "未命名文章")
    content = params.get("htmlContent", "")
    platform_settings = params.get("platformSettings", {})
    publish_type = platform_settings.get("publishType", "news")
    
    if publish_type == "dynamic":
        publish_url = "https://baijiahao.baidu.com/builder/rc/edit?type=dynamic&is_from_cms=1"
    else:
        publish_url = "https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1"
        
    cookie_json_str = params.get("cookieJson", "")

    print(f"DEBUG: 启动浏览器, Session 路径: {session_dir}")
    
    with sync_playwright() as p:
        # 使用持久化上下文 (核心：保持登录状态)
        context = p.chromium.launch_persistent_context(
            user_data_dir=session_dir,
            headless=False,
            slow_mo=500,
            args=['--start-maximized']
        )
        
        page = context.new_page()
        try:
            print(f"DEBUG: 访问发布页面: {publish_url}")
            
            # 1. 注入 Cookie
            if cookie_json_str:
                try:
                    cookies = json.loads(cookie_json_str)
                    context.add_cookies(cookies)
                except Exception as ce:
                    print(f"DEBUG: 警告: Cookie 注入失败: {ce}")

            page.goto(publish_url, timeout=60000)
            
            # 2. 检测登录
            if "login" in page.url or page.locator("text='登录'").count() > 0:
                print("DEBUG: 检测到未登录状态。正在等待用户扫码登录 (超时 180 秒)...")
                try:
                    page.wait_for_url("**/builder/rc/edit**", timeout=180000)
                    print("DEBUG: 登录成功！")
                    page.wait_for_load_state("networkidle", timeout=30000)
                except Exception:
                    return {"success": False, "need_login": True, "message": "登录超时"}

            # 3. 填充标题
            try:
                title_locator = page.locator('div[data-lexical-editor="true"]').first
                title_locator.fill(title, timeout=15000)
                # 填充完立即失去焦点，防止之后的键盘模拟误入标题
                page.evaluate("() => { document.activeElement && document.activeElement.blur(); }")
                time.sleep(0.5)
            except Exception as e:
                print(f"DEBUG: 标题填充有误或已跳过: {e}")
            
            # 4. 定位与填充 (内容先行，文末收尾话题)
            try:
                print("DEBUG: 正在智能识别正文编辑器类型...")
                # 4.1 编辑器嗅探
                is_iframe = page.locator('#ueditor_0').count() > 0
                editor_found = False
                
                if is_iframe:
                    print("DEBUG: 命中 Iframe 编辑器模式 (UEditor)")
                    frame = page.frame_locator('#ueditor_0')
                    frame.locator('body').click(position={"x": 5, "y": 5}, force=True)
                    time.sleep(0.5)
                    editor_found = True
                else:
                    print("DEBUG: 尝试 Div 编辑器模式 (Lexical)")
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
                    editor_found = page.evaluate(identify_js)

                # 4.2 填充标题
                if not is_iframe:
                    title_loc = page.locator("#real-title-editor")
                    if title_loc.count() > 0:
                        print(f"DEBUG: 填充标题: {title}")
                        title_loc.fill(title)
                        page.evaluate("document.getElementById('real-title-editor').blur()")
                        time.sleep(0.5)

                # 4.3 写入正文 (先行回填)
                print("DEBUG: 正在注入正文 HTML...")
                if is_iframe:
                    # 如果使用 locator().evaluate，第一个参数是元素本身，第二个才是传入的参数
                    write_js = "(bodyEl, html) => { bodyEl.innerHTML = html; }"
                    page.frame_locator('#ueditor_0').locator('body').evaluate(write_js, content)
                else:
                    write_js = """(html) => {
                        const body = document.getElementById('real-body-editor');
                        if (body) {
                            body.focus();
                            document.execCommand('selectAll', false, null);
                            document.execCommand('insertHTML', false, html);
                            return 'ok';
                        }
                    }"""
                    page.evaluate(write_js, content)
                time.sleep(1)

                # 4.4 话题注入 (在文末收尾)
                platform_settings = params.get("platformSettings", {})
                topic = platform_settings.get("selectedTopic")
                if topic:
                    print(f"DEBUG: 开启[文末话题]逻辑: {topic}")
                    # 强力定位到文末
                    if is_iframe:
                        page.frame_locator('#ueditor_0').locator('body').click(position={"x": 5, "y": 9999}, force=True)
                        page.keyboard.press("Control+End")
                    else:
                        body_loc = page.locator("#real-body-editor")
                        body_loc.click(position={"x": 5, "y": 5}, force=True) # 先点下
                        page.keyboard.press("Control+End")
                    
                    time.sleep(0.5)
                    page.keyboard.press("Enter") # 换行再打话题
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
                                    item.dispatchEvent(new MouseEvent('mousedown', {bubbles: true}));
                                    item.click();
                                    item.dispatchEvent(new MouseEvent('mouseup', {bubbles: true}));
                                    return { success: true, text: content };
                                }
                            }
                            return { success: false };
                        }"""
                        res = page.evaluate(click_js, topic)
                        if not res.get("success"): page.keyboard.press("Enter")
                        
                        time.sleep(1.5)
                        if page.locator(".topic-modal-panel").count() > 0: page.keyboard.press("Enter")
                        time.sleep(1)
                    except Exception as te:
                        print(f"DEBUG: 话题选择阶段异常: {te}")
                        page.keyboard.press("Escape")

            except Exception as ce:
                raise Exception(f"内容/话题注入环节深度故障: {ce}")

            # 5. 设置封面
            if publish_type != "dynamic":
                print("DEBUG: 开始设置封面...")
                # 如果出现“移动话题到文末”的提示框，点击确定
            def handle_move_modal():
                try:
                    move_modal_btn = page.get_by_role("button", name="确定").last
                    if move_modal_btn.count() > 0 and "移至正文末尾" in page.content():
                        print("DEBUG: 检测到‘移动话题至文末’提示，自动点击确定")
                        move_modal_btn.click(timeout=3000)
                        time.sleep(1)
                except: pass

            handle_move_modal()

            # 5. 设置封面与话题
            print("DEBUG: 开始设置封面/话题...")
            try:
                # 5.1 点击单图
                single_radio = page.get_by_text("单图", exact=True).first
                if single_radio.count() > 0:
                    single_radio.click(timeout=5000)
                    time.sleep(0.5)

                has_set_cover = False
                
                # 优先级 1: 使用本地物理路径直接上传封面 (最稳定)
                local_cover_path = params.get("localCoverPath")
                if local_cover_path and os.path.exists(local_cover_path):
                    print(f"DEBUG: 检测到本地封面路径，准备上传: {local_cover_path}")
                    # 寻找上传按钮：可能是 .upload-btn 或包含了“上传图片”文字的元素
                    upload_target = page.locator(".upload-btn, .web-uploader-container, .bjh-upload-wrapper").first
                    if upload_target.count() > 0:
                        try:
                            with page.expect_file_chooser() as fc_info:
                                upload_target.click(timeout=5000)
                            file_chooser = fc_info.value
                            file_chooser.set_files(local_cover_path)
                            print("DEBUG: 本地封面图上传指令已发送")
                            time.sleep(3) # 等待上传处理
                            has_set_cover = True
                        except Exception as ue:
                            print(f"DEBUG: 尝试上传本地封面失败: {ue}")
                
                # 优先级 2: 提取正文图 (兜底)
                if not has_set_cover:
                    extract_span = page.get_by_text("提取正文图", exact=True).first
                    if extract_span.count() > 0:
                        extract_span.click(timeout=3000)
                        print("DEBUG: 已尝试点击 '提取正文图'")
                        time.sleep(2)
                        
                        # 检查是否有提取成功的确认按钮
                        confirm_btn = page.locator(".extract-modal-confirm, button:has-text('确定')").last
                        if confirm_btn.count() > 0:
                            confirm_btn.click(timeout=3000)
                            print("DEBUG: 已点击提取确认按钮")
                            time.sleep(1)
                        
                        has_set_cover = True
                
                # 5.3 封面兜底：AI 封面状态机
                if not has_set_cover:
                    print("DEBUG: 所有设置尝试均未成功，启动 AI 封面状态机...")
                    js_script = """(title) => {
                      return new Promise((resolve) => {
                        let step = 0; let attempts = 0;
                        const interval = setInterval(() => {
                          try {
                            attempts++;
                            if (step === 0) {
                               var allDivs = document.querySelectorAll('div');
                               var coverBtn = null;
                               for (let i = 0; i < allDivs.length; i++) {
                                 if (allDivs[i].textContent.trim() === '选择封面' && allDivs[i].className.includes('-content')) {
                                   coverBtn = allDivs[i]; break;
                                 }
                               }
                               if (coverBtn) { coverBtn.click(); step = 1; attempts = 0; }
                               else if (attempts > 10) { clearInterval(interval); resolve('cover_btn_not_found'); }
                            } else if (step === 1) {
                               var aiTab = document.querySelector('div.cheetah-tabs-tab[data-node-key="ai"]');
                               if (aiTab) { aiTab.click(); step = 2; attempts = 0; }
                               else if (attempts > 10) { clearInterval(interval); resolve('ai_tab_not_found'); }
                            } else if (step === 2) {
                               var modal = document.querySelector('.cheetah-modal');
                               if (!modal) { if(attempts > 5) { clearInterval(interval); resolve('modal_not_found'); } return; }
                               var promptInput = modal.querySelector('textarea#content') || modal.querySelector('textarea');
                               if(promptInput) {
                                   promptInput.value = title;
                                   var event = document.createEvent('HTMLEvents');
                                   event.initEvent('input', true, true);
                                   promptInput.dispatchEvent(event);
                                   var sendIcon = modal.querySelector('div[class*="-iconWrap"] img') || modal.querySelector('div[class*="-iconWrap"]');
                                   if(sendIcon) {
                                       setTimeout(() => sendIcon.click(), 500); step = 3; attempts = 0;
                                   } else {
                                       var genBtns = modal.querySelectorAll('span');
                                       for(let i=0; i<genBtns.length; i++) {
                                           if(genBtns[i].textContent.trim().includes('根据全文智能生成封面')) {
                                               setTimeout(() => genBtns[i].click(), 500);
                                               step = 3; attempts = 0; break;
                                           }
                                       }
                                   }
                               }
                            } else if (step === 3) {
                               var modal = document.querySelector('.cheetah-modal');
                               if (!modal) return;
                               var confirmBtn = null;
                               var btns = modal.querySelectorAll('button');
                               for(var i=0; i<btns.length; i++) {
                                   if(btns[i].textContent.trim().includes('确定')) { confirmBtn = btns[i]; break; }
                               }
                               if (confirmBtn) {
                                   if (confirmBtn.textContent.includes('(1)')) { step = 4; attempts = 0; }
                                   else if (attempts > 5 && attempts % 10 === 0) {
                                       var ims = modal.querySelectorAll('img[class*="-img"], canvas[class*="-canvas"]');
                                       if (ims.length > 0) ims[0].click();
                                   }
                               }
                               if (attempts > 120) { clearInterval(interval); resolve('timeout_images'); }
                            } else if (step === 4) {
                               if (attempts === 2) {
                                   var modal = document.querySelector('.cheetah-modal');
                                   if (modal) {
                                       var confirmBtn2 = null;
                                       var btns2 = modal.querySelectorAll('button');
                                       for(var j=0; j<btns2.length; j++) {
                                           if(btns2[j].textContent.trim().includes('确定 (1)')) { confirmBtn2 = btns2[j]; break; }
                                       }
                                       if (confirmBtn2) {
                                           var rect = confirmBtn2.getBoundingClientRect();
                                           var clickEvent = new MouseEvent('click', { bubbles: true, cancelable: true, view: window, clientX: rect.left + rect.width / 2, clientY: rect.top + rect.height / 2 });
                                           confirmBtn2.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
                                           confirmBtn2.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
                                           confirmBtn2.dispatchEvent(clickEvent);
                                       }
                                   }
                                   step = 5; attempts = 0;
                               }
                            } else if (step === 5) {
                               var modal = document.querySelector('.cheetah-modal');
                               if (!modal || modal.style.display === 'none' || attempts > 10) { clearInterval(interval); resolve('cover_selected'); }
                            }
                          } catch(e) { clearInterval(interval); resolve('error: ' + e.message); }
                        }, 1000);
                      });
                    }"""
                    page.evaluate(js_script, title)

                # 5.4 原生话题设置 (已迁移至正文仿真交互，此处保留日志说明)
                print("DEBUG: 底部原生话题设置已跳过，改用正文仿真交互")

            except Exception as e:
                print(f"DEBUG: 封面环节异常: {e}")
                if "cover" in str(e).lower(): raise e

            time.sleep(3)
            # 6. 发布
            is_draft = params.get("draft", False)
            if is_draft:
                smart_action(page, "存草稿", "存草稿")
            else:
                smart_action(page, "发布", "发布")

            print("DEBUG: 发布指令已执行，正在留存页面 10 秒供观察...")
            time.sleep(10)
            return {"success": True, "message": "发布成功", "final_url": page.url}

        except Exception as e:
            print(f"DEBUG: 发生异常: {e}")
            return {"success": False, "message": f"程序异常: {e}"}
        finally:
            print("DEBUG: 流程结束，保持浏览器开启状态（如需关闭请手动或取消注释）")
            # context.close()
            # browser.close()
