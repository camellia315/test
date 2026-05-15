<template>
  <div class="page-card">
    <div class="page-head">
      <div>
        <h2 class="page-title">消息通知</h2>
        <p class="page-subtitle">系统通知、交易消息、活动消息统一查看。</p>
      </div>
      <div class="head-actions">
        <el-tag type="danger" effect="dark">未读 {{ unreadCount }}</el-tag>
        <el-button :loading="loading" @click="loadData">刷新</el-button>
        <el-button type="primary" plain :disabled="unreadCount === 0" @click="readAll">全部已读</el-button>
        <el-button v-if="isAdmin" type="primary" @click="openPublishDialog">发布系统通知</el-button>
      </div>
    </div>

    <div class="toolbar">
      <el-select v-model="query.type" clearable placeholder="消息类型" style="width: 160px">
        <el-option label="系统通知" :value="1" />
        <el-option label="交易消息" :value="2" />
        <el-option label="活动消息" :value="3" />
        <el-option label="评论消息" :value="4" />
        <el-option label="聊天消息" :value="5" />
      </el-select>
      <el-select v-model="query.isRead" clearable placeholder="阅读状态" style="width: 140px">
        <el-option label="未读" :value="0" />
        <el-option label="已读" :value="1" />
      </el-select>
      <el-button type="primary" :loading="loading" @click="loadList(1)">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ typeText(row.type) }}</template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="Number(row.isRead) === 1 ? 'success' : 'warning'">
            {{ Number(row.isRead) === 1 ? '已读' : '未读' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-if="Number(row.isRead) !== 1" link type="primary" @click="readOne(row)">标记已读</el-button>
          <el-button v-if="row.linkUrl" link type="success" @click="openLink(row.linkUrl)">前往</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :current-page="query.page"
        :page-size="query.size"
        @current-change="loadList"
      />
    </div>

    <el-dialog v-model="publishVisible" title="发布系统通知" width="520px" align-center>
      <el-form label-position="top">
        <el-form-item label="目标用户ID（留空=全体启用用户）">
          <el-input-number v-model="publishForm.targetUserId" :min="1" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="publishForm.title" maxlength="100" show-word-limit placeholder="例如：系统维护通知" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="publishForm.content"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请输入通知内容"
          />
        </el-form-item>
        <el-form-item label="跳转链接（可选）">
          <el-input v-model="publishForm.linkUrl" placeholder="/activities 或 /market" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishLoading" @click="submitPublish">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  pageNotifications,
  publishSystemNotification
} from '../api/user'
import { getRoles } from '../utils/auth'

const loading = ref(false)
const publishLoading = ref(false)
const unreadCount = ref(0)
const rows = ref([])
const total = ref(0)
const isAdmin = computed(() => getRoles().includes('ADMIN'))

const query = reactive({
  page: 1,
  size: 10,
  type: undefined,
  isRead: undefined
})

const publishVisible = ref(false)
const publishForm = reactive({
  targetUserId: null,
  title: '',
  content: '',
  linkUrl: ''
})

const checkResp = (resp, message) => {
  if (!resp || Number(resp.code) !== 0) {
    throw new Error(resp?.message || message)
  }
  return resp.data || {}
}

const typeText = (type) => {
  const map = {
    1: '系统',
    2: '交易',
    3: '活动',
    4: '评论',
    5: '私信'
  }
  return map[Number(type)] || `类型${type}`
}

const formatTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const loadUnread = async () => {
  try {
    const data = checkResp(await fetchUnreadNotificationCount(), '加载未读数失败')
    unreadCount.value = Number(data.unreadCount || 0)
  } catch {
    unreadCount.value = 0
  }
}

const loadList = async (page = query.page) => {
  query.page = page
  loading.value = true
  try {
    const data = checkResp(await pageNotifications({
      page: query.page,
      size: query.size,
      type: query.type,
      isRead: query.isRead
    }), '加载消息列表失败')
    rows.value = Array.isArray(data.records) ? data.records : []
    total.value = Number(data.total || 0)
  } catch (error) {
    ElMessage.error(error.message || '加载消息列表失败')
  } finally {
    loading.value = false
  }
}

const loadData = async () => {
  await Promise.all([loadUnread(), loadList(1)])
}

const readOne = async (row) => {
  try {
    checkResp(await markNotificationRead(row.id), '标记已读失败')
    await loadData()
  } catch (error) {
    ElMessage.error(error.message || '标记已读失败')
  }
}

const readAll = async () => {
  try {
    checkResp(await markAllNotificationsRead(), '全部已读失败')
    ElMessage.success('已全部标记为已读')
    await loadData()
  } catch (error) {
    ElMessage.error(error.message || '全部已读失败')
  }
}

const resetQuery = () => {
  query.type = undefined
  query.isRead = undefined
  loadList(1)
}

const openLink = (url) => {
  if (!url) return
  if (url.startsWith('http://') || url.startsWith('https://')) {
    window.open(url, '_blank')
    return
  }
  window.location.href = url
}

const openPublishDialog = () => {
  publishForm.targetUserId = null
  publishForm.title = ''
  publishForm.content = ''
  publishForm.linkUrl = ''
  publishVisible.value = true
}

const submitPublish = async () => {
  if (!publishForm.title.trim() || !publishForm.content.trim()) {
    ElMessage.warning('标题和内容不能为空')
    return
  }
  publishLoading.value = true
  try {
    const data = checkResp(await publishSystemNotification({
      targetUserId: publishForm.targetUserId || null,
      title: publishForm.title.trim(),
      content: publishForm.content.trim(),
      linkUrl: publishForm.linkUrl.trim() || null
    }), '发布通知失败')
    ElMessage.success(`发布成功，已发送 ${data.sentCount || 0} 条`)
    publishVisible.value = false
    await loadData()
  } catch (error) {
    ElMessage.error(error.message || '发布通知失败')
  } finally {
    publishLoading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.page-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.page-subtitle {
  margin: -8px 0 0;
  font-size: 13px;
  color: #64748b;
}

.head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: center;
}
</style>
