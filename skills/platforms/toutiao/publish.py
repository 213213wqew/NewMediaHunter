# -*- coding: utf-8 -*-
"""
今日头条：仅负责发布流程。
先注入 Cookie，再访问发布页、填表、发布。不负责登录、不负责存 Token。
"""
import os
import sys
import json
import time
import re
from playwright.sync_api import sync_playwright

_ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from core.inject import inject_cookies
from core.window_layout import get_window_layout
from core.browser import KEEP_AWAKE_ARGS, inject_keep_awake
from platforms.toutiao.config import LOGIN_URL, LOGIN_WAIT_TIMEOUT_MS

# 发布页 URL
PUBLISH_URL = "https://mp.toutiao.com/profile_v4/graphic/publish?from=toutiao_pc"

def log_temp_file(uploads_dir, file_path):
    """记录临时文件路径到 temp_files.json，供 Java 清理"""
    try:
        log_path = os.path.join(uploads_dir, "temp_files.json")
        temp_files = []
        if os.path.exists(log_path):
            with open(log_path, "r", encoding="utf-8") as f:
                temp_files = json.load(f)
        
        abs_path = os.path.abspath(file_path)
        if abs_path not in temp_files:
            temp_files.append(abs_path)
            with open(log_path, "w", encoding="utf-8") as f:
                json.dump(temp_files, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"DEBUG: 记录临时文件失败: {e}")

