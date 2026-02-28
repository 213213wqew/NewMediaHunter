<template>
  <div class="hot-news-view">
    <div class="page-header">
      <div>
        <div class="page-title">热点资讯发现</div>
        <div class="page-subtitle">根据标题关键词，全网深度抓取最具潜力的创作素材</div>
      </div>
      <div class="header-actions">
        <div class="search-group">
          <input 
            v-model="keyword" 
            placeholder="输入搜索关键词 (如：新能源、AI大模型)..." 
            @keyup.enter="handleSearch"
          />
          <button class="btn btn-primary" :disabled="loading" @click="handleSearch">
            {{ loading ? '抓取中...' : '发现热点' }}
          </button>
          <button class="btn btn-outline clear-btn" :disabled="loading" @click="handleClear">
            清空内容
          </button>
        </div>
      </div>
    </div>

    <!-- 自定义加载遮罩 -->
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
        <div class="loader-text">AI 正在寻觅散落全网的热点...</div>
      </div>
    </div>

    <!-- 资讯列表瀑布流风格 -->
    <div class="news-grid" :class="{ 'is-loading': loading }">
      <div v-for="item in newsList" :key="item.id" class="news-item-card card">
        <div class="card-tag">{{ item.source }}</div>
        <div class="card-body">
          <h3 class="news-title">{{ item.title }}</h3>
          <p class="news-summary">{{ item.summary }}</p>
          <!-- 标签 -->
          <div v-if="item.tags && item.tags.length" class="tag-row">
            <span v-for="t in item.tags" :key="t" class="news-tag">{{ t }}</span>
          </div>
          <div class="card-footer">
            <div class="meta">
              <span class="hot-score">🔥 {{ item.hotScore }}</span>
              <a v-if="item.sourceUrl" :href="item.sourceUrl" target="_blank" class="source-link" :title="item.sourceUrl">
                🔗 {{ item.source || '查看出处' }}
              </a>
              <span v-else class="time">📅 {{ new Date(item.publishTime).toLocaleTimeString() }}</span>
            </div>
            <button class="btn btn-primary btn-sm" @click="handleCopyToCreate(item)">
              立即一键创作 🪄
            </button>
          </div>
        </div>
      </div>

      <div v-if="newsList.length === 0 && !loading && !hasSearched" class="empty-placeholder">
        <div class="icon">📰</div>
        <h3>开启您的灵感之旅</h3>
        <p>在上方输入感兴趣的关键词，AI将为您精准抓取全网热度最高的资讯素材</p>
      </div>
      <div v-if="newsList.length === 0 && !loading && hasSearched" class="empty-placeholder">
        <div class="icon">🔍</div>
        <h3>暂无相关热点</h3>
        <p>当前关键词在今日热榜上尚未出现，试试换个词？例如单独搜"AI"、"模型"等短词效果更好</p>
      </div>
    </div>

    <!-- 加载更多 -->
    <div v-if="newsList.length > 0 && !loading" class="load-more-container">
      <button 
        class="btn btn-outline load-more-btn" 
        @click="handleLoadMore"
        :disabled="loadingMore || !hasMore"
      >
        <span v-if="loadingMore" class="spinner-small"></span>
        {{ loadingMore ? '努力抓取中...' : (hasMore ? '👇 加载更多热点' : '没有更多内容了') }}
      </button>
    </div>
  </div>

</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { fetchHotNews, type HotNews } from '../../api/ai';
import { ElMessage } from 'element-plus';

const router = useRouter();
const keyword = ref('');
const loading = ref(false);
const loadingMore = ref(false);
const newsList = ref<HotNews[]>([]);
const allNewsList = ref<HotNews[]>([]);
const hasMore = ref(true);
const hasSearched = ref(false);

const handleSearch = async () => {
  // 即使 keyword 为空也允许搜索，此时抓取全网综合热榜
  loading.value = true;
  newsList.value = [];
  allNewsList.value = [];
  hasMore.value = false;
  hasSearched.value = false;
  try {
    const res = await fetchHotNews(keyword.value || '');
    if (res && res.length > 0) {
      allNewsList.value = res;
      newsList.value = allNewsList.value.slice(0, 12);
      hasMore.value = allNewsList.value.length > 12;
    }
  } catch (err) {
    ElMessage.error('抓取热点失败');
  } finally {
    loading.value = false;
    hasSearched.value = true;
  }
};

const handleLoadMore = async () => {
  if (loadingMore.value) return;
  
  const currentLen = newsList.value.length;
  const nextItems = allNewsList.value.slice(currentLen, currentLen + 12);
  
  if (nextItems.length > 0) {
    loadingMore.value = true;
    setTimeout(() => {
      newsList.value.push(...nextItems);
      hasMore.value = newsList.value.length < allNewsList.value.length;
      loadingMore.value = false;
    }, 300); // 假装有个极短的网络请求动画，优化体验
  } else {
    hasMore.value = false;
    ElMessage.warning('目前该关键词下没有更多新鲜资讯了，试试换个词？');
  }
};

const handleCopyToCreate = (item: HotNews) => {
  localStorage.setItem('auto_create_news', JSON.stringify(item));
  router.push('/article');
};

const handleClear = () => {
  keyword.value = '';
  sessionStorage.removeItem('hotNewsList');
  sessionStorage.removeItem('hotNewsAllList');
  sessionStorage.removeItem('hotNewsKeyword');
  // 清空后自动加载全网热点
  handleSearch();
};

watch(newsList, (newVal) => {
  sessionStorage.setItem('hotNewsList', JSON.stringify(newVal));
}, { deep: true });

