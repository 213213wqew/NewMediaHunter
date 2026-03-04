<template>
  <div class="video-create-view">
    <div class="page-header">
      <div>
        <div class="page-title">视频创作</div>
        <div class="page-subtitle">选择视频后把文件名与地址传给后端，由后台注入网址并分发</div>
      </div>
    </div>

    <div class="video-layout">
      <!-- 左侧：视频列表 -->
      <main class="video-main card">
        <div class="section-row">
          <div class="section-title">📹 视频列表</div>
          <input
            ref="fileInputRef"
            type="file"
            multiple
            accept=".mp4,.mov,.avi,.wmv,.webm,.mkv,.m4v,video/*"
            style="display: none"
            @change="handleFilePick"
          />
          <button type="button" class="btn btn-primary btn-select-folder" @click="openFilePicker">
            📁 选择视频文件
          </button>
        </div>

        <div class="video-list">
          <div v-for="(item, index) in videos" :key="item.id" class="video-item">
            <span class="item-index">{{ index + 1 }}</span>
            <input
              v-model="item.videoUrl"
              class="input input-addr input-full"
              placeholder="地址+文件名"
            />
            <button type="button" class="btn-remove" title="删除" @click="removeVideo(index)">
              ✕
            </button>
          </div>

          <div v-if="videos.length === 0" class="empty-tip">
            点击「选择视频文件」多选视频，文件名与地址会传给后端，无需本地上传
          </div>
        </div>
      </main>

      <!-- 右侧：分发渠道（内部滚动，不撑开整页） -->
      <aside class="video-sidebar card">
        <div class="sidebar-scroll-wrap">
          <div class="section-title">📡 分发渠道</div>
          <p class="sidebar-hint">选择需要发布的账号，未勾选的账号不会发布；点击卡片可展开/折叠该平台的详细设置（视频页默认发布类型为「视频」）</p>
          <div v-if="platformList.length === 0" class="loading-hint">正在加载平台列表...</div>
          <div v-else class="account-grid">
          <div v-for="plat in platformList" :key="plat.id" class="platform-config-card">
            <div
              class="account-chip"
              :class="{ selected: expandedPlatforms[plat.platformKey] }"
              @click="toggleExpanded(plat.platformKey)"
            >
              <div class="acc-icon">{{ getPlatformIcon(plat.platformKey) }}</div>
              <div class="acc-info">
                <div class="acc-name">{{ plat.platformName }}</div>
                <div class="acc-platform-status">
                  <span class="credential-ok">✓ 支持自动化发布</span>
                  <span class="expand-hint">{{ expandedPlatforms[plat.platformKey] ? '▼ 收起' : '▶ 展开设置' }}</span>
                </div>
              </div>
            </div>

            <!-- 独立设置区：展开时显示 -->
            <div v-if="expandedPlatforms[plat.platformKey] && (form.platformSettings as any)[plat.platformKey]" class="platform-settings-box">
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
                    <option value="video">视频</option>
                    <option value="dynamic">动态</option>
                  </select>
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
                <input
                  v-model="(form.platformSettings as any)[plat.platformKey].tags"
                  class="mini-input"
                  :placeholder="['bjh', 'baijiahao'].includes(plat.platformKey) ? '辅助话题...' : '标签1, 标签2...'"
                />
                <div v-if="(platformTopics as any)[plat.platformKey]?.length > 0" class="mini-topic-pool">
                  <div class="pool-header">
                    <div class="pool-title">🔥 实时热点池 (点击锁定主话题)</div>
                  </div>
                  <div class="pool-list">
                    <template v-if="['bjh', 'baijiahao'].includes(plat.platformKey)">
                      <span
                        class="pool-item special-command"
                        :class="{ active: (form.platformSettings as any)[plat.platformKey]?.selectedTopic === '__DEFAULT__' }"
                        @click.stop="handleTopicClick(plat.platformKey, '__DEFAULT__')"
                      >
                        🌟 选用默认话题
                      </span>
                      <span
                        class="pool-item special-command"
                        :class="{ active: (form.platformSettings as any)[plat.platformKey]?.selectedTopic === '__HOTTEST__' }"
                        @click.stop="handleTopicClick(plat.platformKey, '__HOTTEST__')"
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

          <div v-if="form.selectedAccountIds.length > 0" class="selected-summary selected-summary-visible">
            已选 <strong>{{ form.selectedAccountIds.length }}</strong> 个账号（仅向勾选账号发布）
          </div>

          <div class="publish-actions">
            <button
              class="btn btn-primary publish-btn"
              :disabled="publishing || !canPublish"
              @click="handlePublish"
            >
              {{ publishing ? '发布中...' : '🚀 一键发布' }}
            </button>
            <div v-if="publishStatus" class="publish-status" :class="publishStatus">
              <span class="status-icon">{{ publishStatus === 'success' ? '✓' : publishStatus === 'error' ? '✕' : '' }}</span>
              {{ publishStatusText }}
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { getPlatformList } from '../../api/platform';
import { getAccountList } from '../../api/account';
import { saveArticle } from '../../api/article';
import { submitPublishTask } from '../../api/publish';
import { getPlatformTasks } from '../../api/ai';
import type { Platform, Account } from '../../types';
import { ElMessage } from 'element-plus';

