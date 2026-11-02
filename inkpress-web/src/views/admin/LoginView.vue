<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
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
        <h1>InkPress 管理后台</h1>
        <p>基于 Spring Boot + Vue 的个人博客与文章管理系统</p>
      </div>

      <div class="mode-tabs">
        <span class="tab" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</span>
        <span class="tab" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</span>
      </div>

      <el-form @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名（3-20 位字母数字下划线）" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item v-if="mode === 'register'">
          <el-input v-model="form.nickname" placeholder="昵称（选填）" :prefix-icon="User" size="large" maxlength="20" />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="mode === 'register' ? '密码（6-32 位）' : '密码'"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="submit">
          {{ mode === 'register' ? '注 册' : '登 录' }}
        </el-button>
      </el-form>

      <p class="login-hint">首次启动自动创建管理员：admin / admin123</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #2b3a4b 60%, #3a4a5c 100%);
}

.login-card {
  width: 380px;
  background: #fff;
  border-radius: 12px;
  padding: 36px 32px 24px;
}

.login-brand {
  text-align: center;
  margin-bottom: 20px;
}

.login-brand .logo-mark {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: #303133;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
}

.login-brand h1 {
  font-size: 20px;
  margin: 12px 0 4px;
}

.login-brand p {
  color: #909399;
  font-size: 12px;
  margin: 0;
}

.mode-tabs {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-bottom: 18px;
}

.tab {
  font-size: 15px;
  color: #909399;
  cursor: pointer;
  padding-bottom: 6px;
  border-bottom: 2px solid transparent;
}

.tab.active {
  color: #409eff;
  font-weight: 600;
  border-bottom-color: #409eff;
}

.login-btn {
  width: 100%;
  margin-top: 4px;
}

.login-hint {
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
  margin-top: 16px;
}
</style>
