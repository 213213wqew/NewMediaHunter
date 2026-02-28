<template>
  <div class="automation-workspace">
    <!-- 左侧控制面板 -->
    <div class="sidebar">
      <div class="panel-header">
        <h3>自动化工作台</h3>
      </div>
      
      <div class="control-group">
        <label>选择待发文章</label>
        <el-select v-model="selectedArticleId" placeholder="请选择文章" @change="handleArticleChange">
          <el-option v-for="item in articles" :key="item.id" :label="item.title" :value="item.id!" />
        </el-select>
      </div>

      <div class="control-group">
        <label>选择发布平台</label>
        <el-select v-model="selectedAccountId" placeholder="请选择账号">
          <el-option v-for="item in accounts" :key="item.id" :label="`${item.accountName} (${getPlatformName(item.platformId)})`" :value="item.id" />
        </el-select>
      </div>

      <div class="action-buttons">
        <el-button type="primary" :loading="loading" @click="startSession" :disabled="!selectedAccountId || !!sessionId">
          开启发布窗口
        </el-button>
        <el-button type="danger" @click="stopSession" :disabled="!sessionId">
          关闭会话
        </el-button>
      </div>

      <el-divider>自动化填单</el-divider>
      
      <div class="auto-fill-panel" v-if="sessionId">
        <div class="selector-input">
          <label>标题选择器</label>
          <el-input v-model="selectors.title" placeholder="如: input.title-input" />
          <el-button size="small" type="success" @click="executeAction('fill', selectors.title, currentArticle?.title)">填入标题</el-button>
        </div>

        <div class="selector-input">
          <label>正文选择器 (或富文本框)</label>
          <el-input v-model="selectors.content" placeholder="如: .editor-content" />
          <el-button size="small" type="success" @click="executeAction('fill', selectors.content, currentArticle?.content)">填入正文</el-button>
        </div>

        <el-alert title="提示" type="info" :closable="false" show-icon>
          填入正文建议针对具体平台调试选择器。大部分平台支持直接填充内容或模拟粘贴。
        </el-alert>
      </div>
    </div>

    <!-- 右侧预览中心 -->
    <div class="preview-area">
      <div v-if="sessionId" class="live-preview">
        <div class="preview-header">
          <span>实时画面预览 (每秒更新)</span>
          <el-tag size="small" type="success">会话活跃: {{ sessionId }}</el-tag>
        </div>
        <div class="screen-container">
          <img 
            :src="'data:image/png;base64,' + currentSnapshot" 
            v-if="currentSnapshot" 
            class="screenshot" 
            @click="handleScreenshotClick"
            ref="screenshotRef"
          />
          <div v-else class="preview-placeholder">正在获取画面...</div>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-empty description="请从左侧选择平台并启动会话" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { getArticleList } from '../../api/article';
import { getAccountList } from '../../api/account';
import { getPlatformList } from '../../api/platform';
import type { Article, Account, Platform } from '../../types';
import request from '../../utils/request';
import { ElMessage } from 'element-plus';

const articles = ref<Article[]>([]);
const accounts = ref<Account[]>([]);
const platforms = ref<Platform[]>([]);
const selectedArticleId = ref<number | null>(null);
const selectedAccountId = ref<number | null>(null);
const sessionId = ref<string | null>(null);
const currentSnapshot = ref<string | null>(null);
const loading = ref(false);
const timer = ref<any>(null);
const screenshotRef = ref<HTMLImageElement | null>(null);

const selectors = ref({
  title: 'input[placeholder*="标题"]',
  content: '.w-e-text-container [contenteditable="true"]'
});

const currentArticle = computed(() => articles.value.find(a => a.id === selectedArticleId.value));

onMounted(async () => {
  const [artRes, accRes, platRes] = await Promise.all([
    getArticleList(),
    getAccountList(),
    getPlatformList()
  ]);
  articles.value = artRes as any;
  accounts.value = accRes as any;
  platforms.value = platRes as any;
});

onUnmounted(() => {
  stopSession();
});

const getPlatformName = (platformId: number) => {
  return platforms.value.find(p => p.id === platformId)?.platformName || '未知平台';
};

