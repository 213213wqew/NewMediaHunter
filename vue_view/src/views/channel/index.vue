<template>
  <div>
    <div class="page-header">
      <div>
        <div class="page-title">🎯 分发渠道配置</div>
        <div class="page-subtitle">选择启用或停用系统支持的发布平台。只有在此启用的平台，才会出现在“内容创作”中供您选择。</div>
      </div>
      <button class="btn btn-primary" @click="saveActivePlatforms">💾 保存设置</button>
    </div>

    <div class="channel-grid">
      <div v-for="plat in displayPlatforms" :key="plat.platformKey" class="channel-card" :class="{ 'is-active': activePlatformKeys.includes(plat.platformKey) }" @click="togglePlatform(plat.platformKey)">
        <div class="channel-header">
          <div class="platform-info">
            <span class="platform-icon">{{ getPlatformIcon(plat.id) }}</span>
            <span class="account-name" style="margin-bottom: 0;">{{ plat.platformName }}</span>
          </div>
          <div @click.stop>
            <el-switch :model-value="activePlatformKeys.includes(plat.platformKey)" @change="togglePlatform(plat.platformKey)" />
          </div>
        </div>
        <div class="account-status">
          <span style="color: var(--text-muted); font-size: 11px;">平台标识码: {{ plat.platformKey }}</span>
        </div>

        <!-- 专题/任务同步区域 -->
        <div v-if="['bjh', 'baijiahao'].includes(plat.platformKey)" class="sync-section" @click.stop>
           <div class="sync-info">
              <span class="sync-count">本地话题池: {{ (platformStatus as any)[plat.platformKey]?.count || 0 }} 条</span>
              <span v-if="(platformStatus as any)[plat.platformKey]?.lastUpdate" class="sync-time">上次同步: {{ formatDate((platformStatus as any)[plat.platformKey]?.lastUpdate) }}</span>
           </div>

           <!-- 话题列表预览 -->
           <div v-if="(platformStatus as any)[plat.platformKey]?.tasks?.length > 0" class="topics-preview">
              <div v-for="(task, idx) in (platformStatus as any)[plat.platformKey].tasks" :key="idx" class="topic-item">
                 #{{ task.topic.replace(/#/g, '') }}#
              </div>
           </div>

           <button 
             class="btn-sync" 
             :disabled="(platformStatus as any)[plat.platformKey]?.syncing"
             @click="handleSyncTopics(plat.platformKey)"
           >
             <i v-if="(platformStatus as any)[plat.platformKey]?.syncing" class="el-icon-loading"></i>
             {{ (platformStatus as any)[plat.platformKey]?.syncing ? '同步中...' : '同步实时热点' }}
           </button>
        </div>
      </div>
      
      <div v-if="displayPlatforms.length === 0" style="text-align: center; padding: 40px; color: var(--text-muted); grid-column: 1 / -1;">
        正在加载系统支持的发布渠道...
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getPlatformList } from '../../api/platform';
import { syncPlatformTasks, getPlatformTasks } from '../../api/ai';
import type { Platform } from '../../types';
import { ElMessage } from 'element-plus';

const displayPlatforms = ref<any[]>([]);
const activePlatformKeys = ref<string[]>([]);
const platformStatus = ref<Record<string, { syncing: boolean, count: number, lastUpdate: string | null, tasks: any[] }>>({});

const builtinPlatforms = [
  { id: 1, platformName: '百度百家号', platformKey: 'bjh', icon: '🐾' },
  { id: 2, platformName: '腾讯企鹅号', platformKey: 'qq', icon: '🐧' },
  { id: 3, platformName: '今日头条号', platformKey: 'tt', icon: '📰' },
  { id: 4, platformName: '新浪看点', platformKey: 'sina', icon: '👁️' },
  { id: 5, platformName: '搜狐号', platformKey: 'sohu', icon: '🦊' },
  { id: 6, platformName: '网易号', platformKey: '163', icon: '🎵' },
  { id: 7, platformName: '微信公众号', platformKey: 'wx', icon: '💬' },
  { id: 8, platformName: '小红书', platformKey: 'xhs', icon: '📕' }
];

