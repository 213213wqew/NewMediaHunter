<template>
  <div class="content-hub-container">
    <!-- 三栏布局：资源池 | 编辑器 | 分发设置 -->
    <div class="hub-layout">
      


      <!-- 第二栏：核心编辑器 -->
      <main class="hub-main editor-workspace">
        <div class="editor-header">
          <input v-model="form.title" class="main-title-input" placeholder="请在这里输入文章标题..." />
          <div class="ai-writing-banner" v-if="valueHtml">
            <div class="ai-actions">
              <button 
                class="btn btn-secondary glow-effect" 
                :disabled="aiWriting || !valueHtml" 
                @click="handleAiPolish"
              >
                ✨ AI 润色原文
              </button>
              <button 
                v-if="selectedNews"
                class="btn btn-ghost" 
                @click="handleReCompare"
                style="margin-left: 10px; border: 1px solid rgba(59, 130, 246, 0.5); color: #3b82f6;"
              >
                🔍 对比原文
              </button>
              <button 
                class="btn btn-ghost" 
                :disabled="aiWriting || !valueHtml" 
                @click="handleAiSmartImages"
                style="margin-left: 10px; border: 1px solid rgba(255,255,255,0.1)"
              >
                🖼️ 一键智能插图
              </button>
            </div>
          </div>
        </div>

        <div class="editor-scroller scroller">
          <div class="editor-wrapper card" style="position: relative; overflow: hidden;">
            <!-- AI Loading Overlay -->
            <div v-if="aiWriting" class="ai-writing-overlay">
              <div class="ai-loader"></div>
              <div class="ai-text">✨ 正在深度分析原文与创作中...</div>
            </div>
            
            <Toolbar
              class="w-toolbar"
              :editor="editorRef"
              :defaultConfig="toolbarConfig"
              mode="default"
            />
            <Editor
              class="w-editor"
              v-model="valueHtml"
              :defaultConfig="editorConfig"
              mode="default"
              @onCreated="handleCreated"
            />
          </div>
          
          <div class="form-group-inline card">
            <label>文章摘要</label>
            <input v-model="form.summary" placeholder="AI 摘要或手动输入简要描述..." />
            <button class="btn btn-ghost" @click="handleAiSummary" :disabled="!valueHtml">AI 生成</button>
          </div>
        </div>
      </main>

      <!-- 第三栏：分发设置 -->
      <aside class="hub-sidebar publish-settings">
        <div class="panel-header">
          <div class="section-title">📡 分发渠道</div>
        </div>
        
        <div class="settings-content scroller">
          <div class="card setting-card">
            <div v-if="platformList.length === 0" class="account-empty-hint">
              正在加载平台列表...
            </div>
            <div v-else class="account-grid">
                <div
                  v-for="plat in platformList" :key="plat.id"
                  class="account-chip"
                  :class="{ 
                    selected: form.selectedPlatforms.includes(plat.platformKey)
                  }"
                  @click="togglePlatform(plat)"
                >
                  <div class="acc-icon">{{ getPlatformIcon(plat.id) }}</div>
                  <div class="acc-info">
                    <div class="acc-name">{{ plat.platformName }}</div>
                    <div class="acc-platform-status">
                      <span class="credential-ok">✓ 支持自动化发布</span>
                    </div>
                  </div>
                  <div class="selection-indicator"></div>
                </div>
            </div>
          </div>

          <div class="panel-header" style="margin-top: 10px;">
            <div class="section-title">⚙️ 发布选项</div>
          </div>

          <div class="card setting-card">
            <div class="form-group">
              <label class="form-label">发布类型</label>
              <div class="publish-type-selector">
                <div class="type-item" :class="{ active: form.publishType === 'news' }" @click="form.publishType = 'news'">图文</div>
                <div class="type-item" :class="{ active: form.publishType === 'video' }" @click="form.publishType = 'video'">视频</div>
                <div class="type-item" :class="{ active: form.publishType === 'dynamic' }" @click="form.publishType = 'dynamic'">动态</div>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">内容分类</label>
              <select v-model="form.category" class="form-input">
                <option value="">-- 请选择分类 --</option>
                <option>科技互联网</option>
                <option>财经金融</option>
                <option>生活方式</option>
                <option>娱乐明星</option>
                <option>体育赛事</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">来源标签</label>
              <input v-model="form.tags" class="form-input" placeholder="标签1, 标签2..." />
              <button class="btn-text" @click="handleAiTags" :disabled="!valueHtml">AI 提取</button>
            </div>
            
            <div class="schedule-switch">
              <div class="switch-row">
                <span>📅 定时发布</span>
                <el-switch v-model="form.isScheduled" />
              </div>
              <el-date-picker
                v-if="form.isScheduled"
                v-model="form.scheduledTime"
                type="datetime"
                placeholder="选择时间"
                style="width: 100%; margin-top: 10px;"
                value-format="YYYY-MM-DDTHH:mm:ss"
              />
            </div>
          </div>

          <div class="panel-header" style="margin-top: 10px;">
            <div class="section-title">📱 仿真预览视角</div>
          </div>

          <div class="card setting-card automation-preview-card">
            <div v-if="automationActive" class="live-viewport">
              <div class="viewport-header">
                <span>正在自动化发布至: {{ currentProcessingPlatform }}</span>
                <span class="pulse-dot"></span>
              </div>
              <img :src="'data:image/png;base64,' + currentSnapshot" v-if="currentSnapshot" class="preview-snapshot" />
              <div v-else class="loading-preview">正在连接浏览器视口...</div>
            </div>
            <div v-else class="preview-placeholder-modern">
              <div class="placeholder-icon">🤖</div>
              <div class="placeholder-text">发布时将在此显示实时自动化画面</div>
              <button class="btn btn-ghost btn-sm" @click="handleSimulationPreview">切换至仿真预览</button>
            </div>
          </div>
        </div>

        <div class="publish-footer">
          <button class="btn btn-ghost" @click="handleSave(0)">💾 保存草稿</button>
          <button 
            class="btn btn-primary publish-btn" 
            :disabled="publishing" 
            @click="handlePublish"
          >
            {{ publishing ? '分发中...' : '🚀 一键同步发布' }}
          </button>
        </div>
      </aside>

    </div>

    <!-- 预览弹窗：支持仿真排版 -->
    <div v-if="showPreview" class="preview-overlay" @click.self="showPreview = false">
      <div class="preview-modal card" :class="'view-' + selectedPlatform">
        <div class="preview-header">
          <div class="section-title" style="margin: 0;">📱 {{ platforms.find(p => p.id === selectedPlatform)?.name }} 仿真预览</div>
          <button class="btn btn-ghost" @click="showPreview = false">❌ 关闭</button>
        </div>
        <div class="preview-body scroller">
          <div class="mobile-frame" :style="selectedPlatform === 'bjh' ? 'background: #fdfdfd;' : ''">
            <div class="mobile-status-bar" :style="selectedPlatform === 'bjh' ? 'color: #333;' : ''">9:41</div>
            
            <!-- 百家号风格展示 -->
            <template v-if="selectedPlatform === 'bjh'">
              <h1 class="preview-title bjh-title">{{ form.title || '（未命名）' }}</h1>
              <div class="bjh-author-bar">
                <div class="bjh-avatar">百</div>
                <div class="bjh-author-info">
                  <div class="name">百度资讯号</div>
                  <div class="meta">官方认证 · 1.2亿阅读</div>
                </div>
                <div class="bjh-follow-btn">+ 关注</div>
              </div>
            </template>

            <!-- 今日头条风格展示 -->
            <template v-else-if="selectedPlatform === 'tt'">
              <h1 class="preview-title tt-title">{{ form.title || '（未命名）' }}</h1>
              <div class="tt-author-bar">
                <div class="tt-avatar-wrapper">
                  <div class="tt-avatar">头</div>
                  <div class="tt-v-mark">V</div>
                </div>
                <div class="tt-author-info">
                  <div class="name">头条号创作中心</div>
                  <div class="meta">优质科技领域创作者 · 刚刚</div>
                </div>
                <button class="tt-follow-btn">关注</button>
              </div>
            </template>
            
            <h1 v-else class="preview-title">{{ form.title || '（未命名）' }}</h1>

            <div class="preview-author-bar" v-if="selectedPlatform !== 'default' && selectedPlatform !== 'bjh' && selectedPlatform !== 'tt'">
              <div class="avatar"></div>
              <div class="author-info">
                <div class="name">创作者中心专栏</div>
                <div class="meta">2024-05-24 · 互联网优质创作者</div>
              </div>
            </div>
            
            <div class="preview-content w-e-text-container" :class="selectedPlatform" v-html="valueHtml"></div>
            
            <div class="preview-footer-bar" v-if="selectedPlatform !== 'default'">
              <div class="interaction-hint">写下你的评论...</div>
              <div class="icons">❤️ 💬 ⭐ ⬆️</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- AI 润色对比弹窗 -->
    <div v-if="showPolishDialog" class="polish-overlay" @click.self="showPolishDialog = false">
      <div class="polish-modal card">
        <div class="polish-header">
          <div class="section-title">🔄 {{ polishMode === 'rewrite' ? 'AI 改写建议对比' : 'AI 润色建议对比' }}</div>
          <div class="header-actions">
            <button class="btn btn-ghost" style="margin-right: 10px;" @click="showPolishDialog = false">放弃建议</button>
            <button class="btn btn-primary" @click="applyPolish">采纳并替换 ✅</button>
          </div>
        </div>
        <div class="polish-comparison">
          <div class="comparison-column original">
            <div class="column-label">{{ polishMode === 'rewrite' ? '原文热点摘要' : '编辑器原始内容' }}</div>
            <div class="content-box scroller w-e-text-container" v-html="originalContent"></div>
          </div>
          <div class="comparison-column polished">
            <div class="column-label">AI {{ polishMode === 'rewrite' ? '深度改写版' : '润色优化版' }}</div>
            <div class="content-box scroller w-e-text-container" v-html="polishedContent"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import '@wangeditor/editor/dist/css/style.css'; 
