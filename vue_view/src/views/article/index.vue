<template>
  <div class="content-hub-container">
    <!-- 三栏布局：资源池 | 编辑器 | 分发设置 -->
    <div class="hub-layout">
      


      <!-- 第二栏：核心编辑器 -->
      <main class="hub-main editor-workspace">
        <div class="editor-header">
          <input v-model="form.title" class="main-title-input" placeholder="请在这里输入文章标题..." />
          <div class="ai-writing-banner" v-if="valueHtml || (leftTab === 'news' && selectedNews)">
            <div class="ai-actions">
              <!-- 新增：创作风格选择联动 -->
              <div class="spec-selector-group">
                <div class="mini-selector" title="一键智能撰稿将按此处选择的提示词风格生成正文">
                  <span class="sel-label">撰稿风格：</span>
                  <select v-model="selectedSpecId" class="banner-select">
                    <option :value="null">-- 默认风格 --</option>
                    <option v-for="s in specListByCat('GENERATION')" :key="s.id" :value="s.id">
                      {{ s.name }}{{ s.isDefault ? ' (默认)' : '' }}
                    </option>
                  </select>
                </div>
                <div class="mini-selector" title="AI 分解润色将按此处选择的润色提示词处理正文">
                  <span class="sel-label">润色风格：</span>
                  <select v-model="selectedPolishSpecId" class="banner-select">
                    <option :value="null">-- 默认风格 --</option>
                    <option v-for="s in specListByCat('POLISH')" :key="s.id" :value="s.id">
                      {{ s.name }}{{ s.isDefault ? ' (默认)' : '' }}
                    </option>
                  </select>
                </div>
              </div>

              <button 
                v-if="leftTab === 'news'"
                class="btn btn-primary glow-effect" 
                :disabled="aiWriting || !selectedNews" 
                @click="handleAiWrite"
                style="margin-right: 10px;"
              >
                🪄 一键智能撰稿
              </button>
              <button 
                class="btn btn-secondary" 
                :disabled="aiWriting || !valueHtml" 
                @click="handleAiPolish"
              >
                ✨ AI 分解润色
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
                🖼️ 智能插图
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
              :key="'tb-' + editorKey"
              class="w-toolbar"
              :editor="editorRef"
              :defaultConfig="toolbarConfig"
              mode="default"
            />
            <Editor
              :key="'ed-' + editorKey"
              class="w-editor"
              v-model="valueHtml"
              :defaultConfig="editorConfig"
              mode="default"
              @onCreated="handleCreated"
              @onChange="handleEditorChange"
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
            <div v-for="plat in platformList" :key="plat.id" class="platform-config-card">
              <div
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

              <!-- 独立设置区：仅在选中时显示 -->
              <div v-if="form.selectedPlatforms.includes(plat.platformKey) && (form.platformSettings as any)[plat.platformKey]" class="platform-settings-box">
                <!-- 该平台下选择要发布的账号 -->
                <div class="mini-form-group account-select-block">
                  <label>📌 选择要发布的账号</label>
                  <div v-if="getAccountsForPlatform(plat).length === 0" class="no-account-hint">该平台暂无绑定账号，请先在「账号管理」中绑定</div>
                  <div v-else class="account-check-list">
                    <label
                      v-for="acc in getAccountsForPlatform(plat)"
                      :key="acc.id"
                      class="account-check-item"
                    >
                      <input
                        type="checkbox"
                        :checked="isAccountSelected(acc.id)"
                        @change="toggleAccountSelection(acc.id)"
                      />
                      <span class="account-check-name">{{ acc.accountName }}</span>
                    </label>
                  </div>
                </div>

                <div class="settings-grid">
                  <div class="mini-form-group">
                    <label>📝 发布类型</label>
                    <select v-model="(form.platformSettings as any)[plat.platformKey].publishType" class="mini-input">
                      <option value="news">图文</option>
                      <option v-if="!['bjh', 'baijiahao'].includes(plat.platformKey)" value="video">视频</option>
                      <option value="dynamic">动态</option>
                    </select>
                  </div>

                  <!-- 百家号封面设置：仅在非动态类型时显示 -->
                  <div v-if="(['bjh', 'baijiahao'].includes(plat.platformKey)) && (form.platformSettings as any)[plat.platformKey].publishType !== 'dynamic'" class="mini-form-group span-2-rows">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                      <label>🖼️ 封面图 (共 {{ articleImages.length }} 张)</label>
                      <button class="btn-mini-ai" style="padding: 2px 6px; font-size: 10px;" title="重新从正文提取图片" @click.stop="forceUpdateImages">🔄 提取</button>
                    </div>
                    <div class="cover-preview-wrapper" style="margin-top: 0;">
                      <div v-show="articleImages.length > 0" class="cover-preview">
                        <img :src="getCurrentCover(plat.platformKey)" />
                        
                        <!-- 封面切换控制 -->
                        <div v-show="articleImages.length > 1" class="cover-nav">
                          <button class="nav-btn prev" @click="prevCover(plat.platformKey)">◀</button>
                          <span class="nav-info">{{ getCoverIndexLabel(plat.platformKey) }}</span>
                          <button class="nav-btn next" @click="nextCover(plat.platformKey)">▶</button>
                        </div>

                        <span class="cover-badge">{{ (form.platformSettings as any)[plat.platformKey].coverImage === articleImages[0] || !(form.platformSettings as any)[plat.platformKey].coverImage ? '默认' : '自选' }}</span>
                      </div>
                      <div v-show="articleImages.length === 0" class="cover-empty">正文暂无图片</div>
                    </div>
                  </div>

                  <div class="mini-form-group">
                    <label>🗂️ 内容分类</label>
                    <select v-model="(form.platformSettings as any)[plat.platformKey].category" class="mini-input">
                      <option value="">-- 选择分类 --</option>
                      <option>科技互联网</option>
                      <option>财经金融</option>
                      <option>生活方式</option>
                      <option>娱乐明星</option>
                      <option>体育赛事</option>
                    </select>
                  </div>
                </div>
                <div class="mini-form-group" style="margin-top: 10px;">
                  <label>🏷️ {{ plat.platformKey === 'bjh' || plat.platformKey === 'baijiahao' ? '实时流量话题' : '来源标签' }}</label>
                  <div style="display: flex; gap: 5px;">
                    <input 
                      v-model="(form.platformSettings as any)[plat.platformKey].tags" 
                      class="mini-input" 
                      :placeholder="['bjh', 'baijiahao'].includes(plat.platformKey) ? '辅助话题...' : '标签1, 标签2...'" 
                      style="flex: 1;" 
                    />
                    <button 
                      class="btn-mini-ai" 
                      :disabled="platformAiLoading[plat.platformKey]"
                      @click.stop="handlePlatformAiTags(plat.platformKey)"
                    >
                      <span v-if="platformAiLoading[plat.platformKey]" class="mini-loader"></span>
                      <span v-else>AI</span>
                    </button>
                  </div>
                  
                  <!-- 本地话题池展示 -->
                  <div v-if="(platformTopics as any)[plat.platformKey]?.length > 0" class="mini-topic-pool">
                    <div class="pool-header">
                       <div class="pool-title">🔥 实时热点池 (点击锁定主话题)</div>
                    </div>
                    <div class="pool-list">
                      <!-- 新增：特殊指令模式 (仅限百家号相关) -->
                      <template v-if="['bjh', 'baijiahao'].includes(plat.platformKey)">
                        <span 
                          class="pool-item special-command"
                          :class="{ active: (form.platformSettings as any)[plat.platformKey]?.selectedTopic === '__DEFAULT__' }"
                          @click.stop="handleTopicClick(plat.platformKey, '__DEFAULT__')"
                          title="自动在弹窗中选择带有‘默认’关键字的话题"
                        >
                          🌟 选用默认话题
                        </span>
                        <span 
                          class="pool-item special-command"
                          :class="{ active: (form.platformSettings as any)[plat.platformKey]?.selectedTopic === '__HOTTEST__' }"
                          @click.stop="handleTopicClick(plat.platformKey, '__HOTTEST__')"
                          title="自动选择今日最热列表中的第一个话题"
                        >
                          🔥 选用今日第一热点
                        </span>
                      </template>
                      
                      <span 
                        v-for="(t, idx) in (platformTopics as any)[plat.platformKey]" 
                        :key="idx" 
                        class="pool-item"
                        :class="{ active: (form.platformSettings as any)[plat.platformKey]?.selectedTopic === t.topic }"
                        @click.stop="handleTopicClick(plat.platformKey, t.topic)"
                      >
                        #{{ t.topic.replace(/#/g, '') }}#
                      </span>
                    </div>
                  </div>
                </div>
                <div class="mini-schedule">
                  <label class="schedule-label">
                    <input type="checkbox" v-model="(form.platformSettings as any)[plat.platformKey].isScheduled" /> 定时发布
                  </label>
                  <el-date-picker
                    v-if="(form.platformSettings as any)[plat.platformKey].isScheduled"
                    v-model="(form.platformSettings as any)[plat.platformKey].scheduledTime"
                    type="datetime"
                    size="small"
                    placeholder="选择时间"
                    style="width: 100%; margin-top: 5px;"
                    value-format="YYYY-MM-DDTHH:mm:ss"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
        </div>

        <div class="selected-channels-area" v-if="selectedAccountNames.length > 0">
          <div class="sc-title">已选分发渠道 ({{ selectedAccountNames.length }})</div>
          <div class="sc-list">
            <span class="sc-tag" v-for="(name, idx) in selectedAccountNames" :key="idx">
              <i class="platform-icon">🎯</i> {{ name }}
            </span>
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
            
            <!-- 仿真预览视角下的动态注入展示 -->
            <div class="preview-content w-e-text-container" :class="selectedPlatform">
               <div v-if="(form.platformSettings as any)[selectedPlatform]?.selectedTopic" style="color:#3b82f6; font-weight:bold; margin-bottom:10px;">
                  #{{ (form.platformSettings as any)[selectedPlatform].selectedTopic.replace(/#/g, '') }}#
               </div>
               <div v-html="valueHtml"></div>
            </div>
            
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
import { onBeforeUnmount, ref, shallowRef, onMounted, onActivated, reactive, computed, nextTick } from 'vue';

