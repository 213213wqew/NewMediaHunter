<template>
  <div class="login-container">
    <div class="login-bg-shapes">
      <div class="shape shape-1"></div>
      <div class="shape shape-2"></div>
      <div class="shape shape-3"></div>
      <div class="shape shape-4"></div>
    </div>
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
        <button style="text-align: center;" type="submit" class="btn btn-primary login-btn glow-effect" :disabled="loading">
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

/* 动态网格点缀与模糊光斑背景 */
.login-bg-shapes {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  z-index: 1;
}

.shape {
  position: absolute;
  filter: blur(80px);
  border-radius: 50%;
  animation: float 20s infinite ease-in-out alternate;
  opacity: 0.6;
}

.shape-1 {
  width: 500px;
  height: 500px;
  background: #3b82f6; /* Blue */
  top: -100px;
  left: -100px;
  animation-delay: 0s;
}

.shape-2 {
  width: 400px;
  height: 400px;
  background: #8b5cf6; /* Purple */
  bottom: -50px;
  right: -50px;
  animation-delay: -5s;
}

.shape-3 {
  width: 600px;
  height: 600px;
  background: #0ea5e9; /* Light Blue */
  top: 40%;
  left: 30%;
  animation-delay: -10s;
}

.shape-4 {
  width: 300px;
  height: 300px;
  background: #ec4899; /* Pink */
  top: 10%;
  right: 20%;
  animation-delay: -15s;
}

@keyframes float {
  0% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(30px, 50px) scale(1.1); }
  100% { transform: translate(-20px, 20px) scale(0.9); }
}

/* 毛玻璃卡片 */
.glass-panel {
  background: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 50px 48px;
  border-radius: 24px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(255, 255, 255, 0.05) inset;
  width: 420px;
  z-index: 2;
  transition: transform 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
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
  animation: pulse 3s infinite alternate;
}

@keyframes pulse {
  0% { transform: scale(1); filter: brightness(1); }
  100% { transform: scale(1.05); filter: brightness(1.2); }
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

.login-btn::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 50%;
  height: 100%;
  background: linear-gradient(to right, rgba(255,255,255,0) 0%, rgba(255,255,255,0.2) 50%, rgba(255,255,255,0) 100%);
  transform: skewX(-25deg);
  animation: shine 3s infinite;
}

@keyframes shine {
  0% { left: -100%; }
  20% { left: 200%; }
  100% { left: 200%; }
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
.login-btn:disabled::after {
  display: none;
}

</style>