import { onBeforeUnmount, ref, shallowRef, onMounted, reactive } from 'vue';
import { useRoute } from 'vue-router';
import { Editor, Toolbar } from '@wangeditor/editor-for-vue';
import { getAccountList } from '../../api/account';
import { saveArticle, getArticleList, getArticle } from '../../api/article';
import { submitPublishTask, getAiSummary, getAiSuggestedTitles, getAiTags, getAiCategory } from '../../api/publish';
import { fetchHotNews, fetchArticleContent, generateArticle, matchImage, polishArticle, suggestImages, type HotNews } from '../../api/ai';
import { getPlatformList } from '../../api/platform';
import type { Account, Article, Platform } from '../../types';
import request from '../../utils/request';
import { ElMessage } from 'element-plus';

// ----------------- 状态定义 -----------------
const $route = useRoute();
const leftTab = ref('articles'); // articles | news
const leftLoading = ref(false);
const articleList = ref<Article[]>([]);
const newsList = ref<HotNews[]>([]);
const newsKeyword = ref('科技热点');
const selectedNews = ref<HotNews | null>(null);
const currentArticleId = ref<number | null>(null);
const fetchedOriginalText = ref('');

const editorRef = shallowRef();
const valueHtml = ref('');
const showPreview = ref(false);
const aiWriting = ref(false);
const publishing = ref(false);
const aiLoading = ref(false);