defineOptions({ name: 'ArticlePage' });
import { useRoute } from 'vue-router';
import { Editor, Toolbar } from '@wangeditor/editor-for-vue';
import { getAccountList } from '../../api/account';
import { saveArticle, getArticleList, getArticle } from '../../api/article';
import { submitPublishTask, getAiSummary, getAiSuggestedTitles, getAiTags, getAiCategory } from '../../api/publish';
import { getPlatformList } from '../../api/platform';
import { 
    fetchHotNews, 
    fetchArticleContent, 
    generateArticle, 
    matchImage, 
    polishArticle, 
    suggestImages, 
    syncPlatformTasks, 
    getPlatformTasks, 
    matchHotTopics,
    generateImage,
    type HotNews 
} from '../../api/ai';
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
const editorKey = ref(1);
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
  coverUrl: '',
  selectedAccounts: [] as number[],
  selectedPlatforms: [] as string[], // 使用平台 Key
  platformSettings: {} as Record<string, {
    category: string;
    tags: string;
    selectedTopic: string; // 已锁定的主话题
    publishType: string;
    coverImage: string;
    isScheduled: boolean;
    scheduledTime: string;
  }>
});

const platformAiLoading = reactive<Record<string, boolean>>({});
const platformTopics = reactive<Record<string, any[]>>({}); // 存储各平台的本地话题/任务池
const autoPrependTopic = ref(true); // 默认开启话题进正文逻辑

