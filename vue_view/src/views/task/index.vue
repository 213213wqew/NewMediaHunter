<template>
  <div>
    <div class="page-header">
      <div>
        <div class="page-header-top">
          <div class="page-title">我的发文记录</div>
          <div class="tab-switcher">
            <div class="tab-btn" :class="{ active: currentTab === 'tasks' }" @click="currentTab = 'tasks'">🚀 发布任务</div>
            <div class="tab-btn" :class="{ active: currentTab === 'drafts' }" @click="currentTab = 'drafts'">📝 文稿库</div>
          </div>
        </div>
        <div class="page-subtitle">追踪各平台分发任务的实时进度与结果，或继续编辑您的创作草稿</div>
      </div>
      <div style="display: flex; gap: 8px;" v-if="currentTab === 'tasks'">
        <span class="badge badge-success"><span class="badge-dot"></span>成功 {{ successCount }}</span>
        <span class="badge badge-warning"><span class="badge-dot"></span>进行中 {{ loadingCount }}</span>
        <span class="badge badge-danger"><span class="badge-dot"></span>失败 {{ errorCount }}</span>
      </div>
    </div>

    <div class="table-container" v-loading="loading">
      <!-- 任务列表 -->
      <table v-if="currentTab === 'tasks'">
        <thead>
          <tr>
            <th>任务ID</th>
            <th>文章ID</th>
            <th>账号ID</th>
            <th>进度</th>
            <th>结果</th>
            <th>执行详情</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in taskList" :key="task.id" class="task-row" @click="showDetails(task)">
            <td style="color: var(--text-muted); font-family: monospace; font-size: 12px;">#{{ task.id }}</td>
            <td style="color: var(--text-primary);">#{{ task.articleId }}</td>
            <td style="color: var(--text-secondary);">Account #{{ task.accountId }}</td>
            <td style="min-width: 140px;">
              <div style="display: flex; align-items: center; gap: 8px;">
                <div class="progress-bar" style="flex: 1;">
                  <div class="progress-fill" :class="task.publishStatus === 3 ? 'success' : task.publishStatus === 4 ? 'error' : ''" :style="`width: ${getProgress(task.publishStatus)}%`"></div>
                </div>
                <span style="font-size: 11px; color: var(--text-muted); min-width: 28px;">{{ getProgress(task.publishStatus) }}%</span>
              </div>
            </td>
            <td>
              <span v-if="task.publishStatus === 3">
                <a :href="task.platformArticleUrl" target="_blank" @click.stop style="color: var(--accent-blue); font-size: 12px; text-decoration: none;">🔗 查看文章</a>
              </span>
              <span v-else-if="task.publishStatus === 4">
                <span class="badge badge-danger" :title="task.errorMessage"><span class="badge-dot"></span>失败</span>
              </span>
              <span v-else>
                <span class="badge badge-muted"><span class="badge-dot"></span>{{ getStatusLabel(task.publishStatus) }}</span>
              </span>
            </td>
            <td style="color: var(--text-muted); font-size: 12px; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
              {{ task.errorMessage || '正常执行中...' }}
            </td>
          </tr>
          <tr v-if="taskList.length === 0">
            <td colspan="6" style="text-align: center; padding: 60px 40px; color: var(--text-muted);">
              <div style="font-size: 24px; margin-bottom: 12px;">📭</div>
              <div>暂无发布记录</div>
              <div style="font-size: 13px; margin-top: 8px; color: var(--text-secondary);">
                您还没有发送过分发任务，去“内容创作”页面写好文章点击“立即发布”吧！
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 草稿列表 -->
      <table v-else>
        <thead>
          <tr>
            <th>文稿ID</th>
            <th>标题</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="draft in draftList" :key="draft.id">
            <td style="color: var(--text-muted); font-family: monospace; font-size: 12px;">#{{ draft.id }}</td>
            <td style="font-weight: 500; color: var(--text-primary); max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ draft.title }}</td>
            <td>
              <span class="badge" :class="draft.status === 0 ? 'badge-muted' : 'badge-success'">
                <span class="badge-dot"></span>{{ draft.status === 0 ? '草稿' : '已就绪' }}
              </span>
            </td>
            <td style="color: var(--text-secondary); font-size: 12px;">{{ new Date(draft.createTime).toLocaleString() }}</td>
            <td>
              <router-link :to="`/article?id=${draft.id}`" class="btn-edit-inline">继续编辑 ✍️</router-link>
              <button class="btn-text-danger" style="margin-left: 10px;" @click="handleDeleteDraft(draft.id!)">删除 🗑️</button>
            </td>
          </tr>
          <tr v-if="draftList.length === 0">
            <td colspan="5" style="text-align: center; padding: 40px; color: var(--text-muted);">暂无文稿内容</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 任务审计详情抽屉 -->
    <div class="drawer-overlay" v-if="drawerVisible" @click="drawerVisible = false">
      <div class="drawer-content" @click.stop>
        <div class="drawer-header">
          <div class="drawer-title">
            <span class="icon">📜</span> 任务审计详情
            <span class="task-id">#{{ selectedTask?.id }}</span>
          </div>
          <button class="close-btn" @click="drawerVisible = false">×</button>
        </div>

        <div class="drawer-body">
          <div v-if="loadingLogs" class="loading-state">
            <div class="spinner"></div>
            加载审计日志中...
          </div>
          
          <div v-else class="log-timeline">
            <div v-for="log in logs" :key="log.id" class="log-item" :class="log.logLevel.toLowerCase()">
              <div class="log-time">{{ log.createTime.replace('T', ' ').substring(11, 19) }}</div>
              <div class="log-info">
                <div class="log-msg">{{ log.message }}</div>
                <div v-if="log.requestData" class="log-code-box">
                  <div class="code-header">Request</div>
                  <pre>{{ log.requestData }}</pre>
                </div>
                <div v-if="log.responseData" class="log-code-box">
                  <div class="code-header">Response ({{ log.httpStatus }})</div>
                  <pre>{{ log.responseData }}</pre>
                </div>
                <div v-if="log.stackTrace" class="log-code-box error">
                  <div class="code-header">Error Stack Trace</div>
                  <pre>{{ log.stackTrace }}</pre>
                </div>
              </div>
            </div>
            <div v-if="logs.length === 0" class="empty-logs">
              暂无审计日志记录
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted } from 'vue';
import { getTaskList, getTaskLogs } from '../../api/publish';
import { getArticleList, deleteArticle } from '../../api/article';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { PublishTask, PublishLog, Article } from '../../types';