// 润色对比相关
const showPolishDialog = ref(false);
const polishMode = ref<'polish' | 'rewrite'>('polish'); // polish: 润色, rewrite: 改写
const originalContent = ref('');
const polishedContent = ref('');

const form = reactive({
  title: '',
  summary: '',
  category: '',
  tags: '',
  selectedAccounts: [] as number[],
  selectedPlatforms: [] as string[], // 使用平台 Key
  publishType: 'news', // news | video | dynamic
  isScheduled: false,
  scheduledTime: '',
});

// 自动化实时预览相关
const automationActive = ref(false);
const sessionId = ref<string | null>(null);
const currentSnapshot = ref<string | null>(null);
const autoTimer = ref<any>(null);
const currentProcessingPlatform = ref<string | null>(null);
const platformList = ref<Platform[]>([]);

// 预览平台
const selectedPlatform = ref('default');
const platforms = [
  { id: 'default', name: '标准网页' },
  { id: 'bjh', name: '百家号 (移动端)' },
  { id: 'tt', name: '今日头条 (移动端)' }
];

// WangEditor 配置
const toolbarConfig = { excludeKeys: ['fullScreen'] };
const editorConfig = { 
  placeholder: '在这里开启您的创作之旅...',
  MENU_CONF: {
    uploadImage: {
      server: '/api/file/upload',
      fieldName: 'file',
      maxFileSize: 5 * 1024 * 1024,
      headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
      customInsert(res: any, insertFn: any) {
        if (res.errno === 0) insertFn(res.data.url, res.data.alt, res.data.url);
        else ElMessage.error('图片上传失败');
      }
    }
  }
};

// ----------------- 生命周期 -----------------
onMounted(async () => {
    // 1. 兼容旧版跳转逻辑
    const pt = localStorage.getItem('pending_article_title');
    const pc = localStorage.getItem('pending_article_content');
    if (pt) { form.title = pt; localStorage.removeItem('pending_article_title'); }
    if (pc) { valueHtml.value = pc; localStorage.removeItem('pending_article_content'); }

    // 2. 处理来自“热点资讯”独立页面的透传数据
    const autoNewsData = localStorage.getItem('auto_create_news');
    if (autoNewsData) {
      try {
        const item = JSON.parse(autoNewsData) as HotNews;
        selectedNews.value = item;
        form.title = item.title; // 立即填充标题
        leftTab.value = 'news'; // 切换到资讯标签
        localStorage.removeItem('auto_create_news');
        
        // 自动触发撰稿
        setTimeout(() => {
          handleAiWrite();
        }, 500);
      } catch (e) {
        console.error('Failed to parse auto_create_news', e);
      }
    }
    
    // 3. 处理路由参数传递的 ID（从“我的发文”跳转编辑）
    const queryId = $route.query.id;
    if (queryId) {
      const targetId = Number(queryId);
      getArticle(targetId).then(found => {
        if (found) {
          handleLoadArticle(found);
          ElMessage.info('已成功加载文稿草稿');
        }
      }).catch(e => console.warn('加载草稿失败', e));
    }

    // 4. 并发加载其他数据（放到后面防止阻塞前面的同步/本地逻辑）
    loadMyArticles();
    loadAccounts();
    
    try {
      const platRes = await getPlatformList();
      platformList.value = platRes || [];
    } catch (e) {
      console.error('加载平台列表失败', e);
      platformList.value = [];
    }
});

onBeforeUnmount(() => {
    const editor = editorRef.value;
    if (editor) editor.destroy();
});

// ----------------- 数据加载 -----------------
const loadMyArticles = async () => {
  leftLoading.value = true;
  try {
    const res = await getArticleList();
    articleList.value = res || [];
  } finally {
    leftLoading.value = false;
  }
};

const loadAccounts = async () => {
  try {
    const res = await getAccountList();
    accountList.value = res || [];
  } catch (err) {}
};

const accountList = ref<Account[]>([]);

// ----------------- 热点资讯 -----------------
const handleFetchNews = async () => {
  if (!newsKeyword.value) return;
  leftLoading.value = true;
  try {
    const res = await fetchHotNews(newsKeyword.value);
    newsList.value = res || [];
    if (res && res.length > 0) selectedNews.value = res[0] || null;
  } finally {
    leftLoading.value = false;
  }
};

const handleSelectNews = (item: HotNews) => {
  selectedNews.value = item;
};

// ----------------- 核心交互 -----------------
const handleLoadArticle = (item: Article) => {
  currentArticleId.value = item.id!;
  form.title = item.title;
  form.summary = item.summary || '';
  form.category = item.category || '';
  form.tags = item.tags || '';
  valueHtml.value = item.content;
};