interface VideoItem {
  id: number;
  title: string;
  videoUrl: string;
}

const VIDEO_EXTS = ['.mp4', '.mov', '.avi', '.wmv', '.webm', '.mkv', '.m4v'];
function isVideoFile(file: File): boolean {
  const name = (file.name || '').toLowerCase();
  return VIDEO_EXTS.some(ext => name.endsWith(ext));
}

let nextId = 1;
const fileInputRef = ref<HTMLInputElement | null>(null);
const platformList = ref<Platform[]>([]);
const accountList = ref<Account[]>([]);
const publishing = ref(false);
const videos = reactive<VideoItem[]>([]);

const form = reactive({
  /** 勾选要发布的账号 ID，仅向这些账号发布 */
  selectedAccountIds: [] as number[],
  selectedPlatforms: [] as string[],
  platformSettings: {} as Record<string, {
    category: string;
    tags: string;
    selectedTopic: string;
    publishType: string;
    isScheduled: boolean;
    scheduledTime: string;
    selectedSkillId?: string;
  }>,
});

const platformTopics = reactive<Record<string, any[]>>({});
/** 哪些平台处于展开状态（点击卡片只切换折叠/展开，不取消选中） */
const expandedPlatforms = reactive<Record<string, boolean>>({});

const publishStatus = ref<'success' | 'error' | ''>('');
const publishStatusText = ref('');

const validVideos = computed(() =>
  videos.filter(v => v.title.trim() !== '')
);

const canPublish = computed(() =>
  validVideos.value.length > 0 && form.selectedAccountIds.length > 0
);

/** 该平台下已绑定的账号列表，各自显示各自的 */
function getAccountsForPlatform(plat: Platform): Account[] {
  return accountList.value.filter(a => a.platformId === plat.id);
}

function isAccountSelected(accountId: number): boolean {
  return form.selectedAccountIds.includes(accountId);
}

function toggleAccountSelection(accountId: number) {
  const idx = form.selectedAccountIds.indexOf(accountId);
  if (idx >= 0) {
    form.selectedAccountIds.splice(idx, 1);
  } else {
    form.selectedAccountIds.push(accountId);
  }
}

function openFilePicker() {
  fileInputRef.value?.click();
}

function handleFilePick(e: Event) {
  const input = e.target as HTMLInputElement;
  const files = input.files;
  if (!files?.length) {
    input.value = '';
    return;
  }
  const videoFiles = Array.from(files).filter(isVideoFile);
  if (videoFiles.length === 0) {
    ElMessage.warning('未选中视频文件（支持 MP4、MOV、AVI、WMV 等）');
    input.value = '';
    return;
  }
  for (const file of videoFiles) {
    const name = file.name.replace(/\.[^.]+$/, '') || file.name;
    videos.push({
      id: nextId++,
      title: name,
      videoUrl: file.name,
    });
  }
  ElMessage.success(`已添加 ${videoFiles.length} 个视频，将把文件名与地址传给后端`);
  input.value = '';
}

function removeVideo(index: number) {
  videos.splice(index, 1);
}

/** 仅切换该平台的展开/折叠，不改变是否选中（默认全部选中） */
function toggleExpanded(platformKey: string) {
  expandedPlatforms[platformKey] = !expandedPlatforms[platformKey];
}

function initPlatformSettingsForPlatform(key: string) {
  if ((form.platformSettings as any)[key]) return;
  (form.platformSettings as any)[key] = {
    category: '',
    tags: '',
    selectedTopic: ['bjh', 'baijiahao'].includes(key) ? '__DEFAULT__' : '',
    publishType: 'video',
    isScheduled: false,
    scheduledTime: '',
  };
}

