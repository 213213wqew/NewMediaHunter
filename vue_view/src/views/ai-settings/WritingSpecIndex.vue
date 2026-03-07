<template>
  <div class="writing-spec-page">
    <div class="page-header">
      <div class="header-info">
        <h1 class="page-title">AI 写作规范管理</h1>
        <p class="page-subtitle">定义您专属的 AI 创作风格，支持多场景提示词切换</p>
      </div>
      <div class="header-actions">
        <!-- 头部可以放些全局开关或导出功能 -->
      </div>
    </div>

    <div class="main-layout">
      <!-- 左侧分类过滤器 -->
      <aside class="sidebar-filters">
        <div v-for="f in filters" :key="f.value" class="filter-item" :class="{ active: currentFilter === f.value }"
          @click="currentFilter = f.value">
          <span class="label">{{ f.label }}</span>
          <span class="count">{{ getCountByCat(f.value) }}</span>
        </div>
        
        <div class="init-trigger" @click="handleInit">
          <i class="ri-refresh-line"></i>
          <span>还原所有建议预设</span>
        </div>
      </aside>

      <!-- 主要内容区 -->
      <main class="content-area">
        <div class="list-header">
          <h2 class="list-title">
            {{ currentFilter === 'ALL' ? '全部写作规范' : getCatLabel(currentFilter) + '规范' }}
          </h2>
          <button class="btn btn-primary" @click="handleCreate">
            <i class="ri-add-line"></i> 新增自定义
          </button>
        </div>

        <div v-if="loading" class="loading-state">
          <div class="spinner"></div>
          <p>正在同步创作规范库...</p>
        </div>

        <div v-else-if="filteredSpecs.length === 0" class="empty-state">
          <i class="ri-inbox-line"></i>
          <p>当前分类下暂无规范，请点击还原建议或新增自定义</p>
        </div>

        <div v-else class="spec-grid">
          <div v-for="item in filteredSpecs" :key="item.id" class="spec-card shadow-hover"
            :class="{ active: item.isDefault }">
            <div class="spec-header">
              <div class="spec-meta">
                <div class="spec-title-row">
                  <h3 class="spec-name">{{ item.name }}</h3>
                  <span v-if="item.isSystem" class="preset-label system-label">官方预设</span>
                  <span v-else class="preset-label custom-label">自定义</span>
                </div>
                <div class="spec-category">{{ getCatLabel(item.category) }}</div>
              </div>
              <div class="header-action-area">
                <div v-if="item.isDefault" class="active-badge">
                  <i class="ri-checkbox-circle-fill"></i>
                  ✓ 默认启用
                </div>
                <button v-else class="btn-set-default" title="点击设为默认" @click="handleSetDefault(item)">
                  设为默认
                </button>
              </div>
            </div>

            <div class="spec-body">
              <p class="spec-preview">{{ item.promptContent }}</p>
            </div>

            <div class="spec-footer">
              <span class="time">{{ formatDate(item.updatedAt) }}</span>
              <div class="spec-ops">
                <button v-if="!item.isSystem" class="op-btn" @click="handleEdit(item)"><i class="ri-edit-line"></i></button>
                <button v-if="!item.isSystem" class="op-btn delete" @click="handleDelete(item.id!)"><i class="ri-delete-bin-line"></i></button>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- 编辑/新增对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingSpec.id ? '编辑规范' : '新增写作规范'" width="600px" 
      class="premium-spec-dialog" append-to-body destroy-on-close>
      <div class="spec-form">
        <div class="form-row">
          <div class="form-group flex-1">
            <label class="form-label">规范名称</label>
            <input v-model="editingSpec.name" class="form-input" placeholder="例如：毒舌人间清醒风" />
          </div>
          <div class="form-group flex-1">
            <label class="form-label">适用范围</label>
            <select v-model="editingSpec.category" class="form-input">
              <option value="GENERATION">文章生成</option>
              <option value="POLISH">内容润色</option>
              <option value="SUMMARY">摘要生成</option>
            </select>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">系统提示词 (System Prompt) - 核心灵魂</label>
          <textarea v-model="editingSpec.promptContent" class="form-textarea" rows="8"
            placeholder="请在此输入高拟人化、去AI味的Prompt指令..."></textarea>
        </div>

        <div class="form-group">
          <el-checkbox v-model="editingSpec.isDefault">设置为该分类的默认风格</el-checkbox>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <button class="btn btn-ghost" @click="dialogVisible = false">取消</button>
          <button class="btn btn-primary" :disabled="saving" @click="handleSave">
            {{ saving ? '正在保存...' : '保存规范' }}
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import {
  getSpecList,
  saveSpec,
  deleteSpec,
  setDefaultSpec,
  initDefaultSpecs,
  getSpecPresets,
  type AiWritingSpecification
} from '../../api/aiSpec';
import { ElMessage, ElMessageBox } from 'element-plus';