const handleCreated = (editor: any) => {
  editorRef.value = editor;
};

const toggleAccount = (acc: any) => {
  const id = acc.id as number;
  const idx = form.selectedAccounts.indexOf(id);
  if (idx > -1) form.selectedAccounts.splice(idx, 1);
  else form.selectedAccounts.push(id);
};

const togglePlatform = (plat: Platform) => {
  const idx = form.selectedPlatforms.indexOf(plat.platformKey);
  if (idx > -1) {
    form.selectedPlatforms.splice(idx, 1);
  } else {
    form.selectedPlatforms.push(plat.platformKey);
  }
};

const getAccountCount = (platformId: number) => {
  return accountList.value.filter(a => a.platformId === platformId).length;
};

const getPlatformIcon = (platformId: number) => {
  switch (platformId) {
    case 1: return '🐧'; // 腾讯/企鹅号
    case 2: return '百度'; // 百家号 
    case 3: return '头条'; // 今日头条
    default: return '🔗';
  }
};

// ----------------- AI 能力 -----------------
const handleAiWrite = async () => {
  if (!selectedNews.value) return;
  aiWriting.value = true;
  
  // 1. 强制展示 Loading 状态描述，提升交互感知
  ElMessage.info('正在深度分析原文正文，请稍候...');
  
  try {
    // 2. 首先尝试抓取正文内容
    let originalText = selectedNews.value.summary;
    try {
      const contentRes = await fetchArticleContent(selectedNews.value.url);
      if (contentRes && contentRes.content) {
        originalText = contentRes.content;
      }
    } catch (e) {
      console.warn('抓取正文失败，将退而求其使用摘要进行改写', e);
    }
    
    fetchedOriginalText.value = originalText;

    // 3. 提交给 AI 进行深度改写，将原始文本作为“大纲/上下文”传入
    const res = await generateArticle(selectedNews.value.title, originalText);
    
    // 4. 更新对比弹窗内容
    // 左侧显示抓取到的原文正文，右侧显示 AI 改写后的 HTML
    originalContent.value = `<h3>${selectedNews.value.title}</h3>` + 
                            `<div class="origin-meta">来源：${selectedNews.value.source} | 链接：${selectedNews.value.url}</div>` +
                            `<div class="origin-body">${originalText.replace(/\n\n/g, '<br><br>')}</div>`;
                            
    polishedContent.value = res.content;
    polishMode.value = 'rewrite';
    showPolishDialog.value = true;
    
    ElMessage.success('AI 已根据原文正文完成深度改写！');
  } catch (err) {
    ElMessage.error('AI 改写失败，请检查网络或 AI 配置');
  } finally {
    aiWriting.value = false;
  }
};

const handleAiSummary = async (isAuto?: boolean | Event) => {
  if (!valueHtml.value) return;
  try {
    const res = await getAiSummary(valueHtml.value);
    form.summary = res;
    if (isAuto !== true) ElMessage.success('摘要刷新成功');
  } catch (err) {}
};

const handleAiTags = async (isAuto?: boolean | Event) => {
  if (!valueHtml.value) return;
  try {
    const res = await getAiTags(valueHtml.value);
    form.tags = res.join(', ');
    if (isAuto !== true) ElMessage.success('标签提取成功');
  } catch (err) {}
};

const handleAiCategory = async (isAuto?: boolean | Event) => {
  if (!valueHtml.value) return;
  try {
    const categoriesStr = ['科技互联网', '财经金融', '生活方式', '娱乐明星', '体育赛事'].join(', ');
    const res = await getAiCategory(valueHtml.value, categoriesStr);
    if (res) {
      form.category = res;
      if (isAuto !== true) ElMessage.success('分类自动识别成功');
    }
  } catch (err) {}
};

const handleAiPolish = async () => {
  if (!valueHtml.value) return;
  aiWriting.value = true;
  try {
    const res = await polishArticle(valueHtml.value);
    originalContent.value = valueHtml.value;
    polishedContent.value = res.content;
    polishMode.value = 'polish';
    showPolishDialog.value = true;
  } catch (err) {
    ElMessage.error('润色失败');
  } finally {
    aiWriting.value = false;
  }
};

const handleReCompare = () => {
  if (!selectedNews.value) return;
  
  const originalHtml = fetchedOriginalText.value 
     ? `<h3>${selectedNews.value.title}</h3><div class="origin-meta">来源：${selectedNews.value.source} | 链接：${selectedNews.value.url}</div><div class="origin-body">${fetchedOriginalText.value.replace(/\n\n/g, '<br><br>')}</div>`
     : `<h3>${selectedNews.value.title}</h3><p>${selectedNews.value.summary}</p><p>来源：${selectedNews.value.source}</p>`;
  
  originalContent.value = originalHtml;
  polishedContent.value = valueHtml.value; // 当前编辑器内容
  polishMode.value = 'rewrite'; // 使用改写模式的 Label
  showPolishDialog.value = true;
};

