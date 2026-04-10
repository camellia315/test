<template>
  <div class="stats-page">
    <section class="hero-card">
      <div class="hero-text">
        <h2>校园平台数据大屏</h2>
        <p>聚合用户、失物招领、活动报名核心指标，支持趋势分析和排行榜导出。</p>
      </div>
      <div class="hero-actions">
        <el-select v-model="granularity" style="width: 110px">
          <el-option label="按天" value="DAY" />
          <el-option label="按周" value="WEEK" />
          <el-option label="按月" value="MONTH" />
        </el-select>
        <el-select v-model="size" style="width: 110px">
          <el-option v-for="item in sizeOptions" :key="item" :label="`${item}期`" :value="item" />
        </el-select>
        <el-button :loading="loading" type="primary" @click="loadDashboard">刷新数据</el-button>
        <el-button :loading="exportLoading" plain @click="handleExport">导出Excel</el-button>
      </div>
    </section>

    <el-row :gutter="16" class="metric-row">
      <el-col v-for="card in metricCards" :key="card.label" :xs="24" :sm="12" :md="6">
        <div class="metric-card">
          <p class="metric-label">{{ card.label }}</p>
          <p class="metric-value">{{ card.value }}</p>
          <p class="metric-sub">{{ card.sub }}</p>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="16">
        <div class="chart-card">
          <div class="card-head">
            <h3>趋势分析</h3>
            <span>{{ trendTitle }}</span>
          </div>
          <div ref="trendChartRef" class="chart-canvas"></div>
        </div>
      </el-col>
      <el-col :xs="24" :lg="8">
        <div class="chart-card">
          <div class="card-head">
            <h3>失物找回率</h3>
            <span>实时汇总</span>
          </div>
          <div ref="recoveryChartRef" class="chart-canvas"></div>
        </div>
      </el-col>
    </el-row>

    <div class="chart-card">
      <div class="card-head">
        <h3>热门活动 TOP10（按报名量）</h3>
        <span>{{ lastUpdatedText }}</span>
      </div>
      <div ref="activityChartRef" class="chart-canvas chart-short"></div>
    </div>

    <el-row :gutter="16" class="ranking-row">
      <el-col :xs="24" :lg="8">
        <div class="ranking-card">
          <h3>热心用户 TOP10</h3>
          <el-table :data="rankings.helpfulUsers" size="small" stripe>
            <el-table-column prop="rank" label="#" width="56" />
            <el-table-column prop="name" label="用户" min-width="120" />
            <el-table-column prop="total" label="发布数" width="92" />
          </el-table>
        </div>
      </el-col>
      <el-col :xs="24" :lg="8">
        <div class="ranking-card">
          <h3>热门活动 TOP10</h3>
          <el-table :data="rankings.hotActivities" size="small" stripe>
            <el-table-column prop="rank" label="#" width="56" />
            <el-table-column prop="title" label="活动" min-width="120" />
            <el-table-column prop="total" label="报名" width="92" />
          </el-table>
        </div>
      </el-col>
      <el-col :xs="24" :lg="8">
        <div class="ranking-card">
          <h3>活跃社团排行</h3>
          <el-table :data="rankings.activeClubs" size="small" stripe>
            <el-table-column prop="rank" label="#" width="56" />
            <el-table-column prop="name" label="社团" min-width="110" />
            <el-table-column prop="activityTotal" label="活动数" width="92" />
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { exportStatsReport, getStatsDashboard } from '../api/stats'

const loading = ref(false)
const exportLoading = ref(false)

const granularity = ref('DAY')
const size = ref(14)
const sizeOptionsMap = {
  DAY: [7, 14, 30],
  WEEK: [8, 12, 24],
  MONTH: [6, 12, 24]
}

const overview = reactive({
  userTotal: 0,
  todayNewUsers: 0,
  lostFoundTotal: 0,
  lostFoundToday: 0,
  lostFoundRecovered: 0,
  lostFoundRecoveryRate: 0,
  activityTotal: 0,
  activityToday: 0,
  activityApplyTotal: 0,
  activityApplyToday: 0
})

const trends = reactive({
  labels: [],
  userRegisterTrend: [],
  lostFoundPublishTrend: [],
  activityApplyTrend: []
})

const rankings = reactive({
  helpfulUsers: [],
  hotActivities: [],
  activeClubs: []
})

const trendChartRef = ref(null)
const recoveryChartRef = ref(null)
const activityChartRef = ref(null)
let trendChart = null
let recoveryChart = null
let activityChart = null

const lastUpdatedAt = ref(null)

const sizeOptions = computed(() => sizeOptionsMap[granularity.value] || [14])

watch(granularity, () => {
  if (!sizeOptions.value.includes(size.value)) {
    size.value = sizeOptions.value[0]
  }
})

