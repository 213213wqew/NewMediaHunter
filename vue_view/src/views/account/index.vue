<template>
  <div>
    <div class="page-header">
      <div>
        <div class="page-title">账号管理</div>
        <div class="page-subtitle">绑定和管理各平台发布渠道账号</div>
      </div>
      <button class="btn btn-primary" @click="showBindDialog = true">+ 绑定新账号</button>
    </div>

    <div class="table-container">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>账号昵称</th>
            <th>平台ID</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="acc in accounts" :key="acc.id">
            <td style="color: var(--text-muted); font-family: monospace;">#{{ acc.id }}</td>
            <td>
              <div style="display: flex; align-items: center; gap: 10px;">
                <div style="width:32px;height:32px;border-radius:8px;background:rgba(255,255,255,0.06);display:flex;align-items:center;justify-content:center;font-size:16px;">👤</div>
                <span style="color: var(--text-primary); font-weight: 500;">{{ acc.accountName }}</span>
              </div>
            </td>
            <td>
              <span class="platform-tag platform-baijiahao">Platform: {{ acc.platformId }}</span>
              <div v-if="acc.appId" style="font-size: 11px; color: var(--success); margin-top: 4px;">● API 凭证已配置</div>
              <div v-if="acc.cookieData" style="font-size: 11px; color: var(--info); margin-top: 4px;">● Cookie 凭证已配置</div>
            </td>
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
            <td colspan="5" style="text-align: center; padding: 40px; color: var(--text-muted);">暂无已绑定的账号</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 简单的绑定弹窗模拟 -->
    <div v-if="showBindDialog" class="card bind-dialog-card">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 25px;">
        <div class="section-title" style="margin: 0;">🔗 绑定新账号</div>
        <button class="btn btn-ghost" @click="showGuide = !showGuide" style="font-size: 13px; color: var(--primary);">
          {{ showGuide ? '📖 隐藏获取教程' : '❓ 如何获取参数？' }}
        </button>
      </div>

      <div class="dialog-layout">
        <!-- 表单部分 -->
        <div class="form-container">
          <div class="form-group">
            <label class="form-label">选择平台</label>
            <select v-model="newAcc.platformId" class="form-input">
              <option v-for="p in platforms" :key="p.id" :value="p.id">{{ p.platformName }}</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">账号昵称</label>
            <input v-model="newAcc.accountName" class="form-input" placeholder="输入账号名称(如: 我的百家号)" />
          </div>
          <div class="form-group">
            <label class="form-label">App ID</label>
            <input v-model="newAcc.appId" class="form-input" placeholder="输入官方平台分配的 App ID" />
          </div>
          <div class="form-group">
            <label class="form-label">App Token (Secret)</label>
            <input v-model="newAcc.appSecret" class="form-input" type="password" placeholder="输入官方平台分配的 Token/Secret" />
          </div>
          <div class="form-group">
            <label class="form-label">Cookie 数据 (如有)</label>
            <textarea v-model="newAcc.cookieData" class="form-input" rows="3" placeholder="某些平台(如头条)如无API可用，可填入登录后的 Cookie"></textarea>
          </div>
        </div>

        <!-- 教程辅助部分 (条件显示) -->
        <div v-if="showGuide" class="guide-container scroller">
          <div class="guide-content">
            <!-- 百家号 (ID: 1) -->
            <div v-if="newAcc.platformId === 1">
              <h4 style="color: var(--primary); margin-bottom: 12px; display: flex; align-items: center; gap: 5px;">
                <span>百度百家号获取指南</span>
              </h4>
              <div class="step-box">
                <div class="step-item">
                  <div class="step-num">1</div>
                  <div class="step-txt">登录 <a href="https://baijiahao.baidu.com/" target="_blank">百家号后台</a></div>
                </div>
                <div class="step-item">
                  <div class="step-num">2</div>
                  <div class="step-txt"><b>API模式 (推荐)</b>: 找“开发者服务”->“服务管理” (个人号可能被隐藏)</div>
                </div>
                <div class="step-item">
                  <div class="step-num">3</div>
                  <div class="step-txt"><b>Cookie模式 (备选)</b>: 若找不到API，请按下方头条方法抓取百家号Cookie，填入下方文本框</div>
                </div>
              </div>
              <p style="font-size: 11px; color: #ffab00; margin-top: 15px;">※ 个人账号如找不到API，请直接使用Cookie模式</p>
            </div>

            <!-- 今日头条 (ID: 3) -->
            <div v-else-if="newAcc.platformId === 3 || true"> <!-- 默认展示头条/通用方案 -->
              <h4 style="color: var(--primary); margin-bottom: 12px;">Cookie 提取“保姆级”步骤：</h4>
              <div class="step-box">
                <div class="step-item">
                  <div class="step-num">1</div>
                  <div class="step-txt">电脑浏览器登录后台，按 <b>F12</b></div>
                </div>
                <div class="step-item">
                  <div class="step-num">2</div>
                  <div class="step-txt">顶部选 <b>Network(网络)</b>，按 <b>F5</b> 刷新</div>
                </div>
                <div class="step-item">
                  <div class="step-num">3</div>
                  <div class="step-txt">随便点左侧一个请求，看右侧 <b>Headers</b></div>
                </div>
                <div class="step-item">
                  <div class="step-num">4</div>
                  <div class="step-txt">找 <b>cookie:</b> 开头的一长串并复制</div>
                </div>
              </div>
              <div style="margin-top: 15px; background: rgba(0,0,0,0.2); padding: 8px; border-radius: 6px; font-size: 11px; color: #94a3b8;">
                案例：BDUSS=xxxx... 或 __ac_nonce=xxxx...
              </div>
            </div>

            <!-- 默认/其他平台 -->
            <div v-else>
              <h4 style="color: var(--primary); margin-bottom: 12px;">当前平台获取指南：</h4>
              <div class="step-box">
                <div class="step-item">
                  <div class="step-num">1</div>
                  <div class="step-txt">打开该平台官方管理后台</div>
                </div>
                <div class="step-item">
                  <div class="step-num">2</div>
                  <div class="step-txt">进入相关 <b>账号设置</b> 或 <b>开发者中心</b></div>
                </div>
                <div class="step-item">
                  <div class="step-num">?</div>
                  <div class="step-txt">详见 <a href="file:///C:/Users/Administrator/.gemini/antigravity/brain/cd192d7e-6f2d-4083-b3b7-31808549ffdb/platform_credential_guide.md" target="_blank">全平台文档</a></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 25px; padding-top: 20px; border-top: 1px solid rgba(255,255,255,0.05);">
        <button class="btn btn-ghost" @click="showBindDialog = false">取消</button>
        <button class="btn btn-primary" @click="handleSaveAccount">提交绑定</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getAccountList, saveAccount, deleteAccount } from '../../api/account';
