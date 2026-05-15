<template>
  <div class="page-card">
    <h2 class="page-title">系统设置</h2>
    <p class="subtitle">管理员配置项入口（当前版本提供运行时配置说明和健康状态查看）。</p>

    <el-alert
      title="管理员身份由 user-service 的 auth.admin-user-ids 配置决定，修改后需重启 user-service。"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />

    <el-descriptions border :column="1">
      <el-descriptions-item label="当前登录用户ID">#{{ userId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="当前角色">{{ rolesText }}</el-descriptions-item>
      <el-descriptions-item label="建议配置文件">
        backend/user-service/src/main/resources/application.yml
      </el-descriptions-item>
      <el-descriptions-item label="管理员配置项">
        auth.admin-user-ids: 1,2,3
      </el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { getRoles, getUser } from '../../utils/auth'

const userId = computed(() => Number(getUser()?.id || 0))
const rolesText = computed(() => {
  const roles = getRoles()
  return roles.length ? roles.join(' / ') : 'USER'
})
</script>

<style scoped>
.subtitle {
  margin: -8px 0 12px;
  color: #64748b;
  font-size: 13px;
}
</style>
