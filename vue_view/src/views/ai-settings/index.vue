<template>
  <div>
    <div class="page-header">
      <div>
        <div class="page-title">🤖 AI 模型设置</div>
        <div class="page-subtitle">为您的账号配置专属大模型 API，所有 AI 功能将使用此配置调用</div>
      </div>
      <div v-if="savedProvider" class="badge badge-success">
        <span class="badge-dot"></span>已配置：{{ savedProvider }}
      </div>
    </div>

    <!-- 提供商选择 -->
    <div class="card" style="margin-bottom: 20px;">
      <div class="section-title">🌐 选择模型提供商</div>
      <div class="group-label">🌍 国际模型</div>
      <div class="provider-grid">
        <div v-for="p in internationalProviders" :key="p.id"
          class="provider-card" :class="{ selected: form.provider === p.id }" @click="selectProvider(p)">
          <div class="provider-icon">{{ p.icon }}</div>
          <div class="provider-name">{{ p.name }}</div>
          <div class="provider-desc">{{ p.desc }}</div>
        </div>
      </div>
      <div class="group-label" style="margin-top: 16px;">🇨🇳 国内模型</div>
      <div class="provider-grid">
        <div v-for="p in domesticProviders" :key="p.id"
          class="provider-card" :class="{ selected: form.provider === p.id }" @click="selectProvider(p)">
          <div class="provider-icon">{{ p.icon }}</div>
          <div class="provider-name">{{ p.name }}</div>
          <div class="provider-desc">{{ p.desc }}</div>
        </div>
      </div>
    </div>

    <!-- 配置表单 -->
    <div class="card" style="margin-bottom: 20px;">
      <div class="section-title">⚙️ 接口参数配置</div>
      <div class="form-grid">

        <div class="form-group full-width">
          <label class="form-label">API 接口地址 (Base URL)</label>
          <input v-model="form.baseUrl" class="form-input" placeholder="例如：https://api.openai.com/v1" />
          <div v-if="currentProvider" class="form-hint">
            💡 {{ currentProvider.name }} 默认接口：
            <span class="hint-url" @click="form.baseUrl = currentProvider.defaultUrl">{{ currentProvider.defaultUrl }}</span>
          </div>
        </div>

        <div class="form-group full-width">
          <label class="form-label">API Key</label>
          <div class="input-with-toggle">
            <input v-model="form.apiKey" :type="showKey ? 'text' : 'password'"
              class="form-input" placeholder="填写您的 API Key" />
            <button class="toggle-btn" @click="showKey = !showKey">{{ showKey ? '🙈 隐藏' : '👁 显示' }}</button>
          </div>
          <div class="form-hint">🔒 API Key 仅属于您的账号，其他用户无法访问</div>
        </div>

        <div class="form-group full-width">
          <label class="form-label">模型名称</label>
          <div class="model-row">
            <!-- Combobox：输入框内直接搜索，下方弹出过滤列表 -->
            <div class="searchable-select" ref="selectWrap">
              <input
                class="form-input searchable-input"
                v-model="modelSearch"
                :placeholder="form.modelName || (fetchedModels.length > 0 ? '🔍 输入关键词搜索模型...' : (currentProvider?.defaultModel ? '手填或点右侧获取：' + currentProvider.defaultModel : '填写模型名称'))"
                @focus="modelDropOpen = true"
                @input="modelDropOpen = true"
              />
              <!-- 下拉浮层 -->
              <div v-if="modelDropOpen && fetchedModels.length > 0" class="drop-list">
                <template v-if="filteredModels.length > 0">
                  <div
                    v-for="m in filteredModels" :key="m"
                    class="drop-item"
                    :class="{ active: form.modelName === m }"
                    @mousedown.prevent="pickModel(m)"
                  >{{ m }}</div>
                </template>
                <div v-else class="drop-hint">无匹配结果</div>
              </div>
            </div>
            <button class="fetch-btn" @click="handleFetchModels" :disabled="fetchingModels">
              {{ fetchingModels ? '⏳' : '🔄 获取模型' }}
            </button>
          </div>
          <div v-if="form.modelName" class="form-hint" style="color: var(--accent-blue);">
            ✅ 已选：{{ form.modelName }}
          </div>
          <div v-if="fetchError" class="form-hint" style="color: #ff3b30;">{{ fetchError }}</div>
          <!-- 快捷 Chip（未获取模型列表时显示） -->
          <div v-if="currentProvider?.quickModels?.length && fetchedModels.length === 0" class="model-chips">
            <span v-for="m in currentProvider.quickModels" :key="m"
              class="chip" :class="{ active: form.modelName === m }"
              @click="form.modelName = m; modelSearch = ''">{{ m }}</span>
          </div>
        </div>

        <!-- AI 绘图功能开关（付费提醒） -->
        <div class="form-group full-width" style="margin-top: 10px;">
          <div class="setting-item-row">
            <div>
              <div class="form-label" style="display: flex; align-items: center; gap: 6px;">
                🎨 启用 AI 高清重绘 (Premium)
                <span class="badge badge-warning" style="font-size: 10px; padding: 2px 6px;">付费功能</span>
              </div>
              <div class="form-hint">
                开启后，一键创作将使用 DALL-E 3 为您生成**原创、高清、无水印**的配图。<br/>
                <span style="color: #ff9500;">⚠️ 注意：</span> 该功能会按张扣除您的 API 额度（每张约 0.4-0.8 元）。
              </div>
            </div>
            <el-switch v-model="form.enableAiImage" active-color="#007aff" />
          </div>
          <div v-if="!form.enableAiImage" class="form-hint" style="margin-top: 4px; color: #34c759;">
            💡 当前模式：<b>免费匹配</b> (使用 Unsplash 免费图库，无 API 消耗)。
          </div>
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap;">
      <button class="btn btn-secondary" @click="handleTest" :disabled="testing">
        {{ testing ? '⏳ 测试中...' : '🔌 测试连接' }}
      </button>
      <button class="btn btn-primary" @click="handleSave" :disabled="saving">
        {{ saving ? '⏳ 保存中...' : '💾 保存配置' }}
      </button>
      <div v-if="testResult" class="test-result" :class="testResult.success ? 'success' : 'error'">
        {{ testResult.success ? '✅' : '❌' }} {{ testResult.message }}
      </div>
    </div>

    <!-- 写作规范入口指引 -->
    <div class="card" style="margin-top: 24px; display: flex; justify-content: space-between; align-items: center;">
      <div>
        <div class="section-title" style="margin-bottom: 4px;">📜 AI 写作规范</div>
        <div class="form-hint">通过设置不同的写作风格提示词（如：爆款风格、新闻风格），让 AI 生成的内容更符合您的品味。</div>
      </div>
      <router-link to="/ai-writing-specs" class="btn btn-primary btn-sm">去管理规范</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { getAiConfig, saveAiConfig, testAiConfig } from '../../api/aiConfig';