const currentTab = ref('tasks');
const taskList = ref<PublishTask[]>([]);
const draftList = ref<Article[]>([]);
const loading = ref(false);
const timer = ref<any>(null);

// 详情抽屉状态
const drawerVisible = ref(false);
const selectedTask = ref<PublishTask | null>(null);
const logs = ref<PublishLog[]>([]);
const loadingLogs = ref(false);

const successCount = computed(() => taskList.value.filter(t => t.publishStatus === 3).length);
const loadingCount = computed(() => taskList.value.filter(t => t.publishStatus < 3).length);
const errorCount = computed(() => taskList.value.filter(t => t.publishStatus === 4).length);

const loadData = async () => {
  if (loading.value) return;
  try {
    const [tasks, drafts] = await Promise.all([
      getTaskList(),
      getArticleList()
    ]);
    taskList.value = tasks || [];
    // 展示所有属于我的文章内容 (不再局限于 status=0)
    draftList.value = drafts || [];
  } catch (err) {
    console.error(err);
  }
};

const handleDeleteDraft = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这份草稿吗？删除后无法恢复。', '警告', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    });
    
    await deleteArticle(id);
    ElMessage.success('草稿已删除');
    loadData();
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('删除失败');
    }
  }
};

const showDetails = async (task: PublishTask) => {
  selectedTask.value = task;
  drawerVisible.value = true;
  loadingLogs.value = true;
  try {
    const res = await getTaskLogs(task.id);
    logs.value = res.reverse(); // 时间正序显示
  } catch (err) {
    console.error('加载日志失败', err);
  } finally {
    loadingLogs.value = false;
  }
};

