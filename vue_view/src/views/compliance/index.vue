<template>
  <div class="compliance-center">
    <!-- 顶部概览 -->
    <section class="page-header">
      <div class="header-info">
        <h2>🛡️ 内容合规中心</h2>
        <p>动态管理系统的敏感词库，确保各平台发布安全。</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-primary" @click="showAddDialog = true">
          <span class="icon">+</span> 添加违禁词
        </button>
      </div>
    </section>

    <!-- 词库搜索与列表 -->
    <div class="content-card word-list-container">
      <div class="list-toolbar">
        <div class="search-box">
          <input 
            v-model="searchQuery" 
            type="text" 
            placeholder="搜索关键词..." 
            class="form-input"
          />
        </div>
        <div class="stats-mini">
          当前词库总量: <strong>{{ words.length }}</strong>
        </div>
      </div>

      <table class="data-table">
        <thead>
          <tr>
            <th>敏感词</th>
            <th>类别</th>
            <th>创建时间</th>
            <th class="actions-cell">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="word in filteredWords" :key="word.id">
            <td>
              <span class="word-tag">{{ word.word }}</span>
            </td>
            <td>
              <span class="category-badge">{{ word.category || '未分类' }}</span>
            </td>
            <td>{{ word.createTime ? word.createTime.replace('T', ' ').substring(0, 16) : '-' }}</td>
            <td class="actions-cell">
              <button class="btn-text btn-danger" @click="handleDelete(word)">删除</button>
            </td>
          </tr>
          <tr v-if="filteredWords.length === 0">
            <td colspan="4" class="empty-cell">未找到匹配的敏感词</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 添加弹窗 -->
    <div v-if="showAddDialog" class="modal-overlay">
      <div class="modal-content">
        <h3>添加新敏感词</h3>
        <div class="form-group">
          <label>敏感词汇</label>
          <input v-model="newWordForm.word" type="text" placeholder="请输入敏感词..." class="form-input" />
        </div>
        <div class="form-group">
          <label>词汇类别</label>
          <select v-model="newWordForm.category" class="form-input">
            <option value="政治">政治敏感</option>
            <option value="广告">违规广告</option>
            <option value="低俗">低俗色情</option>
            <option value="滥用">滥用用语</option>
            <option value="其他">其他</option>
          </select>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="showAddDialog = false">取消</button>
          <button class="btn btn-primary" :disabled="!newWordForm.word" @click="handleAdd">确认添加</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { getSensitiveWords, addSensitiveWord, deleteSensitiveWord } from '../../api/publish';
import type { SensitiveWord } from '../../types';

const words = ref<SensitiveWord[]>([]);
const searchQuery = ref('');
const showAddDialog = ref(false);
const newWordForm = ref({
  word: '',
  category: '政治'
});

const fetchWords = async () => {
  try {
    const res = await getSensitiveWords();
    words.value = res;
  } catch (err) {
    console.error('获取词库失败', err);
  }
};

const filteredWords = computed(() => {
  if (!searchQuery.value) return words.value;
  return words.value.filter(item => 
    item.word.toLowerCase().includes(searchQuery.value.toLowerCase())
  );
});

const handleAdd = async () => {
  if (!newWordForm.value.word) return;
  try {
    await addSensitiveWord(newWordForm.value);
    showAddDialog.value = false;
    newWordForm.value.word = '';
    fetchWords();
    alert('✅ 添加成功！词库已即时刷新。');
  } catch (err) {
    alert('❌ 添加失败，可能词汇已存在。');
  }
};

const handleDelete = async (word: SensitiveWord) => {
  if (!confirm(`确定要从词库中删除 "${word.word}" 吗？`)) return;
  try {
    await deleteSensitiveWord(word.id);
    fetchWords();
  } catch (err) {
    alert('❌ 删除失败');
  }
};

onMounted(fetchWords);
</script>

<style scoped>
.compliance-center {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.word-list-container {
  padding: 0;
  overflow: hidden;
}

.list-toolbar {
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-box {
  width: 300px;
}

.word-tag {
  background: rgba(231, 76, 60, 0.1);
  color: #e74c3c;
  padding: 4px 10px;
  border-radius: 4px;
  font-weight: 600;
  border: 1px solid rgba(231, 76, 60, 0.2);
}

.category-badge {
  background: rgba(255, 255, 255, 0.05);
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}

.empty-cell {
  padding: 60px !important;
  text-align: center;
  color: var(--text-secondary);
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(8px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2000;
}

.modal-content {
  background: #1a1a1a;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  width: 400px;
  padding: 30px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5);
}

.modal-footer {
  margin-top: 30px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: var(--text-secondary);
  font-size: 14px;
}
</style>