const applyPolish = async () => {
  let finalContent = polishedContent.value;
  
  // 尝试从 AI 生成的内容中提取第一个标题（h1, h2或h3），作为文章的正式标题
  const titleMatch = finalContent.match(/<h[1-3][^>]*>(.*?)<\/h[1-3]>/i);
  if (titleMatch && titleMatch[1]) {
    // 提取纯文本标题（去除内部可能有的嵌套标签）
    const extractedTitle = titleMatch[1].replace(/<[^>]+>/g, '').trim();
    if (extractedTitle) {
      form.title = extractedTitle;
      // 从正文中移除该大标题，避免与页面上方的主标题重复
      finalContent = finalContent.replace(titleMatch[0], '').replace(/^\s*(<br\/?>\s*)+/, '');
    }
  }

  valueHtml.value = finalContent;
  showPolishDialog.value = false;
  ElMessage.success('已应用 AI 润色版本');
  
  // 如果是深度改写（一键创作流程），且摘要、标签或分类为空，则自动触发生成
  if (polishMode.value === 'rewrite') {
    const tasks = [];
    if (!form.summary) tasks.push(handleAiSummary(true));
    if (!form.tags) tasks.push(handleAiTags(true));
    if (!form.category) tasks.push(handleAiCategory(true));
    
    if (tasks.length > 0) {
      ElMessage.info('正在根据正文自动生成辅助信息...');
      await Promise.all(tasks).catch(() => {});
    }
  }

  // 润色采纳后自动保存，确保用户润色的内容不丢失
  await handleSave(0);
};

const handleAiSmartImages = async () => {
  if (!valueHtml.value) return;
  aiLoading.value = true;
  try {
    const images = await suggestImages(valueHtml.value);
    if (!images || images.length === 0) return;
    
    // 简单的智能插入逻辑：寻找 h2 标签并在其后插入图片
    let content = valueHtml.value;
    images.forEach(url => {
      const imgHtml = `<p style="text-align:center;"><img src="${url}" style="max-width:100%; border-radius:8px; margin:15px 0;" /></p>`;
      // 找第一个未被处理过的 h2
      const h2Match = content.match(/<\/h2>/);
      if (h2Match) {
         content = content.replace(/<\/h2>/, `</h2>${imgHtml}`);
      } else {
         content += imgHtml;
      }
    });
    valueHtml.value = content;
    ElMessage.success('已根据文章内容智能匹配并插入配图');
  } catch (err) {
    ElMessage.error('插图推荐失败');
  } finally {
    aiLoading.value = false;
  }
};

const handleSimulationPreview = () => {
  showPreview.value = true;
};

// ----------------- 保存与发布 -----------------
const handleSave = async (status: number) => {
  if (!form.title || !valueHtml.value) {
    ElMessage.warning('请先完善标题和正文');
    return null;
  }
  try {
    const res = await saveArticle({
      id: currentArticleId.value || undefined,
      title: form.title,
      summary: form.summary,
      content: valueHtml.value,
      category: form.category,
      tags: form.tags,
      status: status
    });
    
    if (res && res.id) {
        currentArticleId.value = res.id;
    }

    ElMessage.success(status === 0 ? '草稿已保存' : '文章已提交');
    loadMyArticles();
    return res as any;
  } catch (err) {
    ElMessage.error('操作失败');
    return null;
  }
};

const handlePublish = async () => {
  if (form.selectedPlatforms.length === 0) return ElMessage.warning('请选择至少一个发布平台');
  
  publishing.value = true;
  automationActive.value = true;
  
  try {
    const article = await handleSave(1);
    if (!article) return;

    // 依次处理选中的平台
    for (const platformKey of form.selectedPlatforms) {
      currentProcessingPlatform.value = platformKey;
      
      const platformObj = platformList.value.find(p => p.platformKey === platformKey);
      if (!platformObj) continue;
      
      const account = accountList.value.find(a => a.platformId === platformObj.id);

      ElMessage.info(`正在同步至 ${platformObj.platformName}...`);

      // === 百家号：使用专用 Skill API ===
      if (platformKey === 'baijiahao' && form.publishType === 'news') {
        const skillRes = await request.post<any>('/automation/publish/baijiahao', {
          title: form.title,
          htmlContent: valueHtml.value,
          category: form.category,
          cookieJson: account?.cookieData || '',
          draft: false
        });

        if (skillRes.code === 200) {
          const resultData = skillRes.data;
          // 用 Skill 返回的 sessionId 启动画面轮询
          if (resultData.sessionId) {
            if (autoTimer.value) clearInterval(autoTimer.value);
            sessionId.value = resultData.sessionId;
            startAutomationPolling();
          }
          
          if (resultData.needLogin) {
            ElMessage.warning(`${platformObj.platformName} 需要手动登录，请在弹出的浏览器中完成登录`);
            // 等待用户登录
            await new Promise(resolve => setTimeout(resolve, 15000));
          } else {
            ElMessage.success(`${platformObj.platformName} 自动发布完成！`);
          }
        } else {
          ElMessage.error(`${platformObj.platformName} 发布失败: ${skillRes.msg}`);
        }
        
        continue; // 处理下一个平台
      }

      // === 其他平台：通用自动化流程 ===
      let targetUrl = getPlatformEditorUrl(platformKey, form.publishType);
      const startRes = await request.post<any>('/automation/start', {
        url: targetUrl,
        cookieJson: account?.cookieData || ''
      });

      if (startRes.code === 200) {
        if (autoTimer.value) clearInterval(autoTimer.value);
        
        const oldSessionId = sessionId.value;
        sessionId.value = startRes.data;
        startAutomationPolling();
        
        if (oldSessionId) {
          request.delete(`/automation/session/${oldSessionId}`).catch(() => {});
        }

        await new Promise(resolve => setTimeout(resolve, 5000));
        
        const selectors = getPlatformSelectors(platformKey);
        await request.post('/automation/action', {
          sessionId: sessionId.value,
          type: 'fill',
          selector: selectors.title,
          value: form.title
        });
        
        await request.post('/automation/action', {
          sessionId: sessionId.value,
          type: 'fill',
          selector: selectors.content,
          value: valueHtml.value
        });

        ElMessage.success(`${platformObj.platformName} 同步填充完成`);
        await new Promise(resolve => setTimeout(resolve, 3000));
      }
    }
    
    ElMessage.success('选定平台的内容同步已全部完成！');
  } catch (e) {
    ElMessage.error('自动化分发过程中出现异常');
  } finally {
    publishing.value = false;
    // 保持 automationActive 为 true 以便用户查看最后的结果，或者可以提供关闭按钮
  }
};