import request from '../../utils/request';

const internationalProviders = [
  { id: 'openai', icon: '🟢', name: 'OpenAI', desc: 'GPT-4o / o1 / o3',
    defaultUrl: 'https://api.openai.com/v1', defaultModel: 'gpt-4o',
    quickModels: ['gpt-4o', 'gpt-4o-mini', 'o1', 'o3-mini'] },
  { id: 'gemini', icon: '🔵', name: 'Google Gemini', desc: 'Gemini 2.0 / 1.5 Pro',
    defaultUrl: 'https://generativelanguage.googleapis.com/v1beta', defaultModel: 'gemini-1.5-pro',
    quickModels: ['gemini-2.0-flash', 'gemini-1.5-pro', 'gemini-1.5-flash'] },
  { id: 'claude', icon: '🟠', name: 'Anthropic Claude', desc: 'Claude 3.5 Sonnet / Haiku',
    defaultUrl: 'https://api.anthropic.com/v1', defaultModel: 'claude-3-5-sonnet-20241022',
    quickModels: ['claude-3-5-sonnet-20241022', 'claude-3-5-haiku-20241022'] },
  { id: 'ollama', icon: '🖥️', name: 'Ollama（本地）', desc: '完全离线，无需 Key',
    defaultUrl: 'http://localhost:11434/v1', defaultModel: 'llama3', quickModels: [] },
  { id: 'custom', icon: '⚙️', name: '自定义接口', desc: '任意 OpenAI 兼容接口',
    defaultUrl: 'http://your-api-server/v1', defaultModel: 'your-model', quickModels: [] }
];