// 状态定义
const showPresets = ref(true);
const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const currentFilter = ref('ALL');
const specs = ref<AiWritingSpecification[]>([]);

const filters = [
  { label: '全部规范', value: 'ALL' },
  { label: '文章生成', value: 'GENERATION' },
  { label: '内容润色', value: 'POLISH' },
  { label: '摘要生成', value: 'SUMMARY' },
];

interface EditingSpec extends Partial<AiWritingSpecification> {
  name: string;
  category: "GENERATION" | "POLISH" | "SUMMARY" | "TAGS" | "CATEGORY";
  promptContent: string;
  isDefault: boolean;
}

const editingSpec = reactive<EditingSpec>({
  name: '',
  category: 'GENERATION',
  promptContent: '',
  isDefault: false
});

// 数据加载
const loadSpecs = async () => {
  loading.value = true;
  try {
    const res: any = await getSpecList(currentFilter.value === 'ALL' ? undefined : currentFilter.value);
    // 兼容返回格式
    specs.value = Array.isArray(res) ? res : (res?.data || []);
  } catch (err) {
    ElMessage.error('加载规范失败');
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadSpecs();
});

// 计算属性
const filteredSpecs = computed(() => {
  if (currentFilter.value === 'ALL') return specs.value;
  return specs.value.filter(s => s.category === currentFilter.value);
});

const getCountByCat = (cat: string) => {
  if (cat === 'ALL') return specs.value.length;
  return specs.value.filter(s => s.category === cat).length;
};

// 辅助函数
const getCatLabel = (cat: string) => {
  const f = filters.find(f => f.value === cat);
  return f ? f.label.replace('规范', '') : cat;
};

const getIconByCat = (cat: string) => {
  switch (cat) {
    case 'GENERATION': return 'ri-fire-line';
    case 'POLISH': return 'ri-magic-line';
    case 'SUMMARY': return 'ri-align-justify';
    default: return 'ri-settings-line';
  }
};

const formatDate = (dateStr?: string) => {
  if (!dateStr) return new Date().toLocaleDateString();
  return new Date(dateStr).toLocaleDateString();
};

// 交互逻辑
const handleCreate = () => {
  Object.assign(editingSpec, {
    id: undefined,
    name: '',
    category: 'GENERATION',
    promptContent: '',
    isDefault: false
  });
  dialogVisible.value = true;
};

const handleApplyPreset = (p: any) => {
  Object.assign(editingSpec, {
    id: undefined,
    name: p.name,
    category: p.category,
    promptContent: p.promptContent,
    isDefault: false
  });
  dialogVisible.value = true;
};

const handleEdit = (spec: AiWritingSpecification) => {
  Object.assign(editingSpec, spec);
  dialogVisible.value = true;
};

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定删除该规范吗？', '提示', { type: 'warning' });
    await deleteSpec(id);
    ElMessage.success('已删除');
    loadSpecs();
  } catch (err) {}
};