const platformList = ref<Platform[]>([]);

// 写作规范联动
import { getSpecList, type AiWritingSpecification } from '../../api/aiSpec';
const fullSpecList = ref<AiWritingSpecification[]>([]);
const selectedSpecId = ref<number | null>(null);
const selectedPolishSpecId = ref<number | null>(null);

const specListByCat = (cat: string) => {
  return fullSpecList.value.filter(s => s.category === cat);
};

const loadWritingSpecs = async () => {
  try {
    const res = await getSpecList();
    fullSpecList.value = res || [];
    
    // 自动预设默认项
    const genDefault = fullSpecList.value.find(s => s.category === 'GENERATION' && s.isDefault);
    if (genDefault) selectedSpecId.value = genDefault.id!;
    
    const polishDefault = fullSpecList.value.find(s => s.category === 'POLISH' && s.isDefault);
    if (polishDefault) selectedPolishSpecId.value = polishDefault.id!;
  } catch (e) {
    console.error('加载 AI 规范失败', e);
  }
};

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
    // 处理跨页面跳转数据（首次加载 + keep-alive 复用共享逻辑）
    checkPendingData();

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

    // 4. 并发加载其他数据
    loadWritingSpecs();
    loadMyArticles();
    // 4. 加载平台和账号列表 (受活动渠道限制)
    try {
      const [pRes, aRes] = await Promise.all([
        getPlatformList(),
        getAccountList()
      ]);
      
      // 过滤渠道：只显示用户在“分发渠道”页面启用的平台
      let allPlatforms = pRes || [];
      const savedPlatforms = localStorage.getItem('active_platforms');
      if (savedPlatforms) {
        try {
          const activeKeys = JSON.parse(savedPlatforms);
          if (Array.isArray(activeKeys)) {
            // 注意：如果没有在渠道页配置过任何内容，默认是全显（activeKeys 可能为空数组如果我们特意清空了，但这里遵循用户真实选择）
            allPlatforms = allPlatforms.filter(p => activeKeys.includes(p.platformKey));
          }
        } catch(e) { console.warn('解析 active_platforms 缓存失败'); }
      }
      
      platformList.value = allPlatforms;
      accountList.value = aRes || [];
      
      // 默认选中启用配置平台下的所有账号
      const platformIds = new Set(allPlatforms.map((p: Platform) => p.id));
      form.selectedAccounts = (aRes || []).filter((a: Account) => platformIds.has(a.platformId)).map((a: Account) => a.id);
      
      // 用户要求：内容创作中，只要在分发渠道中显示的（已过滤启用的）都是默认选中的
      if (allPlatforms.length > 0 && form.selectedPlatforms.length === 0) {
        form.selectedPlatforms = allPlatforms.map(p => p.platformKey);
        // 初始化配置，防止模板中使用 v-model 报错
        allPlatforms.forEach(p => {
          if (!(form.platformSettings as any)[p.platformKey]) {
            (form.platformSettings as any)[p.platformKey] = {
              category: '',
              tags: '',
              selectedTopic: ['bjh', 'baijiahao'].includes(p.platformKey) ? '__DEFAULT__' : '', 
              publishType: 'news',
              coverImage: '',
              isScheduled: false,
              scheduledTime: ''
            };
          }
        });
      }

      // 自动加载话题池
      allPlatforms.forEach(p => {
        if (['bjh', 'baijiahao'].includes(p.platformKey)) {
          getPlatformTasks(p.platformKey).then(ts => {
            (platformTopics as any)[p.platformKey] = ts;
            // 只有当 selectedTopic 仍然为空时才尝试自动选择话题池第一个
            const settings = (form.platformSettings as any)[p.platformKey];
            if (ts && ts.length > 0 && settings && !settings.selectedTopic) {
              settings.selectedTopic = ts[0].topic;
            }
          });
        }
      });
    } catch (e) {
      console.error('加载平台列表失败', e);
      platformList.value = [];
    }
});

// keep-alive 重新激活时，检查是否有新的跳转数据
onActivated(() => {
    checkPendingData();
});

