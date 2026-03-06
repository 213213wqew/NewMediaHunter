<template>
  <div class="hot-news-view">
    <!-- 顶部 Tab -->
    <div class="top-tabs">
      <button
        v-for="t in tabs"
        :key="t.id"
        class="tab-btn"
        :class="{ active: activeTab === t.id }"
        @click="activeTab = t.id"
      >
        {{ t.label }}
      </button>
    </div>



    <!-- 筛选区 -->
    <div class="filter-section">
      <div class="filter-row">
        <span class="filter-label">媒体平台</span>
        <div class="filter-chips">
          <button
            v-for="p in platforms"
            :key="p"
            class="chip"
            :class="{ active: filters.platform === p }"
            @click="filters.platform = filters.platform === p ? '' : p"
          >
            {{ p }}
          </button>
        </div>
      </div>
      <div class="filter-row">
        <span class="filter-label">内容类型</span>
        <div class="filter-chips">
          <button
            v-for="c in contentTypes"
            :key="c"
            class="chip"
            :class="{ active: filters.contentType === c }"
            @click="filters.contentType = filters.contentType === c ? '' : c"
          >
            {{ c }}
          </button>
        </div>
      </div>
      <div class="filter-row">
        <span class="filter-label">内容领域</span>
        <div class="filter-chips domain-chips">
          <button
            v-for="d in domains"
            :key="d"
            class="chip"
            :class="{ active: filters.domains.includes(d) }"
            @click="toggleDomain(d)"
          >
            {{ d }}
          </button>
        </div>
      </div>
      <div class="filter-row">
        <span class="filter-label">发布时间</span>
        <div class="filter-chips">
          <button
            v-for="t in publishTimes"
            :key="t.value"
            class="chip"
            :class="{ active: filters.publishTime === t.value }"
            @click="filters.publishTime = filters.publishTime === t.value ? '' : t.value"
          >
            {{ t.label }}
          </button>
           
        </div>
      </div>
      <div class="filter-row">
        <span class="filter-label">内容排序</span>
        <div class="filter-chips">
          <button
            v-for="s in sortOptions"
            :key="s.value"
            class="chip"
            :class="{ active: filters.sort === s.value }"
            @click="filters.sort = s.value"
          >
            {{ s.label }}
          </button>
        </div>
      </div>
    </div>

    <!-- 关键词搜索 + 操作 -->
    <div class="search-bar">
      <div class="search-group">
        <span class="search-icon">🔍</span>
        <input
          v-model="keyword"
          placeholder="输入关键词搜索"
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="action-btns">
        <button class="btn btn-outline" @click="handleReset">
          <span class="action-icon">↻</span> 重置
        </button>
        <button class="btn btn-primary" :disabled="loading" @click="handleSearch">
          {{ loading ? '抓取中...' : '发现热点' }}
        </button>
        <button class="btn btn-outline" :disabled="sortedList.length === 0" @click="handleDownload">
          <span class="action-icon">↓</span> 下载数据
        </button>
      </div>
    </div>

    <!-- 加载遮罩 -->
    <div v-if="loading" class="custom-loader-overlay">
      <div class="loader-content">
        <div class="running-animation">
          <div class="runner">🏃</div>
          <div class="dust-particles">
            <span class="dust d1">💨</span>
            <span class="dust d2">💨</span>
            <span class="dust d3">💨</span>
          </div>
        </div>
        <div class="loader-text">正在抓取热点...</div>
      </div>
    </div>

    <!-- 表格：表头（阅读/评论/点赞/时间可点击排序，默认阅读量从大到小） -->
    <div v-if="sortedList.length > 0 && !loading" class="table-wrap">
      <div class="table-header-row">
        <span class="col-title">标题</span>
        <span class="col-platform">平台</span>
        <span class="col-domain">领域</span>
        <span class="col-reads sortable" @click="setSort('reads')" :title="sortTitle('reads')">
          阅读(播放) <span v-if="sortBy === 'reads'" class="sort-icon">{{ sortOrder === 'desc' ? '↓' : '↑' }}</span>
        </span>
        <span class="col-comments sortable" @click="setSort('comments')" :title="sortTitle('comments')">
          评论量 <span v-if="sortBy === 'comments'" class="sort-icon">{{ sortOrder === 'desc' ? '↓' : '↑' }}</span>
        </span>
        <span class="col-likes sortable" @click="setSort('likes')" :title="sortTitle('likes')">
          点赞量 <span v-if="sortBy === 'likes'" class="sort-icon">{{ sortOrder === 'desc' ? '↓' : '↑' }}</span>
        </span>
        <span class="col-time sortable" @click="setSort('time')" :title="sortTitle('time')">
          发布时间 <span v-if="sortBy === 'time'" class="sort-icon">{{ sortOrder === 'desc' ? '↓' : '↑' }}</span>
        </span>
        <span class="col-actions">操作</span>
      </div>
      <!-- 表格：全量显示，一行一条 -->
      <div
        v-for="item in sortedList"
        :key="item.id"
        class="table-row"
      >
        <div class="col-title cell-title">
          <div class="title-main">{{ item.title }}</div>
          <div class="title-meta">{{ item.source }}</div>
          <div v-if="item.tags && item.tags.length" class="title-keywords">
            关键词: {{ domainTags(item.tags).join('、') }}
          </div>
        </div>
        <span class="col-platform">{{ item.source }}</span>
        <span class="col-domain">{{ domainLabel(item.tags) }}</span>
        <span class="col-reads">{{ parseReadsFromSummary(item.summary) || formatReads(item.hotScore) }}</span>
        <span class="col-comments">{{ parseCommentsFromSummary(item.summary) || '—' }}</span>
        <span class="col-likes">{{ parseLikesFromSummary(item.summary) || '—' }}</span>
        <span class="col-time">{{ formatTime(item.publishTime) }}</span>
        <div class="col-actions cell-actions">
          <a v-if="item.sourceUrl" :href="item.sourceUrl" target="_blank" rel="noopener" class="link-action">查看</a>
          <a v-else href="#" class="link-action" @click.prevent>查看</a>
          <a href="#" class="link-action" @click.prevent="handleCopyLink(item)">复制链接</a>
          <button type="button" class="link-action btn-create" @click="handleCopyToCreate(item)">
            一键创作
          </button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="sortedList.length === 0 && !loading && hasSearched" class="empty-placeholder">
      <div class="icon">🔍</div>
      <h3>暂无相关热点</h3>
      <p>试试调整筛选条件或关键词，或点击「发现热点」重新抓取</p>
    </div>
    <div v-if="sortedList.length === 0 && !loading && !hasSearched" class="empty-placeholder">
      <div class="icon">📰</div>
      <h3>开启您的灵感之旅</h3>
      <p>设置筛选条件后点击「发现热点」抓取素材</p>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { fetchHotNews, type HotNews } from '../../api/ai';
