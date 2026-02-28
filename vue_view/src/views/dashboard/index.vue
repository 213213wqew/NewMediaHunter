<template>
  <div>
    <div class="page-header">
      <div>
        <div class="page-title">控制大盘</div>
        <div class="page-subtitle">多平台内容分发全局指挥中心</div>
      </div>
      <div class="badge badge-success">
        <span class="badge-dot"></span> 实时同步中
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <div class="stat-card blue">
        <div class="stat-icon">🔗</div>
        <div class="stat-value">{{ stats.totalAccounts }}</div>
        <div class="stat-label">已绑定账号</div>
        <div class="stat-trend">实时数据</div>
      </div>
      <div class="stat-card purple">
        <div class="stat-icon">📤</div>
        <div class="stat-value">{{ stats.totalTasks }}</div>
        <div class="stat-label">累计分发任务</div>
        <div class="stat-trend" style="color: var(--accent-blue)">队列运行中</div>
      </div>
      <div class="stat-card green">
        <div class="stat-icon">✅</div>
        <div class="stat-value">{{ stats.successRate.toFixed(1) }}%</div>
        <div class="stat-label">近期发布成功率</div>
        <div class="stat-trend" style="color: var(--accent-green)">总体表现</div>
      </div>
      <div class="stat-card orange">
        <div class="stat-icon">📝</div>
        <div class="stat-value">{{ stats.totalArticles }}</div>
        <div class="stat-label">累计管理文稿数</div>
        <div class="stat-trend">包含所有历史创作及草稿</div>
      </div>
    </div>

    <!-- 图表区域 -->
    <div class="chart-grid">
      <div class="card chart-card">
        <div class="section-title">📈 近7日分发趋势</div>
        <div ref="trendChartRef" style="height: 300px; width: 100%;"></div>
      </div>
      <div class="card chart-card">
        <div class="section-title">📊 平台分发占比</div>
        <div ref="platformChartRef" style="height: 300px; width: 100%;"></div>
      </div>
    </div>

    <div class="two-col" style="margin-top: 20px;">
      <!-- 最近任务 -->
      <div class="card">
        <div class="section-title">🕐 最近发布记录</div>
        <div class="table-container" style="border: none; border-radius: 8px;">
          <table>
            <thead>
              <tr>
                <th>文章ID</th>
                <th>发布状态</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in recentTasks" :key="item.id">
                <td style="color: var(--text-primary);">#{{ item.articleId }}</td>
                <td>
                  <span class="badge" :class="getStatusClass(item.publishStatus)">
                    <span class="badge-dot"></span>{{ getStatusText(item.publishStatus) }}
                  </span>
                </td>
                <td style="font-size: 12px; color: var(--text-muted);">{{ new Date(item.createTime).toLocaleString() }}</td>
              </tr>
              <tr v-if="recentTasks.length === 0">
                <td colspan="3" style="text-align: center; padding: 20px; color: var(--text-muted);">暂无记录</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 快捷工具盒 -->
      <div class="card">
        <div class="section-title">⚡ 快速创作</div>
        <div style="padding: 10px 0;">
          <p style="color: var(--text-muted); font-size: 14px; margin-bottom: 20px;">开始撰写您的下一篇精彩报道吧，洗涤引擎已准备就绪。</p>
          <button class="btn btn-primary" @click="$router.push('/article')" style="width: 100%;">
            ✍️ 撰写新文章
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import * as echarts from 'echarts';
import { getTaskList, getPublishStats } from '../../api/publish';
import type { PublishTask, PublishStats } from '../../types';

const tasks = ref<PublishTask[]>([]);
const stats = ref<PublishStats>({
  totalAccounts: 0,
  totalArticles: 0,
  totalTasks: 0,
  successRate: 0,
  seriesData: [],
  platformData: []
});

const trendChartRef = ref<HTMLElement | null>(null);
const platformChartRef = ref<HTMLElement | null>(null);
let trendChart: echarts.ECharts | null = null;
let platformChart: echarts.ECharts | null = null;

const recentTasks = computed(() => tasks.value.slice(0, 5));

const initCharts = () => {
  if (trendChartRef.value) {
    trendChart = echarts.init(trendChartRef.value, 'dark');
    updateTrendChart();
  }
  if (platformChartRef.value) {
    platformChart = echarts.init(platformChartRef.value, 'dark');
    updatePlatformChart();
  }
};

const updateTrendChart = () => {
  if (!trendChart) return;
  trendChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: stats.value.seriesData.map(d => d.name),
      axisLine: { lineStyle: { color: '#444' } }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#333' } }
    },
    series: [{
      data: stats.value.seriesData.map(d => d.value),
      type: 'line',
      smooth: true,
      color: '#007aff',
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,122,255,0.3)' },
          { offset: 1, color: 'rgba(0,122,255,0)' }
        ])
      }
    }]
  });
};

const updatePlatformChart = () => {
  if (!platformChart) return;
  platformChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    legend: { bottom: '0', textStyle: { color: '#aaa' } },
    series: [
      {
        name: '平台分布',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 8,
          borderColor: '#1e1e1e',
          borderWidth: 2
        },
        label: { show: false },
        emphasis: {
          label: {
            show: true,
            fontSize: '14',
            fontWeight: 'bold'
          }
        },
        data: stats.value.platformData
      }
    ]
  });
};

onMounted(async () => {
  try {
    const [taskRes, statsRes] = await Promise.all([
      getTaskList(),
      getPublishStats()
    ]);
    tasks.value = taskRes || [];
    stats.value = statsRes;
    
    setTimeout(() => {
      initCharts();
    }, 100);
  } catch (err) {
    console.error('Dashboard load failed', err);
  }
});

const getStatusClass = (status: number) => {
  if (status === 3) return 'badge-success';
  if (status === 4) return 'badge-danger';
  if (status === 2) return 'badge-warning';
  return 'badge-muted';
};

const getStatusText = (status: number) => {
  switch (status) {
    case 0: return '待处理';
    case 1: return '排队中';
    case 2: return '发布中';
    case 3: return '成功';
    case 4: return '失败';
    default: return '未知';
  }
};
</script>

<style scoped>
.chart-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
}

.chart-card {
  padding: 20px;
}

@media (max-width: 992px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