// 抽取跨页面数据检查逻辑
const checkPendingData = () => {
    // 1. 兼容旧版跳转逻辑
    const pt = localStorage.getItem('pending_article_title');
    const pc = localStorage.getItem('pending_article_content');
    const pcover = localStorage.getItem('pending_article_cover');
    if (pt) { form.title = pt; localStorage.removeItem('pending_article_title'); }
    if (pc) { valueHtml.value = pc; localStorage.removeItem('pending_article_content'); }
    if (pcover) { form.coverUrl = pcover; localStorage.removeItem('pending_article_cover'); }

    // 2. 处理来自"热点资讯"的一键创作
    const autoNewsData = localStorage.getItem('auto_create_news');
    if (autoNewsData) {
      try {
        const item = JSON.parse(autoNewsData) as HotNews;
        selectedNews.value = item;
        form.title = item.title;
        leftTab.value = 'news';
        localStorage.removeItem('auto_create_news');
        setTimeout(() => handleAiWrite(), 500);
      } catch (e) {
        console.error('Failed to parse auto_create_news', e);
      }
    }
    
    // 3. 处理路由参数传递的 ID（从"我的发文"跳转编辑）
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
};

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

function getAccountsForPlatform(plat: Platform): Account[] {
  return accountList.value.filter((a: Account) => a.platformId === plat.id);
}

function isAccountSelected(accountId: number): boolean {
  return form.selectedAccounts.includes(accountId);
}

function toggleAccountSelection(accountId: number) {
  const idx = form.selectedAccounts.indexOf(accountId);
  if (idx >= 0) {
    form.selectedAccounts.splice(idx, 1);
  } else {
    form.selectedAccounts.push(accountId);
  }
}

// 计算选中的账号名称，用于展示分发渠道
const selectedAccountNames = computed(() => {
  return form.selectedAccounts.map(id => {
    const account = accountList.value.find(a => a.id === id);
    if (account) {
      // 优先从 platformList 查找，如果 platformList 没有，再从硬编码的 platforms 查找
      const platform = platformList.value.find(p => p.id === account.platformId) || platforms.find(p => p.id === String(account.platformId));
      const pName = platform ? (platform as any).platformName || (platform as any).name : '未知平台';
      return `${pName} - ${account.accountName}`;
    }
    return `未知账号`;
  });
});

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
  valueHtml.value = item.content;

  // 加载各平台独立设置 (从 JSON 反序列化)
  if (item.platformSettings) {
    try {
      const savedSettings = JSON.parse(item.platformSettings);
      Object.keys(savedSettings).forEach(key => {
        form.platformSettings[key] = {
           ...savedSettings[key]
        };
      });
      // 保持已选平台状态
      form.selectedPlatforms = Object.keys(savedSettings);
    } catch (e) {
      console.error('解析平台配置失败', e);
    }
  } else {
    // 兼容老数据逻辑
    form.selectedPlatforms.forEach(key => {
        if (form.platformSettings[key]) {
            form.platformSettings[key].category = (item as any).category || '';
            form.platformSettings[key].tags = (item as any).tags || '';
        }
    });
  }
};

const handleCreated = (editor: any) => {
  editorRef.value = editor;
  // 编辑器创建完成后，立即刷新图片列表
  nextTick(() => refreshArticleImages());
};

const toggleAccount = (acc: any) => {
  const id = acc.id as number;
  const idx = form.selectedAccounts.indexOf(id);
  if (idx > -1) form.selectedAccounts.splice(idx, 1);
  else form.selectedAccounts.push(id);
};

const togglePlatform = (plat: Platform) => {
  const key = plat.platformKey;
  const idx = form.selectedPlatforms.indexOf(key);
  if (idx === -1) {
    form.selectedPlatforms.push(key);
    // 初始化该平台的独立设置
    form.platformSettings[key] = {
      category: '',
      tags: '',
      selectedTopic: '', 
      publishType: 'news',
      coverImage: '',
      isScheduled: false,
      scheduledTime: ''
    };
  } else {
    form.selectedPlatforms.splice(idx, 1);
    delete form.platformSettings[key];
  }
};

const getAccountCount = (platformId: number) => {
  return accountList.value.filter(a => a.platformId === platformId).length;
};

// 辅助：从编辑器内部模型中提取所有图片 URL
const articleImages = ref<string[]>([]);

// 从 HTML 字符串中正则提取所有图片 URL（不依赖编辑器状态，避免 setHtml 后 valueHtml 被覆盖导致 0 张）
function extractImageUrlsFromHtml(html: string): string[] {
  if (!html || typeof html !== 'string') return [];
  const allUrls = new Set<string>();
  // 兼容 src="url"、src='url'、src = "url"，属性顺序任意
  const reg = /<img[^>]+>/gi;
  let match;
  while ((match = reg.exec(html)) !== null) {
    const srcMatch = match[0].match(/\ssrc\s*=\s*["']([^"']+)["']/i);
    if (srcMatch && srcMatch[1] && !srcMatch[1].includes('data:image/svg+xml')) {
      allUrls.add(srcMatch[1].trim());
    }
  }
  return Array.from(allUrls);
}

// 辅助：当图片列表变化时，同步保证所有选中平台的 coverImage 始终有效
const syncCoverImages = (imgs: string[]) => {
  form.selectedPlatforms.forEach((platKey: string) => {
    const settings = (form.platformSettings as any)[platKey];
    if (settings) {
      // 如果当前没有封面图，或者当前封面图不在新的图片列表中，强制重置为新的第一张
      if (!settings.coverImage || !imgs.includes(settings.coverImage)) {
        settings.coverImage = imgs.length > 0 ? imgs[0] : '';
      }
    }
  });
};