def run(params: dict, session_dir: str, cookie_json_str: str) -> dict:
    """
    发布流程：注入 Cookie -> 打开发布页 -> 填标题正文 -> 洗图 -> 设置选项 -> 发布。
    """
    title = params.get("title", "未命名文章")
    content = params.get("htmlContent") or params.get("content", "")
    summary = params.get("summary", "")
    tags = params.get("tags", "")
    
    # 平台设置
    platform_settings = params.get("platformSettings", {}) or {}
    toutiao_ps = platform_settings.get("toutiao") or platform_settings or {}
    
    # 素材目录
    uploads_dir = params.get("localUploadsDir", "")
    if not uploads_dir:
        potential_root_uploads = os.path.abspath(os.path.join(_ROOT, "..", "uploads"))
        if os.path.exists(potential_root_uploads):
            uploads_dir = potential_root_uploads
        else:
            uploads_dir = os.path.abspath(os.path.join(_ROOT, "..", "java-work", "uploads"))

    with sync_playwright() as p:
        w, h, x, y = get_window_layout(session_dir)
        context = p.chromium.launch_persistent_context(
            user_data_dir=session_dir,
            headless=False,
            slow_mo=300,
            no_viewport=True,
            args=[f"--window-size={w},{h}", f"--window-position={x},{y}", "--start-maximized"] + KEEP_AWAKE_ARGS,
        )
        page = context.new_page()
        try:
            inject_keep_awake(page)
            inject_cookies(context, cookie_json_str)
            page.goto(PUBLISH_URL, timeout=60000)
            
            # --- 初始化页面状态：调整缩放与关闭干扰面板 ---
            print("DEBUG: 正在调整页面缩放 (80%) 以捕获完整结构...")
            page.evaluate("document.body.style.zoom = '0.9'")
            time.sleep(1)

            # 关闭“创作助手”侧边栏（如果存在）
            print("DEBUG: 正在检查并关闭“头条创作助手”面板...")
            try:
                # 根据截图 320，关闭按钮由 .ai-assistant-panel 类下的 .close-btn 组成
                close_btn = page.locator(".ai-assistant-panel .close-btn, .byte-icon--close").first
                if close_btn.count() > 0 and close_btn.is_visible():
                    close_btn.click()
                    print("DEBUG: 已关闭创作助手侧边栏")
                    time.sleep(1)
            except Exception as e:
                print(f"DEBUG: 关闭面板动作跳过 (可能未出现): {e}")

            # 登录检查
            if "/auth/page/login" in page.url or page.locator("text='登录'").count() > 0:
                print("DEBUG: 未登录，等待用户...")
                try:
                    page.wait_for_url("**/profile_v4/graphic/publish**", timeout=LOGIN_WAIT_TIMEOUT_MS)
                except Exception:
                    return {"success": False, "need_login": True, "message": "登录超时"}

            # 1. 填写标题
            print(f"DEBUG: 正在填写标题: {title}")
            title_input = page.locator('textarea[placeholder*="请输入文章标题"]').first
            title_input.wait_for(state="visible", timeout=30000)
            title_input.fill(title)
            time.sleep(2) # 增加缓冲，等待标题同步
            
            # 2. 激活编辑器 (点击正文区域以释放工具栏状态)
            print("DEBUG: 正在激活编辑器...")
            editor_loc = page.locator(".ProseMirror").first
            editor_loc.click()
            time.sleep(2) # 填充正文前多等一会儿

            # 3. 循序式填充正文：判断文字与图片顺序依次写入
            import html as py_html
            # 按 <img> 标签分割内容，保留分割符
            chunks = re.split(r'(<img[^>]+>)', content)
            url_map = {} # 初始化用于追踪已上传的 CDN 链接，供封面逻辑使用
            print(f"DEBUG: 稿件已切分为 {len(chunks)} 个区块，开始循序填充...")
            
            for chunk in chunks:
                if not chunk.strip():
                    continue
                
                if chunk.startswith('<img'):
                    # --- 图片处理区块 ---
                    img_src_match = re.search(r'src=[\'"]?([^\'"\s>]+)[\'"]?', chunk)
                    if img_src_match:
                        url = py_html.unescape(img_src_match.group(1))
                        local_path = None
                        try:
                            # 定位本地文件
                            if "/api/file/viewInternal/" in url or "/api/file/view/" in url:
                                filename = url.split("/")[-1].split("?")[0]
                                local_path = os.path.normpath(os.path.join(uploads_dir, filename))
                            elif url.startswith("http"):
                                api_resp = context.request.get(url, timeout=15000)
                                if api_resp.ok:
                                    img_data = api_resp.body()
                                    local_path = os.path.join(uploads_dir, f"tmp_{int(time.time())}.jpg")
                                    with open(local_path, "wb") as f:
                                        f.write(img_data)
                                    log_temp_file(uploads_dir, local_path)
                            
                            if local_path and os.path.exists(local_path):
                                print(f"DEBUG: 命中图片区块，准备上传: {os.path.basename(local_path)}")
                                # 点击工具栏图片按钮
                                image_btn = page.locator(".syl-toolbar-tool.image button").first
                                if image_btn.count() > 0:
                                    image_btn.click()
                                    time.sleep(2.5) # 打开弹窗后多等一会儿
                                    
                                    # 智能规避“保存到我的素材”导致的报错 (截图 467)
                                    try:
                                        save_material_cb = page.locator('label.byte-checkbox:has-text("保存到我的素材")').first
                                        if save_material_cb.count() > 0:
                                            is_checked = save_material_cb.evaluate("el => el.classList.contains('byte-checkbox-checked')")
                                            if is_checked:
                                                save_material_cb.click()
                                                print("DEBUG: 已主动取消『保存到我的素材』勾选以规避并发冲突")
                                    except: pass

                                    # 精准定位上传面板中的隐藏 input
                                    file_input = page.locator('.upload-handler-drag input[type="file"]').first
                                    if file_input.count() == 0:
                                        file_input = page.locator('input[type="file"][accept*="image"]').last
                                    
                                    if file_input.count() > 0:
                                        file_input.set_input_files(local_path)
                                        print(f"DEBUG: 已注入图片文件，开始等待上传完成检测...")
                                        
                                        # 增加精准检测：等待上传面板中出现真实 CDN/私有地址图片 (参考截图 512)
                                        # 非 blob: 协议的 http(s) 地址即代表后端处理完成按钮已激活
                                        try:
                                            # 最多等待 15 秒
                                            page.wait_for_selector('.image-list img[src^="http"]:not([src*="blob:"])', timeout=15000)
                                            print("DEBUG: 检测到后端返回的图片私密地址，上传已就绪")
                                            # 按照用户建议：有了地址后，额外等一秒再点
                                            time.sleep(1)
                                        except Exception as e:
                                            print(f"DEBUG: 警告：等待上传回显超时，尝试强行确认: {e}")

                                        # 点击确定
                                        confirm_btn = page.locator("button:has-text('确定'), .upload-btn.byte-btn-primary").last
                                        if confirm_btn.count() > 0:
                                            # 此时按钮理论上已经不处于禁用状态
                                            is_disabled = confirm_btn.evaluate("el => el.disabled")
                                            if is_disabled:
                                                print("DEBUG: 确定按钮仍处于禁用状态，补丁式补偿等待...")
                                                time.sleep(2)
                                            confirm_btn.click()
                                            
                                            # 轮询获取编辑器内的链接
                                            for _ in range(6):
                                                time.sleep(1) # 缩短单次轮询间隔
                                                new_url = page.evaluate("""() => {
                                                    const imgs = Array.from(document.querySelectorAll('.ProseMirror img'));
                                                    return imgs.length > 0 ? imgs[imgs.length - 1].src : null;
                                                }""")
                                                if new_url and ("p3-toutiao" in new_url or "byteimg" in new_url):
                                                    url_map[url] = new_url
                                                    break
                                            
                                            print(f"DEBUG: 图片已确定插入编辑器 (CDN回显: {url in url_map})")
                                            time.sleep(1.5) # 压缩图片插入后的死等时间，更快进入下一块
                                        else:
                                            print("DEBUG: 警告：未找到确定按钮")
                        except Exception as ex:
                            print(f"DEBUG: 循序图片处理失败: {ex}")
                else:
                    # --- 文本处理区块 ---
                    print("DEBUG: 命中文字区块，准备注入...")
                    page.evaluate("""(html) => {
                        const editor = document.querySelector('.ProseMirror');
                        if (editor) {
                            editor.focus();
                            // 使用 insertHTML 在当前光标处精准插入
                            document.execCommand('insertHTML', false, html);
                            editor.dispatchEvent(new Event('input', { bubbles: true }));
                        }
                    }""", chunk)
                    time.sleep(0.8) # 显著压缩文字块注入后的缓冲，提升连贯性
 
            time.sleep(2)
 
            # 3.1 展开发文设置（分类、标签等通常折叠在这里）
            print("DEBUG: 正在展开发文设置...")
            try:
                setting_btn = page.locator('div:has-text("发文设置"), .byte-collapse-item-header:has-text("发文设置")').last
                if setting_btn.count() > 0:
                    # 检查是否已展开
                    is_expanded = setting_btn.evaluate("""(el) => {
                        const next = el.nextElementSibling;
                        return next && next.clientHeight > 0;
                    }""")
                    if not is_expanded:
                        setting_btn.scroll_into_view_if_needed()
                        setting_btn.click()
                        time.sleep(2) # 展开设置多等一会儿
            except Exception as e:
                print(f"DEBUG: 展开发文设置异常: {e}")

            # 3.2 填写分类
            category = toutiao_ps.get("category")
            if category:
                print(f"DEBUG: 正在选择分类: {category}")
                try:
                    # 今日头条分类通常是一个下拉框
                    cat_selector = page.locator('.byte-select:has-text("分类"), .byte-select:has([placeholder*="分类"])').first
                    if cat_selector.count() > 0:
                        cat_selector.click()
                        time.sleep(2) # 列表出现多等一会儿
                        # 在弹框中选择
                        option = page.locator(f'.byte-select-option:has-text("{category}")').first
                        if option.count() > 0:
                            option.click()
                            print(f"DEBUG: 已选择分类: {category}")
                            time.sleep(1.5) # 给自动保存一些时间
                except Exception as e:
                    print(f"DEBUG: 选择分类失败: {e}")
            
            # 3.3 填写标签
            tag_str = toutiao_ps.get("tags") or tags
            if tag_str:
                print(f"DEBUG: 正在填写标签: {tag_str}")
                try:
                    # 标签通常位于“添加标签”或“来源”附近
                    tag_input = page.locator('input[placeholder*="标签"], input[placeholder*="添加关键词"]').first
                    if tag_input.count() > 0:
                        tag_input.scroll_into_view_if_needed()
                        # 头条标签可能需要一个一个输入并按回车
                        split_tags = re.split(r'[,，\s]+', tag_str)
                        for t in split_tags:
                            if t.strip():
                                tag_input.fill(t.strip())
                                time.sleep(0.5)
                                tag_input.press("Enter")
                                time.sleep(2.5) # 头条标签自动保存较慢，多等一会儿
                except Exception as e:
                    print(f"DEBUG: 填写标签失败: {e}")
            
            # 4. 设置封面
            cover_type = toutiao_ps.get("coverType", "single") # single, triple, none
            
            # 自动降级逻辑：如果设置了单图/三图，但文章无图且没传本地封面，则设为无封面
            local_cover_path = params.get("localCoverPath")
            # 重新提取图片列表用于封面检测
            detect_imgs = re.findall(r'<img[^>]+src=[\'"]?([^\'"\s>]+)[\'"]?', content)
            has_images = len(url_map) > 0 or len(detect_imgs) > 0
            if (cover_type == "single" or cover_type == "triple") and not has_images and not (local_cover_path and os.path.exists(local_cover_path)):
                print("DEBUG: 检测到文章无图且未提供封面，自动降级为『无封面』")
                cover_type = "none"

            cover_map = {"single": "单图", "triple": "三图", "none": "无封面"}
            target_text = cover_map.get(cover_type, "单图")
            print(f"DEBUG: 设置封面类型: {target_text}")
            try:
                # 寻找封面单选按钮
                cover_label = page.locator("label.byte-radio, label.byte-radio-group-item").filter(has_text=target_text).first
                if cover_label.count() > 0:
                    cover_label.scroll_into_view_if_needed()
                    cover_label.click()
                    time.sleep(2) # 封面模式切换多等一会儿
                
                # 如果是单图/三图且传了封面
                if (cover_type == "single" or cover_type == "triple") and local_cover_path and os.path.exists(local_cover_path):
                    print(f"DEBUG: 上传自定义封面: {local_cover_path}")
                    # 点击上传占位符
                    add_btn = page.locator(".byte-upload-trigger-area, .byte-upload__input-container").first
                    if add_btn.count() > 0:
                        add_btn.click()
                        time.sleep(2)
                        file_input = page.locator('input[type="file"][accept*="image"]').last
                        file_input.set_input_files(local_cover_path)
                        time.sleep(3)
                        # 点击确定
                        confirm_btn = page.locator("button:has-text('确定')").last
                        if confirm_btn.count() > 0:
                            confirm_btn.click()
                            time.sleep(2) # 封面确定多等一会儿
            except Exception as e:
                print(f"DEBUG: 设置封面异常: {e}")

            # 5. 设置作品声明
            work_statements = toutiao_ps.get("workStatements", [])
            if work_statements:
                print(f"DEBUG: 正在设置作品声明: {work_statements}")
                for ws in work_statements:
                    try:
                        # 头条的作品声明通常是 byte-checkbox
                        checkbox = page.locator("label.byte-checkbox").filter(has_text=ws).first
                        if checkbox.count() > 0:
                            # 检查是否已勾选
                            is_checked = checkbox.evaluate("el => el.classList.contains('byte-checkbox-checked')")
                            if not is_checked:
                                checkbox.scroll_into_view_if_needed()
                                time.sleep(2)
                                checkbox.click()
                                print(f"DEBUG: 已勾选作品声明: {ws}")
                                time.sleep(3) # 减少高频点击导致的 JS 错误
                    except Exception as e:
                        print(f"DEBUG: 勾选作品声明失败 {ws}: {e}")

            # 6. 发布或存草稿
            # 今日头条 PC 端通常是实时自动保存草稿的，没有显式的“存草稿”按钮在发布前
            time.sleep(4) # 发布前给最后 4 秒缓冲
            is_draft = params.get("isDraft") or params.get("draft") or False
            if is_draft:
                print("DEBUG: 任务为存草稿，今日头条已实时自动保存，直接返回成功")
                return {"success": True, "message": "文章已自动保存草稿", "final_url": page.url}
            else:
                print("DEBUG: 正在执行发布流程...")
                # 根据截图 758 观察 DOM，确认发布按钮带有 .publish-btn-last 类
                publish_btn = page.locator('button.publish-btn-last, button:has-text("确认发布"), button:has-text("预览并发布")').last
                if publish_btn.count() > 0:
                    publish_btn.scroll_into_view_if_needed()
                    print(f"DEBUG: 命中发布按钮: {publish_btn.inner_text()}")
                    publish_btn.click()
                    time.sleep(4) # 最终提交多留 1 秒
                    
                    # 检查是否有二次确认弹窗 (例如“不再提示”或“确定发布”)
                    final_confirm = page.locator('button:has-text("确认发布"), button.byte-btn-primary:has-text("发布"), button:has-text("确定")').last
                    if final_confirm.count() > 0 and final_confirm.is_visible():
                        final_confirm.click()
                        print("DEBUG: 已点击二次确认动作")
                
            time.sleep(5)
            # 头条发布后通常会跳转到管理页或显示成功提示
            return {"success": True, "message": "已成功提交发布请求", "final_url": page.url}

        except Exception as e:
            print(f"DEBUG: 发布过程发生异常: {e}")
            return {"success": False, "message": str(e)}
        finally:
            context.close()
