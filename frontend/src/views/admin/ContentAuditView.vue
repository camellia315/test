<template>
  <div class="audit-page">
    <div class="page-card">
      <div class="page-head">
        <div>
          <h2 class="page-title">内容审核</h2>
          <p class="subtitle">仅审核新发布的校园活动。</p>
        </div>
        <el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button>
      </div>

      <el-row :gutter="12" class="summary-row">
        <el-col :xs="24" :md="24">
          <div class="summary-card">
            <p>待审核活动</p>
            <h3>{{ activityTotal }}</h3>
          </div>
        </el-col>
      </el-row>
    </div>

    <div class="page-card">
      <h3 class="block-title">活动发布待审核</h3>
      <el-table v-loading="loading" :data="activityRows" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="发布人" width="100">
          <template #default="{ row }">#{{ row.userId }}</template>
        </el-table-column>
        <el-table-column prop="location" label="地点" min-width="140" />
        <el-table-column label="开始时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="approveActivity(row)">通过</el-button>
            <el-button link type="danger" @click="rejectActivity(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditActivity, pagePendingAuditActivities } from '../../api/activity'
import { getRoles, getUser } from '../../utils/auth'

const loading = ref(false)
const activityRows = ref([])
const currentUserId = computed(() => Number(getUser()?.id || 0))
const currentRole = computed(() => {
  const roles = getRoles()
  if (roles.includes('ADMIN')) return 'ADMIN'
  if (roles.includes('AUDITOR')) return 'AUDITOR'
  return 'USER'
})
const activityTotal = computed(() => activityRows.value.length)

const checkResp = (resp, msg) => {
  if (!resp || Number(resp.code) !== 0) {
    throw new Error(resp?.message || msg)
  }
  return resp.data || {}
}

const formatTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const loadAll = async () => {
  loading.value = true
  try {
    const activityRes = await pagePendingAuditActivities({ page: 1, size: 20, operatorRole: currentRole.value })
    activityRows.value = checkResp(activityRes, '加载活动审核队列失败').records || []
  } catch (error) {
    ElMessage.error(error.message || '加载审核列表失败')
  } finally {
    loading.value = false
  }
}

const approveActivity = async (row) => {
  try {
    checkResp(await auditActivity(row.id, {
      auditorId: currentUserId.value,
      auditorRole: currentRole.value,
      status: 1,
      reason: ''
    }), '活动审核通过失败')
    ElMessage.success('活动发布已通过')
    await loadAll()
  } catch (error) {
    ElMessage.error(error.message || '活动审核通过失败')
  }
}

const rejectActivity = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回活动发布', {
      inputPlaceholder: '例如：时间信息不完整',
      inputValidator: (input) => (input && input.trim() ? true : '请输入驳回原因')
    })
    checkResp(await auditActivity(row.id, {
      auditorId: currentUserId.value,
      auditorRole: currentRole.value,
      status: 2,
      reason: value.trim()
    }), '活动驳回失败')
    ElMessage.success('已驳回该活动发布')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '活动驳回失败')
    }
  }
}

onMounted(loadAll)
</script>

<style scoped>
.audit-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
  flex-wrap: wrap;
}

.subtitle {
  margin: -8px 0 0;
  font-size: 13px;
  color: #64748b;
}

.summary-row {
  margin: 12px 0 0 !important;
}

.summary-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px;
  background: #fff;
}

.summary-card p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.summary-card h3 {
  margin: 8px 0 0;
  font-size: 24px;
}

.block-title {
  margin: 0 0 12px;
}
</style>
