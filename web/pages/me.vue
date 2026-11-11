<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { $api, useAuthUser, hydrateAuthUser } from '~/composables/useApi'

interface Me {
  id: number
  email: string
  nickname: string
  avatarUrl: string
  bio: string
  roles: string[]
}
interface DeviceRow { id: number; name: string; lastUsedAt: string; lastUsedIp: string; createdAt: string }

const user = useAuthUser()
const me = ref<Me | null>(null)
const nickname = ref('')
const bio = ref('')
const oldPassword = ref('')
const newPassword = ref('')
const digestSubscribed = ref(false)
const devices = ref<DeviceRow[]>([])
const issueName = ref('')
const issuedToken = ref('')

onMounted(async () => {
  hydrateAuthUser()
  if (!localStorage.getItem('inkpress.access')) { navigateTo('/login'); return }
  me.value = await $api<Me>('GET', '/users/me')
  nickname.value = me.value.nickname
  bio.value = me.value.bio
  digestSubscribed.value = (await $api<{ digestSubscribed: boolean }>('GET', '/users/me/subscription')).digestSubscribed
  devices.value = await $api<DeviceRow[]>('GET', '/devices')
})

async function saveProfile() {
  await $api('PUT', '/users/me', { nickname: nickname.value, bio: bio.value })
  me.value = await $api<Me>('GET', '/users/me')
  // 同步顶栏用户态
  if (user.value) user.value = { ...user.value, nickname: me.value.nickname }
  ElMessage.success('资料已保存')
}

async function changePassword() {
  await $api('PUT', '/users/me/password', { oldPassword: oldPassword.value, newPassword: newPassword.value })
  oldPassword.value = ''
  newPassword.value = ''
  ElMessage.success('密码已修改（全会话已吊销，请用新密码重新登录）')
  setTimeout(() => navigateTo('/login'), 1200)
}

async function toggleDigest(v: boolean) {
  await $api('PUT', '/users/me/subscription', { digestSubscribed: v })
  ElMessage.success(v ? '已订阅每周精选（每封邮件可一键退订）' : '已取消订阅')
}

async function issueDevice() {
  const { value } = await ElMessageBox.prompt(
    '签发设备令牌需验证密码（令牌明文仅显示一次，请妥善保存）', `为「${issueName.value || '新设备'}」签发`, {
      inputType: 'password',
      inputPlaceholder: '登录密码',
    })
  // 复用登录端点语义：邮箱+密码+设备名
  const r = await $api<{ deviceToken: string; deviceId: number }>('POST', '/auth/device', {
    email: me.value!.email, password: value, deviceName: issueName.value || 'inkpress-cli',
  })
  issuedToken.value = r.deviceToken
  devices.value = await $api<DeviceRow[]>('GET', '/devices')
  ElMessage.success(`设备令牌已签发（#${r.deviceId}）`)
}

async function revokeDevice(row: DeviceRow) {
  await ElMessageBox.confirm(`吊销设备「${row.name}」后其本机工具将立即失效，确定？`, '吊销确认')
  await $api('DELETE', `/devices/${row.id}`)
  devices.value = await $api<DeviceRow[]>('GET', '/devices')
}
</script>

<template>
  <div class="me-page">
    <h2 class="grad-title">个人中心</h2>

    <div class="me-grid">
      <div class="card">
        <div class="sec">资料</div>
        <div class="form-row"><label>邮箱</label><input :value="me?.email" disabled></div>
        <div class="form-row"><label>昵称</label><input v-model="nickname"></div>
        <div class="form-row"><label>简介</label><textarea v-model="bio" rows="2" /></div>
        <el-button type="primary" @click="saveProfile">保存资料</el-button>
        <div v-if="me" class="muted" style="margin-top:8px">角色：{{ me.roles.join(' / ') }}</div>
      </div>

      <div class="card">
        <div class="sec">修改密码</div>
        <div class="form-row"><label>原密码</label><input v-model="oldPassword" type="password"></div>
        <div class="form-row"><label>新密码</label><input v-model="newPassword" type="password"></div>
        <el-button type="primary" :disabled="!oldPassword || !newPassword" @click="changePassword">修改密码</el-button>
      </div>

      <div class="card">
        <div class="sec">邮件订阅</div>
        <div style="display:flex;align-items:center;gap:12px">
          <el-switch v-model="digestSubscribed" @change="toggleDigest" />
          <span>每周精选（周一 09:00，近 7 天热文 Top5）</span>
        </div>
        <p class="muted">评论提醒邮件不受订阅开关影响；每封邮件正文附带一键退订链接。</p>
      </div>

      <div class="card">
        <div class="sec">设备令牌（本机分发工具）</div>
        <div style="display:flex;gap:8px;margin-bottom:10px">
          <input v-model="issueName" placeholder="设备名，如 my-laptop" style="flex:1">
          <el-button @click="issueDevice">签发令牌</el-button>
        </div>
        <div v-if="issuedToken" class="token-box">
          <div class="muted">新令牌（仅显示一次）：</div>
          <code>{{ issuedToken }}</code>
        </div>
        <table class="data">
          <thead><tr><th>设备</th><th>最近使用</th><th>IP</th><th></th></tr></thead>
          <tbody>
            <tr v-for="d in devices" :key="d.id">
              <td>{{ d.name }} <span class="muted">#{{ d.id }}</span></td>
              <td class="muted">{{ (d.lastUsedAt || '从未').replace('T', ' ').slice(0, 16) }}</td>
              <td class="muted">{{ d.lastUsedIp || '—' }}</td>
              <td><button class="danger" @click="revokeDevice(d)">吊销</button></td>
            </tr>
          </tbody>
        </table>
        <p class="muted">令牌供本机 inkpress 分发工具使用（inkpress login）。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.me-page { max-width: 860px; margin: 0 auto; }
.me-page h2 { margin: 0 0 20px; }
.me-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
@media (max-width: 800px) { .me-grid { grid-template-columns: 1fr; } }
.sec { font-weight: 600; margin-bottom: 12px; }
.token-box { background: #10131a; border: 1px solid var(--line); border-radius: 8px; padding: 10px; margin-bottom: 10px; word-break: break-all; }
.token-box code { color: #c4b5fd; font-size: .85em; }
</style>