const domesticProviders = [
  { id: 'qianwen', icon: '🔴', name: '通义千问', desc: 'Qwen-Max / Plus / Turbo',
    defaultUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', defaultModel: 'qwen-max',
    quickModels: ['qwen-max', 'qwen-plus', 'qwen-turbo', 'qwen-long'] },
  { id: 'deepseek', icon: '💜', name: 'DeepSeek', desc: 'DeepSeek-V3 / R1',
    defaultUrl: 'https://api.deepseek.com/v1', defaultModel: 'deepseek-chat',
    quickModels: ['deepseek-chat', 'deepseek-reasoner'] },
  { id: 'zhipu', icon: '🟡', name: '智谱 GLM', desc: 'GLM-4 / 4-Flash / Air',
    defaultUrl: 'https://open.bigmodel.cn/api/paas/v4', defaultModel: 'glm-4',
    quickModels: ['glm-4', 'glm-4-flash', 'glm-4-air', 'glm-4-long'] },
  { id: 'siliconflow', icon: '⚡', name: '硅基流动', desc: '聚合国内外多种开源模型',
    defaultUrl: 'https://api.siliconflow.cn/v1', defaultModel: 'deepseek-ai/DeepSeek-V3', quickModels: [] },
  { id: 'moonshot', icon: '🌙', name: '月之暗面 Kimi', desc: 'Moonshot-v1-8k/32k/128k',
    defaultUrl: 'https://api.moonshot.cn/v1', defaultModel: 'moonshot-v1-32k',
    quickModels: ['moonshot-v1-8k', 'moonshot-v1-32k', 'moonshot-v1-128k'] },
  { id: 'baichuan', icon: '🐋', name: '百川 AI', desc: 'Baichuan4 / Turbo',
    defaultUrl: 'https://api.baichuan-ai.com/v1', defaultModel: 'Baichuan4',
    quickModels: ['Baichuan4', 'Baichuan4-Turbo', 'Baichuan4-Air'] },
  { id: 'minimax', icon: '🟣', name: 'MiniMax', desc: 'abab7 / 6.5s',
    defaultUrl: 'https://api.minimax.chat/v1', defaultModel: 'abab6.5s-chat',
    quickModels: ['abab6.5s-chat', 'abab7-chat'] },
  { id: 'lingyiwanwu', icon: '🔶', name: '零一万物', desc: 'Yi-Large / Medium',
    defaultUrl: 'https://api.lingyiwanwu.com/v1', defaultModel: 'yi-large',
    quickModels: ['yi-large', 'yi-medium', 'yi-spark'] },
  { id: 'stepfun', icon: '🪜', name: '阶跃星辰', desc: 'step-2-16k / step-1-8k',
    defaultUrl: 'https://api.stepfun.com/v1', defaultModel: 'step-2-16k',
    quickModels: ['step-2-16k', 'step-1-8k', 'step-1-128k'] },
  { id: 'spark', icon: '✨', name: '讯飞星火', desc: 'Spark Max / Lite',
    defaultUrl: 'https://spark-api-open.xf-yun.com/v1', defaultModel: 'spark-max',
    quickModels: ['spark-max', 'spark-pro', 'spark-lite'] },
  { id: 'hunyuan', icon: '🌊', name: '腾讯混元', desc: 'Hunyuan-Pro / Lite',
    defaultUrl: 'https://api.hunyuan.cloud.tencent.com/v1', defaultModel: 'hunyuan-pro',
    quickModels: ['hunyuan-pro', 'hunyuan-standard', 'hunyuan-lite'] },
];

const allProviders = [...internationalProviders, ...domesticProviders];

const form = ref({ provider: 'deepseek', baseUrl: 'https://api.deepseek.com/v1', apiKey: '', modelName: 'deepseek-chat', enableAiImage: false });
const showKey = ref(false);
const testing = ref(false);
const saving = ref(false);
const savedProvider = ref('');
const testResult = ref<{ success: boolean; message: string } | null>(null);
const fetchedModels = ref<string[]>([]);
const fetchingModels = ref(false);
const fetchError = ref('');