const loadData = async () => {
  try {
    let plats = await getPlatformList();
    if (!plats || plats.length === 0) {
        // Fallback to builtin for display if API fails
        plats = builtinPlatforms as any;
    }
    
    // Ensure all backend platforms have necessary names/keys
    displayPlatforms.value = plats.map(p => {
        const builtin = builtinPlatforms.find(b => b.id === p.id || b.platformKey === p.platformKey);
        return {
            ...p,
            platformName: (p as any).platformName || (p as any).name || (builtin?.platformName || '未知平台'),
            platformKey: p.platformKey || (builtin?.platformKey || `p_${p.id}`)
        };
    });

    // Load saved preferences
    const saved = localStorage.getItem('active_platforms');
    if (saved) {
      try {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed) && parsed.length > 0) {
            activePlatformKeys.value = parsed;
        } else {
            // Default select all if array is empty (user hasn't explicitly disabled all)
            // Or maybe user DID disable all? If user saved an empty array, honor it.
            activePlatformKeys.value = parsed;
        }
      } catch (e) {
        activePlatformKeys.value = displayPlatforms.value.map(p => p.platformKey);
      }
    } else {
      // Default: active all platforms initially
      activePlatformKeys.value = displayPlatforms.value.map(p => p.platformKey);
    }

    // 初始化各个平台的任务状态
    displayPlatforms.value.forEach(async p => {
      if (['bjh', 'baijiahao'].includes(p.platformKey)) {
        try {
          const tasks = await getPlatformTasks(p.platformKey);
          platformStatus.value[p.platformKey] = {
            syncing: false,
            count: tasks.length,
            lastUpdate: tasks.length > 0 ? tasks[0].updateTime : null,
            tasks: tasks
          };
        } catch (e) {
          console.warn('获取平台任务状态失败', p.platformKey);
        }
      }
    });
  } catch (err) {
    console.error(err);
    ElMessage.error('加载渠道配置失败');
  }
};

onMounted(loadData);

const getPlatformIcon = (platformId: any) => {
    const b = builtinPlatforms.find(x => x.id === Number(platformId) || String(x.id) === String(platformId));
    return b ? b.icon : '🌐';
};

const togglePlatform = (key: string) => {
  const idx = activePlatformKeys.value.indexOf(key);
  if (idx === -1) {
    activePlatformKeys.value.push(key);
  } else {
    activePlatformKeys.value.splice(idx, 1);
  }
};

const saveActivePlatforms = () => {
  localStorage.setItem('active_platforms', JSON.stringify(activePlatformKeys.value));
  ElMessage.success('设置已保存！在返回内容创作时将只显示您启用的平台');
};

const handleSyncTopics = async (key: string) => {
  const syncKey = (key === 'baijiahao') ? 'bjh' : key;
  if (!platformStatus.value[syncKey]) {
     platformStatus.value[syncKey] = { syncing: false, count: 0, lastUpdate: null, tasks: [] };
  }
  platformStatus.value[syncKey].syncing = true;
  ElMessage.info(`正在同步 ${syncKey} 平台的实时热点，请稍候...`);
  
  try {
    const res = await syncPlatformTasks(syncKey);
    if (res.success) {
      ElMessage.success(`同步成功！已获取 ${res.count} 条最新热点话题`);
      // 重新加载该平台状态
      const tasks = await getPlatformTasks(syncKey);
      platformStatus.value[syncKey] = {
        syncing: false,
        count: tasks.length,
        lastUpdate: tasks.length > 0 ? tasks[0].updateTime : null,
        tasks: tasks
      };
    } else {
      ElMessage.error(res.message || '同步失败');
      platformStatus.value[syncKey].syncing = false;
    }
  } catch (err: any) {
    ElMessage.error('同步过程中发生错误，请检查网络或后端日志');
    platformStatus.value[syncKey].syncing = false;
  }
};

const formatDate = (dateStr: string | null | undefined) => {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return `${d.getMonth() + 1}-${d.getDate()} ${d.getHours()}:${d.getMinutes().toString().padStart(2, '0')}`;
};
</script>

<style scoped>
.channel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.channel-card {
  background: var(--surface-light);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.channel-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  border-color: rgba(255, 255, 255, 0.1);
}

.channel-card.is-active {
  background: rgba(33, 150, 243, 0.05);
  border-color: rgba(33, 150, 243, 0.3);
}

.channel-card.is-active::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: var(--primary);
}

.channel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.platform-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.platform-icon {
  font-size: 20px;
  background: rgba(255,255,255,0.05);
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.account-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.sync-section {
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px dashed var(--border-color);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sync-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sync-count {
  font-size: 12px;
  color: var(--text-primary);
  font-weight: 500;
}

.sync-time {
  font-size: 11px;
  color: var(--text-muted);
}

.btn-sync {
  background: var(--surface-light);
  border: 1px solid var(--primary);
  color: var(--primary);
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 600;
}

.btn-sync:hover:not(:disabled) {
  background: var(--primary);
  color: #fff;
}

.btn-sync:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  border-color: var(--text-muted);
  color: var(--text-muted);
}

.topics-preview {
  margin: 10px 0;
  max-height: 120px;
  overflow-y: auto;
  padding: 8px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.topic-item {
  font-size: 11px;
  background: var(--primary-light, rgba(33, 150, 243, 0.1));
  color: var(--primary);
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid rgba(33, 150, 243, 0.2);
  white-space: nowrap;
}

/* 隐藏滚动条但保留功能 */
.topics-preview::-webkit-scrollbar {
  width: 4px;
}
.topics-preview::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 2px;
}
</style>
