<script setup lang="ts">
import { saveAuth, base, type AuthData } from '~/composables/useApi'

const tab = ref<'login' | 'register' | 'reset'>('login')
const email = ref('')
const password = ref('')
const nickname = ref('')
const code = ref('')
const newPassword = ref('')
const busy = ref(false)
const error = ref('')
const notice = ref('')

async function post<T>(path: string, body: unknown): Promise<T> {
  const resp = await fetch(base() + path, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  const text = await resp.text()
  try {
    const json = JSON.parse(text)
    if (json.code !== 0) throw new Error(json.message || `HTTP ${resp.status}`)
    return json.data as T
  } catch (e) {
    // 响应非 JSON（代理/安全层拦截页等）：给出可读提示而非原始解析错误
    if (e instanceof SyntaxError) {
      throw new Error(`服务异常 HTTP ${resp.status}：${text.slice(0, 60) || '(空响应)'}`)
    }
    throw e
  }
}

async function doLogin() {
  error.value = ''; notice.value = ''
  busy.value = true
  try {
    const data = await post<AuthData>('/auth/login', { email: email.value, password: password.value })
    saveAuth(data)
    navigateTo('/')
  } catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}

async function doRegister() {
  error.value = ''; notice.value = ''
  busy.value = true
  try {
    await post('/auth/register', { email: email.value, password: password.value, nickname: nickname.value })
    notice.value = '注册成功：验证码已发送至邮箱（开发环境未配 SMTP 时输出在服务端日志），请查收并完成激活。'
    notice.value += ' 切到"激活"步骤输入验证码。'
  } catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}

async function doVerify() {
  error.value = ''; notice.value = ''
  busy.value = true
  try {
    await post('/auth/verify-email', { email: email.value, code: code.value })
    notice.value = '激活成功，现在可以登录了。'
    tab.value = 'login'
  } catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}

async function doForgot() {
  error.value = ''; notice.value = ''
  busy.value = true
  try {
    await post('/auth/forgot-password', { email: email.value })
    notice.value = '若邮箱已注册，重置验证码已发送（开发环境见服务端日志）。'
  } catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}

async function doReset() {
  error.value = ''; notice.value = ''
  busy.value = true
  try {
    await post('/auth/reset-password', { email: email.value, code: code.value, newPassword: newPassword.value })
    notice.value = '密码已重置，请用新密码登录。'
    tab.value = 'login'
  } catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}
</script>

<template>
  <div class="auth-wrap">
    <div class="card auth-card">
      <div class="tabs">
        <button :class="{ active: tab === 'login' }" @click="tab = 'login'">登录</button>
        <button :class="{ active: tab === 'register' }" @click="tab = 'register'">注册</button>
        <button :class="{ active: tab === 'reset' }" @click="tab = 'reset'">找回密码</button>
      </div>

      <div class="form-row"><label>邮箱</label><input v-model="email" type="email" placeholder="you@example.com"></div>

      <template v-if="tab === 'login'">
        <div class="form-row"><label>密码</label><input v-model="password" type="password" @keyup.enter="doLogin"></div>
        <button class="primary" :disabled="busy" @click="doLogin">登录</button>
      </template>

      <template v-else-if="tab === 'register'">
        <div class="form-row"><label>昵称</label><input v-model="nickname" placeholder="怎么称呼你"></div>
        <div class="form-row"><label>密码</label><input v-model="password" type="password"></div>
        <button class="primary" :disabled="busy" @click="doRegister">注册并获取验证码</button>
        <div class="form-row" style="margin-top:14px"><label>邮箱验证码（激活）</label>
          <div style="display:flex;gap:8px">
            <input v-model="code" placeholder="6 位数字">
            <button :disabled="busy" @click="doVerify">激活</button>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="form-row"><button :disabled="busy" @click="doForgot">发送重置验证码</button></div>
        <div class="form-row"><label>验证码</label><input v-model="code"></div>
        <div class="form-row"><label>新密码</label><input v-model="newPassword" type="password"></div>
        <button class="primary" :disabled="busy" @click="doReset">重置密码</button>
      </template>

      <div v-if="error" class="error-text">{{ error }}</div>
      <div v-if="notice" class="ok-text">{{ notice }}</div>
    </div>
  </div>
</template>

<style scoped>
.auth-wrap { max-width: 420px; margin: 30px auto; }
.auth-card { padding: 26px; }
.tabs { display: flex; gap: 8px; margin-bottom: 18px; }
.tabs button { flex: 1; }
.tabs button.active { background: var(--primary); border-color: var(--primary); color: #fff; }
</style>
