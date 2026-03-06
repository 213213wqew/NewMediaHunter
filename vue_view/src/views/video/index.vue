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
          <div v-for="(item, index) in videos" :key="item.id" class="video-item" :class="item.status">
            <span class="item-index" v-if="item.status === 'idle'">{{ index + 1 }}</span>
            <span class="item-status-icon" v-else>
              <span v-if="item.status === 'queued'">⏳</span>
              <i v-else-if="item.status === 'publishing'" class="el-icon-loading"></i>
              <span v-else-if="item.status === 'error'">⚠️</span>
              <span v-else-if="item.status === 'success'">✓</span>
            </span>
            <input
              v-model="item.videoUrl"
              class="input input-addr input-full"
              :class="{ 'input-disabled': publishing }"
              :disabled="publishing"
              placeholder="地址+文件名"
            />
            <div class="item-status-info" v-if="item.status !== 'idle'">
              <span class="status-label">{{ getStatusLabel(item.status) }}</span>
              <span class="status-detail" v-if="item.statusText">{{ item.statusText }}</span>
            </div>
            <button v-if="item.status !== 'publishing' && item.status !== 'queued'" type="button" class="btn-remove" title="删除" @click="removeVideo(index)">
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
          <p class="sidebar-hint">多视频按选中账号轮询分配（第 1 个视频→第 1 个账号，第 2 个→第 2 个…），每个账号依次上传、最多 9 个账号同时进行。点击卡片可展开/折叠详细设置。</p>
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
              <!-- 今日头条专属：生成图文（默认选中，发布时若选中则技能内勾选） -->
              <div v-if="isToutiao(plat)" class="mini-form-group toutiao-generate-article">
                <label class="generate-article-label">
                  <input
                    type="checkbox"
                    v-model="(form.platformSettings as any)[plat.platformKey].generateArticle"
                  />
                  <span>生成图文</span>
                </label>
                <div class="generate-article-hint">生成的内容将与视频同时发布</div>
              </div>
              <!-- 今日头条专属：选择作品声明（单选） -->
              <div v-if="isToutiao(plat)" class="mini-form-group work-statement-block">
                <label>📋 选择作品声明</label>
                <div class="work-statement-list">
                  <label
                    v-for="opt in WORK_STATEMENT_OPTIONS"
                    :key="opt.value"
                    class="work-statement-item"
                  >
                    <input
                      type="radio"
                      :name="'workStatement-' + plat.platformKey"
                      :value="opt.value"
                      v-model="(form.platformSettings as any)[plat.platformKey].workStatement"
                    />
                    <span>{{ opt.label }}</span>
                  </label>
                </div>
              </div>
              <!-- 百家号专属：分类 + 活动投稿（与百家号后台一致） -->
              <template v-if="isBaijiahao(plat)">
                <div class="mini-form-group bajiahao-category-row">
                  <label class="bajiahao-label">
                    分类
                    <span class="bajiahao-info-icon" title="选择内容分类">ⓘ</span>
                  </label>
                  <select
                    v-model="(form.platformSettings as any)[plat.platformKey].category"
                    class="mini-input bajiahao-category-select"
                  >
                    <option
                      v-for="opt in BAJIAHAO_CATEGORIES"
                      :key="opt.value || '__empty__'"
                      :value="opt.value"
                    >
                      {{ opt.label }}
                    </option>
                  </select>
                </div>
                <div class="mini-form-group bajiahao-activity-block">
                  <label class="bajiahao-label">活动投稿</label>
                  <input
                    v-model="(form.platformSettings as any)[plat.platformKey].selectedActivity"
                    type="text"
                    class="mini-input bajiahao-activity-input"
                    placeholder="输入活动名称匹配，留空则选第一个"
                  />
                  <div class="bajiahao-activity-hint">发布时在页面中按名称匹配；不填则默认选第一个活动</div>
                </div>
              </template>
              <!-- 视频页隐藏：发布类型、内容分类、来源标签（仅隐藏 UI，数据仍提交给后端） -->
              <div class="video-settings-hidden">
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
              </div>
              <!-- 定时不在此页设置，仅隐藏 UI，数据仍保留 -->
              <div class="video-settings-hidden mini-schedule">
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
            已选 <strong>{{ form.selectedAccountIds.length }}</strong> 个账号（多视频将轮询分配，每账号依次上传）
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
import { submitVideoBatch, getTaskStatus } from '../../api/publish';
import { getPlatformTasks } from '../../api/ai';
import type { Platform, Account } from '../../types';
import { ElMessage } from 'element-plus';

interface VideoItem {
  id: number;
  title: string;
  videoUrl: string;
  status: 'idle' | 'queued' | 'publishing' | 'success' | 'error';
  statusText?: string;
  taskId?: number;
}

