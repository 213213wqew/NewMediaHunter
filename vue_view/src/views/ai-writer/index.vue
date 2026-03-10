<template>
  <div class="ai-writer-container">
    <div class="page-header">
      <div>
        <div class="page-title">智能创作中心 <span class="badge">BETA</span></div>
        <div class="page-subtitle">输入您的兴趣关键词，通过 AI 抓取全网热点并自动撰写深度长文</div>
      </div>
    </div>

    <div class="workspace">
      <!-- 左侧：热点发现 -->
      <div class="discovery-panel card">
        <div class="panel-header">
          <div class="section-title">🔍 热点发现</div>
          <div class="search-box">
            <input 
              v-model="keyword" 
              placeholder="输入兴趣爱好 (如: AI技术, 汽车, 旅行)..." 
              @keyup.enter="handleFetchNews"
            />
            <button class="btn btn-primary" :disabled="fetching" @click="handleFetchNews">
              {{ fetching ? '抓取中...' : '发现热点' }}
            </button>
          </div>
        </div>

        <div class="news-list" v-loading="fetching">
          <div 
            v-for="item in newsList" 
            :key="item.id" 
            class="news-card"
            :class="{ active: selectedNews?.id === item.id }"
            @click="selectedNews = item"
          >
            <div class="news-tag">{{ item.source }}</div>
            <div class="news-title">{{ item.title }}</div>
            <div class="news-meta">
              <span>🔥 热度: {{ item.hotScore }}</span>
              <span>📅 {{ new Date(item.publishTime).toLocaleTimeString() }}</span>
            </div>
          </div>
          <div v-if="newsList.length === 0 && !fetching" class="empty-news">
            请输入关键词并点击“发现热点”开始探索
          </div>
        </div>
      </div>

      <!-- 右侧：AI 创作预览 -->
      <div class="creation-panel card">
        <div class="panel-header">
          <div class="section-title">🪄 AI 自动创作</div>
          <div class="panel-actions">
            <button 
              class="btn btn-primary glow-effect" 
              :disabled="!selectedNews || creating" 
              @click="handleGenerate"
            >
              {{ creating ? 'AI 正在思考并撰写中...' : '开始一键写稿' }}
            </button>
          </div>
        </div>

        <div class="tasks-area" v-if="selectedNews">
           <div class="tasks-header">
              <span class="tasks-title">百家号流量任务加持</span>
              <button class="btn btn-ghost btn-sm" :disabled="fetchingTasks" @click="handleFetchTasks">
                 {{ fetchingTasks ? '🌐 启动探针抓取中...' : '🔥 智能获取实时任务' }}
              </button>
           </div>
           <div class="tasks-list" v-if="tasks.length > 0">
              <div 
                 v-for="(task, idx) in tasks" 
                 :key="idx"
                 class="task-pill"
                 :class="{ active: selectedTask?.topic === task.topic }"
                 @click="selectedTask = selectedTask?.topic === task.topic ? null : task"
              >
                 <span class="topic">{{ task.topic }}</span>
                 <span class="count">{{ task.participants }}</span>
              </div>
           </div>
        </div>

        <div class="article-workspace" v-loading="creating">
          <div v-if="generatedContent" class="generated-result">
            <div class="result-header">
              <input v-model="generatedTitle" class="title-input" placeholder="输入文章标题" />
              <div class="cover-match">
                <img v-if="coverUrl" :src="coverUrl" class="cover-preview" />
                <button v-else class="btn btn-ghost" @click="handleMatchImage">🖼️ 自动配图</button>
              </div>
            </div>
            
            <div class="preview-scroll">
              <div class="preview-content" v-html="generatedContent"></div>
            </div>

            <div class="result-footer">
              <button class="btn btn-primary" @click="handleToEditor">
                去编辑器进一步修饰并发布
              </button>
            </div>
          </div>
          
          <div v-else class="creation-placeholder">
            <div class="placeholder-icon">🤖</div>
            <h3>AI 助手就绪</h3>
            <p>从左侧选中一条感兴趣的热点资讯，点击“开始一键写稿”按钮，系统将为您自动生成高质量的原创文章。</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { fetchHotNews, fetchArticleContent, generateArticle, matchImage, polishArticle, getPlatformTasks, matchHotTopics, type HotNews } from '../../api/ai';
import { ElMessage } from 'element-plus';

const router = useRouter();
const keyword = ref('');
const fetching = ref(false);
const creating = ref(false);
const newsList = ref<HotNews[]>([]);
const selectedNews = ref<HotNews | null>(null);

const generatedTitle = ref('');
const generatedContent = ref('');
const coverUrl = ref('');

// 流量任务
const tasks = ref<any[]>([]);
const fetchingTasks = ref(false);
const selectedTask = ref<any>(null);

const handleFetchTasks = async () => {
    fetchingTasks.value = true;
    try {
        const res = await getPlatformTasks('bjh');
        tasks.value = res;
        if (res.length > 0) {
            ElMessage.success(`成功加载 ${res.length} 个本地缓存热点任务`);
        } else {
            ElMessage.warning('本地任务池为空，请先在分发渠道中进行同步');
        }
    } catch (err: any) {
        ElMessage.error(err.response?.data?.message || err.message || '获取任务失败');
    } finally {
        fetchingTasks.value = false;
    }
};

// 抓取新闻
const handleFetchNews = async () => {
  if (!keyword.value) return ElMessage.warning('请输入搜索关键词');
  fetching.value = true;
  try {
    const res = await fetchHotNews(keyword.value);
    newsList.value = res || [];
    if (res && res.length > 0) {
      selectedNews.value = res[0] || null;
    } else {
      selectedNews.value = null;
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || err.message || '抓取失败，请稍后重试');
  } finally {
    fetching.value = false;
  }
};

