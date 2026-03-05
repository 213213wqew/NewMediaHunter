<template>
  <div class="layout" :data-theme="theme" v-if="$route.path !== '/login'">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-logo">
        <div class="logo-icon">📡</div>
        <div>
          <div class="logo-text">新闻发布中心</div>
          <div class="logo-sub">Multi-Platform Hub</div>
        </div>
      </div>

      <nav class="sidebar-nav">
        <div class="nav-section-title">核心功能</div>
        <router-link to="/dashboard" class="nav-item" :class="{ active: $route.path === '/dashboard' }">
          <span class="nav-icon">📊</span> 控制大盘
        </router-link>
        <router-link to="/article" class="nav-item" :class="{ active: $route.path === '/article' }">
          <span class="nav-icon">✍️</span> 内容创作
        </router-link>
        <router-link to="/hot-news" class="nav-item" :class="{ active: $route.path === '/hot-news' }">
          <span class="nav-icon">🔥</span> 热点资讯
        </router-link>
        <router-link to="/video" class="nav-item" :class="{ active: $route.path === '/video' }">
          <span class="nav-icon">🎬</span> 视频创作
        </router-link>
        <router-link to="/media" class="nav-item" :class="{ active: $route.path === '/media' }">
          <span class="nav-icon">📦</span> 素材中心
        </router-link>
        <router-link to="/compliance" class="nav-item" :class="{ active: $route.path === '/compliance' }">
          <span class="nav-icon">🛡️</span> 合规中心
        </router-link>

        <div class="nav-section-title">账号与任务</div>
        <router-link to="/account" class="nav-item" :class="{ active: $route.path === '/account' }">
          <span class="nav-icon">🔗</span> 账号管理
        </router-link>
        <router-link to="/channel" class="nav-item" :class="{ active: $route.path === '/channel' }">
          <span class="nav-icon">🎯</span> 分发渠道
        </router-link>
        <router-link to="/task" class="nav-item" :class="{ active: $route.path === '/task' }">
          <span class="nav-icon">📋</span> 我的发文
        </router-link>
        <router-link to="/ai-settings" class="nav-item" :class="{ active: $route.path === '/ai-settings' }">
          <span class="nav-icon">🤖</span> AI 设置
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="sidebar-user">
          <div class="user-avatar">{{ userName ? userName.charAt(0).toUpperCase() : 'U' }}</div>
          <div style="width: 100%;">
            <div class="user-name">{{ userName || '未登录' }}</div>
            <div class="user-role" style="display:flex; justify-content:space-between; align-items:center;">
              <span>{{ userRole || 'GUEST' }}</span>
              <a href="javascript:void(0)" @click="handleLogout" style="color:#ccc; font-size:12px; text-decoration:none;">[退出]</a>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="main-content">
      <header class="topbar">
        <div class="topbar-title">
          <span class="topbar-badge"></span>
          系统运行中
        </div>
        <div class="topbar-actions">
          <button type="button" class="theme-toggle" :title="theme === 'dark' ? '切换为亮色' : '切换为暗色'" @click="toggleTheme">
            <span v-if="theme === 'dark'">☀️ 亮色</span>
            <span v-else>🌙 暗色</span>
          </button>
        </div>
      </header>
      <div class="page-content">
        <transition name="fade-slide" mode="out-in">
          <router-view :key="$route.path" />
        </transition>
      </div>
    </div>
  </div>
  <router-view v-else />
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';
import { ref, watch, onMounted } from 'vue';

const $route = useRoute();
const router = useRouter();

const userName = ref('');
const userRole = ref('');
const theme = ref<'light' | 'dark'>('dark');

const THEME_KEY = 'app-theme';

onMounted(() => {
  const saved = localStorage.getItem(THEME_KEY) as 'light' | 'dark' | null;
  if (saved === 'light' || saved === 'dark') theme.value = saved;
});

function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark';
  localStorage.setItem(THEME_KEY, theme.value);
}

watch(
  () => $route.path,
  () => {
    userName.value = localStorage.getItem('username') || '';
    userRole.value = localStorage.getItem('role') || '';
  },
  { immediate: true }
);

const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('role');
    router.push('/login');
};
</script>