const startSession = async () => {
  const account = accounts.value.find(a => a.id === selectedAccountId.value);
  if (!account) return;

  const platform = platforms.value.find(p => p.id === account.platformId);
  if (!platform) return;

  // 预设平台编辑页 URL
  let targetUrl = '';
  switch (platform.platformKey) {
    case 'sina': targetUrl = 'https://mp.sina.com.cn/main/editor'; break;
    case 'sohu': targetUrl = 'https://mp.sohu.com/mpfe/v3/main/news/addarticle'; break;
    case 'toutiao': targetUrl = 'https://mp.toutiao.com/profile_v4/graphic/publish'; break;
    case 'netease': targetUrl = 'https://mp.163.com/v2/index.html#/article/editor/news'; break;
    case 'baijiahao': targetUrl = 'https://baijiahao.baidu.com/builder/rc/edit?type=news'; break;
    case 'dayuhao': targetUrl = 'https://mp.dayu.com/dashboard/article/write'; break;
    case 'qiehao': targetUrl = 'https://om.qq.com/article/index'; break;
    default: targetUrl = 'https://www.google.com';
  }

  loading.value = true;
  try {
    const res = await request.post<any>('/automation/start', {
      url: targetUrl,
      cookieJson: account.cookieData
    });
    if (res.code === 200) {
      sessionId.value = res.data;
      startPolling();
      ElMessage.success('会话已启动，正在加载预览...');
    }
  } catch (e) {
    ElMessage.error('启动失败');
  } finally {
    loading.value = false;
  }
};

const stopSession = async () => {
  if (timer.value) {
    clearInterval(timer.value);
    timer.value = null;
  }
  if (sessionId.value) {
    await request.delete(`/automation/session/${sessionId.value}`);
    sessionId.value = null;
    currentSnapshot.value = null;
  }
};

const startPolling = () => {
  timer.value = setInterval(async () => {
    if (!sessionId.value) return;
    try {
      const res = await request.get<any>(`/automation/snapshot/${sessionId.value}`);
      if (res.code === 200) {
        currentSnapshot.value = res.data;
      }
    } catch (e) {
      console.error('获取截图失败', e);
    }
  }, 1000);
};

const executeAction = async (type: string, selector: string, value: string = '') => {
  if (!sessionId.value) return;
  try {
    const res = await request.post<any>('/automation/action', {
      sessionId: sessionId.value,
      type,
      selector,
      value
    });
    if (res.code === 200) {
      ElMessage.success('操作指令已发送');
    }
  } catch (e) {
    ElMessage.error('操作失败');
  }
};

const handleArticleChange = () => {
  // 可以根据所选文章动态调整一些逻辑
};

const handleScreenshotClick = async (event: MouseEvent) => {
  if (!sessionId.value || !screenshotRef.value) return;

  const rect = screenshotRef.value.getBoundingClientRect();
  const x = Math.round(event.clientX - rect.left);
  const y = Math.round(event.clientY - rect.top);

  try {
    await request.post<any>('/automation/action', {
      sessionId: sessionId.value,
      type: 'click',
      x,
      y
    });
  } catch (e) {
    console.error('点击同步失败', e);
  }
};
</script>

<style scoped>
.automation-workspace {
  display: flex;
  height: calc(100vh - 100px);
  gap: 20px;
  background: #f5f7fa;
  padding: 20px;
}

.sidebar {
  width: 320px;
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.panel-header h3 {
  margin: 0;
  color: #303133;
}

.control-group label {
  display: block;
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.action-buttons {
  display: flex;
  gap: 10px;
}

.auto-fill-panel {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.selector-input {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.selector-input label {
  font-size: 12px;
  color: #909399;
}

.preview-area {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.live-preview {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.preview-header {
  padding: 10px 20px;
  background: #303133;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.screen-container {
  flex: 1;
  overflow: auto;
  background: #000;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 10px;
}

.screenshot {
  max-width: 100%;
  border: 4px solid #409eff;
  border-radius: 4px;
}

.preview-placeholder {
  color: #909399;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