function handleTopicClick(platKey: string, topic: string) {
  const s = (form.platformSettings as any)[platKey];
  if (!s) return;
  s.selectedTopic = s.selectedTopic === topic ? '' : topic;
}

function getPlatformIcon(key: string): string {
  const map: Record<string, string> = {
    baijiahao: '📘',
    bjh: '📘',
    toutiao: '📰',
    sina: '🔴',
    sohu: '🦊',
    netease: '📧',
    dayuhao: '🐟',
    qiehao: '🐧',
  };
  return map[key] || '📤';
}

async function handlePublish() {
  const list = validVideos.value;
  const accountIds = form.selectedAccountIds.slice();
  if (!list.length || !accountIds.length) return;
  publishing.value = true;
  publishStatus.value = '';
  publishStatusText.value = '';
  try {
    let successCount = 0;
    for (const v of list) {
      const article = await saveArticle({
        title: v.title.trim(),
        content: '',
        contentType: 'video',
        videoUrl: v.videoUrl.trim(),
        status: 1,
        platformSettings: Object.keys(form.platformSettings).length ? JSON.stringify(form.platformSettings) : undefined,
      });
      if (article?.id) {
        await submitPublishTask({ articleId: article.id, accountIds });
        successCount++;
      }
    }

    publishStatus.value = 'success';
    publishStatusText.value = `分发完成：已提交 ${list.length} 个视频至 ${accountIds.length} 个账号，请到「我的发文」查看`;
    ElMessage.success(publishStatusText.value);
    videos.splice(0, videos.length);
  } catch (err: any) {
    publishStatus.value = 'error';
    publishStatusText.value = '分发失败：' + (err?.message || err?.response?.data?.message || '请稍后重试');
    ElMessage.error(publishStatusText.value);
  } finally {
    publishing.value = false;
  }
}

onMounted(async () => {
  try {
    const [pRes, accounts] = await Promise.all([
      getPlatformList(),
      getAccountList(),
    ]);
    let allPlatforms = pRes || [];
    // 与内容创作一致：只显示在「分发渠道配置」中启用的平台
    const savedPlatforms = localStorage.getItem('active_platforms');
    if (savedPlatforms) {
      try {
        const activeKeys = JSON.parse(savedPlatforms);
        if (Array.isArray(activeKeys)) {
          allPlatforms = allPlatforms.filter((p: Platform) => activeKeys.includes(p.platformKey));
        }
      } catch (_) {}
    }
    platformList.value = allPlatforms;
    accountList.value = accounts || [];
    // 默认不勾选任何账号，用户需在分发渠道中手动勾选要发布的账号（未勾选则不发）
    form.selectedAccountIds = [];
    form.selectedPlatforms = allPlatforms.map((p: Platform) => p.platformKey);
    allPlatforms.forEach((p: Platform) => initPlatformSettingsForPlatform(p.platformKey));
    allPlatforms.forEach((p: Platform) => {
      if (['bjh', 'baijiahao'].includes(p.platformKey)) {
        getPlatformTasks(p.platformKey).then(ts => {
          (platformTopics as any)[p.platformKey] = ts || [];
        }).catch(() => {});
      }
    });
  } catch (e) {
    ElMessage.error('加载平台或账号失败');
  }
});
</script>

<style scoped>
.video-create-view {
  padding: 0 20px 20px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
}
.page-header {
  margin-bottom: 24px;
  flex-shrink: 0;
}
.page-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary, #1a1a1a);
}
.page-subtitle {
  font-size: 13px;
  color: var(--text-muted, #666);
  margin-top: 4px;
}

.video-layout {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 24px;
  align-items: stretch;
  min-height: 0;
  flex: 1;
  max-height: calc(100vh - 140px);
}
@media (max-width: 900px) {
  .video-layout {
    grid-template-columns: 1fr;
    max-height: none;
  }
}

.video-main {
  padding: 20px;
  min-height: 0;
  overflow-y: auto;
}
.video-sidebar {
  padding: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  max-height: calc(100vh - 140px);
  overflow: hidden;
}
.sidebar-scroll-wrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 20px;
  padding-right: 12px;
}
.section-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #1a1a1a);
}
.btn-select-folder {
  padding: 10px 20px;
  font-size: 15px;
}