const handleSetDefault = async (spec: AiWritingSpecification) => {
  try {
    await setDefaultSpec(spec.id!);
    ElMessage.success(`[${spec.name}] 已设为默认风格`);
    loadSpecs();
  } catch (err) {
    ElMessage.error('设置失败');
  }
};

const handleSave = async () => {
  if (!editingSpec.name || !editingSpec.promptContent) {
    return ElMessage.warning('请填写完整内容');
  }
  saving.value = true;
  try {
    await saveSpec({ ...editingSpec } as any);
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    loadSpecs();
  } catch (err) {
    ElMessage.error('保存失败');
  } finally {
    saving.value = false;
  }
};

const handleInit = async () => {
  try {
    await ElMessageBox.confirm('这会重置系统默认建议预设，不会删除您的自定义内容，是否继续？', '提示');
    await initDefaultSpecs();
    ElMessage.success('还原预设成功');
    loadSpecs();
  } catch (err) {}
};
</script>

<style scoped>
.writing-spec-page {
  --spec-card-bg: var(--bg-card);
  --spec-border: var(--border-color);
  --spec-text-main: var(--text-primary);
  --spec-text-sub: var(--text-secondary);
  --spec-text-muted: var(--text-muted);
  --spec-accent: var(--accent-blue);
  --spec-accent-soft: rgba(79, 142, 247, 0.1);
  --spec-header-gradient: linear-gradient(135deg, rgba(79, 142, 247, 0.1), rgba(124, 92, 191, 0.1));
  
  max-width: 1400px;
  margin: 0 auto;
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.shadow-premium {
  box-shadow: 0 10px 40px -10px rgba(0, 0, 0, 0.2);
}

.shadow-hover {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.shadow-hover:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 30px -5px rgba(0, 0, 0, 0.3);
  border-color: var(--spec-accent) !important;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 32px;
}

.page-title {
  font-size: 26px;
  font-weight: 800;
  color: var(--spec-text-main);
  margin-bottom: 6px;
  letter-spacing: -0.5px;
}

.page-subtitle {
  color: var(--spec-text-sub);
  font-size: 14px;
}

.preset-section {
  background: var(--spec-header-gradient);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.05) !important;
  padding: 28px;
  margin-bottom: 32px;
  position: relative;
  border-radius: 20px;
}

.section-badge {
  position: absolute;
  top: 14px;
  right: 14px;
  font-size: 10px;
  background: var(--spec-accent);
  color: white;
  padding: 2px 10px;
  border-radius: 20px;
  font-weight: 800;
  letter-spacing: 1px;
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 6px;
  color: var(--spec-text-main);
}

.section-desc {
  font-size: 13px;
  color: var(--spec-text-sub);
  margin-bottom: 20px;
}