const getPlatformEditorUrl = (key: string, type: string) => {
  if (key === 'baijiahao') {
    switch (type) {
      case 'video': return 'https://baijiahao.baidu.com/builder/rc/edit?type=videoV2&is_from_cms=1';
      case 'dynamic': return 'https://baijiahao.baidu.com/builder/rc/edit?type=events&is_from_cms=1';
      default: return 'https://baijiahao.baidu.com/builder/rc/edit?type=news&is_from_cms=1';
    }
  }
  
  switch (key) {
    case 'sina': return 'https://mp.sina.com.cn/main/editor';
    case 'sohu': return 'https://mp.sohu.com/mpfe/v3/main/news/addarticle';
    case 'toutiao': return 'https://mp.toutiao.com/profile_v4/graphic/publish';
    case 'netease': return 'https://mp.163.com/v2/index.html#/article/editor/news';
    case 'dayuhao': return 'https://mp.dayu.com/dashboard/article/write';
    case 'qiehao': return 'https://om.qq.com/article/index';
    default: return 'https://www.google.com';
  }
};

const getPlatformSelectors = (key: string) => {
  // 基础选择器配置，实际应用中建议存储在数据库
  return {
    title: 'input[placeholder*="标题"]',
    content: '.w-e-text-container [contenteditable="true"]'
  };
};

const startAutomationPolling = () => {
  if (autoTimer.value) clearInterval(autoTimer.value);
  autoTimer.value = setInterval(async () => {
    if (!sessionId.value) return;
    try {
      const res = await request.get<any>(`/automation/snapshot/${sessionId.value}`);
      if (res.code === 200) currentSnapshot.value = res.data;
    } catch (e) {}
  }, 1000);
};
</script>

<style scoped>
.content-hub-container {
  height: calc(100vh - 80px); /* 减去顶部边距 */
  margin: -20px;
  overflow: hidden;
}

.hub-layout {
  display: grid;
  grid-template-columns: 1fr 340px;
  height: 100%;
  background: #0f172a;
}

.hub-sidebar {
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.02);
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  /* 关键：和 hub-main 一样，必须约束高度才能触发内部滚动 */
  min-height: 0;
  overflow: hidden;
}

.publish-settings {
  border-right: none;
  border-left: 1px solid rgba(255, 255, 255, 0.05);
  min-height: 0;
  overflow: hidden;
  background: var(--bg-card);
}

/* AI Writing Overlay */
.ai-writing-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(15, 23, 42, 0.85);
  backdrop-filter: blur(4px);
  z-index: 99;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #fff;
  border-radius: 8px;
}