.video-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}
.video-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: var(--bg-secondary, #f8f9fa);
  border-radius: 8px;
  border: 1px solid var(--border-color, #eee);
}
.item-index {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--accent-blue, #1890ff);
  color: #fff;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
}
.input-name {
  flex: 0 1 200px;
  min-width: 120px;
}
.input-addr {
  flex: 1;
  min-width: 0;
}
.input-full {
  flex: 1 1 100%;
  min-width: 0;
}
.input {
  padding: 8px 12px;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 6px;
  font-size: 14px;
  box-sizing: border-box;
}
.btn-remove {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted, #999);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}
.btn-remove:hover {
  background: rgba(231, 76, 60, 0.1);
  color: #e74c3c;
}

.empty-tip {
  padding: 32px 20px;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted, #888);
  background: var(--bg-secondary, #f8f9fa);
  border-radius: 8px;
  border: 1px dashed var(--border-color, #ddd);
}

.sidebar-hint {
  font-size: 12px;
  color: var(--text-muted, #666);
  margin-bottom: 14px;
  line-height: 1.5;
}
.loading-hint {
  font-size: 13px;
  color: var(--text-muted);
  padding: 12px 0;
}

.account-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.platform-config-card {
  margin-bottom: 4px;
  background: rgba(30, 41, 59, 0.3);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  overflow: hidden;
  transition: all 0.3s ease;
}
.platform-config-card:hover {
  background: rgba(30, 41, 59, 0.5);
  border-color: rgba(59, 130, 246, 0.3);
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
  transition: all 0.3s ease;
  position: relative;
}
.account-chip:hover {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.15);
}
.account-chip.selected {
  background: rgba(59, 130, 246, 0.1);
  border-color: #3b82f6;
  box-shadow: 0 0 20px rgba(59, 130, 246, 0.15);
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
.acc-info { flex: 1; }
.acc-name {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
  margin-bottom: 2px;
}
.acc-platform-status {
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.credential-ok { color: #10b981; }
.expand-hint {
  color: #94a3b8;
  font-size: 10px;
}

.account-select-block {
  margin-bottom: 14px;
}
.account-select-block label:first-child {
  font-size: 12px;
  color: var(--text-muted, #94a3b8);
  margin-bottom: 8px;
  display: block;
}
.no-account-hint {
  font-size: 12px;
  color: var(--text-muted, #94a3b8);
  padding: 8px 0;
}
.account-check-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 140px;
  overflow-y: auto;
}
.account-check-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 0;
}
.account-check-item input[type="checkbox"] {
  flex-shrink: 0;
}
.account-check-name {
  color: var(--text-primary, #e2e8f0);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.platform-settings-box {
  padding: 16px;
  background: rgba(15, 23, 42, 0.6);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  border-left: 3px solid #3b82f6;
  margin: 0 12px 12px 12px;
  border-radius: 0 0 12px 12px;
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
}
.mini-input:focus {
  border-color: #3b82f6;
  background: rgba(0, 0, 0, 0.6);
}
.skill-highlight .mini-input { border-color: rgba(59, 130, 246, 0.4); }
.mini-schedule {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}
.schedule-label {
  font-size: 12px;
  color: #94a3b8;
  cursor: pointer;
}
.mini-topic-pool {
  margin-top: 12px;
  background: rgba(0, 0, 0, 0.2);
  padding: 12px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.03);
}
.pool-header { margin-bottom: 10px; }
.pool-title { font-size: 11px; color: #64748b; font-weight: 600; }
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
  font-weight: 600;
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

.no-account-tip {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: normal;
}

.selected-summary {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 12px;
}
.selected-summary-visible {
  font-size: 14px;
  color: var(--text-primary, #333);
  margin-top: 14px;
  padding: 8px 12px;
  background: rgba(24, 144, 255, 0.08);
  border-radius: 8px;
  border: 1px solid rgba(24, 144, 255, 0.25);
}
.selected-summary-visible strong {
  color: var(--accent-blue, #1890ff);
}

.publish-actions {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color, #eee);
}
.publish-status {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.publish-status.success {
  background: rgba(82, 196, 26, 0.15);
  border: 1px solid rgba(82, 196, 26, 0.4);
  color: #52c41a;
}
.publish-status.error {
  background: rgba(255, 77, 79, 0.12);
  border: 1px solid rgba(255, 77, 79, 0.35);
  color: #ff4d4f;
}
.publish-status .status-icon {
  font-weight: 700;
  font-size: 14px;
}
.publish-btn {
  width: 100%;
  padding: 12px 20px;
  font-size: 15px;
  border-radius: 8px;
}
.publish-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