const metricCards = computed(() => ([
  {
    label: '平台用户总数',
    value: formatNumber(overview.userTotal),
    sub: `今日新增 ${formatNumber(overview.todayNewUsers)}`
  },
  {
    label: '失物招领发布',
    value: formatNumber(overview.lostFoundTotal),
    sub: `今日发布 ${formatNumber(overview.lostFoundToday)}`
  },
  {
    label: '失物找回率',
    value: `${Number(overview.lostFoundRecoveryRate || 0).toFixed(2)}%`,
    sub: `累计找回 ${formatNumber(overview.lostFoundRecovered)}`
  },
  {
    label: '活动总量',
    value: formatNumber(overview.activityTotal),
    sub: `今日新增 ${formatNumber(overview.activityToday)}`
  },
  {
    label: '活动报名总数',
    value: formatNumber(overview.activityApplyTotal),
    sub: `今日报名 ${formatNumber(overview.activityApplyToday)}`
  },
  {
    label: '趋势粒度',
    value: granularityLabel(granularity.value),
    sub: `窗口长度 ${size.value} 期`
  }
]))

const trendTitle = computed(() => `${granularityLabel(granularity.value)}趋势（最近 ${size.value} 期）`)

const lastUpdatedText = computed(() => {
  if (!lastUpdatedAt.value) return '尚未加载'
  const dt = lastUpdatedAt.value
  const pad = (n) => String(n).padStart(2, '0')
  return `更新于 ${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())} ${pad(dt.getHours())}:${pad(dt.getMinutes())}:${pad(dt.getSeconds())}`
})

function unwrap(payload, fallbackMessage) {
  if (!payload || Number(payload.code) !== 0) {
    throw new Error(payload?.message || fallbackMessage)
  }
  return payload.data || {}
}

function formatNumber(value) {
  return new Intl.NumberFormat('zh-CN').format(Number(value || 0))
}

function granularityLabel(value) {
  if (value === 'WEEK') return '按周'
  if (value === 'MONTH') return '按月'
  return '按天'
}

function applyOverview(data) {
  overview.userTotal = Number(data.userTotal || 0)
  overview.todayNewUsers = Number(data.todayNewUsers || 0)
  overview.lostFoundTotal = Number(data.lostFoundTotal || 0)
  overview.lostFoundToday = Number(data.lostFoundToday || 0)
  overview.lostFoundRecovered = Number(data.lostFoundRecovered || 0)
  overview.lostFoundRecoveryRate = Number(data.lostFoundRecoveryRate || 0)
  overview.activityTotal = Number(data.activityTotal || 0)
  overview.activityToday = Number(data.activityToday || 0)
  overview.activityApplyTotal = Number(data.activityApplyTotal || 0)
  overview.activityApplyToday = Number(data.activityApplyToday || 0)
}

function applyTrends(data) {
  trends.labels = Array.isArray(data.labels) ? data.labels : []
  trends.userRegisterTrend = Array.isArray(data.userRegisterTrend) ? data.userRegisterTrend : []
  trends.lostFoundPublishTrend = Array.isArray(data.lostFoundPublishTrend) ? data.lostFoundPublishTrend : []
  trends.activityApplyTrend = Array.isArray(data.activityApplyTrend) ? data.activityApplyTrend : []
}

function applyRankings(data) {
  rankings.helpfulUsers = Array.isArray(data.helpfulUsers) ? data.helpfulUsers : []
  rankings.hotActivities = Array.isArray(data.hotActivities) ? data.hotActivities : []
  rankings.activeClubs = Array.isArray(data.activeClubs) ? data.activeClubs : []
}

function ensureChart(chart, domRef) {
  if (!domRef?.value) return chart
  if (chart) return chart
  return echarts.init(domRef.value)
}

function renderTrendChart() {
  trendChart = ensureChart(trendChart, trendChartRef)
  if (!trendChart) return

  trendChart.setOption({
    color: ['#2563eb', '#f97316', '#14b8a6'],
    tooltip: { trigger: 'axis' },
    legend: { data: ['用户注册', '失物发布', '活动报名'] },
    grid: { left: 44, right: 20, top: 42, bottom: 36 },
    xAxis: {
      type: 'category',
      data: trends.labels
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '用户注册',
        type: 'line',
        smooth: true,
        data: trends.userRegisterTrend,
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '失物发布',
        type: 'line',
        smooth: true,
        data: trends.lostFoundPublishTrend,
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '活动报名',
        type: 'line',
        smooth: true,
        data: trends.activityApplyTrend,
        areaStyle: { opacity: 0.12 }
      }
    ]
  })
}