import { getPlatformList } from '../../api/platform';
import type { Account, Platform } from '../../types';

const accounts = ref<Account[]>([]);
const platforms = ref<Platform[]>([]);
const showBindDialog = ref(false);
const showGuide = ref(false);
const newAcc = ref({
  platformId: 1,
  accountName: '',
  appId: '',
  appSecret: '',
  cookieData: '',
  status: 1
});

const loadData = async () => {
  try {
    accounts.value = await getAccountList();
    platforms.value = await getPlatformList();
  } catch (err) {
    console.error(err);
  }
};

onMounted(loadData);

const handleSaveAccount = async () => {
  if (!newAcc.value.accountName) return;
  try {
    await saveAccount(newAcc.value);
    showBindDialog.value = false;
    newAcc.value.accountName = '';
    newAcc.value.appId = '';
    newAcc.value.appSecret = '';
    newAcc.value.cookieData = '';
    loadData();
  } catch (err) {
    alert('绑定失败，请检查后端数据库连接');
  }
};

const handleDelete = async (id: number) => {
  if (!confirm('确定要解绑该账号吗？')) return;
  try {
    await deleteAccount(id);
    loadData();
  } catch (err) {
    alert('删除失败');
  }
};
</script>

<style scoped>
.bind-dialog-card {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 100;
  width: 650px; /* 宽度增加 */
  max-width: 90vw;
  box-shadow: 0 0 100px rgba(0,0,0,0.8);
  transition: width 0.3s ease;
}

.dialog-layout {
  display: flex;
  gap: 30px;
}

.form-container {
  flex: 1;
}

.guide-container {
  width: 240px;
  background: rgba(59, 130, 246, 0.05);
  border-radius: 12px;
  padding: 15px;
  border: 1px dashed rgba(59, 130, 246, 0.2);
  height: 380px;
  overflow-y: auto;
}

.guide-content b {
  color: #fff;
}

.guide-content a {
  color: var(--primary);
  text-decoration: underline;
}

.step-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.step-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.step-num {
  width: 18px;
  height: 18px;
  background: var(--primary);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  flex-shrink: 0;
  margin-top: 2px;
}

.step-txt {
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.4;
}
</style>
