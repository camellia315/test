<template>
  <div class="admin-analytics-page">
    <div class="page-card hero-card">
      <div>
        <h2 class="page-title">管理员数据分析</h2>
        <p class="subtitle">聚焦用户结构、内容运营趋势与审核压力，避免与首页统计重复。</p>
      </div>
      <div class="hero-actions">
        <el-tag type="info" effect="plain">趋势窗口：最近7天</el-tag>
        <el-button type="primary" :loading="loading" @click="loadData">刷新分析</el-button>
      </div>
    </div>

    <el-row :gutter="12" class="summary-row">
      <el-col :xs="24" :sm="12" :md="6">
        <div class="summary-card">
          <p>平台用户总数</p>
          <h3>{{ formatNumber(userOverview.totalUsers) }}</h3>
          <span>今日新增 {{ formatNumber(userOverview.todayNewUsers) }}</span>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="summary-card success">
          <p>用户启用率</p>
          <h3>{{ formatRate(activeRate) }}%</h3>
          <span>启用 {{ formatNumber(userOverview.activeUsers) }} / 禁用 {{ formatNumber(userOverview.disabledUsers) }}</span>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="summary-card warning">
          <p>待审核活动</p>
          <h3>{{ formatNumber(pending.activity) }}</h3>
          <span>{{ pendingLevelText }}</span>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <div class="summary-card info">
          <p>内容总量</p>
          <h3>{{ formatNumber(contentTotal) }}</h3>
          <span>失物 {{ formatNumber(overview.lostFoundTotal) }} · 活动 {{ formatNumber(overview.activityTotal) }}</span>
        </div>
      </el-col>
    </el-row>

    <div class="page-card">
      <div class="block-head">
        <h3 class="block-title">用户结构与内容占比</h3>
        <span class="block-tip">最近更新：{{ lastUpdatedText }}</span>
      </div>
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <div class="inner-block">
            <p class="inner-title">用户状态</p>
            <div class="progress-line">
              <span>启用率</span>
              <el-progress :percentage="activeRate" status="success" />
            </div>
            <div class="progress-line">
              <span>禁用率</span>
              <el-progress :percentage="disabledRate" status="exception" />
            </div>
          </div>
        </el-col>
        <el-col :xs="24" :md="12">
          <div class="inner-block">
            <p class="inner-title">内容占比</p>
            <div class="progress-line">
              <span>失物招领占比</span>
              <el-progress :percentage="lostShare" />
            </div>
            <div class="progress-line">
              <span>校园活动占比</span>
              <el-progress :percentage="activityShare" color="#6366f1" />
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <div class="page-card">
      <div class="block-head">
        <h3 class="block-title">趋势分析（最近7天）</h3>
        <span class="block-tip">固定窗口：DAY x 7</span>
      </div>
      <el-table :data="trendRows" stripe size="small">
        <el-table-column prop="label" label="时间" min-width="120" />
        <el-table-column prop="user" label="用户注册" min-width="120" />
        <el-table-column prop="lostFound" label="失物发布" min-width="120" />
        <el-table-column prop="activityApply" label="活动报名" min-width="120" />
      </el-table>
      <div class="action-row">
        <el-button type="primary" @click="router.push('/admin/content-audit')">去内容审核</el-button>
        <el-button @click="router.push('/admin/users')">去用户管理</el-button>
        <el-button @click="router.push('/admin/settings')">去系统设置</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getStatsOverview, getStatsTrends } from '../../api/stats'
import { pagePendingAuditActivities } from '../../api/activity'
import { getAdminUserOverview } from '../../api/user'

const TREND_GRANULARITY = 'DAY'
const TREND_SIZE = 7

const router = useRouter()
const loading = ref(false)
const lastUpdatedAt = ref(null)

const overview = reactive({
  lostFoundTotal: 0,
  lostFoundToday: 0,
  lostFoundRecovered: 0,
  lostFoundRecoveryRate: 0,
  activityTotal: 0,
  activityToday: 0,
  activityApplyTotal: 0,
  activityApplyToday: 0
})

const userOverview = reactive({
  totalUsers: 0,
  activeUsers: 0,
  disabledUsers: 0,
  todayNewUsers: 0
})

const trends = reactive({
  labels: [],
  userRegisterTrend: [],
  lostFoundPublishTrend: [],
  activityApplyTrend: []
})

const pending = reactive({
  activity: 0
})

const contentTotal = computed(() => Number(overview.lostFoundTotal || 0) + Number(overview.activityTotal || 0))

const activeRate = computed(() => {
  const total = Number(userOverview.totalUsers || 0)
  if (!total) return 0
  return Number(((Number(userOverview.activeUsers || 0) * 100) / total).toFixed(2))
})

const disabledRate = computed(() => {
  const total = Number(userOverview.totalUsers || 0)
  if (!total) return 0
  return Number(((Number(userOverview.disabledUsers || 0) * 100) / total).toFixed(2))
})

const lostShare = computed(() => {
  const total = Number(contentTotal.value || 0)
  if (!total) return 0
  return Number(((Number(overview.lostFoundTotal || 0) * 100) / total).toFixed(2))
})

const activityShare = computed(() => {
  const total = Number(contentTotal.value || 0)
  if (!total) return 0
  return Number(((Number(overview.activityTotal || 0) * 100) / total).toFixed(2))
})