function renderRecoveryChart() {
  recoveryChart = ensureChart(recoveryChart, recoveryChartRef)
  if (!recoveryChart) return

  const recovered = Number(overview.lostFoundRecovered || 0)
  const total = Number(overview.lostFoundTotal || 0)
  const unrecovered = Math.max(total - recovered, 0)
  const percent = Number(overview.lostFoundRecoveryRate || 0)

  recoveryChart.setOption({
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'gauge',
        startAngle: 200,
        endAngle: -20,
        radius: '88%',
        center: ['50%', '52%'],
        progress: { show: true, width: 12, itemStyle: { color: '#10b981' } },
        axisLine: { lineStyle: { width: 12, color: [[1, '#dbeafe']] } },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        detail: {
          valueAnimation: true,
          fontSize: 24,
          offsetCenter: [0, '20%'],
          formatter: (value) => `${Number(value).toFixed(2)}%`
        },
        title: {
          offsetCenter: [0, '-12%'],
          fontSize: 12
        },
        data: [{ value: percent, name: '找回率' }]
      },
      {
        type: 'pie',
        radius: ['0%', '0%'],
        data: [
          { value: recovered, name: '已找回' },
          { value: unrecovered, name: '未找回' }
        ]
      }
    ]
  })
}

function renderActivityChart() {
  activityChart = ensureChart(activityChart, activityChartRef)
  if (!activityChart) return

  const names = rankings.hotActivities.map((item) => item.title || `Activity#${item.activityId}`)
  const values = rankings.hotActivities.map((item) => Number(item.total || 0))

  activityChart.setOption({
    color: ['#6366f1'],
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 18, top: 24, bottom: 76 },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: {
        interval: 0,
        rotate: 24
      }
    },
    yAxis: {
      type: 'value',
      minInterval: 1
    },
    series: [
      {
        type: 'bar',
        barWidth: 26,
        data: values,
        itemStyle: { borderRadius: [6, 6, 0, 0] }
      }
    ]
  })
}

function renderCharts() {
  renderTrendChart()
  renderRecoveryChart()
  renderActivityChart()
}

function resizeCharts() {
  trendChart?.resize()
  recoveryChart?.resize()
  activityChart?.resize()
}

async function loadDashboard() {
  loading.value = true
  try {
    const payload = await getStatsDashboard({
      granularity: granularity.value,
      size: size.value
    })
    const data = unwrap(payload, '加载统计数据失败')

    applyOverview(data.overview || {})
    applyTrends(data.trends || {})
    applyRankings(data.rankings || {})

    lastUpdatedAt.value = new Date()

    await nextTick()
    renderCharts()
  } catch (error) {
    ElMessage.error(error.message || '加载统计数据失败')
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  exportLoading.value = true
  try {
    const response = await exportStatsReport({
      granularity: granularity.value,
      size: size.value
    })
    const disposition = response.headers?.['content-disposition'] || ''
    const match = disposition.match(/filename\*=utf-8''([^;]+)|filename="?([^"]+)"?/)
    const fileName = decodeURIComponent(match?.[1] || match?.[2] || `campus-stats-${Date.now()}.xlsx`)

    const blob = new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = window.URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = fileName
    anchor.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error(error.message || '导出失败')
  } finally {
    exportLoading.value = false
  }
}

onMounted(() => {
  loadDashboard()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  trendChart?.dispose()
  recoveryChart?.dispose()
  activityChart?.dispose()
})
</script>

<style scoped>
.stats-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-card {
  background: linear-gradient(135deg, #0f172a 0%, #1d4ed8 52%, #22c55e 100%);
  border-radius: 16px;
  color: #f8fafc;
  padding: 20px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.hero-text h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.hero-text p {
  margin: 8px 0 0;
  color: rgba(248, 250, 252, 0.9);
  font-size: 13px;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.metric-row {
  margin: 0 !important;
}

.metric-card {
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  padding: 16px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
}

.metric-label {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.metric-value {
  margin: 8px 0 4px;
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;
}

.metric-sub {
  margin: 0;
  color: #0ea5e9;
  font-size: 12px;
}

.chart-row,
.ranking-row {
  margin: 0 !important;
}

.chart-card {
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  padding: 14px 14px 8px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 8px;
}

.card-head h3 {
  margin: 0;
  font-size: 15px;
  color: #0f172a;
}

.card-head span {
  color: #64748b;
  font-size: 12px;
}

.chart-canvas {
  width: 100%;
  height: 340px;
}

.chart-short {
  height: 300px;
}

.ranking-card {
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  padding: 14px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
}

.ranking-card h3 {
  margin: 0 0 10px;
  font-size: 15px;
  color: #0f172a;
}

@media (max-width: 992px) {
  .chart-canvas,
  .chart-short {
    height: 280px;
  }
}
</style>