watch(keyword, (newVal) => {
  sessionStorage.setItem('hotNewsKeyword', newVal);
});

watch(allNewsList, (newVal) => {
  sessionStorage.setItem('hotNewsAllList', JSON.stringify(newVal));
}, { deep: true });

onMounted(() => {
  const cachedAll = sessionStorage.getItem('hotNewsAllList');
  const cachedNews = sessionStorage.getItem('hotNewsList');
  const cachedKeyword = sessionStorage.getItem('hotNewsKeyword');
  if (cachedAll) {
    try {
      allNewsList.value = JSON.parse(cachedAll);
      if (cachedNews) {
        newsList.value = JSON.parse(cachedNews);
      }
      if (cachedKeyword) keyword.value = cachedKeyword;
      hasMore.value = newsList.value.length < allNewsList.value.length;
      hasSearched.value = true;
    } catch (e) {
      console.error('Failed to restore cached hot news', e);
      handleSearch(); // 恢复失败则重新加载
    }
  } else {
    // 首次进入自动加载
    handleSearch();
  }
});
</script>

<style scoped>
.hot-news-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
  position: relative;
  min-height: 500px;
}

.search-group {
  display: flex;
  gap: 12px;
  background: rgba(255, 255, 255, 0.05);
  padding: 6px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.search-group input {
  background: transparent;
  border: none;
  color: white;
  padding: 0 15px;
  width: 300px;
  outline: none;
  font-size: 14px;
}

.clear-btn {
  border-color: rgba(239, 68, 68, 0.3);
  color: #ef4444;
}

.clear-btn:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.5);
  color: #f87171;
}

.news-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 24px;
}

.news-item-card {
  position: relative;
  display: flex;
  flex-direction: column;
  transition: transform 0.3s;
  overflow: hidden;
}

.news-item-card:hover {
  transform: translateY(-5px);
}

.card-tag {
  position: absolute;
  top: 0;
  right: 0;
  background: rgba(59, 130, 246, 0.2);
  color: #3b82f6;
  font-size: 10px;
  padding: 4px 10px;
  border-bottom-left-radius: 10px;
  font-weight: 600;
}

.news-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 12px;
  line-height: 1.4;
  color: #f8fafc;
}

.news-summary {
  font-size: 14px;
  color: #94a3b8;
  line-height: 1.6;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}

.news-tag {
  background: rgba(99, 102, 241, 0.15);
  color: #a5b4fc;
  border: 1px solid rgba(99, 102, 241, 0.3);
  border-radius: 12px;
  padding: 2px 10px;
  font-size: 11px;
}

.source-link {
  color: #60a5fa;
  font-size: 12px;
  text-decoration: none;
  transition: color 0.2s;
}
.source-link:hover { color: #93c5fd; text-decoration: underline; }

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 15px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.meta {
  display: flex;
  gap: 15px;
  font-size: 12px;
  color: #64748b;
}

.hot-score {
  color: #f59e0b;
  font-weight: 600;
}

.btn-sm {
  padding: 8px 16px;
  font-size: 13px;
}

.empty-placeholder {
  grid-column: 1 / -1;
  text-align: center;
  padding: 100px 0;
  color: #64748b;
}

.empty-placeholder .icon {
  font-size: 64px;
  margin-bottom: 20px;
  opacity: 0.3;
}

/* 自定义加载遮罩 */
.custom-loader-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.7);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
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
  width: 120px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  margin-bottom: 8px;
}

.runner {
  position: relative;
  z-index: 2;
  /* 我们把翻转直接加在 DOM 上还是在 keyframes 统一控制？统一用 keyframes 控制 */
  animation: runBounce 0.4s ease-in-out infinite alternate;
}

.dust-particles {
  position: absolute;
  left: 10px; /* 在小人左边（他朝右跑，所以是身后） */
  bottom: 0px;
  display: flex;
  font-size: 20px;
  z-index: 1;
}

.dust {
  position: absolute;
  opacity: 0;
}

.d1 { animation: dustFloat 0.8s linear infinite; }
.d2 { animation: dustFloat 0.8s linear infinite 0.25s; }
.d3 { animation: dustFloat 0.8s linear infinite 0.5s; }

/* 跑步的小人默认是朝左跑的，我们用 scaleX(-1) 翻转他，让他朝右跑 */
@keyframes runBounce {
  0% { transform: scaleX(-1) translateY(0) rotate(0deg); }
  /* 向右跑时，身体往前倾，视觉上顺时针旋转，因为被水平翻转过了，顺时针视觉等价于 rotate(-15deg) */
  100% { transform: scaleX(-1) translateY(-12px) rotate(-15deg); }
}

@keyframes dustFloat {
  0% { opacity: 0.8; transform: translate(0, 0) scale(0.5); }
  /* 往左后方飘散，逐渐变大且透明 */
  100% { opacity: 0; transform: translate(-40px, -15px) scale(1.5); }
}

.loader-text {
  color: #a5b4fc;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 1px;
}

/* 瀑布流加载时防抖 */
.news-grid.is-loading {
  min-height: 400px;
  position: relative;
}

/* 加载更多 */
.load-more-container {
  display: flex;
  justify-content: center;
  margin-top: 20px;
  padding-bottom: 30px;
}

.load-more-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  border-radius: 20px;
  font-weight: 500;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #f8fafc;
  cursor: pointer;
  transition: all 0.3s ease;
}

.load-more-btn:hover:not(:disabled) {
  background: rgba(99, 102, 241, 0.1);
  color: #a5b4fc;
  border-color: rgba(99, 102, 241, 0.3);
}

.load-more-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.spinner-small {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  border-top-color: currentColor;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
