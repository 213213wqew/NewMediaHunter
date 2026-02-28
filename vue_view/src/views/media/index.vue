<template>
  <div>
    <div class="page-header">
      <div>
        <div class="page-title">素材管理中心</div>
        <div class="page-subtitle">管理您的图片和视频资源，支持一键预览与清理</div>
      </div>
      <div class="page-actions">
        <label class="btn btn-primary" style="cursor: pointer;">
          📤 上传素材
          <input type="file" hidden @change="handleUpload" accept="image/*,video/*" />
        </label>
      </div>
    </div>

    <div class="card" style="margin-bottom: 20px;">
      <div style="display: flex; gap: 15px; align-items: center;">
        <div class="filter-group">
          <button 
            v-for="f in filters" :key="f.value"
            class="btn btn-ghost"
            :class="{ active: currentFilter === f.value }"
            @click="currentFilter = f.value"
          >
            {{ f.label }}
          </button>
        </div>
        <div style="flex: 1;"></div>
        <div style="color: var(--text-muted); font-size: 13px;">
          共计 <span style="color: var(--accent-blue);">{{ mediaList.length }}</span> 个素材
        </div>
      </div>
    </div>

    <!-- 素材列表网格 -->
    <div class="media-grid">
      <div v-for="item in filteredList" :key="item.id" class="media-card card">
        <div class="media-preview" @click="handlePreview(item)">
          <img v-if="item.fileType === 'image'" :src="item.platformMediaUrl" alt="" />
          <div v-else class="video-placeholder">
            <div style="font-size: 40px;">🎬</div>
            <div style="font-size: 12px; margin-top: 8px;">视频素材</div>
          </div>
          <div class="media-overlay">
            <span>🔍 预览</span>
          </div>
        </div>
        <div class="media-info">
          <div class="media-name" :title="item.originalUrl">{{ item.originalUrl }}</div>
          <div class="media-meta">
            <span>{{ item.fileType === 'image' ? '🖼️ 图片' : '🎥 视频' }}</span>
            <span style="cursor: pointer; color: #ff5555;" @click="handleDelete(item.id!)">🗑️ 删除</span>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="filteredList.length === 0" class="empty-state">
        <div style="font-size: 48px;">📦</div>
        <p>暂无素材，快去上传吧</p>
      </div>
    </div>

    <!-- 预览弹窗 -->
    <div v-if="previewItem" class="preview-overlay" @click.self="previewItem = null">
      <div class="preview-modal card" style="width: auto; max-width: 90vw;">
        <div class="preview-header">
          <div class="section-title" style="margin: 0;">素材详情: {{ previewItem.originalUrl }}</div>
          <button class="btn btn-ghost" @click="previewItem = null">❌ 关闭</button>
        </div>
        <div class="preview-content-box">
          <img v-if="previewItem.fileType === 'image'" :src="previewItem.platformMediaUrl" style="max-width: 100%; max-height: 70vh; display: block;" />
          <video v-else :src="previewItem.platformMediaUrl" controls autoplay style="max-width: 100%; max-height: 70vh;"></video>
        </div>
        <div class="preview-footer">
          <div style="font-size: 12px; color: var(--text-muted);">
            上传时间：{{ new Date(previewItem.createTime!).toLocaleString() }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { getMediaList, deleteMedia } from '../../api/media';
import request from '../../utils/request';
import type { MediaResource } from '../../types';

const mediaList = ref<MediaResource[]>([]);
const currentFilter = ref('all');
const previewItem = ref<MediaResource | null>(null);

const filters = [
  { label: '全部', value: 'all' },
  { label: '图片', value: 'image' },
  { label: '视频', value: 'video' }
];

const loadMedia = async () => {
  try {
    const res = await getMediaList();
    mediaList.value = res || [];
  } catch (err) {
    console.error('Failed to load media', err);
  }
};

const filteredList = computed(() => {
  if (currentFilter.value === 'all') return mediaList.value;
  return mediaList.value.filter(item => item.fileType === currentFilter.value);
});

onMounted(() => {
  loadMedia();
});

const handlePreview = (item: MediaResource) => {
  previewItem.value = item;
};

const handleDelete = async (id: number) => {
  if (!confirm('确定要永久删除该素材吗？')) return;
  try {
    await deleteMedia(id);
    await loadMedia();
  } catch (err) {
    alert('删除失败');
  }
};

const handleUpload = async (e: Event) => {
  const file = (e.target as HTMLInputElement).files?.[0];
  if (!file) return;

  const formData = new FormData();
  formData.append('file', file);

  try {
    await request.post('/file/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    await loadMedia();
  } catch (err) {
    alert('上传失败');
  }
};
</script>

<style scoped>
.filter-group {
  display: flex;
  background: rgba(255, 255, 255, 0.03);
  padding: 4px;
  border-radius: 8px;
}

.filter-group .btn {
  padding: 6px 16px;
  font-size: 13px;
}

.filter-group .btn.active {
  background: var(--accent-blue);
  color: white;
}

.media-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
}

.media-card {
  padding: 0;
  overflow: hidden;
  transition: transform 0.2s;
}

.media-card:hover {
  transform: translateY(-5px);
}

.media-preview {
  position: relative;
  height: 140px;
  background: #2a2a2e;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.media-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.video-placeholder {
  text-align: center;
  color: var(--text-muted);
}

.media-overlay {
  position: absolute;
  top: 0; left: 0; width: 100%; height: 100%;
  background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
  color: white;
  font-size: 14px;
}

.media-card:hover .media-overlay {
  opacity: 1;
}

.media-info {
  padding: 10px 12px;
}

.media-name {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}

.media-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-muted);
}

.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 100px 0;
  color: var(--text-muted);
}

.preview-overlay {
  position: fixed;
  top: 0; left: 0; width: 100%; height: 100%;
  background: rgba(0,0,0,0.9);
  display: flex; justify-content: center; align-items: center;
  z-index: 2000;
  backdrop-filter: blur(5px);
}

.preview-modal {
  background: #1e1e1e;
  padding: 0;
  overflow: hidden;
}

.preview-header {
  padding: 15px 20px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.preview-content-box {
  padding: 10px;
  background: #000;
  display: flex;
  justify-content: center;
}

.preview-footer {
  padding: 10px 20px;
  border-top: 1px solid var(--border-color);
}
</style>
