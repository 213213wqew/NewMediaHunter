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
from core.window_layout import get_window_layout
from core.browser import KEEP_AWAKE_ARGS, inject_keep_awake
from platforms.baijiahao.config import VIDEO_EDIT_URL, LOGIN_WAIT_TIMEOUT_MS


def run(params: dict, session_dir: str, cookie_json_str: str) -> dict:
    title = params.get("title", "")
    video_path = params.get("videoPath") or params.get("videoUrl")
    summary = params.get("summary", "")
    tags = params.get("tags", "")
    is_draft = params.get("draft", False)
    # 前端传来的百家号设置：分类、活动投稿（前端不选则页面也不选）
    category_text = ""
    activity_text = ""
    ps = params.get("platformSettings") or {}
    if isinstance(ps, dict):
        bjh_ps = ps.get("bjh") or ps.get("baijiahao") or {}
        if isinstance(bjh_ps, dict):
            category_text = (bjh_ps.get("category") or "").strip()
            activity_text = (bjh_ps.get("selectedActivity") or "").strip()

    if not video_path:
        return {"success": False, "message": "未提供视频路径"}

    if not os.path.isabs(video_path):
        video_path = os.path.abspath(os.path.join(_ROOT, "..", video_path))

    if not os.path.exists(video_path):
        return {"success": False, "message": f"视频文件不存在: {video_path}"}

    with sync_playwright() as p:
        w, h, x, y = get_window_layout(session_dir)
        context = p.chromium.launch_persistent_context(
            user_data_dir=session_dir,
            headless=False,
            slow_mo=500,
            no_viewport=True,
            args=[f"--window-size={w},{h}", f"--window-position={x},{y}"] + KEEP_AWAKE_ARGS,
        )
        page = context.new_page()
        try:
            inject_keep_awake(page)
            inject_cookies(context, cookie_json_str)
            page.goto(VIDEO_EDIT_URL, timeout=60000)

            # 登录检查
            if "login" in page.url or page.locator("text=登录").count() > 0:
                print("DEBUG: 百家号未登录，等待用户...")
                try:
                    page.wait_for_url("**/builder/rc/edit**", timeout=LOGIN_WAIT_TIMEOUT_MS)
                except Exception:
                    return {"success": False, "need_login": True, "message": "登录超时"}

            # 强制刷新，清除上次运行残留的页面状态，确保处于空白上传状态
            print("DEBUG: 刷新上传页，确保处于空白上传状态...")
            page.reload(wait_until="domcontentloaded", timeout=30000)
            time.sleep(2)
            try:
                page.get_by_text("点击上传或将文件拖动入此区域", exact=False).first.wait_for(state="visible", timeout=15000)
            except Exception:
                pass

            # 整页缩小，小窗口下弹窗能全部进视口，避免按钮 outside viewport
            try:
                page.evaluate("document.body.style.zoom = '0.85'")
            except Exception:
                pass

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

            # 等待上传完成：进度条 role=progressbar 的 aria-valuenow 为 100 表示 100%
            # 页面结构：div.cheetah-progress-* role="progressbar" aria-valuenow="77" aria-valuemin="0" aria-valuemax="100"
            print("DEBUG: 等待视频上传完成（进度条 100%）...")
            try:
                page.wait_for_function(
                    "() => { const bar = document.querySelector('[role=\"progressbar\"]'); return bar && (bar.getAttribute('aria-valuenow') === '100' || Number(bar.getAttribute('aria-valuenow')) >= 100); }",
                    timeout=120000,
                )
                print("DEBUG: 进度条已到 100%，上传完成")
            except Exception:
                try:
                    page.locator("text=上传成功").first.wait_for(state="visible", timeout=15000)
                except Exception:
                    try:
                        page.locator("text=上传完成").first.wait_for(state="visible", timeout=15000)
                    except Exception:
                        page.wait_for_selector("text=封面", timeout=15000)
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

            # ========== 第三步：分类选择（前端不选则页面也不选） ==========
            if category_text:
                print(f"DEBUG: 第三步 分类选择: {category_text!r}")
                time.sleep(1)
                try:
                    # 点击「请选择内容分类」或分类下拉（cheetah-select）打开下拉
                    category_trigger = page.get_by_text("请选择内容分类", exact=False).first
                    if category_trigger.count() == 0:
                        category_trigger = page.locator(".cheetah-select").filter(has_text="分类").first
                    if category_trigger.count() == 0:
                        category_trigger = page.locator("[class*='select']").filter(has_text="分类").first
                    if category_trigger.count() > 0:
                        category_trigger.scroll_into_view_if_needed()
                        time.sleep(0.5)
                        category_trigger.click()
                        time.sleep(1)
                        # 下拉内点击与前端传入一致的选项（页面上可能是 "法律 / 案件解读" 等，兼容斜杠/空格）
                        option = page.get_by_text(category_text, exact=False).first
                        if option.count() == 0:
                            first_part = category_text.split("/")[0].strip()
                            option = page.locator(".cheetah-select-selection-item, [class*='select-item']").filter(has_text=first_part).first
                        if option.count() == 0:
                            option = page.locator("span[title]").filter(has_text=category_text[:20]).first
                        if option.count() > 0:
                            option.click()
                            print("DEBUG: 已选择分类")
                        else:
                            print(f"DEBUG: 未找到分类选项: {category_text!r}")
                    else:
                        print("DEBUG: 未找到分类下拉入口")
                except Exception as e:
                    print(f"DEBUG: 分类选择异常: {e}")
            else:
                print("DEBUG: 第三步 跳过分类（前端未选）")

            # ========== 第四步：活动投稿（用户输入则只点匹配项，留空则只点第一个；绝不先点第一个再点用户选的） ==========
            print("DEBUG: 第四步 活动投稿...")
            time.sleep(1)
            try:
                # 限定在「活动投稿」区域内，避免点到分类/其他区域；class 常含 taskname，排除「更多活动」
                section = page.get_by_text("活动投稿", exact=True).first
                if section.count() == 0:
                    section = page.locator("text=活动投稿").first
                if section.count() > 0:
                    activity_tags_loc = section.locator("..").locator("[class*='taskname'], [class*='task-name']")
                else:
                    activity_tags_loc = page.locator("[class*='taskname'], [class*='task-name']")
                n = activity_tags_loc.count()
                clicked = False
                if activity_text:
                    # 用户有输入：只点匹配项。先精确匹配，再包含匹配，只点一次
                    search = activity_text.strip()
                    for i in range(n):
                        tag = activity_tags_loc.nth(i)
                        try:
                            text = (tag.inner_text() or "").strip()
                        except Exception:
                            continue
                        if "更多活动" in text or not text:
                            continue
                        if text == search or text.strip() == search:
                            tag.scroll_into_view_if_needed()
                            time.sleep(0.3)
                            tag.click()
                            print(f"DEBUG: 已选择活动投稿（精确匹配）: {text!r}")
                            clicked = True
                            break
                    if not clicked:
                        for i in range(n):
                            tag = activity_tags_loc.nth(i)
                            try:
                                text = (tag.inner_text() or "").strip()
                            except Exception:
                                continue
                            if "更多活动" in text or not text:
                                continue
                            if search in text:
                                tag.scroll_into_view_if_needed()
                                time.sleep(0.3)
                                tag.click()
                                print(f"DEBUG: 已选择活动投稿（包含匹配）: {text!r}")
                                clicked = True
                                break
                    if not clicked:
                        fallback = page.get_by_text(activity_text, exact=False).first
                        if fallback.count() > 0:
                            fallback.scroll_into_view_if_needed()
                            fallback.click()
                            print("DEBUG: 已通过文案兜底选择活动投稿")
                else:
                    # 用户未输入：只点第一个（非「更多活动」）
                    for i in range(n):
                        tag = activity_tags_loc.nth(i)
                        try:
                            text = (tag.inner_text() or "").strip()
                        except Exception:
                            continue
                        if "更多活动" in text or not text:
                            continue
                        tag.scroll_into_view_if_needed()
                        time.sleep(0.3)
                        tag.click()
                        print(f"DEBUG: 已选择第一个活动: {text!r}")
                        clicked = True
                        break
                    if not clicked:
                        print("DEBUG: 未找到活动投稿区域或无可选活动")
            except Exception as e:
                print(f"DEBUG: 活动投稿选择异常: {e}")

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
            time.sleep(5)
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