const pendingLevelText = computed(() => {
  const total = Number(pending.activity || 0)
  if (total === 0) return '审核队列空闲'
  if (total <= 5) return '审核压力可控'
  if (total <= 15) return '审核压力中等'
  return '审核压力较高，请优先处理'
})

const trendRows = computed(() => {
  const rows = []
  const labels = Array.isArray(trends.labels) ? trends.labels : []
  for (let i = 0; i < labels.length; i++) {
    rows.push({
      label: labels[i],
      user: Number(trends.userRegisterTrend?.[i] || 0),
      lostFound: Number(trends.lostFoundPublishTrend?.[i] || 0),
      activityApply: Number(trends.activityApplyTrend?.[i] || 0)
    })
  }
  return rows
})

const lastUpdatedText = computed(() => {
  if (!lastUpdatedAt.value) return '-'
  const dt = lastUpdatedAt.value
  const pad = (n) => String(n).padStart(2, '0')
  return `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())} ${pad(dt.getHours())}:${pad(dt.getMinutes())}:${pad(dt.getSeconds())}`
})

function unwrap(resp, fallbackMessage) {
  if (!resp || Number(resp.code) !== 0) {
    throw new Error(resp?.message || fallbackMessage)
  }
  return resp.data || {}
}

function formatNumber(value) {
  return new Intl.NumberFormat('zh-CN').format(Number(value || 0))
}

function formatRate(value) {
  return Number(value || 0).toFixed(2)
}

function applyStatsOverview(data) {
  overview.lostFoundTotal = Number(data.lostFoundTotal || 0)
  overview.lostFoundToday = Number(data.lostFoundToday || 0)
  overview.lostFoundRecovered = Number(data.lostFoundRecovered || 0)
  overview.lostFoundRecoveryRate = Number(data.lostFoundRecoveryRate || 0)
  overview.activityTotal = Number(data.activityTotal || 0)
  overview.activityToday = Number(data.activityToday || 0)
  overview.activityApplyTotal = Number(data.activityApplyTotal || 0)
  overview.activityApplyToday = Number(data.activityApplyToday || 0)
}

function applyUserOverview(data) {
  userOverview.totalUsers = Number(data.totalUsers || 0)
  userOverview.activeUsers = Number(data.activeUsers || 0)
  userOverview.disabledUsers = Number(data.disabledUsers || 0)
  userOverview.todayNewUsers = Number(data.todayNewUsers || 0)
}

function applyTrends(data) {
  trends.labels = Array.isArray(data.labels) ? data.labels : []
  trends.userRegisterTrend = Array.isArray(data.userRegisterTrend) ? data.userRegisterTrend : []
  trends.lostFoundPublishTrend = Array.isArray(data.lostFoundPublishTrend) ? data.lostFoundPublishTrend : []
  trends.activityApplyTrend = Array.isArray(data.activityApplyTrend) ? data.activityApplyTrend : []
}

async function loadData() {
  loading.value = true
  try {
    const [statsOverviewRes, statsTrendRes, userOverviewRes, activityPendingRes] = await Promise.all([
      getStatsOverview(),
      getStatsTrends({ granularity: TREND_GRANULARITY, size: TREND_SIZE }),
      getAdminUserOverview(),
      pagePendingAuditActivities({ page: 1, size: 1, operatorRole: 'ADMIN' })
    ])

    applyStatsOverview(unwrap(statsOverviewRes, '加载管理员统计失败'))
    applyTrends(unwrap(statsTrendRes, '加载趋势分析失败'))

    applyUserOverview(unwrap(userOverviewRes, '加载用户结构失败'))

    const activityPage = unwrap(activityPendingRes, '加载活动待审核失败')
    pending.activity = Number(activityPage.total || 0)

    lastUpdatedAt.value = new Date()
  } catch (error) {
    ElMessage.error(error.message || '加载管理员分析数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.admin-analytics-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
  padding: 14px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
}

.hero-card {
  background: linear-gradient(135deg, #0f172a 0%, #1d4ed8 52%, #0f766e 100%);
  color: #f8fafc;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: 22px;
}

.subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  opacity: 0.92;
}

.hero-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.summary-row {
  margin: 0 !important;
}

.summary-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  padding: 14px;
}

.summary-card.success {
  border-color: #86efac;
  background: #f0fdf4;
}

.summary-card.warning {
  border-color: #fdba74;
  background: #fff7ed;
}

.summary-card.info {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.summary-card p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.summary-card h3 {
  margin: 8px 0 4px;
  font-size: 24px;
  color: #0f172a;
}

.summary-card span {
  color: #64748b;
  font-size: 12px;
}

.block-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 10px;
  flex-wrap: wrap;
}

.block-title {
  margin: 0;
  font-size: 16px;
}

.block-tip {
  color: #64748b;
  font-size: 12px;
}

.inner-block {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
  background: #f8fafc;
}

.inner-title {
  margin: 0 0 10px;
  font-size: 14px;
  color: #0f172a;
}

.progress-line {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}

.progress-line:last-child {
  margin-bottom: 0;
}

.progress-line span {
  font-size: 12px;
  color: #475569;
}

.action-row {
  margin-top: 12px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 992px) {
  .block-head {
    align-items: flex-start;
  }
}
</style>
