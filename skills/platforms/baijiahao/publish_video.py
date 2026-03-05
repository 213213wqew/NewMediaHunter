# -*- coding: utf-8 -*-
"""
百家号视频发布：第一步上传视频，第二步打开封面选择封面。
页面：https://baijiahao.baidu.com/builder/rc/edit?type=videoV2&is_from_cms=1
"""
import os
import sys
import time
from playwright.sync_api import sync_playwright

_ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from core.inject import inject_cookies
from platforms.baijiahao.config import VIDEO_EDIT_URL, LOGIN_WAIT_TIMEOUT_MS


def run(params: dict, session_dir: str, cookie_json_str: str) -> dict:
    title = params.get("title", "")
    video_path = params.get("videoPath") or params.get("videoUrl")
    summary = params.get("summary", "")
    tags = params.get("tags", "")
    is_draft = params.get("draft", False)

    if not video_path:
        return {"success": False, "message": "未提供视频路径"}

    if not os.path.isabs(video_path):
        video_path = os.path.abspath(os.path.join(_ROOT, "..", video_path))

    if not os.path.exists(video_path):
        return {"success": False, "message": f"视频文件不存在: {video_path}"}

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
            page.goto(VIDEO_EDIT_URL, timeout=60000)

            # 登录检查
            if "login" in page.url or page.locator("text=登录").count() > 0:
                print("DEBUG: 百家号未登录，等待用户...")
                try:
                    page.wait_for_url("**/builder/rc/edit**", timeout=LOGIN_WAIT_TIMEOUT_MS)
                except Exception:
                    return {"success": False, "need_login": True, "message": "登录超时"}

            # ========== 第一步：上传视频 ==========
            # 页面上传区：点击上传或将文件拖动入此区域；F12 为 input[type=file] multiple accept=".mp4, .mov, ..."
            # 可能在 [data-testid="video-upload-btn"] 内的 input，或任意 input[type=file] 带 video accept
            print(f"DEBUG: 第一步 正在上传视频: {video_path}")
            file_input = page.locator('input[type="file"][accept*=".mp4"], input[type="file"][accept*="video"]')
            if file_input.count() == 0:
                file_input = page.locator('div[data-testid="video-upload-btn"] input[type="file"]')
            if file_input.count() == 0:
                file_input = page.locator("input[type=file]")
            if file_input.count() > 0:
                file_input.first.set_input_files(video_path)
                print("DEBUG: 已通过 input[type=file] 设置文件")
            else:
                with page.expect_file_chooser() as fc_info:
                    trigger = page.get_by_text("点击上传或将文件拖动入此区域", exact=False)
                    if trigger.count() == 0:
                        trigger = page.locator('[data-testid="video-upload-btn"]')
                    if trigger.count() == 0:
                        trigger = page.get_by_text("上传", exact=False).first
                    if trigger.count() == 0:
                        trigger = page.locator("input[type=file]")
                    trigger.first.click()
                fc_info.value.set_files(video_path)
                print("DEBUG: 已通过文件选择框设置文件")

            # 等待上传完成：出现「上传成功」或进入编辑页（封面/标题区域出现）
            print("DEBUG: 等待视频上传完成...")
            try:
                page.locator("text=上传成功").first.wait_for(state="visible", timeout=120000)
            except Exception:
                try:
                    page.locator("text=上传完成").first.wait_for(state="visible", timeout=120000)
                except Exception:
                    # 无明确文案时，等封面或标题区域出现（表示已进入编辑态）
                    page.wait_for_selector("text=封面", timeout=120000)
            print("DEBUG: 第一步完成，视频已上传")

            # ========== 第二步：打开封面选择封面 ==========
            time.sleep(2)
            print("DEBUG: 第二步 打开封面并选择封面...")
            # 点击「封面」区域或「智能推荐封面」以打开封面选择
            cover_trigger = page.get_by_text("智能推荐封面", exact=False).first
            if cover_trigger.count() == 0:
                cover_trigger = page.locator("text=封面").first
            if cover_trigger.count() == 0:
                cover_trigger = page.get_by_text("选择封面", exact=False).first
            if cover_trigger.count() == 0:
                # 封面区域可能为可点击的缩略图容器
                cover_trigger = page.locator(".cover-wrap, [class*='cover'], [class*='Cover']").first
            if cover_trigger.count() > 0:
                cover_trigger.scroll_into_view_if_needed()
                time.sleep(0.5)
                cover_trigger.click()
                time.sleep(2)
            # 若有「自动获取」「使用推荐」等按钮，点第一个推荐封面或确认
            auto_btn = page.get_by_text("自动获取", exact=False).first
            if auto_btn.count() > 0:
                auto_btn.click()
                time.sleep(2)
            recommend = page.get_by_text("使用推荐", exact=False).first
            if recommend.count() > 0:
                recommend.click()
                time.sleep(1)
            # 弹窗内「确定」「确认」「下一步」
            for btn_text in ["确定", "确认", "下一步"]:
                btn = page.get_by_role("button", name=btn_text).first
                if btn.count() == 0:
                    btn = page.get_by_text(btn_text, exact=True).first
                if btn.count() > 0 and btn.is_visible():
                    btn.click()
                    time.sleep(1)
                    break
            # 若存在第一个推荐封面缩略图，点击选中
            thumb = page.locator("[class*='thumbnail'], [class*='cover-item'], img[src*='cover']").first
            if thumb.count() > 0 and thumb.is_visible():
                thumb.click()
                time.sleep(1)
            print("DEBUG: 第二步 封面选择已处理")

            # ========== 填写标题、简介、话题 ==========
            if title:
                title_sel = page.locator("input[placeholder*='标题']").first
                if title_sel.count() == 0:
                    title_sel = page.locator("[contenteditable=true][data-placeholder*='标题']").first
                if title_sel.count() == 0:
                    title_sel = page.get_by_placeholder("标题").first
                if title_sel.count() > 0:
                    title_sel.fill(title)
                    print("DEBUG: 已填写标题")
            if summary:
                summary_sel = page.locator("textarea[placeholder*='简介'], textarea[placeholder*='描述']").first
                if summary_sel.count() > 0:
                    summary_sel.fill(summary)
            if tags:
                tag_parts = [t.strip() for t in (tags.split(",") if "," in tags else tags.split(" ")) if t.strip()]
                tag_input = page.locator("input[placeholder*='话题'], input[placeholder*='标签']").first
                if tag_input.count() > 0:
                    for part in tag_parts[:10]:
                        tag_input.fill("#" + part)
                        time.sleep(0.3)
                        page.keyboard.press("Enter")

            # ========== 发布或存草稿 ==========
            time.sleep(1)
            if is_draft:
                draft_btn = page.get_by_text("存草稿", exact=True).first
                if draft_btn.count() > 0:
                    draft_btn.click()
                    print("DEBUG: 已点击存草稿")
            else:
                pub_btn = page.get_by_text("发布", exact=True).first
                if pub_btn.count() > 0:
                    pub_btn.click()
                    print("DEBUG: 已点击发布")
            time.sleep(5)
            return {"success": True, "message": "视频发布流程已执行", "final_url": page.url}
        except Exception as e:
            return {"success": False, "message": str(e)}
        finally:
            context.close()
