<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const mode = ref('login') // login | register
const form = reactive({ username: '', password: '', nickname: '' })
const loading = ref(false)

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (mode.value === 'register' && form.password.length < 6) {
    ElMessage.warning('密码长度须在 6-32 位之间')
    return
  }
  loading.value = true
  try {
    if (mode.value === 'register') {
      await register({
        username: form.username,
        password: form.password,
        nickname: form.nickname || undefined
      })
      ElMessage.success('注册成功，已自动登录')
    }
    const { data } = await login({ username: form.username, password: form.password })
    userStore.setLogin(data.token, data.user)
    if (mode.value === 'login') {
      ElMessage.success('登录成功')
    }
    router.push(route.query.redirect || '/admin')
  } finally {
    loading.value = false
  }
}

function switchMode(target) {
  mode.value = target
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <span class="logo-mark">墨</span>
        <h1>INK<em>//</em>PRESS</h1>
        <p class="mono">ADMIN CONSOLE — SPRING BOOT × VUE 3</p>
      </div>

      <div class="mode-tabs mono">
        <span class="tab" :class="{ active: mode === 'login' }" @click="switchMode('login')">[ LOGIN ]</span>
        <span class="tab" :class="{ active: mode === 'register' }" @click="switchMode('register')">[ REGISTER ]</span>
      </div>

      <div class="form-item">
        <label class="mono">USERNAME</label>
        <input v-model="form.username" class="mono" :placeholder="mode === 'register' ? '3-20 位字母数字下划线' : '请输入用户名'" maxlength="20" />
      </div>

      <div class="form-item" v-if="mode === 'register'">
        <label class="mono">NICKNAME <span class="optional">(OPTIONAL)</span></label>
        <input v-model="form.nickname" class="mono" placeholder="昵称" maxlength="20" />
      </div>

      <div class="form-item">
        <label class="mono">PASSWORD</label>
        <input
          v-model="form.password"
          type="password"
          :placeholder="mode === 'register' ? '6-32 位' : '请输入密码'"
          maxlength="32"
          @keyup.enter="submit"
        />
      </div>

      <button class="login-btn mono" :disabled="loading" @click="submit">
        {{ loading ? 'PLEASE WAIT…' : mode === 'register' ? 'CREATE ACCOUNT →' : 'SIGN IN →' }}
      </button>

      <p class="login-hint mono">// DEFAULT ADMIN: admin / admin123</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    linear-gradient(rgba(255, 255, 255, 0.022) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.022) 1px, transparent 1px),
    var(--bg);
  background-size: 44px 44px;
  padding: 24px;
}

.login-card {
  width: 400px;
  max-width: 100%;
  background: var(--surface);
  border: 2px solid var(--text);
  padding: 40px 38px 28px;
  position: relative;
}
.login-card::after {
  content: "+";
  position: absolute;
  top: 12px;
  right: 16px;
  font-family: var(--font-mono);
  font-size: 20px;
  color: var(--acid);
}

.login-brand {
  text-align: center;
  margin-bottom: 26px;
}
.login-brand .logo-mark {
  width: 48px;
  height: 48px;
  background: var(--acid);
  color: #000;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 800;
}
.login-brand h1 {
  font-size: 22px;
  font-weight: 900;
  letter-spacing: 0.04em;
  margin: 14px 0 6px;
  color: var(--text);
}
.login-brand h1 em {
  font-style: normal;
  color: var(--acid);
}
.login-brand p {
  margin: 0;
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--text-faint);
}

.mode-tabs {
  display: flex;
  gap: 10px;
  margin-bottom: 24px;
}
.tab {
  flex: 1;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.1em;
  padding: 9px 0;
  border: 1px solid var(--line-bright);
  color: var(--text-dim);
  cursor: pointer;
  transition: all 0.15s;
}
.tab:hover {
  color: var(--accent);
  border-color: var(--accent);
}
.tab.active {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-ink);
}

.form-item {
  margin-bottom: 16px;
}
.form-item label {
  display: block;
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--text-faint);
  margin-bottom: 7px;
}
.form-item .optional {
  color: var(--line-bright);
}
.form-item input {
  width: 100%;
  background: var(--surface-2);
  border: 1px solid var(--line);
  color: var(--text);
  padding: 11px 13px;
  font-size: 13px;
  letter-spacing: 0.04em;
  outline: none;
  transition: border-color 0.15s;
}
.form-item input:focus {
  border-color: var(--acid);
}
.form-item input::placeholder {
  color: var(--text-faint);
}

.login-btn {
  width: 100%;
  margin-top: 8px;
  background: var(--accent);
  border: none;
  color: var(--accent-ink);
  font-weight: 700;
  font-size: 13px;
  letter-spacing: 0.14em;
  padding: 13px 0;
  cursor: pointer;
  transition: background 0.15s;
}
.login-btn:hover {
  background: var(--accent-hover);
}
.login-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.login-hint {
  text-align: center;
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--text-faint);
  margin: 20px 0 0;
}
</style>