// 作品声明：value 与头条页「作品声明」选项文案一致（含标点），直接传给技能点击
const WORK_STATEMENT_OPTIONS = [
  { value: '取自站外', label: '取自站外' },
  { value: '引用站内', label: '引用站内' },
  { value: '自行拍摄', label: '自行拍摄' },
  { value: 'AI生成', label: 'AI生成' },
  { value: '虚构演绎，故事经历', label: '虚构演绎，故事经历' },
  { value: '投资观点，仅供参考', label: '投资观点，仅供参考' },
  { value: '健康医疗分享，仅供参考', label: '健康医疗分享，仅供参考' },
];

/** 百家号 - 分类（与百家号后台一致） */
const BAJIAHAO_CATEGORIES = [
  { value: '', label: '-- 选择分类 --' },
  { value: '法律/案件解读', label: '法律 / 案件解读' },
  { value: '生活', label: '生活' },
  { value: '科技', label: '科技' },
  { value: '娱乐', label: '娱乐' },
  { value: '财经', label: '财经' },
  { value: '教育', label: '教育' },
  { value: '体育', label: '体育' },
  { value: '汽车', label: '汽车' },
  { value: '游戏', label: '游戏' },
  { value: '旅游', label: '旅游' },
  { value: '军事', label: '军事' },
  { value: '国际', label: '国际' },
  { value: '时尚', label: '时尚' },
  { value: '母婴', label: '母婴' },
  { value: '美食', label: '美食' },
  { value: '其他', label: '其他' },
];

const VIDEO_EXTS = ['.mp4', '.mov', '.avi', '.wmv', '.webm', '.mkv', '.m4v'];
function isVideoFile(file: File): boolean {
  const name = (file.name || '').toLowerCase();
  return VIDEO_EXTS.some(ext => name.endsWith(ext));
}

let nextId = 1;
const fileInputRef = ref<HTMLInputElement | null>(null);
/** 上次选择文件所在目录（File System Access API），下次打开时定位到该文件夹 */
const lastDirHandleRef = ref<FileSystemDirectoryHandle | null>(null);
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
    /** 作品声明单选（如：取自站外、AI生成等） */
    workStatement: string;
    selectedSkillId?: string;
    /** 百家号专用：活动投稿选中的活动名称 */
    selectedActivity?: string;
    /** 今日头条专用：是否勾选「生成图文」，默认 true */
    generateArticle?: boolean;
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

