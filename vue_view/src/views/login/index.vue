<template>
  <div class="login-container">
    <!-- 静态渐变背景，无动画/无模糊，减轻 WebView 卡顿 -->
    <div class="login-bg-static"></div>
    <div class="login-box glass-panel">
      <div class="login-header">
        <div class="logo-icon">📡</div>
        <h2>新闻发布中心</h2>
        <p>欢迎回到系统，请登录</p>
      </div>
      <form class="login-form" @submit.prevent="handleLogin">
        <div class="form-group">
          <label>用户名</label>
          <div class="input-wrapper">
            <span class="input-icon">👤</span>
            <input type="text" v-model="form.username" placeholder="请输入用户名 (如: admin)" required />
          </div>
        </div>
        <div class="form-group">
          <label>密码</label>
          <div class="input-wrapper">
             <span class="input-icon">🔒</span>
            <input type="password" v-model="form.password" placeholder="请输入密码 (如: 123456)" required />
          </div>
        </div>
        <button style="text-align: center;" type="submit" class="btn btn-primary login-btn" :disabled="loading">
          {{ loading ? '登录中...' : '立 即 登 录' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import request from '../../utils/request';
import { ElMessage } from 'element-plus';

const router = useRouter();
const loading = ref(false);
const form = reactive({
  username: '',
  password: ''
});

const handleLogin = async () => {
  if (!form.username || !form.password) return;
  loading.value = true;
  try {
    const res: any = await request.post('/auth/login', form);
    if (res && res.token) {
      localStorage.setItem('token', res.token);
      localStorage.setItem('username', res.username);
      localStorage.setItem('role', res.role);
      try {
        await request.post('/auth/save-session', { token: res.token, username: res.username, role: res.role });
      } catch (_) {}
      ElMessage.success('登录成功');
      router.push('/');
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '登录失败，请检查用户名或密码');
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  width: 100%;
  background-color: #0f172a;
  position: relative;
  overflow: hidden;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
}

/* 静态渐变背景，无动画无模糊，避免 WebView 卡顿 */
.login-bg-static {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 40%, #0f172a 70%, #1e1b4b 100%);
}

/* 卡片：不用 backdrop-filter，改用半透明纯色，减轻卡顿 */
.glass-panel {
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 50px 48px;
  border-radius: 24px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(255, 255, 255, 0.05) inset;
  width: 420px;
  z-index: 2;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.glass-panel:hover {
  transform: translateY(-8px);
  box-shadow: 0 30px 60px -12px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(255, 255, 255, 0.1) inset;
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo-icon {
  font-size: 48px;
  margin-bottom: 15px;
  background: linear-gradient(135deg, #60a5fa, #a78bfa);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  display: inline-block;
}

.login-header h2 {
  margin: 0;
  color: #f8fafc;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 1px;
}

.login-header p {
  margin: 10px 0 0;
  color: #94a3b8;
  font-size: 15px;
}

.form-group {
  margin-bottom: 24px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: #cbd5e1;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.5px;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 16px;
  font-size: 18px;
  color: #64748b;
  transition: color 0.3s ease;
}

.form-group input {
  width: 100%;
  padding: 16px 16px 16px 45px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  box-sizing: border-box;
  font-size: 15px;
  color: #f8fafc;
  background-color: rgba(15, 23, 42, 0.6);
  transition: all 0.3s ease;
}

.form-group input:focus {
  outline: none;
  border-color: #3b82f6;
  background-color: rgba(15, 23, 42, 0.8);
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.2);
}

.form-group input:focus + .input-wrapper .input-icon,
.form-group input:focus ~ .input-icon {
  color: #3b82f6;
}

.form-group input::placeholder {
  color: #475569;
}

.login-btn {
  width: 100%;
  padding: 16px;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  color: white;
  border: none;
  cursor: pointer;
  border-radius: 12px;
  margin-top: 15px;
  letter-spacing: 2px;
  box-shadow: 0 10px 25px -5px rgba(59, 130, 246, 0.5);
  transition: all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  position: relative;
  overflow: hidden;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 15px 30px -5px rgba(139, 92, 246, 0.6);
  background: linear-gradient(135deg, #60a5fa 0%, #a78bfa 100%);
}

.login-btn:disabled {
  background: #475569;
  color: #94a3b8;
  cursor: not-allowed;
  box-shadow: none;
  transform: none;
}

</style>
