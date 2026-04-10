<template>
  <div class="activity-page">
    <div class="page-card">
      <div class="page-head">
        <div>
          <h2 class="page-title">校园活动</h2>
          <p class="page-subtitle">支持活动发布、报名、报名审核、管理员审批与审批记录追踪。</p>
        </div>
        <div class="head-actions">
          <el-tag type="info" effect="plain">{{ roleText }}</el-tag>
          <el-button type="primary" @click="openPublishDialog">发布活动</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="activity-tabs">
        <el-tab-pane label="活动大厅" name="hall">
          <div class="toolbar">
            <el-input
              v-model="hallQuery.keyword"
              placeholder="搜索活动标题"
              clearable
              style="width: 220px"
              @keyup.enter="loadHall"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select
              v-model="hallQuery.categoryId"
              clearable
              filterable
              placeholder="活动分类"
              style="width: 160px"
            >
              <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <el-select v-model="hallQuery.status" clearable placeholder="活动状态" style="width: 160px">
              <el-option label="待审核" :value="0" />
              <el-option label="报名中" :value="1" />
              <el-option label="已结束" :value="2" />
              <el-option label="已驳回" :value="3" />
            </el-select>
            <el-button @click="resetHallQuery">重置</el-button>
            <el-button type="primary" :loading="hallLoading" @click="loadHall">查询</el-button>
          </div>

          <div v-loading="hallLoading">
            <el-empty v-if="hallRows.length === 0" description="暂无活动" />
            <div v-else class="card-grid">
              <div v-for="row in hallRows" :key="row.id" class="activity-card">
                <div class="card-top">
                  <div class="title-row">
                    <div class="card-title" :title="row.title">{{ row.title }}</div>
                    <el-tag :type="activityStatusType(row.status)" size="small">{{ activityStatusText(row.status) }}</el-tag>
                  </div>
                  <div class="meta-row">分类：{{ categoryName(row.categoryId) }}</div>
                  <div class="meta-row">时间：{{ formatDateTime(row.startTime) }} ~ {{ formatDateTime(row.endTime) }}</div>
                  <div class="meta-row">地点：{{ row.location || '未填写' }}</div>
                  <div class="meta-row">
                    名额：{{ row.currentParticipants || 0 }}/{{ row.maxParticipants || 0 }} ·
                    报名审核：{{ row.applyAuditRequired === 1 ? '需要' : '不需要' }}
                  </div>
                </div>
                <div class="card-actions">
                  <el-button size="small" @click="openDetail(row.id)">详情</el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :disabled="row.status !== 1"
                    :loading="actionLoading[row.id] === 'apply'"
                    @click="submitApply(row)"
                  >
                    报名
                  </el-button>
                  <el-button
                    size="small"
                    type="warning"
                    plain
                    :disabled="row.status !== 1"
                    :loading="actionLoading[row.id] === 'cancel'"
                    @click="submitCancel(row)"
                  >
                    取消报名
                  </el-button>
                </div>
              </div>
            </div>
          </div>

          <div class="pager">
            <el-pagination
              background
              layout="prev, pager, next"
              :total="hallTotal"
              :current-page="hallQuery.page"
              :page-size="hallQuery.size"
              @current-change="onHallPageChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="我的发布" name="mine">
          <div class="toolbar">
            <el-select v-model="mineQuery.status" clearable placeholder="活动状态" style="width: 160px">
              <el-option label="待审核" :value="0" />
              <el-option label="报名中" :value="1" />
              <el-option label="已结束" :value="2" />
              <el-option label="已驳回" :value="3" />
            </el-select>
            <el-button @click="resetMineQuery">重置</el-button>
            <el-button type="primary" :loading="mineLoading" @click="loadMine">查询</el-button>
          </div>

          <el-table v-loading="mineLoading" :data="mineRows" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="title" label="标题" min-width="220" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="activityStatusType(row.status)" size="small">{{ activityStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="报名人数" width="140">
              <template #default="{ row }">
                {{ row.currentParticipants || 0 }}/{{ row.maxParticipants || 0 }}
              </template>
            </el-table-column>
            <el-table-column label="报名审核" width="120">
              <template #default="{ row }">
                {{ row.applyAuditRequired === 1 ? '是' : '否' }}
              </template>
            </el-table-column>
            <el-table-column label="开始时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
                <el-button link type="success" @click="openApplyDrawer(row)">报名管理</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              background
              layout="prev, pager, next"
              :total="mineTotal"
              :current-page="mineQuery.page"
              :page-size="mineQuery.size"
              @current-change="onMinePageChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="审批中心" name="audit">
          <el-empty v-if="!canAudit" description="你当前没有管理员/审核员权限" />
          <template v-else>
            <el-table v-loading="auditLoading" :data="auditRows" stripe>
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column prop="title" label="标题" min-width="220" />
              <el-table-column label="发布人" width="100">
                <template #default="{ row }">#{{ row.userId }}</template>
              </el-table-column>
              <el-table-column label="分类" width="120">
                <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
              </el-table-column>
              <el-table-column label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="220" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
                  <el-button link type="success" @click="approveActivityAudit(row)">通过</el-button>
                  <el-button link type="danger" @click="rejectActivityAudit(row)">驳回</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="pager">
              <el-pagination
                background
                layout="prev, pager, next"
                :total="auditTotal"
                :current-page="auditQuery.page"
                :page-size="auditQuery.size"
                @current-change="onAuditPageChange"
              />
            </div>
          </template>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="publishVisible" title="发布活动" width="720px" align-center>
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发布者">
              <el-input :model-value="currentUserText" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <div class="category-field">
                <el-select v-model="publishForm.categoryId" placeholder="选择活动分类" style="width: 100%">
                  <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
                <el-button link type="primary" @click="categoryVisible = true">新增分类</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="活动标题">
          <el-input v-model="publishForm.title" maxlength="100" show-word-limit placeholder="输入活动标题" />
        </el-form-item>

        <el-form-item label="封面图">
          <single-image-upload v-model="publishForm.coverImage" type="activity" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="活动地点">
              <el-input v-model="publishForm.location" placeholder="例如：图书馆报告厅" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="社团ID">
              <el-input-number v-model="publishForm.clubId" :min="1" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="活动时间">
              <el-date-picker
                v-model="publishForm.timeRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DDTHH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大人数（0 不限）">
              <el-input-number v-model="publishForm.maxParticipants" :min="0" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="报名审核">
              <el-switch v-model="publishForm.applyAuditRequired" inline-prompt active-text="需要" inactive-text="无需" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发布角色">
              <el-select v-model="publishForm.publisherRole" placeholder="可选，默认不传">
                <el-option label="不传" value="" />
                <el-option label="CLUB_ADMIN" value="CLUB_ADMIN" />
                <el-option label="ADMIN" value="ADMIN" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="活动描述">
          <el-input
            v-model="publishForm.description"
            type="textarea"
            :rows="4"
            maxlength="3000"
            show-word-limit
            placeholder="输入活动介绍、流程、注意事项"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishSubmitting" @click="submitPublish">提交发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="categoryVisible" title="新增活动分类" width="420px" align-center>
      <el-form label-position="top">
        <el-form-item label="分类名称">
          <el-input v-model="categoryNameInput" maxlength="30" show-word-limit placeholder="例如：创新创业" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryVisible = false">取消</el-button>
        <el-button type="primary" :loading="categorySubmitting" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="活动详情" size="520px" destroy-on-close>
      <div v-loading="detailLoading">
        <el-empty v-if="!detailData" description="暂无详情数据" />
        <template v-else>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="活动ID">#{{ detailData.id }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ detailData.title }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="activityStatusType(detailData.status)">{{ activityStatusText(detailData.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="分类">{{ categoryName(detailData.categoryId) }}</el-descriptions-item>
            <el-descriptions-item label="发布者">#{{ detailData.userId }}</el-descriptions-item>
            <el-descriptions-item label="时间">
              {{ formatDateTime(detailData.startTime) }} ~ {{ formatDateTime(detailData.endTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="地点">{{ detailData.location || '-' }}</el-descriptions-item>
            <el-descriptions-item label="人数">
              {{ detailData.currentParticipants || 0 }}/{{ detailData.maxParticipants || 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="报名审核">
              {{ detailData.applyAuditRequired === 1 ? '需要审核' : '无需审核' }}
            </el-descriptions-item>
            <el-descriptions-item label="描述">
              <div class="desc-content">{{ detailData.description || '暂无描述' }}</div>
            </el-descriptions-item>
          </el-descriptions>

          <div class="audit-record-block">
            <div class="block-title">审批记录</div>
            <el-empty v-if="detailAudits.length === 0" description="暂无审批记录" :image-size="60" />
            <el-timeline v-else>
              <el-timeline-item
                v-for="item in detailAudits"
                :key="item.id"
                :timestamp="formatDateTime(item.auditTime)"
                :type="item.status === 1 ? 'success' : 'danger'"
              >
                审核人 #{{ item.auditorId }}：
                {{ item.status === 1 ? '通过' : '驳回' }}
                <span v-if="item.reason">（原因：{{ item.reason }}）</span>
              </el-timeline-item>
            </el-timeline>
          </div>
        </template>
      </div>
    </el-drawer>

    <el-drawer
      v-model="applyDrawerVisible"
      :title="`报名管理 - ${selectedActivity?.title || ''}`"
      size="720px"
      destroy-on-close
    >
      <div class="toolbar">
        <el-select v-model="applyQuery.status" clearable placeholder="报名状态" style="width: 160px">
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已拒绝" :value="2" />
          <el-option label="已取消" :value="3" />
        </el-select>
        <el-button @click="resetApplyQuery">重置</el-button>
        <el-button type="primary" :loading="applyLoading" @click="loadApplyList">查询</el-button>
      </div>

      <el-table v-loading="applyLoading" :data="applyRows" stripe>
        <el-table-column prop="id" label="报名ID" width="90" />
        <el-table-column label="用户" width="100">
          <template #default="{ row }">#{{ row.userId }}</template>
        </el-table-column>
        <el-table-column label="报名时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.applyTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="applyStatusType(row.status)" size="small">{{ applyStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="180">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              link
              type="success"
              :loading="applyActionLoading[row.id] === 'approve'"
              @click="approveApply(row)"
            >
              通过
            </el-button>
            <el-button
              v-if="row.status === 0"
              link
              type="danger"
              :loading="applyActionLoading[row.id] === 'reject'"
              @click="rejectApply(row)"
            >
              拒绝
            </el-button>
            <span v-if="row.status !== 0" class="muted">已处理</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="applyTotal"
          :current-page="applyQuery.page"
          :page-size="applyQuery.size"
          @current-change="onApplyPageChange"
        />
      </div>
    </el-drawer>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import SingleImageUpload from '../components/upload/SingleImageUpload.vue'
import {
  applyActivity,
  auditActivity,
  cancelActivityApply,
  createActivity,
  createActivityCategory,
  fetchActivityDetail,
  listActivityAuditRecords,
  listActivityCategories,
  pageActivities,
  pageActivityApplies,
  pagePendingAuditActivities,
  reviewActivityApply
} from '../api/activity'
import { fetchUserRoles } from '../api/user'
import { getUser } from '../utils/auth'

const activeTab = ref('hall')
const currentUser = ref(getUser() || {})
const currentUserId = computed(() => Number(currentUser.value?.id || 0))
const currentUserText = computed(() => (currentUserId.value ? `#${currentUserId.value}` : '未登录'))

const roles = ref([])
const canAudit = computed(() => roles.value.includes('ADMIN') || roles.value.includes('AUDITOR'))
const roleText = computed(() => {
  if (!roles.value.length) return '角色：USER'
  return `角色：${roles.value.join(' / ')}`
})

const hallLoading = ref(false)
const hallRows = ref([])
const hallTotal = ref(0)
const hallQuery = reactive({
  page: 1,
  size: 9,
  keyword: '',
  categoryId: null,
  status: null
})

const mineLoading = ref(false)
const mineRows = ref([])
const mineTotal = ref(0)
const mineQuery = reactive({
  page: 1,
  size: 10,
  status: null
})

const auditLoading = ref(false)
const auditRows = ref([])
const auditTotal = ref(0)
const auditQuery = reactive({
  page: 1,
  size: 10
})

const actionLoading = reactive({})
const applyActionLoading = reactive({})

const categories = ref([])
const categoryMap = computed(() => {
  const map = {}
  for (const item of categories.value) {
    map[item.id] = item.name
  }
  return map
})

const publishVisible = ref(false)
const publishSubmitting = ref(false)
const publishForm = reactive({
  title: '',
  categoryId: null,
  coverImage: '',
  description: '',
  location: '',
  timeRange: [],
  maxParticipants: 100,
  applyAuditRequired: false,
  clubId: null,
  publisherRole: ''
})

const categoryVisible = ref(false)
const categorySubmitting = ref(false)
const categoryNameInput = ref('')

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref(null)
const detailAudits = ref([])

const applyDrawerVisible = ref(false)
const selectedActivity = ref(null)
const applyLoading = ref(false)
const applyRows = ref([])
const applyTotal = ref(0)
const applyQuery = reactive({
  page: 1,
  size: 10,
  status: null
})

const checkResp = (resp, fallbackMessage) => {
  if (!resp || Number(resp.code) !== 0) {
    throw new Error(resp?.message || fallbackMessage)
  }
  return resp.data
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const categoryName = (id) => {
  if (!id) return '未分类'
  return categoryMap.value[id] || `分类#${id}`
}

const activityStatusText = (status) => {
  const map = {
    0: '待审核',
    1: '报名中',
    2: '已结束',
    3: '已驳回'
  }
  return map[status] ?? String(status)
}

const activityStatusType = (status) => {
  const map = {
    0: 'warning',
    1: 'success',
    2: 'info',
    3: 'danger'
  }
  return map[status] || 'info'
}

const applyStatusText = (status) => {
  const map = {
    0: '待审核',
    1: '已通过',
    2: '已拒绝',
    3: '已取消'
  }
  return map[status] ?? String(status)
}

const applyStatusType = (status) => {
  const map = {
    0: 'warning',
    1: 'success',
    2: 'danger',
    3: 'info'
  }
  return map[status] || 'info'
}

const loadRoles = async () => {
  try {
    const data = checkResp(await fetchUserRoles(), '获取角色失败')
    roles.value = Array.isArray(data?.roles) ? data.roles : []
  } catch {
    roles.value = []
  }
}

const loadCategories = async () => {
  try {
    const data = checkResp(await listActivityCategories(), '加载分类失败')
    categories.value = Array.isArray(data) ? data : []
  } catch (error) {
    ElMessage.error({ message: error.message || '加载分类失败', duration: 1600 })
  }
}

const loadHall = async () => {
  hallLoading.value = true
  try {
    const data = checkResp(await pageActivities({
      page: hallQuery.page,
      size: hallQuery.size,
      keyword: hallQuery.keyword || undefined,
      categoryId: hallQuery.categoryId ?? undefined,
      status: hallQuery.status ?? undefined
    }), '加载活动列表失败')
    hallRows.value = data.records || []
    hallTotal.value = Number(data.total || 0)
  } catch (error) {
    ElMessage.error({ message: error.message || '加载活动列表失败', duration: 1600 })
  } finally {
    hallLoading.value = false
  }
}

const loadMine = async () => {
  if (!currentUserId.value) {
    mineRows.value = []
    mineTotal.value = 0
    return
  }
  mineLoading.value = true
  try {
    const data = checkResp(await pageActivities({
      page: mineQuery.page,
      size: mineQuery.size,
      status: mineQuery.status ?? undefined,
      publisherUserId: currentUserId.value
    }), '加载我的发布失败')
    mineRows.value = data.records || []
    mineTotal.value = Number(data.total || 0)
  } catch (error) {
    ElMessage.error({ message: error.message || '加载我的发布失败', duration: 1600 })
  } finally {
    mineLoading.value = false
  }
}

const loadAuditQueue = async () => {
  if (!canAudit.value) {
    auditRows.value = []
    auditTotal.value = 0
    return
  }
  auditLoading.value = true
  try {
    const data = checkResp(await pagePendingAuditActivities({
      page: auditQuery.page,
      size: auditQuery.size
    }), '加载待审批活动失败')
    auditRows.value = data.records || []
    auditTotal.value = Number(data.total || 0)
  } catch (error) {
    ElMessage.error({ message: error.message || '加载待审批活动失败', duration: 1600 })
  } finally {
    auditLoading.value = false
  }
}

const resetHallQuery = () => {
  hallQuery.page = 1
  hallQuery.keyword = ''
  hallQuery.categoryId = null
  hallQuery.status = null
  loadHall()
}

const onHallPageChange = (page) => {
  hallQuery.page = page
  loadHall()
}

const resetMineQuery = () => {
  mineQuery.page = 1
  mineQuery.status = null
  loadMine()
}

const onMinePageChange = (page) => {
  mineQuery.page = page
  loadMine()
}

const onAuditPageChange = (page) => {
  auditQuery.page = page
  loadAuditQueue()
}

const openPublishDialog = () => {
  if (!currentUserId.value) {
    ElMessage.warning({ message: '请先登录后再发布活动', duration: 1600 })
    return
  }
  publishForm.title = ''
  publishForm.categoryId = categories.value[0]?.id || null
  publishForm.coverImage = ''
  publishForm.description = ''
  publishForm.location = ''
  publishForm.timeRange = []
  publishForm.maxParticipants = 100
  publishForm.applyAuditRequired = false
  publishForm.clubId = null
  publishForm.publisherRole = canAudit.value ? 'ADMIN' : ''
  publishVisible.value = true
}

const submitPublish = async () => {
  if (!publishForm.title.trim()) {
    ElMessage.warning({ message: '活动标题不能为空', duration: 1400 })
    return
  }
  if (!publishForm.timeRange || publishForm.timeRange.length !== 2) {
    ElMessage.warning({ message: '请选择活动开始和结束时间', duration: 1400 })
    return
  }
  publishSubmitting.value = true
  try {
    checkResp(await createActivity({
      title: publishForm.title.trim(),
      categoryId: publishForm.categoryId || null,
      coverImage: publishForm.coverImage || '',
      description: publishForm.description.trim(),
      location: publishForm.location.trim(),
      startTime: publishForm.timeRange[0],
      endTime: publishForm.timeRange[1],
      maxParticipants: Number(publishForm.maxParticipants || 0),
      applyAuditRequired: Boolean(publishForm.applyAuditRequired),
      clubId: publishForm.clubId || null,
      userId: currentUserId.value,
      publisherRole: publishForm.publisherRole || null
    }), '发布活动失败')
    ElMessage.success({ message: '发布成功，活动已进入待审核', duration: 1600 })
    publishVisible.value = false
    activeTab.value = 'mine'
    mineQuery.page = 1
    await Promise.all([loadMine(), loadHall()])
  } catch (error) {
    ElMessage.error({ message: error.message || '发布活动失败', duration: 1800 })
  } finally {
    publishSubmitting.value = false
  }
}

const submitCategory = async () => {
  const name = categoryNameInput.value.trim()
  if (!name) {
    ElMessage.warning({ message: '分类名称不能为空', duration: 1400 })
    return
  }
  categorySubmitting.value = true
  try {
    const data = checkResp(await createActivityCategory({ name }), '新增分类失败')
    categoryVisible.value = false
    categoryNameInput.value = ''
    await loadCategories()
    if (data?.id) publishForm.categoryId = data.id
    ElMessage.success({ message: '分类已新增', duration: 1400 })
  } catch (error) {
    ElMessage.error({ message: error.message || '新增分类失败', duration: 1600 })
  } finally {
    categorySubmitting.value = false
  }
}
const openDetail = async (activityId) => {
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null
  detailAudits.value = []
  try {
    const [detailRes, auditRes] = await Promise.all([
      fetchActivityDetail(activityId),
      listActivityAuditRecords(activityId)
    ])
    detailData.value = checkResp(detailRes, '加载活动详情失败')
    detailAudits.value = checkResp(auditRes, '加载审批记录失败') || []
  } catch (error) {
    ElMessage.error({ message: error.message || '加载详情失败', duration: 1600 })
  } finally {
    detailLoading.value = false
  }
}

const setActionLoading = (activityId, action, loading) => {
  if (!actionLoading[activityId]) actionLoading[activityId] = ''
  actionLoading[activityId] = loading ? action : ''
}

const submitApply = async (row) => {
  if (!currentUserId.value) {
    ElMessage.warning({ message: '请先登录后再报名', duration: 1400 })
    return
  }
  setActionLoading(row.id, 'apply', true)
  try {
    const data = checkResp(await applyActivity(row.id, { userId: currentUserId.value }), '报名失败')
    if (Number(data?.status) === 0) {
      ElMessage.success({ message: '报名成功，等待发布者审核', duration: 1700 })
    } else {
      ElMessage.success({ message: '报名成功', duration: 1400 })
    }
    await Promise.all([loadHall(), activeTab.value === 'mine' ? loadMine() : Promise.resolve()])
  } catch (error) {
    ElMessage.error({ message: error.message || '报名失败', duration: 1800 })
  } finally {
    setActionLoading(row.id, 'apply', false)
  }
}

const submitCancel = async (row) => {
  if (!currentUserId.value) {
    ElMessage.warning({ message: '请先登录后再取消报名', duration: 1400 })
    return
  }
  setActionLoading(row.id, 'cancel', true)
  try {
    checkResp(await cancelActivityApply(row.id, { userId: currentUserId.value }), '取消报名失败')
    ElMessage.success({ message: '已取消报名', duration: 1400 })
    await Promise.all([loadHall(), activeTab.value === 'mine' ? loadMine() : Promise.resolve()])
  } catch (error) {
    ElMessage.error({ message: error.message || '取消报名失败', duration: 1800 })
  } finally {
    setActionLoading(row.id, 'cancel', false)
  }
}

const openApplyDrawer = async (row) => {
  selectedActivity.value = row
  applyDrawerVisible.value = true
  applyQuery.page = 1
  applyQuery.status = null
  await loadApplyList()
}

const loadApplyList = async () => {
  if (!selectedActivity.value?.id) return
  applyLoading.value = true
  try {
    const data = checkResp(await pageActivityApplies(selectedActivity.value.id, {
      operatorUserId: currentUserId.value,
      status: applyQuery.status ?? undefined,
      page: applyQuery.page,
      size: applyQuery.size
    }), '加载报名列表失败')
    applyRows.value = data.records || []
    applyTotal.value = Number(data.total || 0)
  } catch (error) {
    ElMessage.error({ message: error.message || '加载报名列表失败', duration: 1600 })
  } finally {
    applyLoading.value = false
  }
}

const resetApplyQuery = () => {
  applyQuery.page = 1
  applyQuery.status = null
  loadApplyList()
}

const onApplyPageChange = (page) => {
  applyQuery.page = page
  loadApplyList()
}

const setApplyActionLoading = (applyId, action, loading) => {
  if (!applyActionLoading[applyId]) applyActionLoading[applyId] = ''
  applyActionLoading[applyId] = loading ? action : ''
}

const approveApply = async (row) => {
  setApplyActionLoading(row.id, 'approve', true)
  try {
    checkResp(await reviewActivityApply(selectedActivity.value.id, row.id, {
      operatorUserId: currentUserId.value,
      status: 1,
      reason: ''
    }), '报名审核失败')
    ElMessage.success({ message: '已通过该报名', duration: 1400 })
    await Promise.all([loadApplyList(), loadMine(), loadHall()])
  } catch (error) {
    ElMessage.error({ message: error.message || '报名审核失败', duration: 1800 })
  } finally {
    setApplyActionLoading(row.id, 'approve', false)
  }
}

const rejectApply = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝报名', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：名额已满、信息不完整',
      inputValidator: (input) => (input && input.trim() ? true : '请输入拒绝原因')
    })
    setApplyActionLoading(row.id, 'reject', true)
    checkResp(await reviewActivityApply(selectedActivity.value.id, row.id, {
      operatorUserId: currentUserId.value,
      status: 2,
      reason: value.trim()
    }), '拒绝报名失败')
    ElMessage.success({ message: '已拒绝该报名', duration: 1400 })
    await Promise.all([loadApplyList(), loadMine(), loadHall()])
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error({ message: error.message || '拒绝报名失败', duration: 1800 })
    }
  } finally {
    setApplyActionLoading(row.id, 'reject', false)
  }
}

const approveActivityAudit = async (row) => {
  try {
    checkResp(await auditActivity(row.id, {
      auditorId: currentUserId.value,
      status: 1,
      reason: ''
    }), '活动审批失败')
    ElMessage.success({ message: '审批通过', duration: 1400 })
    await Promise.all([loadAuditQueue(), loadHall(), loadMine()])
  } catch (error) {
    ElMessage.error({ message: error.message || '活动审批失败', duration: 1800 })
  }
}

const rejectActivityAudit = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回活动', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：时间信息不完整',
      inputValidator: (input) => (input && input.trim() ? true : '请输入驳回原因')
    })
    checkResp(await auditActivity(row.id, {
      auditorId: currentUserId.value,
      status: 2,
      reason: value.trim()
    }), '活动驳回失败')
    ElMessage.success({ message: '已驳回活动', duration: 1400 })
    await Promise.all([loadAuditQueue(), loadHall(), loadMine()])
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error({ message: error.message || '活动驳回失败', duration: 1800 })
    }
  }
}

watch(activeTab, (tab) => {
  if (tab === 'hall') {
    loadHall()
    return
  }
  if (tab === 'mine') {
    loadMine()
    return
  }
  if (tab === 'audit') {
    loadAuditQueue()
  }
})

onMounted(async () => {
  await Promise.all([loadRoles(), loadCategories()])
  await loadHall()
})
</script>

<style scoped>
.activity-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.page-subtitle {
  margin: -8px 0 0;
  color: #64748b;
  font-size: 13px;
}

.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.activity-tabs :deep(.el-tabs__content) {
  padding-top: 8px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.activity-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 14px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.title-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta-row {
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
}

.card-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.pager {
  margin-top: 18px;
  display: flex;
  justify-content: center;
}

.category-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.desc-content {
  white-space: pre-wrap;
  line-height: 1.6;
}

.audit-record-block {
  margin-top: 16px;
}

.block-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.muted {
  color: #94a3b8;
}

@media (max-width: 768px) {
  .card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