import { ElMessage } from 'element-plus';

const router = useRouter();
const keyword = ref('');
const loading = ref(false);
const newsList = ref<HotNews[]>([]);
const allNewsList = ref<HotNews[]>([]);
const hasSearched = ref(false);
const pageSize = 12;
const displayCount = ref(pageSize);

// 与创作罐头一致：仅保留三个分类，对应不同 URL
const tabs = [
  { id: 'hot', label: '全网热榜' },       // -> /v1/hots/network
  { id: 'viral', label: '全网爆文' },     // -> /v1/hots/article
  { id: 'lowfans', label: '低粉爆款' },   // -> /v1/hots/popular
];
const activeTab = ref('lowfans');

function getTabParam(): string {
  if (activeTab.value === 'hot') return 'network';
  if (activeTab.value === 'viral') return 'article';
  return 'popular';
}

const platforms = ['今日头条', '百家号', '微博', '小红书', '公众号', 'B站', '快手'];
const contentTypes = ['短图文', '文章', '视频'];
const domains = [
  '全部', '生活', '影视', '体育', '情感', '娱乐', '财经', '三农', '国际', '军事', '搞笑', '美食',
  '汽车', '动漫', '运动健身', '职业职场', '时政社会', '人文社科', '动物宠物', '科学科技', '家居家装',
  '时尚', '旅游', '音乐', '健康', '历史', '游戏', '教育', '法律', '养老', '育儿', '房产', '科普', '综艺', '摄影', '其他',
];
const publishTimes = [
  { value: '3h', label: '3小时内' },
  { value: '12h', label: '12小时内' },
  { value: '1d', label: '1天内' },
  { value: '2d', label: '2天内' },
  { value: '7d', label: '7天内' },
];
const sortOptions = [
  { value: 'reads', label: '阅读(播放)量排序' },
  { value: 'comments', label: '评论量排序' },
  { value: 'time', label: '发布时间排序' },
  { value: 'likes', label: '点赞量排序' },
];