// 生成文章
const handleGenerate = async () => {
  if (!selectedNews.value) return;
  creating.value = true;
  generatedContent.value = '';
  coverUrl.value = '';
  
  try {
    // 附加流量任务上下文
    let finalSummary = selectedNews.value.summary;
    if (selectedTask.value) {
        finalSummary += `\n\n【流量必带要求】：请务必在文章内容中自然地融入和讨论该热点话题：“${selectedTask.value.topic}”，以蹭取平台流量机制。`;
    }

    // 1. 生成正文
    const res = await generateArticle(selectedNews.value.title, finalSummary);
    
    // 强制清理 AI 返回的非标准 HTML 换行和空白幽灵段落
    let cleanHtml = res.content;
    cleanHtml = cleanHtml.replace(/>\s+</g, '><'); // 移除标签之间所有由换行造成的文本节点空白
    cleanHtml = cleanHtml.replace(/<p>(?:\s|&nbsp;|<br\s*\/?>)*<\/p>/gi, ''); // 移除空段落
    cleanHtml = cleanHtml.replace(/\n+/g, ''); // 彻底剥离源代码里的真实换行符
    
    generatedContent.value = cleanHtml;
    generatedTitle.value = selectedNews.value.title; // 初始标题用新闻标题

    // 2. 自动配图
    const imgRes = await matchImage(keyword.value);
    coverUrl.value = imgRes.url;
    
    ElMessage.success('AI 已完成创作！');
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || err.message || 'AI 创作过程中断');
  } finally {
    creating.value = false;
  }
};

// 自动配图 (手动触发)
const handleMatchImage = async () => {
  try {
    const res = await matchImage(keyword.value);
    coverUrl.value = res.url;
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || err.message || '配图失败');
  }
};

// 跳转到编辑器
const handleToEditor = () => {
  // 这里可以考虑用 pinia 存储或者路由 query 传递 (建议用 localStorage 暂时中转)
  localStorage.setItem('pending_article_title', generatedTitle.value);
  localStorage.setItem('pending_article_content', generatedContent.value);
  localStorage.setItem('pending_article_cover', coverUrl.value);
  localStorage.setItem('pending_article_tags', selectedTask.value ? selectedTask.value.topic : '');
  router.push('/article');
};
</script>

<style scoped>
.ai-writer-container {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

.workspace {
  flex: 1;
  display: grid;
  grid-template-columns: 350px 1fr;
  gap: 20px;
  overflow: hidden;
  margin-top: 10px;
}

.card {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0;
}

.panel-header {
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.search-box {
  display: flex;
  gap: 10px;
  margin-top: 15px;
}

.search-box input {
  flex: 1;
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 0 12px;
  color: white;
  font-size: 13px;
}

.news-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.news-card {
  padding: 15px;
  border-radius: 10px;
  margin-bottom: 10px;
  background: rgba(255, 255, 255, 0.02);
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s;
}

.news-card:hover {
  background: rgba(255, 255, 255, 0.05);
}

.news-card.active {
  background: rgba(59, 130, 246, 0.1);
  border-color: #3b82f6;
}

.news-tag {
  font-size: 10px;
  background: rgba(59, 130, 246, 0.2);
  color: #3b82f6;
  display: inline-block;
  padding: 2px 6px;
  border-radius: 4px;
  margin-bottom: 8px;
}

.news-title {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.news-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #64748b;
}

.empty-news {
  text-align: center;
  padding: 40px 20px;
  color: #64748b;
  font-size: 13px;
}

.article-workspace {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
}

.creation-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  text-align: center;
  color: #64748b;
}

.placeholder-icon {
  font-size: 64px;
  margin-bottom: 20px;
  opacity: 0.5;
}

.generated-result {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0;
}

.result-header {
  padding: 24px;
  background: rgba(255, 255, 255, 0.02);
}

.title-input {
  width: 100%;
  background: transparent;
  border: none;
  border-bottom: 2px solid rgba(59, 130, 246, 0.3);
  font-size: 24px;
  font-weight: 600;
  color: white;
  padding-bottom: 10px;
  margin-bottom: 20px;
}

.title-input:focus {
  outline: none;
  border-bottom-color: #3b82f6;
}

.cover-preview {
  width: 100%;
  height: 200px;
  object-fit: cover;
  border-radius: 12px;
  margin-bottom: 20px;
}

.preview-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 40px;
  line-height: 1.8;
  font-size: 16px;
  color: #cbd5e1;
}

:deep(.preview-content h2) {
  color: #f8fafc;
  margin: 30px 0 15px;
  font-size: 20px;
}

:deep(.preview-content p) {
  margin-bottom: 15px;
}

.result-footer {
  padding: 20px 40px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  justify-content: flex-end;
}

.badge {
  font-size: 10px;
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  color: white;
  padding: 2px 8px;
  border-radius: 20px;
  vertical-align: middle;
  margin-left: 10px;
}

.tasks-area {
  padding: 15px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(0, 0, 0, 0.1);
}

.tasks-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.tasks-title {
  font-size: 13px;
  font-weight: 500;
  color: #fbbf24;
}

.tasks-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.task-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.task-pill:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
}

.task-pill.active {
  background: rgba(59, 130, 246, 0.2);
  border-color: #3b82f6;
  color: #60a5fa;
}

.task-pill .topic {
  font-weight: 500;
}

.task-pill .count {
  font-size: 11px;
  color: #94a3b8;
}

.task-pill.active .count {
  color: rgba(96, 165, 250, 0.8);
}
</style>