.preset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.preset-item {
  background: var(--spec-card-bg);
  border: 1px solid var(--spec-border);
  border-radius: 14px;
  padding: 16px;
  display: flex;
  gap: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.preset-item:hover {
  border-color: var(--spec-accent);
  background: rgba(255, 255, 255, 0.05);
}

.preset-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--spec-accent-soft);
  color: var(--spec-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.preset-info { flex: 1; overflow: hidden; }
.preset-name { font-size: 14px; font-weight: 700; color: var(--spec-text-main); margin-bottom: 2px; }
.preset-tag { font-size: 10px; color: var(--spec-accent); font-weight: 800; text-transform: uppercase; margin-bottom: 6px; }
.preset-preview { 
  font-size: 12px; 
  color: var(--spec-text-muted); 
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}

.main-layout {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 32px;
}

.sidebar-filters {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-item {
  padding: 12px 18px;
  border-radius: 12px;
  color: var(--spec-text-sub);
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 500;
}

.filter-item:hover { background: rgba(255, 255, 255, 0.05); color: var(--spec-text-main); }
.filter-item.active { background: var(--spec-accent); color: white; }
.count { font-size: 11px; background: rgba(0, 0, 0, 0.2); padding: 2px 10px; border-radius: 20px; opacity: 0.8; }

.init-trigger {
  margin-top: 32px;
  padding: 16px;
  border: 1px dashed var(--spec-border);
  border-radius: 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--spec-text-muted);
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
}
.init-trigger:hover { border-color: var(--spec-accent); color: var(--spec-accent); background: var(--spec-accent-soft); }

.list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.list-title { font-size: 20px; font-weight: 700; color: var(--spec-text-main); }

.spec-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.spec-card {
  background: var(--spec-card-bg);
  border: 1px solid var(--spec-border);
  border-radius: 18px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
}

.spec-card.active { 
  border-color: var(--spec-accent) !important; 
  border-width: 2px;
  background: rgba(79, 142, 247, 0.04);
}

.spec-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px; }
.spec-meta { flex: 1; }
.spec-title-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; flex-wrap: wrap; }
.spec-name { font-size: 18px; font-weight: 700; color: var(--spec-text-main); margin: 0; }
.preset-label { font-size: 11px; padding: 2px 8px; border-radius: 4px; font-weight: 600; white-space: nowrap; }
.system-label { background: rgba(124, 92, 191, 0.15); color: #c4b5fd; border: 1px solid rgba(124, 92, 191, 0.3); }
.custom-label { background: rgba(79, 142, 247, 0.15); color: var(--spec-accent); border: 1px solid rgba(79, 142, 247, 0.3); }
.spec-category { font-size: 13px; color: var(--spec-text-muted); font-weight: 500; }

.header-action-area { margin-left: 12px; display: flex; align-items: flex-start; }
.active-badge { 
  font-size: 12px; 
  background: var(--spec-accent); 
  color: #fff; 
  padding: 6px 14px; 
  border-radius: 8px; 
  font-weight: 700; 
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.btn-set-default {
  font-size: 12px;
  background: rgba(255,255,255,0.05);
  border: 1px solid var(--spec-border);
  color: var(--spec-text-sub);
  padding: 6px 14px;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.2s;
  white-space: nowrap;
}
.btn-set-default:hover {
  border-color: var(--spec-accent);
  color: var(--spec-accent);
  background: rgba(79, 142, 247, 0.05);
}

.spec-preview {
  font-size: 14px;
  color: var(--spec-text-sub);
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-y: auto;
  min-height: 120px;
  max-height: 200px;
  padding: 14px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.03);
  margin-top: 6px;
}
.spec-preview::-webkit-scrollbar { width: 4px; }
.spec-preview::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.15); border-radius: 4px; }
.spec-preview::-webkit-scrollbar-track { background: transparent; }

.spec-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid var(--spec-border);
  margin-top: auto;
}
.time { font-size: 11px; color: var(--spec-text-muted); }

.spec-ops { display: flex; gap: 8px; }
.op-btn {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: 1px solid var(--spec-border);
  background: transparent;
  color: var(--spec-text-sub);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}
.op-btn:hover { border-color: var(--spec-accent); color: var(--spec-accent); background: var(--spec-accent-soft); }
.op-btn.delete:hover { border-color: var(--accent-red); color: var(--accent-red); background: rgba(248, 113, 113, 0.1); }

.loading-state, .empty-state { text-align: center; padding: 120px 0; color: var(--spec-text-muted); }
.spinner {
  width: 40px; height: 40px; border: 3px solid var(--spec-accent-soft); border-top-color: var(--spec-accent);
  border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto 16px;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* 弹窗专用 */
.spec-form { display: flex; flex-direction: column; gap: 20px; }
.form-row { display: flex; gap: 16px; }
.form-textarea {
  width: 100%; background: rgba(255, 255, 255, 0.04); border: 1px solid var(--spec-border);
  border-radius: 12px; padding: 14px; color: var(--text-primary); font-size: 14px; outline: none;
  resize: vertical;
}
.form-textarea:focus { border-color: var(--spec-accent); }
</style>