const DEFAULT_FILTERS = {
  platform: '今日头条',
  contentType: '短图文',
  domains: [] as string[],
  publishTime: '1d',
  startDate: '',
  endDate: '',
  sort: 'reads',
};

const filters = reactive({
  platform: DEFAULT_FILTERS.platform as string,
  contentType: DEFAULT_FILTERS.contentType as string,
  domains: [...DEFAULT_FILTERS.domains] as string[],
  publishTime: DEFAULT_FILTERS.publishTime as string,
  startDate: DEFAULT_FILTERS.startDate as string,
  endDate: DEFAULT_FILTERS.endDate as string,
  sort: DEFAULT_FILTERS.sort as string,
});

// 表格列排序：默认阅读量从大到小；点击表头切换排序字段与升降序
const sortBy = ref<'reads' | 'comments' | 'likes' | 'time'>('reads');
const sortOrder = ref<'asc' | 'desc'>('desc');

function setSort(field: 'reads' | 'comments' | 'likes' | 'time') {
  if (sortBy.value === field) {
    sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc';
  } else {
    sortBy.value = field;
    sortOrder.value = 'desc';
  }
  saveState();
}

function sortTitle(field: string) {
  const names: Record<string, string> = { reads: '阅读量', comments: '评论量', likes: '点赞量', time: '发布时间' };
  return `按${names[field] || field}排序（点击切换升降序）`;
}

/** 按真实阅读数排序：解析「8.3万」=83000、「9369」=9369，避免用 hotScore 导致 8 万排在 9 千后面 */
function getReadsNum(item: HotNews): number {
  const s = parseReadsFromSummary(item.summary);
  if (!s) return item.hotScore ?? 0;
  const trimmed = s.replace(/,/g, '').trim();
  const hasWan = /万/.test(trimmed);
  const numStr = trimmed.replace(/[^\d.]/g, '');
  if (!numStr) return item.hotScore ?? 0;
  const val = parseFloat(numStr);
  if (Number.isNaN(val)) return item.hotScore ?? 0;
  return hasWan ? val * 10000 : val;
}

function getCommentsNum(item: HotNews): number {
  const s = parseCommentsFromSummary(item.summary);
  if (!s) return 0;
  const n = parseInt(s.replace(/,/g, ''), 10);
  return isNaN(n) ? 0 : n;
}

function getLikesNum(item: HotNews): number {
  const s = parseLikesFromSummary(item.summary);
  if (!s) return 0;
  const n = parseInt(s.replace(/,/g, ''), 10);
  return isNaN(n) ? 0 : n;
}

const STORAGE_KEY_HOT_NEWS_STATE = 'hotNewsFilterState';

function loadSavedState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_HOT_NEWS_STATE);
    if (!raw) return;
    const data = JSON.parse(raw) as Record<string, unknown>;
    if (data.activeTab && ['hot', 'viral', 'lowfans'].includes(data.activeTab as string)) {
      activeTab.value = data.activeTab as string;
    }
    if (typeof data.keyword === 'string') keyword.value = data.keyword;
    if (data.platform && platforms.includes(data.platform as string)) filters.platform = data.platform as string;
    if (data.contentType && contentTypes.includes(data.contentType as string)) filters.contentType = data.contentType as string;
    if (Array.isArray(data.domains)) {
      filters.domains = (data.domains as string[]).filter((d) => domains.includes(d));
    }
    if (data.publishTime && publishTimes.some((t) => t.value === data.publishTime)) filters.publishTime = data.publishTime as string;
    if (typeof data.startDate === 'string') filters.startDate = data.startDate;
    if (typeof data.endDate === 'string') filters.endDate = data.endDate;
    if (data.sort && sortOptions.some((o) => o.value === data.sort)) filters.sort = data.sort as string;
    if (data.sortBy && ['reads', 'comments', 'likes', 'time'].includes(data.sortBy as string)) sortBy.value = data.sortBy as 'reads' | 'comments' | 'likes' | 'time';
    if (data.sortOrder && (data.sortOrder === 'asc' || data.sortOrder === 'desc')) sortOrder.value = data.sortOrder;
  } catch (_) {
    // 忽略损坏或旧格式
  }
}

