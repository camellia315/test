<template>
  <div class="system-page">
    <section class="hero-card">
      <div>
        <h2>系统管理中心</h2>
        <p>集中查看服务状态、管理用户账号启停，减少排障和运营操作成本。</p>
      </div>
      <el-button type="primary" :loading="loading.health || loading.overview || loading.users" @click="loadAll">
        刷新全部
      </el-button>
    </section>

    <el-row :gutter="14" class="metric-row">
      <el-col :xs="24" :sm="12" :md="6">
        <div class="metric-card">
          <p class="metric-label">用户总数</p>
          <p class="metric-value">{{ formatNum(overview.totalUsers) }}</p>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="metric-card">
          <p class="metric-label">启用用户</p>
          <p class="metric-value">{{ formatNum(overview.activeUsers) }}</p>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="metric-card">
          <p class="metric-label">禁用用户</p>
          <p class="metric-value">{{ formatNum(overview.disabledUsers) }}</p>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="metric-card">
          <p class="metric-label">今日新增</p>
          <p class="metric-value">{{ formatNum(overview.todayNewUsers) }}</p>
        </div>
      </el-col>
    </el-row>

    <div class="panel-card">
      <div class="panel-head">
        <h3>启动健康检查</h3>
        <div class="panel-actions">
          <el-tag :type="health.overallUp === true ? 'success' : 'danger'" effect="dark">
            {{ health.overallUp === true ? 'ALL UP' : 'PARTIAL DOWN' }}
          </el-tag>
          <el-button size="small" :loading="loading.health" @click="loadHealth">刷新状态</el-button>
        </div>
      </div>

      <el-table :data="serviceRows" size="small" border>
        <el-table-column prop="name" label="服务" width="180" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="row.up ? 'success' : 'danger'">{{ row.up ? '运行中' : '异常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="220" />
      </el-table>
      <p class="health-time">检查时间：{{ formatTime(health.checkedAt) }}</p>
    </div>

    <div class="panel-card">
      <div class="panel-head">
        <h3>用户管理</h3>
        <div class="toolbar">
          <el-input
            v-model="query.keyword"
            placeholder="搜索用户名 / 用户ID / 用户编号 / 系统ID"
            clearable
            style="width: 260px"
            @keyup.enter="loadUsers(1)"
          />
          <el-select v-model="query.status" clearable placeholder="状态" style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
          <el-button type="primary" :loading="loading.users" @click="loadUsers(1)">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </div>
      </div>

      <el-table :data="userPage.records" border stripe v-loading="loading.users">
        <el-table-column prop="id" label="系统ID" width="90" />
        <el-table-column prop="userNo" label="用户编号" min-width="120" />
        <el-table-column prop="userId" label="用户ID" min-width="140" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="170" />
        <el-table-column prop="phone" label="手机号" min-width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="Number(row.status) === 1 ? 'success' : 'danger'">
              {{ Number(row.status) === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="168">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="Number(row.status) === 1"
              type="danger"
              text
              @click="changeUserStatus(row, 0)"
            >
              禁用
            </el-button>
            <el-button
              v-else
              type="success"
              text
              @click="changeUserStatus(row, 1)"
            >
              启用
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-wrap">
        <el-pagination
          layout="prev, pager, next, total"
          :current-page="userPage.current"
          :page-size="userPage.size"
          :total="userPage.total"
          @current-change="loadUsers"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkStatsStartupHealth } from '../api/stats'
import { getAdminUserOverview, pageAdminUsers, updateAdminUserStatus } from '../api/user'

const loading = reactive({
  health: false,
  overview: false,
  users: false
})

const overview = reactive({
  totalUsers: 0,
  activeUsers: 0,
  disabledUsers: 0,
  todayNewUsers: 0
})

const health = reactive({
  overallUp: false,
  checkedAt: '',
  gateway: {},
  user: {},
  stats: {}
})

const query = reactive({
  keyword: '',
  status: undefined
})

const userPage = reactive({
  records: [],
  total: 0,
  current: 1,
  size: 10
})

const serviceRows = computed(() => ([
  {
    key: 'gateway',
    name: 'api-gateway',
    up: health.gateway?.up === true,
    message: health.gateway?.message || '-'
  },
  {
    key: 'user',
    name: 'user-service',
    up: health.user?.up === true,
    message: health.user?.message || '-'
  },
  {
    key: 'stats',
    name: 'stats-service',
    up: health.stats?.up === true,
    message: health.stats?.message || '-'
  }
]))

function unwrap(resp, fallbackMsg) {
  if (!resp || Number(resp.code) !== 0) {
    throw new Error(resp?.message || fallbackMsg)
  }
  return resp.data || {}
}

function formatNum(value) {
  return new Intl.NumberFormat('zh-CN').format(Number(value || 0))
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

async function loadHealth() {
  loading.health = true
  try {
    const data = unwrap(await checkStatsStartupHealth(), '加载健康检查失败')
    health.overallUp = data.overallUp === true
    health.checkedAt = data.checkedAt || ''
    health.gateway = data.gateway || {}
    health.user = data.user || {}
    health.stats = data.stats || {}
  } catch (error) {
    ElMessage.error(error.message || '加载健康检查失败')
  } finally {
    loading.health = false
  }
}

async function loadOverview() {
  loading.overview = true
  try {
    const data = unwrap(await getAdminUserOverview(), '加载系统概览失败')
    overview.totalUsers = Number(data.totalUsers || 0)
    overview.activeUsers = Number(data.activeUsers || 0)
    overview.disabledUsers = Number(data.disabledUsers || 0)
    overview.todayNewUsers = Number(data.todayNewUsers || 0)
  } catch (error) {
    ElMessage.error(error.message || '加载系统概览失败')
  } finally {
    loading.overview = false
  }
}

async function loadUsers(page = 1) {
  loading.users = true
  try {
    const data = unwrap(await pageAdminUsers({
      keyword: query.keyword || undefined,
      status: query.status,
      page,
      size: userPage.size
    }), '加载用户列表失败')

    userPage.records = Array.isArray(data.records) ? data.records : []
    userPage.total = Number(data.total || 0)
    userPage.current = Number(data.current || page)
    userPage.size = Number(data.size || userPage.size)
  } catch (error) {
    ElMessage.error(error.message || '加载用户列表失败')
  } finally {
    loading.users = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.status = undefined
  loadUsers(1)
}

async function changeUserStatus(row, status) {
  const actionText = status === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认${actionText}用户 ${row.username || row.userId || row.id}？`, '系统提示', {
      type: status === 1 ? 'success' : 'warning'
    })
    unwrap(await updateAdminUserStatus(row.id, status), `${actionText}失败`)
    ElMessage.success(`${actionText}成功`)
    await Promise.all([loadUsers(userPage.current), loadOverview()])
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || `${actionText}失败`)
    }
  }
}

async function loadAll() {
  await Promise.all([loadHealth(), loadOverview(), loadUsers(1)])
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.system-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.hero-card {
  border-radius: 14px;
  padding: 18px 20px;
  color: #f8fafc;
  background: linear-gradient(135deg, #1f2937 0%, #1d4ed8 50%, #0d9488 100%);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.hero-card h2 {
  margin: 0;
  font-size: 22px;
}

.hero-card p {
  margin: 8px 0 0;
  font-size: 13px;
  opacity: 0.92;
}

.metric-row {
  margin: 0 !important;
}

.metric-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  padding: 14px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
}

.metric-label {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.metric-value {
  margin: 8px 0 0;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.panel-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  padding: 14px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  gap: 10px;
  flex-wrap: wrap;
}

.panel-head h3 {
  margin: 0;
  font-size: 16px;
  color: #0f172a;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.health-time {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 12px;
}

.pager-wrap {
  margin-top: 10px;
  display: flex;
  justify-content: center;
}

@media (max-width: 992px) {
  .panel-head {
    align-items: flex-start;
  }
}
</style>
