<template>
  <div>
    <div class="page-header">
      <div>
        <div class="page-title">账号管理</div>
        <div class="page-subtitle">绑定和管理各平台发布渠道账号</div>
      </div>
      <div style="display: flex; gap: 10px;">
        <button class="btn btn-secondary" :disabled="refreshing" @click="handleRefreshStats">
          {{ refreshing ? '正在获取昨日数据…' : '更新数据' }}
        </button>
        <button class="btn btn-primary" @click="showBindDialog = true">+ 绑定新账号</button>
      </div>
    </div>

    <div class="table-container">
      <table>
        <thead>
          <tr>
            <th style="width: 44px;">
              <label class="th-checkbox">
                <input type="checkbox" :checked="isAllSelected" @change="toggleSelectAll" />
              </label>
            </th>
            <th>ID</th>
            <th>账号昵称</th>
            <th>平台</th>
            <th>总粉丝</th>
            <th>总阅读量</th>
            <th>总收入</th>
            <th>昨日粉丝</th>
            <th>昨日阅读</th>
            <th>昨日收益</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="acc in accounts" :key="acc.id">
            <td>
              <label class="row-checkbox">
                <input type="checkbox" :checked="selectedAccountIds.includes(acc.id!)" @change="toggleSelect(acc.id!)" />
              </label>
            </td>
            <td style="color: var(--text-muted); font-family: monospace;">#{{ acc.id }}</td>
            <td>
              <div style="display: flex; align-items: center; gap: 10px;">
                <div style="width:32px;height:32px;border-radius:8px;background:rgba(255,255,255,0.06);display:flex;align-items:center;justify-content:center;font-size:16px;">👤</div>
                <span style="color: var(--text-primary); font-weight: 500;">{{ acc.accountName }}</span>
              </div>
            </td>
            <td>
              <span class="platform-tag">{{ getPlatformName(acc.platformId) }}</span>
              <div v-if="acc.cookieData" style="font-size: 11px; color: var(--success); margin-top: 4px;">● 已登录凭证</div>
            </td>
            <td>{{ getStat(acc.id, 'totalFans') }}</td>
            <td>{{ formatReads(getStat(acc.id, 'totalReads')) }}</td>
            <td>{{ getStat(acc.id, 'totalRevenue') || '—' }}</td>
            <td>{{ getStat(acc.id, 'yesterdayFans') }}</td>
            <td>{{ formatReads(getStat(acc.id, 'yesterdayReads')) }}</td>
            <td>{{ getStat(acc.id, 'yesterdayRevenue') || '—' }}</td>
            <td>
              <span class="badge" :class="acc.status === 1 ? 'badge-success' : 'badge-danger'">
                <span class="badge-dot"></span>{{ acc.status === 1 ? '正常' : '禁用' }}
              </span>
            </td>
            <td>
              <div style="display: flex; gap: 8px;">
                <button class="btn btn-danger" @click="handleDelete(acc.id!)" style="padding: 5px 12px; font-size: 12px;">解绑</button>
              </div>
            </td>
          </tr>
          <tr v-if="accounts.length === 0">
            <td colspan="12" style="text-align: center; padding: 40px; color: var(--text-muted);">暂无已绑定的账号</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 绑定弹窗：仅账号名称 + 选择平台 + 开始绑定 -->
    <div v-if="showBindDialog" class="card bind-dialog-card">
      <div class="section-title" style="margin-bottom: 20px;">🔗 绑定新账号</div>
      <p class="bind-hint">填写账号名称并选择平台，点击「开始绑定」后会在本机弹出该平台登录页，登录成功后凭证将自动保存到本地文件。</p>

      <div class="form-container single-col">
        <div class="form-group">
          <label class="form-label">选择平台</label>
          <el-select v-model="bindForm.platformKey" placeholder="请选择发布平台" class="custom-select" effect="dark" :teleported="false">
            <el-option
              v-for="p in bindPlatforms"
              :key="p.id"
              :label="p.platformName"
              :value="p.platformKey"
            />
          </el-select>
        </div>
        <div class="form-group">
          <label class="form-label">账号名称 <span class="required-hint">（必填，请自定义输入）</span></label>
          <input v-model="bindForm.accountName" class="form-input" placeholder="请自定义输入，如：我的百家号、我的头条号" maxlength="50" />
        </div>
      </div>

      <div style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 25px; padding-top: 20px; border-top: 1px solid rgba(255,255,255,0.05);">
        <button class="btn btn-ghost" @click="closeBindDialog">取消</button>
        <button class="btn btn-primary" :disabled="binding" @click="handleStartBind">
          {{ binding ? '正在打开登录页，请完成登录…' : '开始绑定' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getAccountList, getAccountStats, refreshAccountStats, bindStart, deleteAccount, type AccountStats } from '../../api/account';
import { getPlatformList } from '../../api/platform';
import type { Account, Platform } from '../../types';

const accounts = ref<Account[]>([]);
const platforms = ref<Platform[]>([]);
const statsMap = ref<Record<string, AccountStats>>({});
const showBindDialog = ref(false);
const binding = ref(false);
const refreshing = ref(false);
/** 勾选的账号 ID，仅选中的才参与「更新数据」；默认全选 */
const selectedAccountIds = ref<number[]>([]);
const bindForm = ref({
  platformKey: '',
  accountName: ''
});

const isAllSelected = computed(() => accounts.value.length > 0 && selectedAccountIds.value.length === accounts.value.length);

function toggleSelectAll() {
  if (isAllSelected.value) {
    selectedAccountIds.value = [];
  } else {
    selectedAccountIds.value = accounts.value.map((a) => a.id!).filter((id) => id != null);
  }
}

function toggleSelect(id: number) {
  const i = selectedAccountIds.value.indexOf(id);
  if (i >= 0) {
    selectedAccountIds.value = selectedAccountIds.value.filter((x) => x !== id);
  } else {
    selectedAccountIds.value = [...selectedAccountIds.value, id];
  }
}

function getStat(accountId: number, key: keyof AccountStats): string | number {
  const s = statsMap.value[String(accountId)];
  if (!s) return '—';
  const v = s[key];
  if (v === undefined || v === null) return '—';
  return v as string | number;
}

function formatReads(v: string | number): string {
  if (v === '—' || v == null) return '—';
  const n = typeof v === 'number' ? v : parseInt(String(v).replace(/,/g, ''), 10);
  if (isNaN(n)) return '—';
  return n >= 10000 ? (n / 10000).toFixed(1) + '万' : String(n);
}

/** 绑定时可选平台：目前仅保留百家号、今日头条 */
// 与分发渠道一致：只保留百家号和今日头条
const ALLOWED_PLATFORM_KEYS = ['bjh', 'baijiahao', 'toutiao', 'tt'];
const bindPlatforms = computed(() =>
  platforms.value.filter((p) => ALLOWED_PLATFORM_KEYS.includes((p.platformKey || '').toLowerCase()))
);

function getPlatformName(platformId: number) {
  const p = platforms.value.find((x) => x.id === platformId);
  return p ? p.platformName : `平台 ${platformId}`;
}

const loadData = async () => {
  try {
    accounts.value = await getAccountList();
    platforms.value = await getPlatformList();
    // 默认全选，仅选中的才更新数据
    selectedAccountIds.value = (accounts.value || []).map((a) => a.id!).filter((id) => id != null);
    const first = bindPlatforms.value[0];
    if (first && !bindForm.value.platformKey) {
      bindForm.value.platformKey = first.platformKey;
    }
  } catch (err) {
    console.error(err);
  }
};

const loadStats = async () => {
  try {
    const map = await getAccountStats();
    statsMap.value = map || {};
  } catch (err) {
    console.error(err);
  }
};

onMounted(async () => {
  await loadData();
  await loadStats();
});

const handleRefreshStats = async () => {
  if (accounts.value.length === 0) {
    ElMessage.info('暂无账号，请先绑定');
    return;
  }
  const ids = selectedAccountIds.value.slice();
  if (ids.length === 0) {
    ElMessage.warning('请至少勾选一个账号');
    return;
  }
  refreshing.value = true;
  try {
    const map = await refreshAccountStats(ids);
    statsMap.value = map || {};
    ElMessage.success(`已更新 ${ids.length} 个账号的昨日数据`);
  } catch (err: any) {
    const msg = err?.response?.data?.message ?? err?.message ?? '更新失败';
    ElMessage.error(msg);
  } finally {
    refreshing.value = false;
  }
};

function closeBindDialog() {
  if (!binding.value) {
    showBindDialog.value = false;
    bindForm.value.accountName = '';
  }
}

const handleStartBind = async () => {
  const name = bindForm.value.accountName?.trim();
  const key = bindForm.value.platformKey?.trim();
  if (!name) {
    ElMessage.warning('账号名称为必填，请自定义输入');
    return;
  }
  if (!key) {
    ElMessage.warning('请选择平台');
    return;
  }
  binding.value = true;
  try {
    await bindStart({ platformKey: key, accountName: name });
    ElMessage.success('绑定成功，登录页将自动关闭');
    showBindDialog.value = false;
    bindForm.value.accountName = '';
    await loadData();
    await loadStats();
  } catch (err: any) {
    const msg = err?.response?.data?.message ?? err?.response?.data?.msg ?? err?.message ?? '绑定失败';
    ElMessage.error(msg);
  } finally {
    binding.value = false;
  }
};

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm(
      '确定要解绑该账号吗？解绑后相关的发布凭证将被清除。',
      '安全提示',
      {
        confirmButtonText: '确定解绑',
        cancelButtonText: '取消',
        type: 'warning',
        customClass: 'custom-message-box'
      }
    );
    
    await deleteAccount(id);
    await loadData();
    await loadStats();
    ElMessage.success('账号已成功解绑');
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('删除失败');
    }
  }
};
</script>