// Combobox 状态
const modelSearch = ref('');
const modelDropOpen = ref(false);
const selectWrap = ref<HTMLElement | null>(null);

const currentProvider = computed(() => allProviders.find(p => p.id === form.value.provider));

const filteredModels = computed(() => {
  const kw = modelSearch.value.trim().toLowerCase();
  if (!kw) return fetchedModels.value;
  return fetchedModels.value.filter(m => m.toLowerCase().includes(kw));
});

const selectProvider = (p: typeof allProviders[0]) => {
  form.value.provider = p.id;
  form.value.baseUrl = p.defaultUrl;
  form.value.modelName = p.defaultModel;
  modelSearch.value = '';
  fetchedModels.value = [];
  fetchError.value = '';
  testResult.value = null;
};

const pickModel = (m: string) => {
  form.value.modelName = m;
  modelSearch.value = '';
  modelDropOpen.value = false;
};

const handleClickOutside = (e: MouseEvent) => {
  if (selectWrap.value && !selectWrap.value.contains(e.target as Node)) {
    modelDropOpen.value = false;
  }
};

const handleFetchModels = async () => {
  if (!form.value.baseUrl || !form.value.apiKey) {
    ElMessage.warning('请先填写接口地址和 API Key 再获取模型列表');
    return;
  }
  fetchingModels.value = true;
  fetchedModels.value = [];
  fetchError.value = '';
  modelSearch.value = '';
  try {
    const res = await request.post<{ models: string[] }>('/ai-config/list-models', {
      baseUrl: form.value.baseUrl,
      apiKey: form.value.apiKey,
      provider: form.value.provider
    });
    fetchedModels.value = res?.models || [];
    if (fetchedModels.value.length === 0) {
      fetchError.value = '未获取到模型列表，请检查接口地址和 Key 是否正确';
    } else {
      ElMessage.success(`获取到 ${fetchedModels.value.length} 个可用模型，请在框内搜索选择`);
      const defaultM = currentProvider.value?.defaultModel;
      form.value.modelName = (defaultM && fetchedModels.value.includes(defaultM)) ? defaultM : (fetchedModels.value[0] || '');
      modelDropOpen.value = true;
    }
  } catch (e: any) {
    fetchError.value = '获取模型列表失败：' + (e.message || '接口不支持或 Key 无效');
  } finally {
    fetchingModels.value = false;
  }
};

const handleTest = async () => {
  if (!form.value.baseUrl || !form.value.apiKey) { ElMessage.warning('请先填写接口地址和 API Key'); return; }
  testing.value = true;
  testResult.value = null;
  try {
    const res = await testAiConfig(form.value);
    testResult.value = res;
    if (res.success) ElMessage.success('连接测试成功！');
    else ElMessage.error('连接失败：' + res.message);
  } catch (e: any) {
    testResult.value = { success: false, message: e.message || '未知错误' };
  } finally { testing.value = false; }
};

const handleSave = async () => {
  if (!form.value.provider || !form.value.baseUrl || !form.value.apiKey || !form.value.modelName) {
    ElMessage.warning('请填写全部必填项'); return;
  }
  saving.value = true;
  try {
    await saveAiConfig(form.value);
    savedProvider.value = currentProvider.value?.name || form.value.provider;
    ElMessage.success('AI 配置已保存！');
  } catch (e: any) {
    ElMessage.error('保存失败：' + e.message);
  } finally { saving.value = false; }
};

onMounted(async () => {
  document.addEventListener('click', handleClickOutside);
  try {
    const cfg = await getAiConfig();
    if (cfg) {
      form.value.provider = cfg.provider || 'deepseek';
      form.value.baseUrl = cfg.baseUrl || '';
      form.value.apiKey = cfg.apiKey || '';
      form.value.modelName = cfg.modelName || '';
      form.value.enableAiImage = cfg.enableAiImage || false;
      savedProvider.value = allProviders.find(p => p.id === cfg.provider)?.name || cfg.provider || '';
    }
  } catch (_) {}
});

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
});
</script>