.ai-loader {
  width: 50px;
  height: 50px;
  border: 4px solid rgba(59, 130, 246, 0.2);
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

.ai-text {
  font-size: 16px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 2px 4px rgba(0,0,0,0.5);
  animation: pulse 2s infinite;
}

@keyframes spin { 
  to { transform: rotate(360deg); } 
}

.panel-header {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.form-group {
  margin-bottom: 16px;
  position: relative;
}

.form-label {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.form-input {
  width: 100%;
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: white;
  padding: 8px 12px;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
}

.form-input:focus {
  border-color: #3b82f6;
}

.btn-text {
  position: absolute;
  right: 12px;
  bottom: 8px;
  background: #1e293b;
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #3b82f6;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
}

.btn-text:hover {
  background: #3b82f6;
  color: white;
}

.setting-card {
  margin: 10px 16px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 12px;
}

.account-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.account-chip {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.account-chip:hover {
  background: rgba(255, 255, 255, 0.06);
  transform: translateY(-2px);
  border-color: rgba(255, 255, 255, 0.15);
}

.account-chip.selected {
  background: rgba(59, 130, 246, 0.1);
  border-color: #3b82f6;
  box-shadow: 0 0 20px rgba(59, 130, 246, 0.15);
}

.account-chip.no-credential {
  opacity: 0.8;
}

.acc-icon {
  width: 40px;
  height: 40px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: bold;
  color: #94a3b8;
}

.account-chip.selected .acc-icon {
  background: #3b82f6;
  color: white;
}

.acc-info {
  flex: 1;
}

.acc-name {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
  margin-bottom: 2px;
}

.acc-platform-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.platform-name {
  color: #64748b;
}

.credential-warning {
  color: #f59e0b;
}

.credential-ok {
  color: #10b981;
}

.selection-indicator {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  transition: all 0.2s;
}

.account-chip.selected .selection-indicator {
  background: #3b82f6;
  border-color: #3b82f6;
}

.account-chip.selected .selection-indicator::after {
  content: "✓";
  color: white;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #94a3b8;
}

.tab-header {
  display: flex;
  background: rgba(0, 0, 0, 0.2);
  padding: 4px;
  border-radius: 8px;
  margin-bottom: 12px;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 6px;
  font-size: 13px;
  color: #94a3b8;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
}

.tab-item.active {
  background: #3b82f6;
  color: white;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.search-bar {
  display: flex;
  gap: 8px;
  background: rgba(255, 255, 255, 0.05);
  padding: 4px 10px;
  border-radius: 8px;
}

.search-bar input {
  flex: 1;
  background: transparent;
  border: none;
  color: white;
  font-size: 12px;
  outline: none;
}

.list-container {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.list-item {
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid transparent;
  transition: all 0.2s;
}

.list-item:hover {
  background: rgba(255, 255, 255, 0.05);
}

.list-item.active {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.05);
}

.item-title {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  margin-bottom: 6px;
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-meta {
  font-size: 11px;
  color: #64748b;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.news-source {
  font-size: 10px;
  color: #3b82f6;
  margin-bottom: 4px;
}

.status-tag {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
}

.status-0 { background: rgba(100, 116, 139, 0.1); color: #94a3b8; }
.status-1 { background: rgba(16, 185, 129, 0.1); color: #10b981; }

/* 核心编辑器部分 */
.hub-main {
  display: flex;
  flex-direction: column;
  background: #020617;
  /* 关键：约束高度，让内部 flex 子项可以触发 overflow-y: auto */
  min-height: 0;
  overflow: hidden;
}

.editor-header {
  padding: 20px 40px;
}

.main-title-input {
  width: 100%;
  background: transparent;
  border: none;
  font-size: 28px;
  font-weight: 700;
  color: #f8fafc;
  outline: none;
  margin-bottom: 10px;
}

/* 仿真预览增强样式 */
.bjh-title {
  color: #1a1a1a !important;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  margin-top: 20px;
}

.bjh-author-bar {
  display: flex;
  align-items: center;
  padding: 15px 0;
  margin-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.bjh-avatar {
  width: 36px;
  height: 36px;
  background: #3b82f6;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: bold;
  margin-right: 10px;
}

.bjh-author-info {
  flex: 1;
}

.bjh-author-info .name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.bjh-author-info .meta {
  font-size: 11px;
  color: #999;
}

.bjh-follow-btn {
  background: #f0f5ff;
  color: #3b82f6;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.preview-content.bjh {
  color: #333 !important;
  line-height: 1.8;
  font-size: 16px;
}

.preview-content.bjh :deep(p) {
  margin-bottom: 1.2em;
}

.preview-content.bjh :deep(img) {
  border-radius: 4px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05);
}
/* 今日头条风格 */
.tt-title {
  color: #222 !important;
  font-family: "Source Han Sans CN", sans-serif;
  font-size: 24px !important;
  font-weight: 800 !important;
  line-height: 1.4;
  letter-spacing: 0.5px;
}

.tt-author-bar {
  display: flex;
  align-items: center;
  padding: 12px 0;
}

.tt-avatar-wrapper {
  position: relative;
  margin-right: 12px;
}

.tt-avatar {
  width: 40px;
  height: 40px;
  background: #f85959;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: bold;
}

.tt-v-mark {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 14px;
  height: 14px;
  background: #ffc107;
  border: 2px solid white;
  border-radius: 50%;
  font-size: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: bold;
}

.tt-author-info .name {
  font-size: 15px;
  font-weight: bold;
  color: #222;
}

.tt-author-info .meta {
  font-size: 11px;
  color: #999;
}

.tt-follow-btn {
  border: 1px solid #f85959;
  background: white;
  color: #f85959;
  padding: 2px 12px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  margin-left: auto;
}

.preview-content.tt {
  color: #222 !important;
  font-size: 17px;
}
.ai-writing-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.1) 0%, rgba(139, 92, 246, 0.1) 100%);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 12px;
}

.ai-info {
  font-size: 13px;
  color: #94a3b8;
}

.editor-scroller {
  flex: 1;
  overflow-y: auto;
  padding: 0 40px 40px;
}

.editor-wrapper {
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

/* WangEditor 内容区：给一个最小和最大高度，超出滚动 */
:deep(.w-e-text-container) {
  min-height: 400px !important;
  height: auto !important;
}

.w-toolbar {
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  position: sticky;
  top: 0;
  z-index: 100;
  background: #0f172a;
}

.w-editor {
  flex: 1;
}

.form-group-inline {
  margin-top: 20px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 15px;
}

.form-group-inline label {
  font-size: 13px;
  color: #94a3b8;
  white-space: nowrap;
}

.form-group-inline input {
  flex: 1;
  background: transparent;
  border: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  color: white;
  padding: 5px;
  font-size: 13px;
  outline: none;
}

/* 分发设置侧栏 */
.publish-settings .settings-content {
  flex: 1;
  overflow-y: auto;
  padding: 0 0 16px 0;
  /* 同样需要 min-height: 0 才能被约束 */
  min-height: 0;
}

/* 账号展示卡片样式已迁移至上方 .account-chip */

.publish-footer {
  padding: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.publish-btn {
  height: 48px;
  font-weight: 600;
  font-size: 15px;
}

.scroller::-webkit-scrollbar { width: 6px; }
.scroller::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.2); border-radius: 10px; }
.scroller::-webkit-scrollbar-thumb:hover { background: rgba(59, 130, 246, 0.5); }

:deep(.preview-content) { padding: 0 20px; color: #cbd5e1; line-height: 1.8; }
:deep(.preview-content h2) { color: white; margin-top: 20px; }

/* AI 润色对比弹窗样式 */
.polish-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(10px);
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.polish-modal {
  width: 90%;
  max-width: 1400px;
  height: 85vh;
  display: flex;
  flex-direction: column;
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}

.polish-header {
  padding: 20px 30px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.polish-comparison {
  flex: 1;
  display: flex;
  gap: 2px;
  background: rgba(255, 255, 255, 0.05);
  overflow: hidden;
}

.comparison-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #0f172a;
  overflow: hidden;
}

.column-label {
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.02);
  font-size: 12px;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.content-box {
  flex: 1;
  padding: 30px;
  overflow-y: auto;
  line-height: 1.8;
  color: #cbd5e1;
}

:deep(.origin-meta) {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px dashed rgba(255,255,255,0.05);
}

:deep(.origin-body) {
  font-size: 14px;
  color: #94a3b8;
  line-height: 1.8;
}

:deep(.origin-body br) {
  display: block;
  margin: 10px 0;
  content: "";
}

.polished .content-box {
  background: rgba(59, 130, 246, 0.02);
}

.polished .column-label {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.05);
}

/* 仿真预览增强样式 */
.preview-platform-selector {
  display: flex;
  gap: 8px;
  background: rgba(0, 0, 0, 0.2);
  padding: 4px;
  border-radius: 8px;
}

.platform-item {
  flex: 1;
  text-align: center;
  padding: 6px;
  font-size: 11px;
  color: #94a3b8;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
}

.platform-item.active {
  background: #3b82f6;
  color: white;
}

.simulation-btn {
  margin-top: 15px;
  width: 100%;
  padding: 10px;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.2);
  color: #3b82f6;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.simulation-btn:hover {
  background: #3b82f6;
  color: white;
}

.account-empty-hint {
  padding: 20px;
  text-align: center;
  color: #64748b;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.01);
  border-radius: 10px;
  border: 1px dashed rgba(255, 255, 255, 0.05);
}

/* 仿真预览弹窗适配 */
.preview-modal.view-bjh, .preview-modal.view-tt {
  width: 500px;
  height: 90vh;
}

.mobile-frame {
  max-width: 420px;
  margin: 0 auto;
  background: #fff;
  color: #333;
  min-height: 100%;
  padding: 20px;
  border-radius: 30px;
  box-shadow: 0 0 0 10px #1e293b;
  position: relative;
}

.mobile-status-bar {
  font-size: 12px;
  font-weight: 600;
  text-align: center;
  margin-bottom: 20px;
  color: #000;
}

.view-bjh .preview-title { color: #000; font-size: 22px; line-height: 1.3; margin-bottom: 15px; }
.view-tt .preview-title { color: #222; font-size: 20px; font-weight: 800; margin-bottom: 15px; }

.preview-author-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}

.preview-author-bar .avatar {
  width: 36px;
  height: 36px;
  background: #eee;
  border-radius: 50%;
}

.preview-author-bar .name { font-size: 14px; font-weight: 600; color: #333; }
.preview-author-bar .meta { font-size: 12px; color: #999; }

:deep(.view-bjh .preview-content), :deep(.view-tt .preview-content) {
  color: #333 !important;
  font-size: 16px;
  line-height: 1.6;
}

.preview-footer-bar {
  margin-top: 40px;
  padding-top: 20px;
  border-top: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.interaction-hint {
  background: #f5f5f5;
  padding: 8px 15px;
  border-radius: 20px;
  font-size: 13px;
  color: #999;
  flex: 1;
  margin-right: 20px;
}

.icons { font-size: 18px; color: #666; letter-spacing: 5px; }
/* 发布类型选择器 */
.publish-type-selector {
  display: flex;
  gap: 8px;
  background: rgba(0, 0, 0, 0.2);
  padding: 4px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.type-item {
  flex: 1;
  text-align: center;
  padding: 6px 0;
  font-size: 12px;
  color: #94a3b8;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
}

.type-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #e2e8f0;
}

.type-item.active {
  background: #3b82f6;
  color: white;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

/* 实时自动化预览样式 */
.automation-preview-card {
  padding: 0 !important;
  background: #000 !important;
  overflow: hidden;
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.live-viewport {
  width: 100%;
  display: flex;
  flex-direction: column;
}

.viewport-header {
  background: #1e293b;
  padding: 6px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #94a3b8;
}

.preview-snapshot {
  width: 100%;
  aspect-ratio: 16/10;
  object-fit: contain;
  background: #000;
}

.loading-preview {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  font-size: 12px;
}

.preview-placeholder-modern {
  height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(255,255,255,0.01);
}

.placeholder-icon {
  font-size: 32px;
  opacity: 0.5;
}

.placeholder-text {
  font-size: 12px;
  color: #64748b;
  text-align: center;
  padding: 0 20px;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  background: #ef4444;
  border-radius: 50%;
  box-shadow: 0 0 8px #ef4444;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.5); opacity: 0.5; }
  100% { transform: scale(1); opacity: 1; }
}

.btn-sm {
  padding: 4px 10px;
  font-size: 11px;
}
</style>