<style scoped>
.th-checkbox,
.row-checkbox {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.th-checkbox input,
.row-checkbox input {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.bind-dialog-card {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 100;
  width: 420px;
  max-width: 90vw;
  box-shadow: 0 0 100px rgba(0,0,0,0.8);
}

.bind-hint {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 20px;
  line-height: 1.5;
}

.form-container.single-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.platform-tag {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 6px;
  background: rgba(59, 130, 246, 0.15);
  color: var(--primary);
  font-size: 12px;
}

.required-hint {
  font-weight: normal;
  color: var(--text-muted);
  font-size: 12px;
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-primary);
  border: 1px solid rgba(255, 255, 255, 0.15);
}
.btn-secondary:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.25);
}

/* Element Plus Select Override */
:deep(.custom-select) {
  width: 100%;
}
:deep(.custom-select .el-input__wrapper) {
  background-color: rgba(255, 255, 255, 0.04) !important;
  box-shadow: 0 0 0 1px var(--border-color) inset !important;
  border-radius: 8px;
  padding: 6px 12px;
}
:deep(.custom-select .el-input__inner) {
  color: var(--text-primary) !important;
  height: 38px;
}
:deep(.custom-select .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--accent-blue) inset !important;
}

/* 下拉面板样式 */
:deep(.el-select__popper) {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-color) !important;
  box-shadow: 0 10px 30px rgba(0,0,0,0.5) !important;
}
:deep(.el-select-dropdown__item) {
  color: var(--text-secondary) !important;
}
:deep(.el-select-dropdown__item.hover), 
:deep(.el-select-dropdown__item:hover) {
  background-color: rgba(255, 255, 255, 0.08) !important;
  color: var(--text-primary) !important;
}
:deep(.el-select-dropdown__item.selected) {
  background-color: rgba(79, 142, 247, 0.2) !important;
  color: var(--accent-blue) !important;
  font-weight: 600;
}
</style>
