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

            is_iframe = page.locator("#ueditor_0").count() > 0
            if is_iframe:
                frame = page.frame_locator("#ueditor_0")
                frame.locator("body").click(position={"x": 5, "y": 5}, force=True)
                time.sleep(0.5)
            else:
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
                title_loc = page.locator("#real-title-editor")
                if title_loc.count() > 0:
                    title_loc.fill(title)
                    page.evaluate("document.getElementById('real-title-editor').blur()")
                    time.sleep(0.5)

            if is_iframe:
                write_js = "(bodyEl, html) => { bodyEl.innerHTML = html; }"
                page.frame_locator("#ueditor_0").locator("body").evaluate(write_js, content)
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

            single_radio = page.get_by_text("单图", exact=True).first
            if single_radio.count() > 0:
                single_radio.click(timeout=5000)
            extract_span = page.get_by_text("提取正文图", exact=True).first
            if extract_span.count() > 0:
                extract_span.click(timeout=3000)
                time.sleep(2)

            time.sleep(3)
            is_draft = params.get("draft", False)
            if is_draft:
                _smart_action(page, "存草稿", "存草稿")
            else:
                _smart_action(page, "发布", "发布")
            print("DEBUG: 发布指令已执行")
            time.sleep(10)
            return {"success": True, "message": "发布成功", "final_url": page.url}
        except Exception as e:
            return {"success": False, "message": str(e)}
        finally:
            context.close()