// 刷新图片列表：直接从 HTML 正则提取，不依赖编辑器 API
const refreshArticleImages = (html?: string) => {
  const source = html || valueHtml.value;
  const imgs = extractImageUrlsFromHtml(source);
  articleImages.value = imgs;
  syncCoverImages(imgs);
};

// 编辑器内容变化时防抖刷新图片列表
let editorChangeTimer: ReturnType<typeof setTimeout> | null = null;
const handleEditorChange = () => {
  if (editorChangeTimer) clearTimeout(editorChangeTimer);
  editorChangeTimer = setTimeout(() => {
    refreshArticleImages();
  }, 500);
};

const forceUpdateImages = () => {
  // 直接从当前 HTML 提取图片，不依赖编辑器 API
  const imgs = extractImageUrlsFromHtml(valueHtml.value);
  articleImages.value = imgs;
  syncCoverImages(imgs);
  if (imgs.length > 0) {
    ElMessage.success(`已成功提取 ${imgs.length} 张图片`);
  } else {
    ElMessage.warning('未能从正文中检测到图片，请确认正文已插入图片');
  }
};

// 辅助：提取 HTML 中的第一张图片 URL (兼容旧逻辑)
const extractFirstImage = (html: string) => {
  if (!html) return '';
  const reg = /<img\s+[^>]*?src=["']([^"']+)["']/i;
  const match = html.match(reg);
  return match ? match[1] : '';
};

// 获取当前平台选中的封面，如果没有则默认第一张
const getCurrentCover = (platKey: string) => {
  const imgs = articleImages.value;
  if (imgs.length === 0) return '';
  const settings = (form.platformSettings as any)[platKey];
  if (!settings.coverImage || !imgs.includes(settings.coverImage)) {
    return imgs[0];
  }
  return settings.coverImage;
};

// 封面导航
const getCoverIndexLabel = (platKey: string) => {
  const imgs = articleImages.value;
  const current = getCurrentCover(platKey);
  const idx = imgs.indexOf(current);
  return `${idx + 1} / ${imgs.length}`;
};

const nextCover = (platKey: string) => {
  const imgs = articleImages.value;
  if (imgs.length <= 1) return;
  const current = getCurrentCover(platKey);
  let idx = imgs.indexOf(current);
  idx = (idx + 1) % imgs.length;
  (form.platformSettings as any)[platKey].coverImage = imgs[idx];
};

const prevCover = (platKey: string) => {
  const imgs = articleImages.value;
  if (imgs.length <= 1) return;
  const current = getCurrentCover(platKey);
  let idx = imgs.indexOf(current);
  idx = (idx - 1 + imgs.length) % imgs.length;
  (form.platformSettings as any)[platKey].coverImage = imgs[idx];
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

    // 3. 提交给 AI 进行深度改写，携带选中的撰稿风格 specId（后端按此提示词生成）
    const res = await generateArticle(selectedNews.value.title, originalText, selectedSpecId.value);
    
    if (res?.content == null || (typeof res.content === 'string' && res.content.trim() === '')) {
      ElMessage.error('生成结果为空，请检查 AI 配置或稍后重试');
      return;
    }

    // 4. 更新对比弹窗内容 (后台保留供手动查看)
    originalContent.value = `<h3>${selectedNews.value.title}</h3>` + 
                            `<div class="origin-meta">来源：${selectedNews.value.source} | 链接：${selectedNews.value.url}</div>` +
                            `<div class="origin-body">${originalText.replace(/\n\n/g, '<br><br>')}</div>`;
                            
    polishedContent.value = res.content;
    polishMode.value = 'rewrite';

    // === 直接应用采纳到编辑器 ===
    await updateEditorContent(res.content, true, res.images);
    
    ElMessage.success('正文已按所选撰稿风格生成，并已自动采纳');
  } catch (err) {
    ElMessage.error('AI 改写失败，请检查网络或 AI 配置');
  } finally {
    aiWriting.value = false;
  }
};