function saveState() {
  try {
    localStorage.setItem(STORAGE_KEY_HOT_NEWS_STATE, JSON.stringify({
      activeTab: activeTab.value,
      keyword: keyword.value,
      platform: filters.platform,
      contentType: filters.contentType,
      domains: filters.domains,
      publishTime: filters.publishTime,
      startDate: filters.startDate,
      endDate: filters.endDate,
      sort: filters.sort,
      sortBy: sortBy.value,
      sortOrder: sortOrder.value,
    }));
  } catch (_) {}
}

function toggleDomain(d: string) {
  const i = filters.domains.indexOf(d);
  if (i === -1) filters.domains.push(d);
  else filters.domains.splice(i, 1);
}

function formatTime(t: string) {
  if (!t) return '';
  try {
    const d = new Date(t);
    return isNaN(d.getTime()) ? t : d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
  } catch {
    return t;
  }
}

// 从 summary 解析「阅读 x」「评论 x」「点赞 x」（与后端格式一致）
function parseReadsFromSummary(s: string | undefined): string {
  if (!s) return '';
  const m = s.match(/阅读\s*([^，]+)/);
  return (m && m[1]) ? m[1].trim() : '';
}
function parseCommentsFromSummary(s: string | undefined): string {
  if (!s) return '';
  const m = s.match(/评论\s*([^，]+)/);
  return (m && m[1]) ? m[1].trim() : '';
}
function parseLikesFromSummary(s: string | undefined): string {
  if (!s) return '';
  const m = s.match(/点赞\s*([^。]+)/);
  return (m && m[1]) ? m[1].trim() : '';
}

function formatReads(score: number | undefined): string {
  if (score == null) return '—';
  if (score >= 10000) return (score / 10000).toFixed(1) + '万';
  return String(score);
}

// 领域：取 tags 中非「热点」「深度」的第一项
function domainLabel(tags: string[] | undefined): string {
  if (!tags?.length) return '—';
  const d = tags.find((t) => t !== '热点' && t !== '深度');
  return d || tags[0] || '—';
}

// 关键词展示用：排除 热点、深度
function domainTags(tags: string[] | undefined): string[] {
  if (!tags?.length) return [];
  return tags.filter((t) => t !== '热点' && t !== '深度');
}

function handleCopyLink(item: HotNews) {
  const url = item.sourceUrl || item.url || '';
  if (url) {
    navigator.clipboard.writeText(url).then(() => ElMessage.success('已复制链接'));
  } else {
    ElMessage.warning('暂无链接');
  }
}

function inPublishTimeRange(publishTime: string): boolean {
  if (!filters.publishTime && !filters.startDate && !filters.endDate) return true;
  try {
    const t = new Date(publishTime).getTime();
    const now = Date.now();
    if (filters.publishTime === '3h') return now - t <= 3 * 60 * 60 * 1000;
    if (filters.publishTime === '12h') return now - t <= 12 * 60 * 60 * 1000;
    if (filters.publishTime === '1d') return now - t <= 24 * 60 * 60 * 1000;
    if (filters.publishTime === '2d') return now - t <= 2 * 24 * 60 * 60 * 1000;
    if (filters.publishTime === '7d') return now - t <= 7 * 24 * 60 * 60 * 1000;
    if (filters.startDate) {
      const start = new Date(filters.startDate).getTime();
      if (t < start) return false;
    }
    if (filters.endDate) {
      const end = new Date(filters.endDate).getTime() + 86400000;
      if (t > end) return false;
    }
  } catch {
    return true;
  }
  return true;
}

// 先按时间范围过滤，再在 sortedList 中按当前排序列排序（默认阅读量从大到小）
const filteredList = computed(() => {
  const list = [...allNewsList.value];
  return list.filter((i) => inPublishTimeRange(i.publishTime || ''));
});

const sortedList = computed(() => {
  const list = [...filteredList.value];
  const desc = sortOrder.value === 'desc';
  if (sortBy.value === 'reads') {
    list.sort((a, b) => (desc ? getReadsNum(b) - getReadsNum(a) : getReadsNum(a) - getReadsNum(b)));
  } else if (sortBy.value === 'comments') {
    list.sort((a, b) => (desc ? getCommentsNum(b) - getCommentsNum(a) : getCommentsNum(a) - getCommentsNum(b)));
  } else if (sortBy.value === 'likes') {
    list.sort((a, b) => (desc ? getLikesNum(b) - getLikesNum(a) : getLikesNum(a) - getLikesNum(b)));
  } else {
    list.sort((a, b) => {
      const t1 = new Date(a.publishTime || 0).getTime();
      const t2 = new Date(b.publishTime || 0).getTime();
      return desc ? t2 - t1 : t1 - t2;
    });
  }
  return list;
});