<style scoped>
.group-label { font-size: 12px; font-weight: 600; color: var(--text-muted); margin-top: 12px; margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px; }

.setting-item-row { display: flex; justify-content: space-between; align-items: flex-start; background: rgba(255,255,255,0.03); padding: 16px; border-radius: 12px; border: 1px solid rgba(255,255,255,0.08); }

.provider-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 10px; }
.provider-card { background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; padding: 14px 10px; cursor: pointer; text-align: center; transition: all 0.2s ease; }
.provider-card:hover { border-color: rgba(0,122,255,0.5); background: rgba(0,122,255,0.06); transform: translateY(-2px); }
.provider-card.selected { border-color: var(--accent-blue); background: rgba(0,122,255,0.15); box-shadow: 0 0 0 1px rgba(0,122,255,0.4); }
.provider-icon { font-size: 24px; margin-bottom: 6px; }
.provider-name { font-size: 12px; font-weight: 600; color: var(--text-primary); margin-bottom: 3px; }
.provider-desc { font-size: 11px; color: var(--text-muted); }

.form-grid { display: grid; gap: 16px; margin-top: 16px; }
.form-group { display: flex; flex-direction: column; gap: 6px; }
.form-label { font-size: 13px; font-weight: 500; color: var(--text-secondary); }

.form-input { background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1); border-radius: 8px; padding: 10px 14px; color: var(--text-primary); font-size: 14px; width: 100%; box-sizing: border-box; transition: border-color 0.2s; }
.form-input:focus { outline: none; border-color: var(--accent-blue); }
.form-hint { font-size: 12px; color: var(--text-muted); }
.hint-url { color: var(--accent-blue); cursor: pointer; text-decoration: underline; }

.input-with-toggle { display: flex; gap: 8px; }
.toggle-btn { background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.12); border-radius: 8px; color: var(--text-secondary); padding: 0 14px; cursor: pointer; font-size: 12px; white-space: nowrap; transition: all 0.2s; }
.toggle-btn:hover { background: rgba(255,255,255,0.14); }

.model-row { display: flex; gap: 8px; align-items: flex-start; }

/* Combobox */
.searchable-select { position: relative; flex: 1; }
.searchable-input { width: 100%; }
.drop-list { position: absolute; bottom: calc(100% + 4px); left: 0; right: 0; background: #1e1e2e; border: 1px solid rgba(255,255,255,0.15); border-radius: 10px; max-height: 280px; overflow-y: auto; z-index: 9999; box-shadow: 0 -8px 32px rgba(0,0,0,0.5); scrollbar-width: thin; }
.drop-item { padding: 9px 14px; font-size: 13px; color: var(--text-primary); cursor: pointer; transition: background 0.1s; }
.drop-item:hover, .drop-item.active { background: rgba(0,122,255,0.2); color: var(--accent-blue); }
.drop-hint { padding: 12px 14px; font-size: 13px; color: var(--text-muted); text-align: center; }

.fetch-btn { background: rgba(0,122,255,0.15); border: 1px solid rgba(0,122,255,0.3); border-radius: 8px; color: var(--accent-blue); padding: 10px 16px; cursor: pointer; font-size: 13px; white-space: nowrap; transition: all 0.2s; flex-shrink: 0; }
.fetch-btn:hover { background: rgba(0,122,255,0.25); }
.fetch-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.model-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.chip { background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1); border-radius: 20px; padding: 3px 12px; font-size: 12px; color: var(--text-secondary); cursor: pointer; transition: all 0.2s; }
.chip:hover, .chip.active { background: rgba(0,122,255,0.15); border-color: var(--accent-blue); color: var(--accent-blue); }

.test-result { padding: 8px 14px; border-radius: 8px; font-size: 13px; max-width: 500px; }
.test-result.success { background: rgba(52,199,89,0.12); border: 1px solid rgba(52,199,89,0.3); color: #34c759; }
.test-result.error { background: rgba(255,59,48,0.12); border: 1px solid rgba(255,59,48,0.3); color: #ff3b30; }
</style>
