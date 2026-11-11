<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { $api } from '~/composables/useApi'

interface OssView {
  endpoint: string
  region: string
  bucket: string
  accessKeyMasked: string
  secretKeyMasked: string
  publicBaseUrl: string
  pathStyle: string
  configured: boolean
}

const view = ref<OssView | null>(null)
const form = ref({
  endpoint: '', region: '', bucket: '', accessKey: '', secretKey: '',
  publicBaseUrl: '', pathStyle: 'true',
})
const testing = ref(false)

onMounted(async () => {
  view.value = await $api<OssView>('GET', '/admin/oss-config')
  form.value = {
    endpoint: view.value?.endpoint || '',
    region: view.value?.region || '',
    bucket: view.value?.bucket || '',
    // AK/SK 表单留空 = 不修改；脱敏值只作占位提示
    accessKey: '',
    secretKey: '',
    publicBaseUrl: view.value?.publicBaseUrl || '',
    pathStyle: view.value?.pathStyle || 'true',
  }
})

async function save() {
  await $api('PUT', '/admin/oss-config', {
    endpoint: form.value.endpoint,
    region: form.value.region,
    bucket: form.value.bucket,
    publicBaseUrl: form.value.publicBaseUrl,
    pathStyle: form.value.pathStyle,
    // AK/SK 未输入发 null（不改）；输入空串清除；有值更新
    accessKey: form.value.accessKey === '' ? '' : form.value.accessKey || null,
    secretKey: form.value.secretKey === '' ? '' : form.value.secretKey || null,
  })
  ElMessage.success('OSS 配置已保存')
  view.value = await $api<OssView>('GET', '/admin/oss-config')
  form.value.accessKey = view.value?.accessKeyMasked || ''
  form.value.secretKey = ''
}

async function test() {
  testing.value = true
  try {
    const r = await $api<{ ok: boolean }>('POST', '/admin/oss-config/test')
    r.ok ? ElMessage.success('OSS 连接成功（桶可写）') : ElMessage.error('OSS 连接失败（检查 endpoint/bucket/密钥）')
  } catch (e) { ElMessage.error((e as Error).message) } finally { testing.value = false }
}
</script>

<template>
  <div class="page" style="max-width:680px">
    <div class="head">
      <h2>对象存储 OSS</h2>
      <el-tag size="small" :type="view?.configured ? 'success' : 'info'">
        {{ view?.configured ? '已配置' : '未配置' }}
      </el-tag>
    </div>
    <p class="muted">
      S3 兼容协议，通吃七牛 / 阿里云 OSS / 腾讯 COS / MinIO。上传流程：文件经服务器中转上传到云存储，
      外链存入数据库，前台直接渲染云存储链接（图片与视频同流程）。
    </p>
    <div class="card" v-if="view">
      <div class="form-row"><label>Endpoint（S3 兼容地址）</label>
        <input v-model="form.endpoint" placeholder="https://s3.cn-east-1.qiniucs.com / https://oss-cn-hangzhou.aliyuncs.com / http://inkpress-minio:9000"></div>
      <div class="form-row"><label>Region（部分云固定值，如 us-east-1）</label>
        <input v-model="form.region" placeholder="us-east-1"></div>
      <div class="form-row"><label>Bucket</label>
        <input v-model="form.bucket" placeholder="inkpress-media"></div>
      <div class="form-row"><label>Access Key</label>
        <input v-model="form.accessKey" :placeholder="view.accessKeyMasked || 'AK'"></div>
      <div class="form-row"><label>Secret Key（留空不改；输入空格清除）</label>
        <input v-model="form.secretKey" type="password" :placeholder="view.secretKeyMasked || 'SK'"></div>
      <div class="form-row"><label>公网访问域名（CDN/桶外链前缀，去尾斜杠）</label>
        <input v-model="form.publicBaseUrl" placeholder="https://cdn.example.com"></div>
      <div class="form-row"><label>路径风格（MinIO/部分网关需 true）</label>
        <el-select v-model="form.pathStyle" style="width:100%">
          <el-option value="true" label="true（path-style，MinIO/七牛测试）" />
          <el-option value="false" label="false（virtual-host，云厂商生产）" />
        </el-select>
      </div>
      <div style="display:flex;gap:10px">
        <el-button type="primary" @click="save">保存</el-button>
        <el-button :loading="testing" @click="test">测试连接</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.head h2 { margin: 0; }
</style>