// 抽取公共的内容更新逻辑：支持标题提取、自动辅助信息生成、以及 AI 图片重绘
const updateEditorContent = async (newHtml: string, isRewrite = false, incomingImages?: string[]) => {
  if (newHtml == null || (typeof newHtml === 'string' && newHtml.trim() === '')) return;
  let finalContent = newHtml;
  
  // 1. 尝试从正文中提取并移除标题 (h1-h3)
  const titleMatch = finalContent.match(/<h[1-3][^>]*>(.*?)<\/h[1-3]>/i);
  if (titleMatch && titleMatch[1]) {
    const extractedTitle = titleMatch[1].replace(/<[^>]+>/g, '').trim();
    if (extractedTitle) {
      form.title = extractedTitle;
      finalContent = finalContent.replace(titleMatch[0], '').replace(/^\s*(<br\/?>\s*)+/, '');
    }
  }

  // === 核心增强：异步处理图片重绘，解决水印问题 ===
  if (finalContent.includes('data-ai-rebuild="true"')) {
    const parser = new DOMParser();
    const doc = parser.parseFromString(finalContent, 'text/html');
    const imagesToRebuild = doc.querySelectorAll('img[data-ai-rebuild="true"]');
    
    if (imagesToRebuild.length > 0) {
      ElMessage.info(`正在为您重绘 ${imagesToRebuild.length} 张无水印高清大图...`);
      const rebuildPromises = Array.from(imagesToRebuild).map(async (img) => {
        const prompt = img.getAttribute('data-prompt');
        const originalSrc = img.getAttribute('src');
        try {
          const res = await generateImage(prompt || '');
          if (res && res.url) {
            img.setAttribute('src', res.url);
            // 统一视觉样式
            img.setAttribute('style', 'max-width:100%; border-radius:8px; display:block; margin:15px auto;');
            console.log('[AI Image] Redraw success:', res.url);
          }
        } catch (e) {
          console.error('[AI Image] Redraw failed, falling back to original src:', e);
        } finally {
          // 无论成功还是失败，都移除重建标记，避免重复触发或显示异常
          img.removeAttribute('data-ai-rebuild');
          // 确保原始图也能有基础样式
          if (!img.getAttribute('style')) {
             img.setAttribute('style', 'max-width:100%; border-radius:8px; display:block; margin:10px auto;');
          }
        }
      });
      await Promise.all(rebuildPromises);
      finalContent = doc.body.innerHTML;
    }
  }

  // 2. 应用内容：直接写回 ref，并且强制组件重新挂载，彻底绕开 WangEditor 内置的 Slate 更新引擎以防止 __vnode 报错
  valueHtml.value = finalContent;
  editorKey.value++; 
  await nextTick();

  // 直接使用后端传来的图片URL，如果有的话优先使用后端解析的，避免前端正则提取潜在遗漏
  const imgs = (incomingImages && incomingImages.length > 0) ? incomingImages : extractImageUrlsFromHtml(finalContent);
  articleImages.value = imgs;
  // 第一张自动设为各平台封面
  if (imgs.length > 0) {
    const firstImg = imgs[0];
    form.selectedPlatforms.forEach((platKey: string) => {
      const settings = (form.platformSettings as any)[platKey];
      if (settings && !settings.coverImage) {
        settings.coverImage = firstImg;
      }
    });
  }

  // 3. 如果是深度改写，自动触发摘要和标签提取
  if (isRewrite) {
    const tasks = [];
    if (!form.summary) tasks.push(handleAiSummary(true));
    
    const firstPlat = form.selectedPlatforms[0];
    const settings = firstPlat ? (form.platformSettings as any)[firstPlat] : null;

    if (settings && !settings.tags) tasks.push(handleAiTags(true));
    if (settings && !settings.category) tasks.push(handleAiCategory(true));
    
    if (tasks.length > 0) {
      await Promise.all(tasks).catch(() => {});
    }
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

const handlePlatformAiTags = async (platKey: string) => {
  if (!valueHtml.value) return;
  platformAiLoading[platKey] = true;
  try {
    let finalTags: string[] = [];
    
    // === 特殊逻辑：百家号 (bjh) 优先匹配本地缓存的热门任务/话题 ===
    if (platKey === 'bjh' || platKey === 'baijiahao') {
      try {
        const tasks = await getPlatformTasks('bjh');
        if (tasks && tasks.length > 0) {
           const hotTopicsJson = JSON.stringify(tasks.map((t: any) => ({ topic: t.topic, participants: t.extraInfo })));
           const matched = await matchHotTopics(valueHtml.value, hotTopicsJson);
           if (matched && matched.length > 0) {
              finalTags = matched;
           }
        } else {
           console.log('本地百家号任务池为空，跳过热点匹配');
        }
      } catch (err) {
        console.warn('读取本地热点任务失败，切换回通用标签提取', err);
      }
    }

    // 如果特殊匹配没结果，或者非百家号，则走通用提取
    if (finalTags.length === 0) {
      finalTags = await getAiTags(valueHtml.value);
    }

    if (form.platformSettings[platKey]) {
      form.platformSettings[platKey].tags = finalTags.join(', ');
      ElMessage.success(`已为该平台提取${finalTags.some(t => t.includes('#')) ? '实时热点' : ' AI'} 标签`);
    }
  } catch (err) {
    ElMessage.error('AI 标签提取失败，请重试');
  } finally {
    platformAiLoading[platKey] = false;
  }
};

const handleTopicClick = (platKey: string, topic: string) => {
  if (!form.platformSettings[platKey]) {
    (form.platformSettings as any)[platKey] = {
      category: '',
      tags: '',
      selectedTopic: '',
      publishType: 'article',
      coverImage: '',
      isScheduled: false,
      scheduledTime: ''
    };
  }
  
  const settings = (form.platformSettings as any)[platKey];
  if (!settings) return; // Type guard

  // 切换选中状态：如果点的是已选中的，则取消锁定；否则锁定该话题
  if (settings.selectedTopic === topic) {
     settings.selectedTopic = '';
     ElMessage.info(`已取消锁定话题`);
  } else {
     settings.selectedTopic = topic;
     // 同时将其加入标签列表(去重)
     const normalized = topic.replace(/#/g, '');
     const tagList = (settings.tags || '').split(/[,，]/).map((t: string) => t.trim()).filter((t: string) => t);
     if (!tagList.includes(normalized)) {
        tagList.push(normalized);
        settings.tags = tagList.join(', ');
     }
     ElMessage.success(`已锁定主话题: #${normalized}# (发布时将自动注入正文开头)`);
  }
};

// 将话题插入文章开头
const prependTopicToContent = (topic: string) => {
  if (!valueHtml.value) return;
  const topicTag = `#${topic.replace(/#/g, '')}#`;
  
  // 简单去重：如果内容里已经有了（可能在开头），就不加了
  if (valueHtml.value.includes(topicTag)) return;
  
  // 插入到开头。WangEditor 的 HTML 可能是 <p>...</p>，我们加在最前面
  valueHtml.value = `<p><strong>${topicTag}</strong></p>` + valueHtml.value;
  ElMessage.success(`话题 ${topicTag} 已插入文章开头`);
};

const handleAiTags = async (isAuto?: boolean | Event) => {
  if (!valueHtml.value) return;
  try {
    const res = await getAiTags(valueHtml.value);
    const tagsStr = res.join(', ');
    // 为所有已选平台同步标签
    form.selectedPlatforms.forEach(key => {
      if (form.platformSettings[key]) {
        form.platformSettings[key].tags = tagsStr;
      }
    });
    if (isAuto !== true) ElMessage.success('全平台标签同步成功');
  } catch (err) {}
};

const handleAiCategory = async (isAuto?: boolean | Event) => {
  if (!valueHtml.value) return;
  try {
    const categoriesStr = ['科技互联网', '财经金融', '生活方式', '娱乐明星', '体育赛事'].join(', ');
    const res = await getAiCategory(valueHtml.value, categoriesStr);
    if (res) {
      // 为所有已选平台同步分类
      form.selectedPlatforms.forEach(key => {
        if (form.platformSettings[key]) {
          form.platformSettings[key].category = res;
        }
      });
      if (isAuto !== true) ElMessage.success('全平台分类同步成功');
    }
  } catch (err) {}
};

const handleAiPolish = async () => {
  if (!valueHtml.value) return;
  aiWriting.value = true;
  try {
    const res = await polishArticle(valueHtml.value, selectedPolishSpecId.value);
    if (res?.content == null || (typeof res.content === 'string' && res.content.trim() === '')) {
      ElMessage.error('润色结果为空，请检查 AI 配置或稍后重试');
      return;
    }
    originalContent.value = valueHtml.value;
    polishedContent.value = res.content;
    polishMode.value = 'polish';
    await updateEditorContent(res.content, false, res.images);
    ElMessage.success('文章润色完成，已自动采纳修订版本');
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
  // 右侧「AI 深度改写版」：优先用当前编辑器内容；若编辑器为空但之前已生成过（polishedContent 有值），保留上次结果，避免对比弹窗右侧空白
  if (valueHtml.value && valueHtml.value.trim()) {
    polishedContent.value = valueHtml.value;
  }
  // 若 polishedContent 仍为空且无历史改写结果，保持原值，至少左侧原文会显示
  polishMode.value = 'rewrite';
  showPolishDialog.value = true;
};

const applyPolish = async () => {
  await updateEditorContent(polishedContent.value, polishMode.value === 'rewrite');
  showPolishDialog.value = false;
  ElMessage.success('已应用所选版本');

  // 采纳后自动保存，确保用户润色的内容不丢失
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
    // 全局保存时，分类和标签可以取第一个选中平台作为参考，或者留空
    const firstPlat = form.selectedPlatforms[0];
    const settings = firstPlat ? form.platformSettings[firstPlat] : null;

    const res = await saveArticle({
      id: currentArticleId.value || undefined,
      title: form.title,
      summary: form.summary,
      content: valueHtml.value,
      coverImage: extractFirstImage(valueHtml.value), // 提取封面 (第一张)
      category: settings?.category || '',
      tags: settings?.tags || '',
      platformSettings: JSON.stringify(form.platformSettings),
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
  if (form.selectedAccounts.length === 0) return ElMessage.warning('请选择至少一个发布账号');
  
  publishing.value = true;
  
  try {
    const article = await handleSave(1);
    if (!article) return;

    ElMessage.info('正在提交发布任务至后台队列...');
    
    // 对每个选中平台的额外配置（其实直接随文章平台设置入库，后端会自动处理）
    const res = await submitPublishTask({
        articleId: article.id,
        accountIds: form.selectedAccounts,
        scheduledTime: undefined
    });
    
    if (res && res.length > 0) {
        ElMessage.success(`已成功划拨 ${res.length} 个发布任务至后台队列，将按账号排队全自动异步发布`);
    } else {
        ElMessage.warning('提交发布任务记录为空');
    }
  } catch (e) {
    ElMessage.error('自动化分发提交过程中出现异常');
  } finally {
    publishing.value = false;
  }
};
</script>

<style scoped>
.cover-preview-wrapper {
  background: rgba(0, 0, 0, 0.2);
  border-radius: 8px;
  padding: 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  height: 100%;
  display: flex;
  flex-direction: column;
}
.span-2-rows {
  grid-row: span 2;
}
.cover-preview {
  position: relative;
  width: 100%;
  aspect-ratio: 16/9;
  border-radius: 4px;
  overflow: hidden;
}
.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-badge {
  position: absolute;
  top: 5px;
  left: 5px;
  background: rgba(59, 130, 246, 0.9);
  color: white;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  backdrop-filter: blur(4px);
  z-index: 2;
}
.cover-nav {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  z-index: 2;
}
.nav-btn {
  background: transparent;
  border: none;
  color: white;
  cursor: pointer;
  padding: 4px 8px;
  font-size: 12px;
  transition: all 0.2s;
  border-radius: 4px;
}
.nav-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}
.nav-info {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.8);
  font-weight: 600;
}
.cover-empty {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  font-size: 12px;
}

.account-select-block {
  background: rgba(0, 0, 0, 0.15);
  padding: 12px;
  border-radius: 8px;
  border: 1px dashed rgba(255, 255, 255, 0.1);
  margin-bottom: 12px;
}
.no-account-hint {
  font-size: 12px;
  color: #ff4d4f;
  margin-top: 5px;
}
.account-check-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}
.account-check-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #e2e8f0;
  cursor: pointer;
}
.account-check-item input[type="checkbox"] {
  accent-color: #3b82f6;
  width: 14px;
  height: 14px;
  cursor: pointer;
}

.content-hub-container {
  height: calc(100vh - 80px); /* 减去顶部边距 */
  margin: -20px;
  overflow: hidden;
}

.hub-layout {
  display: grid;
  grid-template-columns: 1fr 460px;
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
  background: #0f172a;
}

.settings-content.scroller {
  flex: 1;
  overflow-y: auto;
  padding: 12px 0;
}

/* 自定义滚动条 */
.scroller::-webkit-scrollbar {
  width: 5px;
}
.scroller::-webkit-scrollbar-track {
  background: transparent;
}
.scroller::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 10px;
}
.scroller::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.2);
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

