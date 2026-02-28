package com.news.publish.service.automation.skill;

import com.microsoft.playwright.*;
import com.news.publish.service.automation.InteractiveBrowserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 百家号图文自动发布 Skill
 * 
 * 基于百家号编辑页真实 DOM 结构：
 * - 标题：Lexical 编辑器 (div[data-lexical-editor="true"], contenteditable)
 * - 正文：UEditor 富文本编辑器 (iframe 内)，通过 UE_V2 API 直接注入 HTML
 * - 发布按钮：button.cheetah-btn-primary.cheetah-btn-solid > span(发布)
 * - 存草稿按钮：button.cheetah-btn-outlined > span(存草稿)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaijiahaoPublishSkill {

    private final InteractiveBrowserService browserService;

    private static final String EDITOR_URL = "https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1";

    /**
     * 执行百家号图文发布
     */
    public PublishResult execute(PublishParams params) {
        log.info("== 百家号图文发布 Skill 启动 == 标题: {}", params.getTitle());
        
        PublishResult result = new PublishResult();
        String sessionId = null;
        
        try {
            // Step 1: 启动浏览器会话
            log.info("[Step 1/6] 启动浏览器会话，导航到百家号编辑页...");
            sessionId = browserService.startSession(EDITOR_URL, params.getCookieJson());
            result.setSessionId(sessionId);
            
            // 等待页面充分加载（百家号编辑器加载较慢）
            Thread.sleep(5000);
            
            // Step 2: 检查登录状态
            log.info("[Step 2/6] 检查登录状态...");
            String currentUrl = browserService.getCurrentUrl(sessionId);
            if (currentUrl != null && (currentUrl.contains("passport") || currentUrl.contains("login"))) {
                log.warn("检测到未登录状态！当前URL: {}", currentUrl);
                result.setSuccess(false);
                result.setMessage("需要手动登录百家号。请在浏览器窗口中完成登录后重试。");
                result.setNeedLogin(true);
                return result;
            }
            
            // 额外等待编辑器初始化（UEditor 需要时间加载）
            log.info("[Step 2/6] 等待编辑器初始化...");
            Object readyResult = browserService.executeScript(sessionId,
                "(() => {" +
                "  return new Promise((resolve) => {" +
                "    let attempts = 0;" +
                "    const check = () => {" +
                "      attempts++;" +
                "      if (window.UE_V2 && window.UE_V2.instants && window.UE_V2.instants['ueditorInstant0']) {" +
                "        resolve('editor_ready');" +
                "      } else if (attempts > 30) {" + // 增加等待时间
                "        resolve('editor_timeout');" +
                "      } else {" +
                "        setTimeout(check, 500);" +
                "      }" +
                "    };" +
                "    check();" +
                "  });" +
                "})()");
            
            if (readyResult == null) {
                throw new RuntimeException("编辑器初始化监控脚本执行失败，可能是页面已关闭。");
            }
            log.info("编辑器就绪检查结果: {}", readyResult);
            
            // Step 3: 填入标题 —— 点击 Lexical 编辑器并键盘输入
            log.info("[Step 3/6] 填入标题: {}", params.getTitle());
            Object scriptTitleRes = browserService.executeScript(sessionId,
                "(() => {" +
                "  var titleEditor = document.querySelector('div[data-lexical-editor=\"true\"][contenteditable=\"true\"]');" +
                "  if (titleEditor) {" +
                "    titleEditor.focus();" +
                "    var p = titleEditor.querySelector('p');" +
                "    if (p) { p.innerHTML = '<br>'; }" +
                "    var range = document.createRange();" +
                "    var sel = window.getSelection();" +
                "    range.selectNodeContents(titleEditor);" +
                "    range.collapse(false);" +
                "    sel.removeAllRanges();" +
                "    sel.addRange(range);" +
                "    return 'title_focused';" +
                "  }" +
                "  return 'title_not_found';" +
                "})()");
            
            if (scriptTitleRes == null) {
                throw new RuntimeException("标题定位脚本执行失败，请检查浏览器是否已关闭。");
            }
            log.info("[Step 3/6] 标题定位结果: {}", scriptTitleRes);
            
            Thread.sleep(500);
            
            // 用键盘逐字输入标题
            browserService.keyboardType(sessionId, params.getTitle());
            log.info("[Step 3/6] 标题输入指令已发送");
            Thread.sleep(1000);
            
            // Step 4: 填入正文 —— 通过 UEditor API 直接注入 HTML
            log.info("[Step 4/6] 填入正文内容 (长度: {} 字符)...", params.getHtmlContent().length());
            
            // 对 HTML 内容进行转义，以安全传入 JS
            String escapedHtml = escapeForJs(params.getHtmlContent());
            
            Object scriptContentRes = browserService.executeScript(sessionId,
                "(() => {" +
                "  try {" +
                "    var editor = window.UE_V2 && window.UE_V2.instants && window.UE_V2.instants['ueditorInstant0'];" +
                "    if (editor && editor.setContent) {" +
                "      editor.setContent('" + escapedHtml + "');" +
                "      return 'content_set_via_ueditor_api';" +
                "    }" +
                "    var iframe = document.querySelector('#ueditor_0');" +
                "    if (iframe && iframe.contentDocument) {" +
                "      var body = iframe.contentDocument.querySelector('body.view');" +
                "      if (body) {" +
                "        body.innerHTML = '" + escapedHtml + "';" +
                "        return 'content_set_via_iframe';" +
                "      }" +
                "    }" +
                "    return 'content_editor_not_found';" +
                "  } catch (e) {" +
                "    return 'content_error: ' + e.message;" +
                "  }" +
                "})()");
            
            if (scriptContentRes == null) {
                throw new RuntimeException("正文注入脚本执行失败。");
            }
            log.info("[Step 4/6] 正文注入结果: {}", scriptContentRes);
            Thread.sleep(2000);
            
            // Step 5: 封面设置（使用 AI 智能生图并确认）
            log.info("[Step 5/6] 尝试进行封面设置（选择封面 -> AI封图 -> 根据全文智能生成封面 -> 确定）...");
            Object coverRes = browserService.executeScript(sessionId,
                "(() => {" +
                "  return new Promise((resolve) => {" +
                "    let step = 0;" +
                "    let attempts = 0;" +
                "    const interval = setInterval(() => {" +
                "      try {" +
                "        attempts++;" +
                "        if (step === 0) {" +
                "           var allDivs = document.querySelectorAll('div');" +
                "           var coverBtn = null;" +
                "           for (let i = 0; i < allDivs.length; i++) {" +
                "             if (allDivs[i].textContent.trim() === '选择封面' && allDivs[i].className.includes('-content')) {" +
                "               coverBtn = allDivs[i];" +
                "               break;" +
                "             }" +
                "           }" +
                "           if (coverBtn) {" +
                "             coverBtn.click(); step = 1; attempts = 0;" +
                "           } else if (attempts > 10) {" +
                "             clearInterval(interval); resolve('cover_btn_not_found');" +
                "           }" +
                "        } else if (step === 1) {" +
                "           var aiTab = document.querySelector('div.cheetah-tabs-tab[data-node-key=\"ai\"]');" +
                "           if (aiTab) {" +
                "               aiTab.click(); step = 2; attempts = 0;" +
                "           } else if (attempts > 10) {" +
                "               clearInterval(interval); resolve('ai_tab_not_found');" +
                "           }" +
                "        } else if (step === 2) {" +
                "           var modal = document.querySelector('.cheetah-modal');" +
                "           if (!modal) { if(attempts > 5) { clearInterval(interval); resolve('modal_not_found'); } return; }" +
                "           var promptInput = modal.querySelector('textarea#content') || modal.querySelector('textarea');" +
                "           if(promptInput) {" +
                "               promptInput.value = '" + escapeForJs(params.getTitle()) + "';" +
                "               var event = document.createEvent('HTMLEvents');" +
                "               event.initEvent('input', true, true);" +
                "               promptInput.dispatchEvent(event);" +
                "               var sendIcon = modal.querySelector('div[class*=\"-iconWrap\"] img') || modal.querySelector('div[class*=\"-iconWrap\"]');" +
                "               if(sendIcon) {" +
                "                   setTimeout(() => sendIcon.click(), 500); step = 3; attempts = 0;" +
                "               } else {" +
                "                   var genBtns = modal.querySelectorAll('span');" +
                "                   let genBtnFound = false;" +
                "                   for(let i=0; i<genBtns.length; i++) {" +
                "                       if(genBtns[i].textContent.trim().includes('根据全文智能生成封面')) {" +
                "                           setTimeout(() => genBtns[i].click(), 500);" +
                "                           genBtnFound = true; step = 3; attempts = 0; break;" +
                "                       }" +
                "                   }" +
                "                   if(!genBtnFound && attempts > 10) {" +
                "                       clearInterval(interval); resolve('generate_btn_not_found');" +
                "                   }" +
                "               }" +
                "           } else if (attempts > 10) {" +
                "               clearInterval(interval); resolve('prompt_input_not_found');" +
                "           }" +
                "        } else if (step === 3) {" +
                "           var modal = document.querySelector('.cheetah-modal');" +
                "           if (!modal) { clearInterval(interval); resolve('modal_closed_prematurely'); return; }" +
                "           var confirmBtn = null;" +
                "           var btns = modal.querySelectorAll('button');" +
                "           for(var i=0; i<btns.length; i++) {" +
                "               if(btns[i].textContent.trim().includes('确定')) {" +
                "                   confirmBtn = btns[i]; break;" +
                "               }" +
                "           }" +
                "           if (confirmBtn) {" +
                "               var btnText = confirmBtn.textContent.trim();" +
                "               if (btnText.includes('(1)')) {" +
                "                   step = 4; attempts = 0;" +
                "               } else if (attempts > 5 && attempts % 10 === 0) {" +
                "                   var ims = modal.querySelectorAll('img[class*=\"-img\"], canvas[class*=\"-canvas\"]');" +
                "                   if (ims.length > 0) ims[0].click();" +
                "               } else if (attempts > 120) {" +
                "                   clearInterval(interval); resolve('timeout_waiting_for_images');" +
                "               }" +
                "           } else if (attempts > 20) {" +
                "               clearInterval(interval); resolve('confirm_btn_missing_in_modal');" +
                "           }" +
                "        } else if (step === 4) {" +
                "           if (attempts === 2) {" +
                "               var modal = document.querySelector('.cheetah-modal');" +
                "               if (modal) {" +
                "                   var confirmBtn2 = null;" +
                "                   var btns2 = modal.querySelectorAll('button');" +
                "                   for(var j=0; j<btns2.length; j++) {" +
                "                       if(btns2[j].textContent.trim().includes('确定 (1)')) {" +
                "                           confirmBtn2 = btns2[j]; break;" +
                "                       }" +
                "                   }" +
                "                   if (confirmBtn2) {" +
                "                       var rect = confirmBtn2.getBoundingClientRect();" +
                "                       var clickEvent = new MouseEvent('click', { bubbles: true, cancelable: true, view: window, clientX: rect.left + rect.width / 2, clientY: rect.top + rect.height / 2 });" +
                "                       var mouseDownEvent = new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window });" +
                "                       var mouseUpEvent = new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window });" +
                "                       confirmBtn2.dispatchEvent(mouseDownEvent);" +
                "                       confirmBtn2.dispatchEvent(mouseUpEvent);" +
                "                       confirmBtn2.dispatchEvent(clickEvent);" +
                "                   }" +
                "               }" +
                "               step = 5; attempts = 0;" +
                "           }" +
                "        } else if (step === 5) {" +
                "           var modal = document.querySelector('.cheetah-modal');" +
                "           if (!modal || modal.style.display === 'none' || attempts > 10) {" +
                "               clearInterval(interval); resolve('cover_selected');" +
                "           }" +
                "        }" +
                "      } catch(e) {" +
                "        clearInterval(interval);" +
                "        resolve('error: ' + e.message);" +
                "      }" +
                "    }, 1000);" +
                "    setTimeout(() => {" +
                "       if(interval) clearInterval(interval);" +
                "       resolve('timeout_after_150s');" +
                "    }, 150000);" +
                "  });" +
                "})()");
            
            log.info("[Step 5/6] 封面操作结果: {}", coverRes);
            
            if (coverRes == null || !coverRes.toString().contains("cover_selected")) {
                log.warn("封面设置未成功，停止执行发布流程以防页面异常跳转。结果: {}", coverRes);
                result.setSuccess(false);
                result.setMessage("封面设置未成功: " + coverRes);
                return result;
            }
            
            Thread.sleep(3000);
            
            // Step 6: 决定是存草稿还是发布
            if (params.isDraft()) {
                log.info("[Step 6/6] 点击存草稿...");
                Object draftRes = browserService.executeScript(sessionId,
                    "(() => {" +
                    "  var allBtns = document.querySelectorAll('button.cheetah-btn-outlined');" +
                    "  for (var i = 0; i < allBtns.length; i++) {" +
                    "    if (allBtns[i].closest('.cheetah-modal')) continue;" +
                    "    if (allBtns[i].textContent.trim().includes('存草稿')) {" +
                    "      allBtns[i].click();" +
                    "      return 'draft_clicked';" +
                    "    }" +
                    "  }" +
                    "  return 'draft_btn_not_found';" +
                    "})()");
                if (draftRes == null) {
                    throw new RuntimeException("存草稿按钮点击脚本执行失败。");
                }
                log.info("[Step 6/6] 存草稿结果: {}", draftRes);
            } else {
                log.info("[Step 6/6] 点击发布按钮...");
                Object pubRes = browserService.executeScript(sessionId,
                    "(() => {" +
                    "  var pBtns = document.querySelectorAll('button.cheetah-btn-primary.cheetah-btn-solid');" +
                    "  for (var i = 0; i < pBtns.length; i++) {" +
                    "    if (pBtns[i].closest('.cheetah-modal')) continue;" +
                    "    if (pBtns[i].textContent.trim().includes('发布')) {" +
                    "      pBtns[i].click();" +
                    "      return 'publish_clicked';" +
                    "    }" +
                    "  }" +
                    "  return 'publish_btn_not_found';" +
                    "})()");
                
                if (pubRes == null) {
                    throw new RuntimeException("发布按钮点击脚本执行失败。");
                }
                log.info("[Step 6/6] 发布结果: {}", pubRes);
            }
            
            Thread.sleep(3000);
            
            String finalUrl = browserService.getCurrentUrl(sessionId);
            log.info("操作完成，当前页面URL: {}", finalUrl);
            
            result.setSuccess(true);
            result.setMessage("百家号图文操作完成");
            result.setFinalUrl(finalUrl);
            
        } catch (Exception e) {
            log.error("百家号发布 Skill 执行异常: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("发布失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 将 Java 字符串转义为可以安全嵌入 JS 单引号字符串的格式
     */
    private String escapeForJs(String input) {
        if (input == null) return "";
        return input
            .replace("\\", "\\\\")   // 反斜杠
            .replace("'", "\\'")      // 单引号
            .replace("\"", "\\\"")    // 双引号
            .replace("\n", "\\n")     // 换行
            .replace("\r", "\\r")     // 回车
            .replace("\t", "\\t")     // Tab
            .replace("</", "<\\/");   // 防止 </script> 提前关闭
    }

    /**
     * 将 HTML 转为纯文本（备用方法）
     */
    private String stripHtml(String html) {
        if (html == null) return "";
        return html
            .replaceAll("<br\\s*/?>", "\n")
            .replaceAll("</p>", "\n")
            .replaceAll("</h[1-6]>", "\n\n")
            .replaceAll("</div>", "\n")
            .replaceAll("<[^>]+>", "")
            .replaceAll("&nbsp;", " ")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .replaceAll("&amp;", "&")
            .replaceAll("\n{3,}", "\n\n")
            .trim();
    }

    // ==================== 数据类 ====================

    @Data
    public static class PublishParams {
        private String title;
        private String htmlContent;
        private String category;
        private String cookieJson;
        private boolean draft = false;
    }

    @Data
    public static class PublishResult {
        private boolean success;
        private boolean needLogin;
        private String message;
        private String sessionId;
        private String finalUrl;
    }
}
