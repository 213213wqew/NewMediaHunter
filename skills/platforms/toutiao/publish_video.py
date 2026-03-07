# -*- coding: utf-8 -*-
"""
今日头条（西瓜视频）：视频发布流程。
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
from platforms.toutiao.config import VIDEO_UPLOAD_URL, LOGIN_WAIT_TIMEOUT_MS


def run(params: dict, session_dir: str, cookie_json_str: str) -> dict:
    title = params.get("title", "")
    video_path = params.get("videoPath") or params.get("videoUrl")
    tags = params.get("tags", "")
    summary = params.get("summary", "")
    # 本技能先屏蔽最终发布：不点「发布」按钮，仅填表单+选声明，便于测试
    skip_final_publish = params.get("skipPublish", True)

    # 作品声明、生成图文：直接使用前端传过来的配置
    work_statement_text = ""
    generate_article = True  # 默认选中生成图文
    ps = params.get("platformSettings") or {}
    if isinstance(ps, dict):
        toutiao_ps = ps.get("toutiao") or {}
        if isinstance(toutiao_ps, dict):
            work_statement_text = (toutiao_ps.get("workStatement") or "").strip()
            if toutiao_ps.get("generateArticle") is not None:
                generate_article = bool(toutiao_ps.get("generateArticle"))
    if not work_statement_text:
        work_statement_text = (params.get("workStatement") or "").strip()

    if not video_path:
        return {"success": False, "message": "未提供视频路径"}
    
    # 转换为绝对路径
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
            args=[f"--window-size={w},{h}", f"--window-position={x},{y}"],
        )
        page = context.new_page()
        try:
            inject_cookies(context, cookie_json_str)
            page.goto(VIDEO_UPLOAD_URL, timeout=60000)

            # 登录检查
            if "/auth/page/login" in page.url or page.locator("text='登录'").count() > 0:
                print("DEBUG: 未登录，等待用户...")
                try:
                    page.wait_for_url("**/profile_v4/xigua/upload-video**", timeout=LOGIN_WAIT_TIMEOUT_MS)
                except Exception:
                    return {"success": False, "need_login": True, "message": "登录超时"}

            # 强制刷新，清除上次运行残留的页面状态（避免页面上仍是「已上传/选封面」导致本次上传失败）
            print("DEBUG: 刷新上传页，确保处于空白上传状态...")
            page.reload(wait_until="domcontentloaded", timeout=30000)
            time.sleep(2)
            # 等待上传区出现再操作
            try:
                page.get_by_text("点击上传或将文件拖入此区域", exact=False).first.wait_for(state="visible", timeout=15000)
            except Exception:
                pass

            # 整页缩小，小窗口下弹窗（封面截取等）能全部进视口，避免「下一步」「确定」等报 outside viewport
            try:
                page.evaluate("document.body.style.zoom = '0.85'")
            except Exception:
                pass

            # 1. 选中并上传文件（基于 F12：https://mp.toutiao.com/profile_v4/xigua/upload-video）
            # 上传区：.xigua-upload-video → .byte-upload-trigger-area，内嵌隐藏 input[type=file]（accept 含 .mp4/.mov 等）
            print(f"DEBUG: 正在上传视频: {video_path}")
            # 方式 A：直接对隐藏的 input[type=file] 设置文件（Playwright 支持隐藏 input，无需弹框）
            file_input = page.locator(
                "div.xigua-upload-video input[type=file], "
                "div.upload-video-trigger input[type=file], "
                "div.byte-upload.xigua-upload-video-trigger input[type=file]"
            )
            if file_input.count() == 0:
                file_input = page.locator("input[type=file][accept*='.mp4'], input[type=file][accept*='video']")
            if file_input.count() > 0:
                file_input.first.set_input_files(video_path)
                print("DEBUG: 已通过 input[type=file] 设置文件（第一步完成）")
            else:
                # 方式 B：点击可见上传区「点击上传或将文件拖入此区域」，触发文件选择框
                with page.expect_file_chooser() as fc_info:
                    trigger = page.get_by_text("点击上传或将文件拖入此区域", exact=False)
                    if trigger.count() == 0:
                        trigger = page.locator("div.byte-upload-trigger-area")
                    if trigger.count() == 0:
                        trigger = page.get_by_text("上传视频", exact=False)
                    if trigger.count() == 0:
                        trigger = page.locator("input[type=file]")
                    trigger.first.click()
                fc_info.value.set_files(video_path)
                print("DEBUG: 已通过点击上传区并设置文件选择框完成")
            
            # 以「上传成功」为唯一完成信号（页面可能不跳转仅原地更新；F12: <span class="percent">上传成功</span>）
            print("DEBUG: 等待视频上传完全完成（出现「上传成功」）...")
            try:
                page.locator("span.percent").filter(has_text="上传成功").first.wait_for(state="visible", timeout=120000)
            except Exception:
                page.get_by_text("上传成功", exact=False).first.wait_for(state="visible", timeout=120000)
            print("DEBUG: 视频上传已完全完成，开始第二步")

            # 2. 第二步：选择封面
            print("DEBUG: 第二步：选择封面...")
            time.sleep(1)
            cover_trigger = page.locator("div.xigua-poster-editor .fake-upload-trigger, div.ziguposter-editor.fake-upload-trigger").first
            if cover_trigger.count() == 0:
                cover_trigger = page.get_by_text("上传封面", exact=False).first
            if cover_trigger.count() > 0:
                cover_trigger.scroll_into_view_if_needed()
                time.sleep(0.5)
                cover_trigger.click()
                time.sleep(1)
                try:
                    page.wait_for_selector("text=封面截取", timeout=10000)
                    print("DEBUG: 封面弹窗已打开，等待自动获取封面...")
                except Exception:
                    pass
                time.sleep(5)
                # 增强版「下一步」点击逻辑
                next_btn = page.locator("div.m-button.red, .m-button.red").filter(has_text="下一步").first
                if next_btn.count() == 0:
                    next_btn = page.get_by_text("下一步", exact=True).first
                if next_btn.count() > 0:
                    next_btn.scroll_into_view_if_needed()
                    time.sleep(1)
                    try:
                        # 先尝试常规点击
                        next_btn.click(timeout=10000)
                    except Exception:
                        print("DEBUG: 常规点击「下一步」失败，尝试强力点击...")
                        time.sleep(2)
                        next_btn.evaluate("el => el.click()") # JS 强制点击
                    print("DEBUG: 已点击下一步，等待封面编辑弹窗...")
                    # 下一步后弹出「封面编辑」modal（F12: div.dialog-container > div.footer > button.btn-1.btn-sure.ml16 确认）
                    try:
                        time.sleep(2)  # 等弹窗动画/渲染
                        page.get_by_text("封面编辑", exact=False).first.wait_for(state="visible", timeout=20000)
                        time.sleep(1)
                        # 刚进封面编辑时可能出现裁剪步骤，需先点「完成裁剪」才能到确认（F12: div.clip-btn 内 clip-btn-content 为「完成裁剪」）
                        clip_btn = page.locator("div.clip-btn").filter(has_text="完成裁剪").first
                        if clip_btn.count() > 0:
                            try:
                                clip_btn.scroll_into_view_if_needed()
                                time.sleep(0.5)
                                clip_btn.click(timeout=10000)
                                print("DEBUG: 已点击「完成裁剪」，等待裁剪界面关闭...")
                                time.sleep(2)
                            except Exception:
                                try:
                                    time.sleep(1)
                                    clip_btn.click(timeout=5000, force=True)
                                    time.sleep(2)
                                except Exception as e:
                                    print(f"DEBUG: 点击完成裁剪失败（可能未出现裁剪）: {e}")
                        # 页面为人机检测：需先点击「文字」后确认按钮才会释放（F12: 左侧 tool-menu 下 icon-name 文字）
                        try:
                            cover_dialog = page.locator("div.xigua-dialog-container, div.dialog-container").first
                            if cover_dialog.count() > 0:
                                text_tab = cover_dialog.locator("div.icon-name").filter(has_text="文字").first
                            else:
                                text_tab = page.locator("div.tool-menu div.icon-name").filter(has_text="文字").first
                            if text_tab.count() == 0:
                                text_tab = page.get_by_text("文字", exact=True).first
                            if text_tab.count() > 0:
                                text_tab.scroll_into_view_if_needed()
                                time.sleep(0.3)
                                try:
                                    text_tab.click(timeout=10000)
                                except Exception:
                                    time.sleep(2)
                                    text_tab.click(timeout=5000, force=True)
                                print("DEBUG: 已点击「文字」通过人机检测，确认按钮将释放")
                                time.sleep(1)
                        except Exception:
                            pass
                        # 释放后的确认按钮在此（F12: button.btn-l.btn-sure.ml16 文案为「确定」）
                        dialog = page.locator("div.dialog-container").first
                        if dialog.count() > 0:
                            confirm_btn = dialog.locator("button.btn-sure, button.btn-l.btn-sure, button.btn-1.btn-sure").first
                        else:
                            confirm_btn = page.locator("button.btn-sure, button.btn-l.btn-sure").first
                        if confirm_btn.count() == 0:
                            confirm_btn = page.locator("button.btn-sure").filter(has_text="确认").first
                        if confirm_btn.count() == 0:
                            confirm_btn = page.locator("button.btn-sure").filter(has_text="确定").first
                        if confirm_btn.count() == 0:
                            confirm_btn = page.get_by_role("button", name="确认").first
                        if confirm_btn.count() == 0:
                            confirm_btn = page.get_by_role("button", name="确定").first
                        if confirm_btn.count() == 0:
                            confirm_btn = page.get_by_text("确认", exact=True).first
                        if confirm_btn.count() == 0:
                            confirm_btn = page.get_by_text("确定", exact=True).first
                        confirm_btn.wait_for(state="visible", timeout=25000)
                        time.sleep(1)
                        try:
                            confirm_btn.click(timeout=15000)
                        except Exception:
                            time.sleep(2)
                            confirm_btn.click(timeout=8000, force=True)
                        print("DEBUG: 已点击确认，封面步骤彻底完成")
                        # 确认后可能再弹出「完成后无法继续编辑,是否确定完成?」需再点确定（F12: div.m-dialog-edit 内 button.m-button.red 确定）
                        try:
                            time.sleep(1)
                            finish_confirm = page.get_by_text("是否确定完成", exact=False).first
                            finish_confirm.wait_for(state="visible", timeout=5000)
                            sure_btn = page.locator("div.m-dialog-edit button.m-button.red").first
                            if sure_btn.count() == 0:
                                sure_btn = page.get_by_text("确定", exact=True).first
                            if sure_btn.count() > 0:
                                try:
                                    sure_btn.click(timeout=10000)
                                except Exception:
                                    sure_btn.click(timeout=5000, force=True)
                                print("DEBUG: 已点击二次确认「确定」，封面流程结束")
                        except Exception:
                            pass
                    except Exception as e:
                        print(f"DEBUG: 封面编辑页确认未找到或超时: {e}")
                    time.sleep(1)
                else:
                    print("DEBUG: 未找到下一步按钮，继续后续步骤")
            else:
                print("DEBUG: 未找到上传封面入口，跳过封面步骤")

            # 3. 填写基本信息（标题、简介、话题）
            print(f"DEBUG: 填写标题: {title}")
            # 标题
            title_input = page.locator("input[placeholder*='标题']").first
            if title_input.count() > 0:
                title_input.fill(title)
            
            # 简介/摘要
            if summary:
                print("DEBUG: 填写简介")
                summary_input = page.locator("textarea[placeholder*='简介']").first
                if summary_input.count() > 0:
                    summary_input.fill(summary)

            # 话题
            if tags:
                print(f"DEBUG: 填写话题: {tags}")
                tag_list = tags.split(",") if "," in tags else tags.split(" ")
                for tag in tag_list:
                    if not tag.strip(): continue
                    tag_input = page.locator("input[placeholder*='话题']").first
                    if tag_input.count() > 0:
                        tag_input.type("#" + tag.strip())
                        time.sleep(1)
                        page.keyboard.press("Enter")

            # 3.4 生成图文：若前端选中则勾选「生成图文」；未选中则不操作。页面结构：label.byte-checkbox 内 span.byte-checkbox-inner-text 为「生成图文」
            if generate_article:
                time.sleep(1)
                try:
                    gen_label = page.locator("label.byte-checkbox").filter(has_text="生成图文").first
                    if gen_label.count() > 0:
                        # 仅当未勾选时点击（label 有 byte-checkbox-checked 表示已勾选）
                        is_checked = gen_label.evaluate("el => el.classList.contains('byte-checkbox-checked')")
                        if not is_checked:
                            gen_label.scroll_into_view_if_needed()
                            time.sleep(0.3)
                            gen_label.click()
                            print("DEBUG: 已勾选「生成图文」")
                        else:
                            print("DEBUG: 「生成图文」已勾选，跳过")
                    else:
                        gen_span = page.locator("span.byte-checkbox-inner-text").filter(has_text="生成图文").first
                        if gen_span.count() > 0:
                            gen_span.scroll_into_view_if_needed()
                            time.sleep(0.3)
                            gen_span.click()
                            print("DEBUG: 已勾选「生成图文」")
                        else:
                            print("DEBUG: 未找到「生成图文」选项")
                except Exception as e:
                    print(f"DEBUG: 生成图文勾选异常: {e}")
            else:
                print("DEBUG: 前端未选中生成图文，跳过")

            # 3.5 作品声明：多窗口并行时 Dialog 的 mask 可能未及时消失，先等 mask 隐藏再点
            if work_statement_text:
                time.sleep(3)
                try:
                    mask = page.locator("div.Dialog-container div.mask").first
                    if mask.count() > 0:
                        mask.wait_for(state="hidden", timeout=12000)
                except Exception:
                    pass
                time.sleep(2)
                print(f"DEBUG: 选择作品声明（前端传入）: {work_statement_text!r}")
                try:
                    text_for_match = work_statement_text.replace("、", "，").strip()
                    st_loc = page.locator("label.byte-checkbox.checkbox-item").filter(has_text=text_for_match).first
                    if st_loc.count() == 0:
                        st_loc = page.locator("label.byte-checkbox").filter(has_text=text_for_match).first
                    if st_loc.count() == 0:
                        st_loc = page.locator("span.byte-checkbox-inner-text").filter(has_text=text_for_match).first
                    if st_loc.count() == 0:
                        st_loc = page.get_by_text(work_statement_text, exact=False).first
                    if st_loc.count() > 0:
                        st_loc.scroll_into_view_if_needed()
                        time.sleep(0.5)
                        try:
                            st_loc.click(timeout=15000)
                        except Exception:
                            # mask 仍拦截时多等几秒再 force 点击
                            time.sleep(4)
                            st_loc.click(timeout=8000, force=True)
                        print("DEBUG: 已选择作品声明")
                    else:
                        print(f"DEBUG: 未找到作品声明选项: {work_statement_text!r}")
                except Exception as e:
                    print(f"DEBUG: 选择作品声明异常: {e}")

            # 4. 发布（已屏蔽：现在不执行发布，仅填表单+作品声明）
            print("DEBUG: 已屏蔽最终发布，不点击发布按钮")
            time.sleep(3)
            is_draft = params.get("draft", False)
            if is_draft:
                draft_btn = page.get_by_text("存草稿", exact=True).first
                if draft_btn.count() > 0:
                    draft_btn.click()
            else:
                publish_btn = page.get_by_text("发布", exact=True).first
                if publish_btn.count() > 0:
                    publish_btn.click()
            time.sleep(5)
            return {"success": True, "message": "视频发布任务已提交", "final_url": page.url}

        except Exception as e:
            return {"success": False, "message": str(e)}
        finally:
            context.close()