async function openFilePicker() {
  const win = window as Window & { showOpenFilePicker?: (opts?: { multiple?: boolean; types?: { description: string; accept: Record<string, string[]> }[]; startIn?: FileSystemDirectoryHandle }) => Promise<FileSystemFileHandle[]> };
  if (typeof win.showOpenFilePicker === 'function') {
    try {
      const opts: { multiple: true; types: { description: string; accept: Record<string, string[]> }[]; startIn?: FileSystemDirectoryHandle } = {
        multiple: true,
        types: [{ description: '视频', accept: { 'video/*': ['.mp4', '.mov', '.avi', '.wmv', '.webm', '.mkv', '.m4v'] } }],
      };
      if (lastDirHandleRef.value) opts.startIn = lastDirHandleRef.value;
      const handles = await win.showOpenFilePicker(opts);
      if (!handles.length) return;
      const files: File[] = [];
      for (const h of handles) {
        files.push(await h.getFile());
      }
      try {
        const first = handles[0];
        const withParent = first as FileSystemFileHandle & { getParent?: () => Promise<FileSystemDirectoryHandle> };
        if (first && typeof withParent.getParent === 'function') {
          lastDirHandleRef.value = await withParent.getParent();
        }
      } catch (_) {}
      const videoFiles = files.filter(isVideoFile);
      if (videoFiles.length === 0) {
        ElMessage.warning('未选中视频文件（支持 MP4、MOV、AVI、WMV 等）');
        return;
      }
      for (const file of videoFiles) {
        const name = file.name.replace(/\.[^.]+$/, '') || file.name;
        videos.push({ 
          id: nextId++, 
          title: name, 
          videoUrl: file.name,
          status: 'idle'
        });
      }
      ElMessage.success(`已添加 ${videoFiles.length} 个视频，下次将从此文件夹打开`);
      return;
    } catch (err: any) {
      if (err?.name === 'AbortError') return;
      ElMessage.warning('当前浏览器不支持记忆文件夹，使用传统选择方式');
    }
  }
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
      status: 'idle'
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

function isBaijiahao(plat: Platform): boolean {
  return plat.platformKey === 'bjh' || plat.platformKey === 'baijiahao';
}

function isToutiao(plat: Platform): boolean {
  return plat.platformKey === 'toutiao';
}

function initPlatformSettingsForPlatform(key: string) {
  const cur = (form.platformSettings as any)[key];
  if (cur) {
    if (key === 'toutiao' && cur.generateArticle === undefined) cur.generateArticle = true;
    return;
  }
  (form.platformSettings as any)[key] = {
    category: '',
    tags: '',
    selectedTopic: ['bjh', 'baijiahao'].includes(key) ? '__DEFAULT__' : '',
    publishType: 'video',
    isScheduled: false,
    scheduledTime: '',
    workStatement: '虚构演绎，故事经历',
    ...(['bjh', 'baijiahao'].includes(key) ? { selectedActivity: '' as string } : {}),
    ...(key === 'toutiao' ? { generateArticle: true } : {}),
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

/** 将已选账号按平台分组，用于同平台内轮询平分 */
function getSelectedAccountsByPlatform(): Map<number, number[]> {
  const byPlatform = new Map<number, number[]>();
  for (const acc of accountList.value) {
    if (!form.selectedAccountIds.includes(acc.id)) continue;
    const arr = byPlatform.get(acc.platformId) || [];
    arr.push(acc.id);
    byPlatform.set(acc.platformId, arr);
  }
  return byPlatform;
}

function getStatusLabel(status: string) {
  const map: Record<string, string> = {
    'queued': '等待发布',
    'publishing': '正在发布',
    'success': '发布成功',
    'error': '发布失败'
  };
  return map[status] || '';
}

/** 轮询任务状态直到结束 */
async function pollTaskStatus(videoItem: VideoItem, taskId: number) {
  const maxRetries = 200; // 约 10 分钟（3s 一次）
  let retries = 0;
  
  const timer = setInterval(async () => {
    retries++;
    if (retries > maxRetries) {
      clearInterval(timer);
      videoItem.status = 'error';
      videoItem.statusText = '发布超时';
      return;
    }
    
    try {
      const task = await getTaskStatus(taskId);
      if (!task) return;
      
      // 0:待处理, 1:待发布(已填写), 2:发布中, 3:成功, 4:失败
      if (task.publishStatus === 3) {
        clearInterval(timer);
        videoItem.status = 'success';
        videoItem.statusText = '发布成功';
      } else if (task.publishStatus === 4) {
        clearInterval(timer);
        videoItem.status = 'error';
        videoItem.statusText = task.errorMessage || '发布失败';
      } else if (task.publishStatus === 2) {
        videoItem.status = 'publishing';
        videoItem.statusText = '正在发布（上传/操作浏览器）...';
      } else {
        // 0/1 统一视为等待发布
        videoItem.status = 'queued';
        videoItem.statusText = '等待发布...';
      }
    } catch (err) {
      console.warn('轮询任务失败:', err);
    }
  }, 3000);
}

async function handlePublish() {
  const accountIds = form.selectedAccountIds.slice();
  const toPublish = videos.filter(v => v.status !== 'success' && v.status !== 'publishing' && v.status !== 'queued');
  if (!toPublish.length || !accountIds.length) return;

  publishing.value = true;
  publishStatus.value = '';
  publishStatusText.value = '';

  try {
    // 先全部标为“等待发布”，再依次保存为文章，并记录「视频项 -> 文章ID」对应关系
    const articleIds: number[] = [];
    const videoByIndex: VideoItem[] = [];
    for (const v of toPublish) {
      v.status = 'queued';
      v.statusText = '准备提交...';
    }
    for (const v of toPublish) {
      const title = (v.title?.trim() || v.videoUrl.replace(/\.[^.]+$/, '')).trim();
      if (!title) continue;
      try {
        const article = await saveArticle({
          title,
          content: '',
          contentType: 'video',
          videoUrl: v.videoUrl.trim(),
          status: 1,
          platformSettings: Object.keys(form.platformSettings).length ? JSON.stringify(form.platformSettings) : undefined,
        });
        if (article?.id) {
          articleIds.push(article.id);
          videoByIndex.push(v);
        }
      } catch (e) {
        v.status = 'error';
        v.statusText = (e as Error)?.message || '保存失败';
      }
    }
    if (articleIds.length === 0) {
      publishStatus.value = 'error';
      publishStatusText.value = '没有成功保存的视频';
      return;
    }
    // 一次性批量提交：后端按账号轮询分配，同一账号串行、最多 9 账号并发，避免一账号多浏览器
    const tasks = await submitVideoBatch({ articleIds, accountIds });
    if (!tasks || tasks.length === 0) {
      publishStatus.value = 'error';
      publishStatusText.value = '提交任务失败';
      return;
    }
    // 任务顺序与 articleIds 一致，按索引绑定到对应视频并轮询
    for (let i = 0; i < videoByIndex.length && i < tasks.length; i++) {
      const t = tasks[i];
      const v = videoByIndex[i];
      if (!t?.id || !v) continue;
      v.taskId = t.id;
      v.status = 'queued';
      v.statusText = '等待发布...';
      pollTaskStatus(v, t.id);
    }
    publishStatus.value = 'success';
    publishStatusText.value = `已提交 ${tasks.length} 个任务，按账号依次执行（每账号一个浏览器）`;
  } catch (err: any) {
    publishStatus.value = 'error';
    publishStatusText.value = '批量提交出错：' + (err?.message || '请检查网络');
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
    // 默认只选中「当前展示的平台」下的账号（例如只配置了头条则只选头条账号）
    const platformIds = new Set(allPlatforms.map((p: Platform) => p.id));
    form.selectedAccountIds = (accounts || []).filter((a: Account) => platformIds.has(a.platformId)).map((a: Account) => a.id);
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
  grid-template-columns: 1fr 420px;
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
  transition: all 0.3s ease;
}
.video-item.publishing {
  border-left: 4px solid var(--accent-blue, #1890ff);
  background: rgba(24, 144, 255, 0.02);
}
.video-item.success {
  border-left: 4px solid #52c41a;
  background: rgba(82, 196, 26, 0.02);
}
.video-item.error {
  border-left: 4px solid #ff4d4f;
  background: rgba(255, 77, 79, 0.02);
}

.item-status-icon {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}
.item-status-icon .el-icon-loading {
  color: var(--accent-blue);
  font-size: 16px;
}

.item-status-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-left: 8px;
  min-width: 120px;
}
.status-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}
.status-detail {
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 150px;
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
  transition: all 0.3s ease;
}
.input-disabled {
  background: var(--bg-tertiary, #f0f1f2);
  color: var(--text-muted, #999);
  cursor: not-allowed;
  border-color: transparent;
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

/* 百家号专属：分类 + 活动投稿 */
.bajiahao-category-row {
  margin-top: 14px;
}
.bajiahao-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-primary, #e2e8f0);
  margin-bottom: 8px;
}
.bajiahao-info-icon {
  font-size: 12px;
  color: #94a3b8;
  cursor: help;
  opacity: 0.85;
}
.bajiahao-category-select {
  width: 100%;
  max-width: 280px;
}
.bajiahao-activity-block {
  margin-top: 16px;
}
.bajiahao-activity-block > .bajiahao-label {
  margin-bottom: 10px;
}
.bajiahao-activity-input {
  width: 100%;
  max-width: 320px;
}
.bajiahao-activity-hint {
  font-size: 11px;
  color: var(--text-muted, #94a3b8);
  margin-top: 6px;
}
.toutiao-generate-article {
  margin-top: 14px;
}
.toutiao-generate-article .generate-article-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-primary, #e2e8f0);
  cursor: pointer;
}
.toutiao-generate-article .generate-article-label input[type="checkbox"] {
  flex-shrink: 0;
}
.toutiao-generate-article .generate-article-hint {
  font-size: 11px;
  color: var(--text-muted, #94a3b8);
  margin-top: 6px;
  margin-left: 24px;
}
.bajiahao-activity-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.bajiahao-activity-chip {
  padding: 6px 12px;
  font-size: 12px;
  color: var(--text-primary, #e2e8f0);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}
.bajiahao-activity-chip:hover {
  background: rgba(59, 130, 246, 0.15);
  border-color: rgba(59, 130, 246, 0.4);
  color: #fff;
}
.bajiahao-activity-chip.active {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border-color: transparent;
  color: #fff;
  font-weight: 500;
}
.bajiahao-more-activities {
  font-size: 12px;
  color: #60a5fa;
  text-decoration: none;
}
.bajiahao-more-activities:hover {
  text-decoration: underline;
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
/* 视频页隐藏：发布类型、内容分类、来源标签，仅隐藏 UI，数据照常提交 */
.video-settings-hidden {
  display: none !important;
}
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
.btn-secondary {
  background: var(--bg-secondary, #475569);
  color: #fff;
  border: none;
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
.work-statement-block {
  margin-top: 14px;
}
.work-statement-block > label:first-child {
  font-size: 12px;
  color: var(--text-muted, #94a3b8);
  margin-bottom: 8px;
  display: block;
}
.work-statement-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  max-height: none;
}
.work-statement-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  cursor: pointer;
  color: var(--text-primary, #e2e8f0);
}
.work-statement-item input[type="radio"],
.work-statement-item input[type="checkbox"] {
  flex-shrink: 0;
}
.work-statement-item input[type="radio"] {
  appearance: none;
  -webkit-appearance: none;
  width: 16px;
  height: 16px;
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 2px;
  background: rgba(0, 0, 0, 0.2);
  position: relative;
  cursor: pointer;
}
.work-statement-item input[type="radio"]:checked {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.25);
}
.work-statement-item input[type="radio"]:checked::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 8px;
  height: 8px;
  background: #3b82f6;
  border-radius: 1px;
}
</style>