const handleSearch = async () => {
  loading.value = true;
  allNewsList.value = [];
  hasSearched.value = false;
  displayCount.value = pageSize;
  try {
    const filter = {
      tab: getTabParam(),
      platform: filters.platform || undefined,
      contentType: filters.contentType || undefined,
      domains: filters.domains.length ? [...filters.domains] : undefined,
      publishTime: filters.publishTime || undefined,
      sort: filters.sort || undefined,
    };
    const res = await fetchHotNews(keyword.value || '', getTabParam(), filter);
    if (res && res.length > 0) {
      allNewsList.value = res;
      hasSearched.value = true;
    } else {
      hasSearched.value = true;
    }
  } catch (err) {
    ElMessage.error('抓取热点失败');
    hasSearched.value = true;
  } finally {
    loading.value = false;
  }
};

const handleReset = () => {
  keyword.value = '';
  filters.platform = DEFAULT_FILTERS.platform;
  filters.contentType = DEFAULT_FILTERS.contentType;
  filters.domains = [...DEFAULT_FILTERS.domains];
  filters.publishTime = DEFAULT_FILTERS.publishTime;
  filters.startDate = DEFAULT_FILTERS.startDate;
  filters.endDate = DEFAULT_FILTERS.endDate;
  filters.sort = DEFAULT_FILTERS.sort;
  sortBy.value = 'reads';
  sortOrder.value = 'desc';
  displayCount.value = pageSize;
  sessionStorage.removeItem('hotNewsList');
  sessionStorage.removeItem('hotNewsAllList');
  sessionStorage.removeItem('hotNewsKeyword');
  saveState();
  ElMessage.success('已重置筛选条件');
};