.spec-selector-group {
  display: flex;
  gap: 20px;
  background: rgba(255, 255, 255, 0.03);
  padding: 8px 16px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.mini-selector {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sel-label {
  font-size: 11px;
  color: #64748b;
  font-weight: 600;
  text-transform: uppercase;
}

.banner-select {
  background: transparent;
  border: none;
  color: var(--accent-blue);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  outline: none;
  padding: 4px 0;
  border-bottom: 1px dashed rgba(79, 142, 247, 0.3);
}

.banner-select:hover {
  border-bottom-style: solid;
}

.banner-select option {
  background: var(--bg-card);
  color: var(--text-primary);
}

.ai-spec-selectors {
  display: flex;
  gap: 20px;
}

.spec-select-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.spec-select-item label {
  font-size: 13px;
  color: #94a3b8;
  white-space: nowrap;
}

.spec-selector {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #e2e8f0;
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 6px;
  outline: none;
  cursor: pointer;
  min-width: 140px;
}

.spec-selector:focus {
  border-color: #3b82f6;
}

.spec-selector option {
  background: #1e293b;
  color: #e2e8f0;
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

/* --- 分发渠道选中样式 --- */
.selected-channels-area {
  margin-top: 15px;
  padding: 15px;
  background: rgba(33, 150, 243, 0.05);
  border-radius: 12px;
  border: 1px solid rgba(33, 150, 243, 0.15);
}

.sc-title {
  font-size: 13px;
  color: #8c92a4;
  margin-bottom: 10px;
  font-weight: 500;
}

.sc-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sc-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  font-size: 13px;
  color: #cad1e3;
  transition: all 0.3s;
}

.sc-tag:hover {
  background: rgba(33, 150, 243, 0.1);
  border-color: rgba(33, 150, 243, 0.3);
  color: #fff;
}

.platform-icon {
  font-style: normal;
  font-size: 14px;
}
/* --- 独立平台配置容器 --- */
.platform-config-card {
  margin-bottom: 16px;
  background: rgba(30, 41, 59, 0.3);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  overflow: hidden;
  transition: all 0.4s cubic-bezier(0.23, 1, 0.32, 1);
}

.platform-config-card:hover {
  background: rgba(30, 41, 59, 0.5);
  border-color: rgba(59, 130, 246, 0.3);
}

.platform-settings-box {
  padding: 16px;
  background: rgba(15, 23, 42, 0.6);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  border-left: 3px solid #3b82f6;
  margin: 0 12px 12px 12px;
  border-radius: 0 0 12px 12px;
  box-shadow: inset 0 2px 10px rgba(0, 0, 0, 0.3);
  animation: slideDown 0.3s ease-out;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.mini-form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mini-form-group label {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.mini-input {
  width: 100%;
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 8px 12px;
  color: #f1f5f9;
  font-size: 12px;
  outline: none;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.mini-input:focus {
  border-color: #3b82f6;
  background: rgba(0, 0, 0, 0.6);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.skill-highlight .mini-input {
  border-color: rgba(59, 130, 246, 0.4);
}

.skill-highlight .mini-input:focus {
  border-color: #60a5fa;
  box-shadow: 0 0 15px rgba(59, 130, 246, 0.2);
}

.btn-mini-ai {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.2), rgba(37, 99, 235, 0.2));
  border: 1px solid rgba(59, 130, 246, 0.3);
  color: #60a5fa;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-mini-ai:hover:not(:disabled) {
  background: rgba(59, 130, 246, 0.3);
  color: white;
  transform: scale(1.05);
}

.mini-schedule {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.mini-topic-pool {
  margin-top: 12px;
  background: rgba(0, 0, 0, 0.2);
  padding: 12px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.03);
}

.pool-header {
  margin-bottom: 10px;
}

.pool-title {
  font-size: 11px;
  color: #64748b;
  font-weight: 600;
}

.pool-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-height: 140px;
  overflow-y: auto;
}

.pool-item {
  font-size: 11px;
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.pool-item:hover {
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.4);
  color: #fff;
}

.pool-item.active {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
  font-weight: 600;
}

.pool-item.active::before {
  content: "✓ ";
  font-size: 10px;
}
.pool-item.special-command {
  background: rgba(59, 130, 246, 0.15);
  border: 1px dashed #3b82f6;
  color: #60a5fa;
  font-weight: bold;
}

.pool-item.special-command.active {
  background: #3b82f6;
  color: white;
  border-style: solid;
}
</style>