onMounted(() => {
  loadData();
  timer.value = setInterval(loadData, 5000);
});

onUnmounted(() => {
  if (timer.value) clearInterval(timer.value);
});

const getProgress = (status: number) => {
  switch (status) {
    case 0: return 10;
    case 1: return 35;
    case 2: return 60;
    case 3: return 100;
    case 4: return 100;
    default: return 0;
  }
};

const getStatusLabel = (status: number) => {
  switch (status) {
    case 0: return '待处理';
    case 1: return '排队中';
    case 2: return '发布中';
    default: return '未知';
  }
};
</script>

<style scoped>
.task-row {
  cursor: pointer;
  transition: background 0.2s;
}

.task-row:hover {
  background: rgba(255, 255, 255, 0.02);
}

/* 抽屉样式 */
.drawer-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  z-index: 2000;
  display: flex;
  justify-content: flex-end;
}

.drawer-content {
  width: 500px;
  background: #1e1e1e;
  height: 100%;
  box-shadow: -10px 0 30px rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  animation: slide-in 0.3s ease-out;
}

@keyframes slide-in {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}

.drawer-header {
  padding: 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.drawer-title {
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-id {
  font-family: monospace;
  font-size: 14px;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.05);
  padding: 2px 6px;
  border-radius: 4px;
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  font-size: 24px;
  cursor: pointer;
  line-height: 1;
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--text-muted);
  gap: 16px;
}

.spinner {
  width: 24px;
  height: 24px;
  border: 2px solid rgba(255, 255, 255, 0.1);
  border-top-color: var(--accent-blue);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 日志轴样式 */
.log-timeline {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.log-item {
  position: relative;
  padding-left: 20px;
  border-left: 2px solid rgba(255, 255, 255, 0.05);
}

.log-item::before {
  content: '';
  position: absolute;
  left: -5px;
  top: 4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #333;
}

.log-item.info::before { background: var(--accent-blue); box-shadow: 0 0 8px var(--accent-blue); }
.log-item.error::before { background: var(--accent-red); box-shadow: 0 0 8px var(--accent-red); }

.log-time {
  font-size: 11px;
  color: var(--text-muted);
  font-family: monospace;
  margin-bottom: 4px;
}

.log-msg {
  font-size: 14px;
  line-height: 1.5;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.log-code-box {
  background: #111;
  border-radius: 6px;
  padding: 12px;
  margin-top: 8px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.log-code-box.error {
  border-color: rgba(231, 76, 60, 0.2);
  background: rgba(231, 76, 60, 0.02);
}

.code-header {
  font-size: 10px;
  text-transform: uppercase;
  color: var(--text-muted);
  letter-spacing: 1px;
  margin-bottom: 6px;
}

pre {
  margin: 0;
  font-family: 'Fira Code', monospace;
  font-size: 12px;
  color: #ccc;
  white-space: pre-wrap;
  word-break: break-all;
}

.empty-logs {
  text-align: center;
  padding: 40px;
  color: var(--text-muted);
  font-size: 14px;
}

/* 新增样式 */
.page-header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.tab-switcher {
  display: flex;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 4px;
}

.tab-btn {
  padding: 6px 16px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 6px;
}

.tab-btn.active {
  background: var(--accent-blue);
  color: white;
  box-shadow: 0 2px 8px rgba(0, 120, 212, 0.3);
}

.btn-edit-inline {
  background: rgba(0, 120, 212, 0.1);
  color: var(--accent-blue);
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  text-decoration: none;
  transition: all 0.2s;
}

.btn-edit-inline:hover {
  background: var(--accent-blue);
  color: white;
}

.btn-text-danger {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-text-danger:hover {
  background: #ef4444;
  color: white;
}
</style>