const handleDownload = () => {
  if (sortedList.value.length === 0) return;
  const headers = ['标题', '平台', '领域', '阅读(播放)', '评论量', '点赞量', '发布时间', '摘要'];
  const rows = sortedList.value.map((i) => [
    i.title,
    i.source,
    (i.tags || []).filter((t) => t !== '热点' && t !== '深度').join(','),
    parseReadsFromSummary(i.summary) || formatReads(i.hotScore),
    parseCommentsFromSummary(i.summary) || '—',
    parseLikesFromSummary(i.summary) || '—',
    formatTime(i.publishTime || ''),
    (i.summary || '').replace(/\s+/g, ' ').slice(0, 80),
  ]);
  const csv = [headers.join(','), ...rows.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(','))].join('\n');
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `热点数据_${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  URL.revokeObjectURL(a.href);
  ElMessage.success('已下载 CSV');
};

const handleCopyToCreate = (item: HotNews) => {
  localStorage.setItem('auto_create_news', JSON.stringify(item));
  router.push('/article');
};

watch([filters, keyword], () => {
  displayCount.value = pageSize;
}, { deep: true });

watch(activeTab, saveState);
watch(keyword, saveState);
watch(filters, saveState, { deep: true });

onMounted(() => {
  // 仅恢复上一次抓取结果与筛选条件，不自动发起抓取；
  // 用户点击「发现热点」按钮时才真正调用 handleSearch。
  loadSavedState();
  const cachedAll = sessionStorage.getItem('hotNewsAllList');
  if (cachedAll) {
    try {
      allNewsList.value = JSON.parse(cachedAll);
      hasSearched.value = true;
    } catch (e) {
      console.error('Failed to restore cached hot news', e);
      allNewsList.value = [];
      hasSearched.value = false;
    }
  } else {
    hasSearched.value = false;
  }
});

watch(allNewsList, (newVal) => {
  sessionStorage.setItem('hotNewsAllList', JSON.stringify(newVal));
}, { deep: true });
watch(keyword, (newVal) => {
  sessionStorage.setItem('hotNewsKeyword', newVal);
});
</script>

<style scoped>
.hot-news-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
  min-height: 500px;
}

.top-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 8px;
}
.tab-btn {
  padding: 8px 16px;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 14px;
  cursor: pointer;
  border-radius: 6px;
}
.tab-btn:hover {
  color: #f8fafc;
  background: rgba(255, 255, 255, 0.05);
}
.tab-btn.active {
  color: #f43f5e;
  font-weight: 600;
}

.filter-section {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.filter-label {
  font-size: 13px;
  color: #94a3b8;
  min-width: 72px;
}
.filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.domain-chips {
  max-width: 100%;
}
.chip {
  padding: 6px 14px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.03);
  color: #cbd5e1;
  font-size: 13px;
  border-radius: 8px;
  cursor: pointer;
}
.chip:hover {
  border-color: rgba(248, 63, 94, 0.4);
  color: #f8fafc;
}
.chip.active {
  border-color: #f43f5e;
  color: #f43f5e;
  background: rgba(244, 63, 94, 0.1);
}
.custom-range {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
}
.custom-range input {
  padding: 4px 8px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.2);
  color: #f8fafc;
  font-size: 12px;
  width: 100px;
}

.search-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.search-group {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.05);
  padding: 8px 14px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  min-width: 260px;
}
.search-icon {
  font-size: 14px;
  opacity: 0.7;
}
.search-group input {
  flex: 1;
  background: transparent;
  border: none;
  color: white;
  outline: none;
  font-size: 14px;
}
.action-btns {
  display: flex;
  gap: 10px;
  align-items: center;
}
.action-icon {
  margin-right: 4px;
}

.table-wrap {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  overflow: hidden;
}

.table-header-row {
  display: grid;
  grid-template-columns: 2fr 0.9fr 0.9fr 0.85fr 0.7fr 0.7fr 1fr 1.2fr;
  gap: 12px;
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  color: #94a3b8;
  background: rgba(255, 255, 255, 0.06);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.table-row {
  display: grid;
  grid-template-columns: 2fr 0.9fr 0.9fr 0.85fr 0.7fr 0.7fr 1fr 1.2fr;
  gap: 12px;
  padding: 14px 16px;
  font-size: 13px;
  color: #e2e8f0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  align-items: start;
}
.table-row:last-child {
  border-bottom: none;
}
.table-row:hover {
  background: rgba(255, 255, 255, 0.03);
}

.cell-title {
  min-width: 0;
}
.title-main {
  font-size: 14px;
  line-height: 1.45;
  color: #f8fafc;
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.title-meta {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 4px;
}
.title-keywords {
  font-size: 12px;
  color: #64748b;
}

.col-platform,
.col-domain,
.col-reads,
.col-comments,
.col-likes,
.col-time {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sortable {
  cursor: pointer;
  user-select: none;
}
.sortable:hover {
  color: #cbd5e1;
}
.sort-icon {
  margin-left: 2px;
  font-size: 12px;
  opacity: 0.9;
}

.cell-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.link-action {
  color: #60a5fa;
  font-size: 13px;
  text-decoration: none;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}
.link-action:hover {
  text-decoration: underline;
  color: #93c5fd;
}
.btn-create {
  color: #a78bfa;
  font-weight: 500;
}
.btn-create:hover {
  color: #c4b5fd;
}

.empty-placeholder {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 20px;
  color: #64748b;
}
.empty-placeholder .icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.4;
}

.custom-loader-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.75);
  backdrop-filter: blur(8px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 100;
  border-radius: 16px;
}
.loader-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}
.running-animation {
  position: relative;
  width: 100px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
}
.runner {
  animation: runBounce 0.4s ease-in-out infinite alternate;
}
.dust-particles {
  position: absolute;
  left: 8px;
  bottom: 0;
  display: flex;
  font-size: 16px;
  z-index: 1;
}
.dust {
  position: absolute;
  opacity: 0;
}
.d1 { animation: dustFloat 0.8s linear infinite; }
.d2 { animation: dustFloat 0.8s linear infinite 0.25s; }
.d3 { animation: dustFloat 0.8s linear infinite 0.5s; }
@keyframes runBounce {
  0% { transform: scaleX(-1) translateY(0); }
  100% { transform: scaleX(-1) translateY(-10px) rotate(-15deg); }
}
@keyframes dustFloat {
  0% { opacity: 0.8; transform: translate(0, 0) scale(0.5); }
  100% { opacity: 0; transform: translate(-30px, -12px) scale(1.2); }
}
.loader-text {
  color: #a5b4fc;
  font-size: 13px;
}

.load-more-container {
  display: flex;
  justify-content: center;
  margin-top: 16px;
  padding-bottom: 24px;
}
.load-more-btn {
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 13px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #f8fafc;
  cursor: pointer;
}
.load-more-btn:hover {
  background: rgba(99, 102, 241, 0.15);
  border-color: rgba(99, 102, 241, 0.3);
  color: #a5b4fc;
}
</style>
