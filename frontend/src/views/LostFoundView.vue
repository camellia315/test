<template>
  <div class="lost-found-page">
    <div class="page-card">
      <!-- 顶部工具栏 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <h2 class="page-title" style="margin: 0">失物招领</h2>
          <el-tag type="info" round size="small" style="margin-left: 10px">共 {{ displayTotal }} 条信息</el-tag>
          <el-radio-group v-model="activePanel" size="small" style="margin-left: 12px" @change="onPanelChange">
            <el-radio-button label="plaza">广场</el-radio-button>
            <el-radio-button label="history">找回记录</el-radio-button>
          </el-radio-group>
        </div>
        <div class="toolbar-right">
          <!-- 视图切换按钮 -->
          <el-radio-group v-model="viewMode" size="small" style="margin-right: 12px">
            <el-radio-button label="card"><el-icon><Grid /></el-icon> 卡片</el-radio-button>
            <el-radio-button label="table"><el-icon><List /></el-icon> 列表</el-radio-button>
          </el-radio-group>
          <el-button type="primary" icon="Plus" @click="openCreateDialog">发布信息</el-button>
        </div>
      </div>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input 
          v-model="query.keyword" 
          placeholder="搜索标题或描述..." 
          class="search-input" 
          clearable 
          @clear="search"
          @keyup.enter="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        
        <el-select v-model="query.itemType" placeholder="类型" clearable class="filter-select" @change="search">
          <el-option label="全部类型" value="" />
          <el-option label="遗失物品" value="LOST" />
          <el-option label="拾取物品" value="FOUND" />
        </el-select>

        <el-select v-model="query.status" placeholder="状态" clearable class="filter-select" @change="search">
          <el-option label="全部状态" value="" />
          <el-option label="寻找中" value="SEARCHING" />
          <el-option label="已找到" value="FOUND" />
          <el-option label="已归还" value="RETURNED" />
        </el-select>

        <el-button @click="resetQuery" icon="Refresh">重置</el-button>
        <el-button type="primary" @click="search" :loading="loading">查询</el-button>
      </div>

      <!-- 内容展示区：加载状态 -->
      <div v-loading="loading" class="content-area">
        
        <!-- 模式 A: 表格视图 -->
        <el-table v-if="viewMode === 'table'" :data="displayRows" stripe style="width: 100%">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="物品信息" min-width="200">
             <template #default="{ row }">
               <div style="display: flex; align-items: center; gap: 10px">
                 <el-image
                   v-if="row._imagePreviewUrl"
                   :src="row._imagePreviewUrl || normalizeImageUrl(row.imageUrl)"
                   style="width: 40px; height: 40px; border-radius: 4px; flex-shrink: 0"
                   fit="cover"
                   :preview-src-list="row._imagePreviewList || []"
                   preview-teleported
                   @error="handleImageError(row)"
                 >
                   <template #error><div class="img-placeholder">暂无图片</div></template>
                 </el-image>
                 <div v-else class="img-placeholder">暂无图片</div>
                 <div>
                   <div style="font-weight: 500">{{ row.title }}</div>
                   <div style="font-size: 12px; color: #999" class="text-truncate">{{ row.description }}</div>
                 </div>
               </div>
             </template>
          </el-table-column>
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag :type="row.itemType === 'LOST' ? 'danger' : 'success'" effect="light" size="small">
                {{ typeText(row.itemType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
             <template #default="{ row }">
                <el-tag :type="tagType(row.status)" size="small" round>{{ statusText(row.status) }}</el-tag>
             </template>
          </el-table-column>
          <el-table-column prop="locationText" label="地点" width="150" show-overflow-tooltip />
          <el-table-column :label="timeColumnLabel" width="160">
            <template #default="{ row }">{{ displayItemTime(row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" @click="openUserSpace(row.userId)">主页</el-button>
              <el-button link type="primary" size="small" @click="openComments(row)">评论</el-button>
              <el-button v-if="canOpenPrivateChat(row)" link type="success" size="small" @click="openPrivateChat(row)">私信</el-button>
              <el-dropdown v-if="hasMoreOps(row)" trigger="click" style="margin-left: 10px">
                <el-button link size="small">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="canChangeStatus(row)" @click="changeStatus(row, 'FOUND')">标记已找到</el-dropdown-item>
                    <el-dropdown-item v-if="canChangeStatus(row)" @click="changeStatus(row, 'RETURNED')">标记已归还</el-dropdown-item>
                    <el-dropdown-item v-if="canDelete(row)" divided @click="removeItem(row)" style="color: #f56c6c">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>

        <!-- 模式 B: 卡片网格视图 -->
        <div v-else class="card-grid">
           <el-empty v-if="displayRows.length === 0" description="没有找到相关信息" />
           <div v-for="item in displayRows" :key="item.id" class="item-card">
              <div class="card-img-wrapper">
                 <el-image
                   v-if="item._imagePreviewUrl"
                   :src="item._imagePreviewUrl || normalizeImageUrl(item.imageUrl)"
                   loading="lazy"
                   fit="cover"
                   class="card-img"
                   :preview-src-list="item._imagePreviewList || []"
                   preview-teleported
                   @error="handleImageError(item)"
                 >
                   <template #error>
                     <div class="card-img-error">
                        <span>暂无图片</span>
                     </div>
                   </template>
                 </el-image>
                 <div v-else class="card-img-error">
                   <span>暂无图片</span>
                 </div>
                 <div class="card-badges">
                    <el-tag :type="item.itemType === 'LOST' ? 'danger' : 'success'" effect="dark" size="small">
                      {{ typeText(item.itemType) }}
                    </el-tag>
                 </div>
              </div>
              <div class="card-body">
                 <div class="card-title" :title="item.title">{{ item.title }}</div>
                 <div class="card-desc">{{ item.description || '暂无描述' }}</div>
                 
                 <div class="card-meta">
                    <div class="meta-row">
                      <el-icon><Location /></el-icon> <span>{{ item.locationText || '未知地点' }}</span>
                    </div>
                    <div class="meta-row">
                      <el-icon><Timer /></el-icon> <span>{{ displayItemTime(item) }}</span>
                    </div>
                 </div>
              </div>
              <div class="card-footer">
                 <el-tag :type="tagType(item.status)" size="small" plain>{{ statusText(item.status) }}</el-tag>
                 <div class="card-actions">
                    <el-button circle size="small" icon="User" @click="openUserSpace(item.userId)"></el-button>
                    <el-button circle size="small" icon="ChatDotRound" @click="openComments(item)"></el-button>
                    <el-button v-if="canOpenPrivateChat(item)" circle size="small" icon="Message" @click="openPrivateChat(item)"></el-button>
                    <el-dropdown v-if="hasMoreOps(item)" trigger="click">
                      <el-button circle size="small" icon="More"></el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item v-if="canChangeStatus(item)" @click="changeStatus(item, 'FOUND')">标记找到</el-dropdown-item>
                          <el-dropdown-item v-if="canChangeStatus(item)" @click="changeStatus(item, 'RETURNED')">标记归还</el-dropdown-item>
                          <el-dropdown-item v-if="canDelete(item)" divided @click="removeItem(item)" style="color: red">删除</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                 </div>
              </div>
           </div>
        </div>

      </div>

      <!-- 分页 -->
      <div class="pager">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="displayTotal"
          :current-page="query.page"
          :page-size="query.size"
          @current-change="onPageChange"
        />
      </div>
    </div>

    <!-- 发布 Dialog -->
    <el-dialog v-model="createVisible" title="发布信息" width="500px" align-center>
      <el-form label-position="top" label-width="80px">
        <el-row :gutter="20">
            <el-col :span="12">
                <el-form-item label="类型">
                  <el-radio-group v-model="createForm.itemType" class="full-width-radio">
                    <el-radio-button label="LOST">遗失</el-radio-button>
                    <el-radio-button label="FOUND">拾取</el-radio-button>
                  </el-radio-group>
                </el-form-item>
            </el-col>
            <el-col :span="12">
                 <el-form-item label="分类">
                    <el-input-number v-model="createForm.categoryId" :min="1" style="width: 100%" />
                 </el-form-item>
            </el-col>
        </el-row>
        <el-form-item label="发布账号">
          <el-input :model-value="currentUserIdText" disabled />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="createForm.title" placeholder="例如：在食堂捡到黑色钱包" />
        </el-form-item>
        
        <el-form-item label="物品图片">
          <single-image-upload v-model="createForm.imageUrl" type="lost-found" />
        </el-form-item>
        
        <el-form-item label="地点">
            <el-input v-model="createForm.locationText" placeholder="丢失或拾取的大致位置" prefix-icon="Location" />
        </el-form-item>
        
        <el-form-item label="详细描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="提供更多细节有助于快速找回..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">立即发布</el-button>
      </template>
    </el-dialog>

    <!-- 评论 Drawer -->
    <el-drawer v-model="commentVisible" title="互动留言" size="400px" destroy-on-close direction="rtl">
        <div class="comment-container">
            <el-input :model-value="currentUserIdText" disabled style="width: 100%; margin-bottom: 10px" />
            <el-input
                v-model="commentForm.content"
                type="textarea"
                :rows="2"
                placeholder="写下你的线索..."
                class="mb-2"
            />
            <div style="text-align: right; margin-bottom: 20px;">
                <el-button type="primary" size="small" :loading="commentSubmitting" @click="submitComment">发送留言</el-button>
            </div>
            
            <div v-if="comments.length > 0">
                <div class="comment-list">
                    <div
                      v-for="comment in comments"
                      :key="comment.id"
                      class="comment-card"
                    >
                      <el-avatar
                        :size="38"
                        :src="commentAvatarUrl(comment)"
                        class="comment-avatar"
                        @error="handleCommentAvatarError(comment)"
                      >
                        {{ commentAvatarText(comment) }}
                      </el-avatar>
                      <div class="comment-main">
                        <div class="comment-meta">
                          <div class="comment-user-line">
                            <span class="comment-user-id">{{ commentDisplayId(comment) }}</span>
                            <span v-if="comment.commenterUsername" class="comment-username">{{ comment.commenterUsername }}</span>
                          </div>
                          <div class="comment-actions">
                            <span class="comment-time">{{ formatDate(comment.createdAt) }}</span>
                            <el-button
                              v-if="canDeleteComment(comment)"
                              link
                              type="danger"
                              size="small"
                              @click="removeComment(comment)"
                            >
                              删除
                            </el-button>
                          </div>
                        </div>
                        <div class="comment-content">{{ comment.content }}</div>
                      </div>
                    </div>
                </div>
            </div>
            <el-empty v-else description="暂无留言，快来提供线索吧" :image-size="60" />
        </div>
    </el-drawer>

    <el-drawer v-model="privateVisible" title="失物私信" size="760px" destroy-on-close>
      <div class="private-wrap">
        <div class="private-left">
          <div class="private-left-title">会话列表</div>
          <el-empty v-if="privateSessions.length === 0" description="暂无私信会话" :image-size="60" />
          <div
            v-for="session in privateSessions"
            :key="`lf-${session.itemId}-${session.otherUserId}`"
            :class="['private-session', { active: session.itemId === privateChat.itemId && session.otherUserId === privateChat.otherUserId }]"
            @click="openPrivateSession(session)"
          >
            <div class="private-session-title">
              {{ privateSessionItemLabel(session) }} · 用户ID：{{ privateSessionUserLabel(session) }}
              <el-badge v-if="session.unreadCount > 0" :value="session.unreadCount" />
            </div>
            <div class="private-session-desc">{{ session.lastMessage || '暂无消息' }}</div>
          </div>
        </div>

        <div class="private-right">
          <div class="private-head">
            <span v-if="privateChat.itemId">
              {{ privateItemLabelById(privateChat.itemId) }} · 对话用户ID：{{ privateUserLabelById(privateChat.otherUserId) }}
            </span>
            <span v-else>请选择会话</span>
            <el-button size="small" @click="refreshPrivateChat" :loading="privateLoading">刷新</el-button>
          </div>
          <div class="private-messages" ref="privateMsgRef">
            <el-empty v-if="privateMessages.length === 0" description="暂无消息" :image-size="80" />
            <div v-for="m in privateMessages" :key="m.id" :class="['private-msg', { self: Number(m.fromUserId) === Number(currentUserId) }]">
              <div class="private-msg-line">
                <span class="private-msg-user">{{ privateUserLabelById(m.fromUserId) }}</span>
                <span>{{ m.content }}</span>
              </div>
            </div>
          </div>
          <div class="private-send">
            <el-input
              v-model="privateDraft"
              placeholder="输入私信内容"
              :disabled="!privateChat.itemId"
              @keyup.enter="sendPrivateChat"
            />
            <el-button type="primary" :disabled="!privateChat.itemId" @click="sendPrivateChat">发送</el-button>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="auditVisible" title="失物发布审核中心" size="760px" destroy-on-close>
      <div class="toolbar">
        <el-button type="primary" :loading="auditLoading" @click="loadAuditQueue">刷新待审核</el-button>
      </div>
      <el-table v-loading="auditLoading" :data="auditRows" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="发布人" width="100">
          <template #default="{ row }">#{{ row.userId }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ typeText(row.itemType) }}</template>
        </el-table-column>
        <el-table-column prop="locationText" label="地点" min-width="120" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="approveAudit(row)">通过</el-button>
            <el-button link type="danger" @click="rejectAudit(row)">驳回</el-button>
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
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import SingleImageUpload from '../components/upload/SingleImageUpload.vue'
import { getUser } from '../utils/auth'
import { buildImageCandidates, normalizeImageUrl } from '../utils/image'
import {
  auditLostFoundItem,
  createLostFoundComment,
  createLostFoundItem,
  deleteLostFoundComment,
  deleteLostFoundItem,
  getLostFoundItemDetail,
  getLostFoundPrivateUnread,
  listLostFoundComments,
  listLostFoundPrivateSessions,
  pageLostFoundHistory,
  pagePendingAuditLostFoundItems,
  pageLostFoundPrivateMessages,
  pageLostFoundItems,
  readLostFoundPrivateMessages,
  sendLostFoundPrivateMessage,
  updateLostFoundStatus
} from '../api/lostFound'
import { fetchUserRoles, fetchUserSpace } from '../api/user'

// --- 状态 ---
const router = useRouter()
const viewMode = ref('card')
const activePanel = ref('plaza')
const loading = ref(false)
const total = ref(0)
const rows = ref([])
const historyTotal = ref(0)
const historyRows = ref([])
const currentUserId = ref(Number(getUser()?.id || 0))
const currentUserIdText = computed(() => currentUserId.value ? `${currentUserId.value}` : '未登录')
const currentUserAvatarUrl = computed(() => normalizeImageUrl(getUser()?.avatarUrl || ''))
const defaultCommentAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const roles = ref([])
const canAudit = computed(() => roles.value.includes('ADMIN'))
const primaryRole = computed(() => {
  if (roles.value.includes('ADMIN')) return 'ADMIN'
  if (roles.value.includes('AUDITOR')) return 'AUDITOR'
  return 'USER'
})
const roleText = computed(() => {
  if (!roles.value.length) return '角色：USER'
  return `角色：${roles.value.join(' / ')}`
})

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  itemType: ''
})

const createVisible = ref(false)
const createSubmitting = ref(false)
const createForm = reactive({
  userId: currentUserId.value,
  categoryId: 1,
  title: '',
  description: '',
  imageUrl: '',
  locationText: '',
  itemType: 'LOST'
})

const commentVisible = ref(false)
const commentSubmitting = ref(false)
const currentItemId = ref(null)
const comments = ref([])
const commentForm = reactive({
  content: ''
})

const auditVisible = ref(false)
const auditLoading = ref(false)
const auditRows = ref([])
const auditTotal = ref(0)
const auditQuery = reactive({
  page: 1,
  size: 10
})

const privateVisible = ref(false)
const privateLoading = ref(false)
const privateSessions = ref([])
const privateMessages = ref([])
const privateDraft = ref('')
const privateMsgRef = ref(null)
const commentAvatarIndexMap = reactive({})
const commentProfileCache = reactive({})
const privateChat = reactive({
  itemId: null,
  otherUserId: null
})
const privateItemTitleMap = reactive({})
const privateUserIdMap = reactive({})
const commentProfileLoadingMap = new Map()
const privateItemLoadingMap = new Map()
const privateUserLoadingMap = new Map()
let privateTimer = null

// 工具函数
const statusText = (status) => {
  const map = {
    'PENDING_AUDIT': '待审核',
    'SEARCHING': '寻找中',
    'FOUND': '已找到',
    'RETURNED': '已归还',
    'REJECTED': '已驳回'
  }
  return map[status] || status || '-'
}

const typeText = (itemType) => {
  const map = { 'LOST': '寻物', 'FOUND': '招领' }
  return map[itemType] || itemType || '-'
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return dateStr.replace('T', ' ').substring(0, 16)
}

const attachImagePreviewFields = (item) => {
  const candidates = buildImageCandidates(item?.imageUrl)
  return {
    ...item,
    _imageCandidates: candidates,
    _imagePreviewUrl: candidates[0] || '',
    _imagePreviewList: candidates
  }
}

const handleImageError = (item) => {
  if (!item) return
  const candidates = Array.isArray(item._imageCandidates) ? item._imageCandidates : []
  const current = item._imagePreviewUrl || ''
  const index = candidates.indexOf(current)
  if (index >= 0 && index < candidates.length - 1) {
    item._imagePreviewUrl = candidates[index + 1]
    return
  }
  item._imagePreviewUrl = ''
}

const tagType = (status) => {
  if (status === 'PENDING_AUDIT') return 'warning'
  if (status === 'SEARCHING') return 'warning'
  if (status === 'FOUND') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

// 辅助函数：统一处理响应
const checkResp = (resp) => {
  if (!resp || resp.code !== 0) {
    throw new Error(resp?.message || '请求失败')
  }
  return resp.data
}

const notifySuccess = (msg) => ElMessage.success({ message: msg, duration: 1500 })
const notifyError = (err, def) => ElMessage.error({ message: err.message || def, duration: 1500 })
const displayRows = computed(() => activePanel.value === 'history' ? historyRows.value : rows.value)
const displayTotal = computed(() => activePanel.value === 'history' ? historyTotal.value : total.value)
const timeColumnLabel = computed(() => activePanel.value === 'history' ? '找回时间' : '时间')
const displayItemTime = (item) => {
  if (activePanel.value === 'history') {
    return formatDate(item?.recoveredAt || item?.createdAt)
  }
  return formatDate(item?.createdAt)
}

const normalizeUserIdText = (value) => {
  const text = String(value || '').trim()
  if (!text) return ''
  return text.replace(/^用户#?/, '').replace(/^#/, '').trim()
}

const privateItemLabelById = (itemId) => {
  const numeric = Number(itemId || 0)
  if (!numeric) return '物品标题待补全'
  const title = String(privateItemTitleMap[String(numeric)] || '').trim()
  return title || `物品ID:${numeric}`
}

const privateUserLabelById = (userId) => {
  const numeric = Number(userId || 0)
  if (!numeric) return '-'
  const displayId = normalizeUserIdText(privateUserIdMap[String(numeric)])
  return displayId || String(numeric)
}

const privateSessionItemLabel = (session) => privateItemLabelById(session?.itemId)
const privateSessionUserLabel = (session) => privateUserLabelById(session?.otherUserId)

const ensurePrivateItemTitle = async (itemId) => {
  const numeric = Number(itemId || 0)
  if (!numeric) return
  const key = String(numeric)
  if (String(privateItemTitleMap[key] || '').trim()) return
  if (privateItemLoadingMap.has(key)) {
    await privateItemLoadingMap.get(key)
    return
  }
  const task = (async () => {
    try {
      const data = checkResp(await getLostFoundItemDetail(numeric))
      const title = String(data?.title || '').trim()
      if (title) {
        privateItemTitleMap[key] = title
      }
    } catch {
    } finally {
      privateItemLoadingMap.delete(key)
    }
  })()
  privateItemLoadingMap.set(key, task)
  await task
}

const ensurePrivateUserId = async (userId) => {
  const numeric = Number(userId || 0)
  if (!numeric) return
  const key = String(numeric)
  if (String(privateUserIdMap[key] || '').trim()) return
  if (privateUserLoadingMap.has(key)) {
    await privateUserLoadingMap.get(key)
    return
  }
  const task = (async () => {
    try {
      const data = checkResp(await fetchUserSpace(numeric))
      const displayId = normalizeUserIdText(data?.userId || data?.userNo || '')
      if (displayId) {
        privateUserIdMap[key] = displayId
      }
    } catch {
    } finally {
      privateUserLoadingMap.delete(key)
    }
  })()
  privateUserLoadingMap.set(key, task)
  await task
}

const hydratePrivateMeta = async (sessions, messages = []) => {
  const itemIds = new Set()
  const userIds = new Set()
  for (const session of sessions || []) {
    const itemId = Number(session?.itemId || 0)
    const otherUserId = Number(session?.otherUserId || 0)
    if (itemId > 0) itemIds.add(itemId)
    if (otherUserId > 0) userIds.add(otherUserId)
  }
  for (const message of messages || []) {
    const fromUserId = Number(message?.fromUserId || 0)
    if (fromUserId > 0) userIds.add(fromUserId)
  }
  if (Number(currentUserId.value || 0) > 0) {
    userIds.add(Number(currentUserId.value))
  }
  await Promise.all([
    Promise.all(Array.from(itemIds).map((id) => ensurePrivateItemTitle(id))),
    Promise.all(Array.from(userIds).map((id) => ensurePrivateUserId(id)))
  ])
}

const ensureCommentProfile = async (userId) => {
  const numeric = Number(userId || 0)
  if (!numeric) return
  const key = String(numeric)
  if (commentProfileCache[key]) return
  if (commentProfileLoadingMap.has(key)) {
    await commentProfileLoadingMap.get(key)
    return
  }
  const task = (async () => {
    try {
      const data = checkResp(await fetchUserSpace(numeric))
      const avatarRaw = data?.avatarUrl || data?.avatar || data?.userAvatar || ''
      commentProfileCache[key] = {
        userId: normalizeUserIdText(data?.userId || ''),
        userNo: normalizeUserIdText(data?.userNo || ''),
        username: String(data?.username || '').trim(),
        avatarUrl: normalizeImageUrl(avatarRaw)
      }
    } catch {
    } finally {
      commentProfileLoadingMap.delete(key)
    }
  })()
  commentProfileLoadingMap.set(key, task)
  await task
}

const patchCommentProfile = (comment) => {
  if (!comment) return
  const key = String(Number(comment.userId || 0))
  const profile = commentProfileCache[key]
  if (!profile) return
  if (!comment.commenterUserId && profile.userId) comment.commenterUserId = profile.userId
  if (!comment.commenterUserNo && profile.userNo) comment.commenterUserNo = profile.userNo
  if (!comment.commenterUsername && profile.username) comment.commenterUsername = profile.username
  if (!comment.commenterAvatarUrl && profile.avatarUrl) comment.commenterAvatarUrl = profile.avatarUrl
}

const hydrateCommentProfiles = async (list) => {
  const userIds = Array.from(new Set((list || []).map((comment) => Number(comment?.userId || 0)).filter((id) => id > 0)))
  await Promise.all(userIds.map((id) => ensureCommentProfile(id)))
  for (const comment of list || []) {
    patchCommentProfile(comment)
  }
}

const loadRoles = async () => {
  if (!currentUserId.value) {
    roles.value = []
    return
  }
  try {
    const data = checkResp(await fetchUserRoles())
    roles.value = Array.isArray(data?.roles) ? data.roles : []
  } catch {
    roles.value = []
  }
}

// API 操作
const loadItems = async () => {
  loading.value = true
  try {
    const data = checkResp(await pageLostFoundItems({ ...query }))
    rows.value = (data.records || []).map(attachImagePreviewFields)
    total.value = data.total || 0
  } catch (error) {
    notifyError(error, '加载列表失败')
  } finally {
    loading.value = false
  }
}

const loadHistoryItems = async () => {
  loading.value = true
  try {
    const data = checkResp(await pageLostFoundHistory({ ...query }))
    historyRows.value = (data.records || []).map(attachImagePreviewFields)
    historyTotal.value = data.total || 0
  } catch (error) {
    notifyError(error, '加载找回历史失败')
  } finally {
    loading.value = false
  }
}

const loadCurrentPanel = async () => {
  if (activePanel.value === 'history') {
    await loadHistoryItems()
    return
  }
  await loadItems()
}

const onPanelChange = () => {
  query.page = 1
  loadCurrentPanel()
}

const search = () => { query.page = 1; loadCurrentPanel() }
const resetQuery = () => { query.keyword = ''; query.status = ''; query.itemType = ''; search() }
const onPageChange = (p) => { query.page = p; loadCurrentPanel() }

const openCreateDialog = () => { 
    if (!currentUserId.value) {
      ElMessage.warning({ message: '请先登录后再发布', duration: 1500 })
      return
    }
    createForm.userId = currentUserId.value
    createVisible.value = true 
}

const submitCreate = async () => {
    if (!createForm.title.trim()) {
        ElMessage.warning('标题不能为空')
        return
    }
    createSubmitting.value = true
    try {
        checkResp(await createLostFoundItem({
            ...createForm,
            userId: currentUserId.value,
            title: createForm.title.trim()
        }))
        notifySuccess('发布成功，已展示在列表中')
        createVisible.value = false
        // 重置表单
        createForm.title = ''
        createForm.description = ''
        createForm.imageUrl = ''
        createForm.locationText = ''
        await loadItems()
    } catch (error) {
        notifyError(error, '发布失败')
    } finally {
        createSubmitting.value = false
    }
}

const openComments = async (row) => { 
    currentItemId.value = row.id
    commentVisible.value = true 
    await loadComments()
}

const loadComments = async () => {
  if (!currentItemId.value) return
  try {
    const data = checkResp(await listLostFoundComments(currentItemId.value))
    comments.value = data || []
    await hydrateCommentProfiles(comments.value)
    const keepKeys = new Set(comments.value.map((item) => commentAvatarKey(item)))
    for (const key of Object.keys(commentAvatarIndexMap)) {
      if (!keepKeys.has(key)) {
        delete commentAvatarIndexMap[key]
      }
    }
    for (const key of keepKeys) {
      commentAvatarIndexMap[key] = 0
    }
  } catch (error) {
    notifyError(error, '加载评论失败')
  }
}

const submitComment = async () => {
    if (!currentItemId.value) return
    if (!currentUserId.value) {
        ElMessage.warning('请先登录后再评论')
        return
    }
    if (!commentForm.content.trim()) {
        ElMessage.warning('内容不能为空')
        return
    }
    commentSubmitting.value = true
    try {
        checkResp(await createLostFoundComment(currentItemId.value, {
            userId: currentUserId.value,
            content: commentForm.content.trim()
        }))
        commentForm.content = ''
        notifySuccess('留言成功')
        await loadComments()
    } catch (error) {
        notifyError(error, '留言失败')
    } finally {
        commentSubmitting.value = false
    }
}

const commentDisplayId = (comment) => {
    const profile = commentProfileCache[String(Number(comment?.userId || 0))] || {}
    const display = normalizeUserIdText(
      comment?.commenterUserId || profile.userId || comment?.commenterUserNo || profile.userNo || comment?.userId
    )
    return display || '-'
}

const commentAvatarKey = (comment) => String(comment?.id || `${comment?.userId || '0'}-${comment?.createdAt || ''}`)

const commentAvatarCandidates = (comment) => {
    const candidates = []
    const profile = commentProfileCache[String(Number(comment?.userId || 0))] || {}
    const primary = buildImageCandidates(comment?.commenterAvatarUrl || comment?.commenterAvatar || comment?.avatarUrl || comment?.avatar || '')
    if (primary.length > 0) candidates.push(...primary)
    const profileAvatarCandidates = buildImageCandidates(profile?.avatarUrl || '')
    if (profileAvatarCandidates.length > 0) candidates.push(...profileAvatarCandidates)
    if (Number(comment?.userId || 0) === Number(currentUserId.value || 0) && currentUserAvatarUrl.value) {
      candidates.unshift(currentUserAvatarUrl.value)
    }
    candidates.push(defaultCommentAvatar)
    return [...new Set(candidates.filter(Boolean))]
}

const commentAvatarUrl = (comment) => {
    const key = commentAvatarKey(comment)
    const candidates = commentAvatarCandidates(comment)
    const index = Number(commentAvatarIndexMap[key] || 0)
    return candidates[Math.min(index, candidates.length - 1)] || defaultCommentAvatar
}

const handleCommentAvatarError = (comment) => {
    const key = commentAvatarKey(comment)
    const candidates = commentAvatarCandidates(comment)
    const currentIndex = Number(commentAvatarIndexMap[key] || 0)
    if (currentIndex < candidates.length - 1) {
      commentAvatarIndexMap[key] = currentIndex + 1
    } else {
      commentAvatarIndexMap[key] = candidates.length - 1
    }
}

const commentAvatarText = (comment) => {
    const profile = commentProfileCache[String(Number(comment?.userId || 0))] || {}
    const display = String(comment?.commenterUserId || profile.userId || comment?.commenterUsername || profile.username || comment?.userId || '?')
    return display.trim().slice(0, 1).toUpperCase()
}

const canDeleteComment = (comment) => {
    return currentUserId.value && Number(comment?.userId) === Number(currentUserId.value)
}

const removeComment = async (comment) => {
    if (!currentItemId.value || !comment?.id || !canDeleteComment(comment)) return
    try {
        await ElMessageBox.confirm('确认删除这条留言吗？', '删除留言', {
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            type: 'warning'
        })
        checkResp(await deleteLostFoundComment(currentItemId.value, comment.id, currentUserId.value))
        notifySuccess('留言已删除')
        await loadComments()
    } catch (error) {
        if (error !== 'cancel') notifyError(error, '删除留言失败')
    }
}

const changeStatus = async (row, status) => {
    if (!canChangeStatus(row)) {
        ElMessage.warning('仅发布人可更新该条目的进展状态')
        return
    }
    try {
        checkResp(await updateLostFoundStatus(row.id, { status, operatorUserId: currentUserId.value }))
        notifySuccess(`状态已更新: ${statusText(status)}`)
        await Promise.all([loadItems(), loadHistoryItems()])
    } catch (error) {
        notifyError(error, '状态更新失败')
    }
}

const removeItem = async (row) => {
    try {
        await ElMessageBox.confirm(`确认删除 "${row.title}" 吗？`, '删除确认', {
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            type: 'warning'
        })
        checkResp(await deleteLostFoundItem(row.id, currentUserId.value))
        notifySuccess('删除成功')
        await Promise.all([loadItems(), loadHistoryItems()])
    } catch (error) {
        if (error !== 'cancel') notifyError(error, '删除失败')
    }
}

const canDelete = (row) => {
    if (!currentUserId.value) return false
    return Number(row.userId) === Number(currentUserId.value)
}
const canOpenPrivateChat = (row) => {
  if (!currentUserId.value) return false
  const targetUserId = Number(row?.userId || 0)
  return targetUserId > 0 && targetUserId !== Number(currentUserId.value)
}

const hasMoreOps = (row) => {
  return canChangeStatus(row) || canDelete(row)
}

const openUserSpace = (publisherUserId) => {
  const uid = Number(publisherUserId || 0)
  if (!uid) return
  router.push(`/space/${uid}`)
}

const canChangeStatus = (row) => {
    if (!currentUserId.value) return false
    if (Number(row.userId) !== Number(currentUserId.value)) return false
    const status = String(row.status || '').toUpperCase()
    return status === 'SEARCHING' || status === 'FOUND' || status === 'RETURNED'
}

const openAuditDrawer = async () => {
    if (!canAudit.value) {
      ElMessage.warning('仅管理员可访问审核中心')
      return
    }
    auditVisible.value = true
    auditQuery.page = 1
    await loadAuditQueue()
}

const onAuditPageChange = (page) => {
  auditQuery.page = page
  loadAuditQueue()
}

const loadAuditQueue = async () => {
  if (!canAudit.value) {
    auditRows.value = []
    auditTotal.value = 0
    return
  }
  auditLoading.value = true
  try {
    const data = checkResp(await pagePendingAuditLostFoundItems({
      page: auditQuery.page,
      size: auditQuery.size,
      operatorRole: primaryRole.value
    }), '加载待审核列表失败')
    auditRows.value = data.records || []
    auditTotal.value = Number(data.total || 0)
  } catch (error) {
    notifyError(error, '加载待审核列表失败')
  } finally {
    auditLoading.value = false
  }
}

const approveAudit = async (row) => {
  try {
    checkResp(await auditLostFoundItem(row.id, {
      auditorId: currentUserId.value,
      auditorRole: primaryRole.value,
      status: 1,
      reason: ''
    }), '审核通过失败')
    notifySuccess('审核已通过')
    await Promise.all([loadAuditQueue(), loadItems(), loadHistoryItems()])
  } catch (error) {
    notifyError(error, '审核通过失败')
  }
}

const rejectAudit = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回发布', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：信息不完整、图片不清晰',
      inputValidator: (input) => (input && input.trim() ? true : '请输入驳回原因')
    })
    checkResp(await auditLostFoundItem(row.id, {
      auditorId: currentUserId.value,
      auditorRole: primaryRole.value,
      status: 2,
      reason: value.trim()
    }), '驳回失败')
    notifySuccess('已驳回该发布')
    await Promise.all([loadAuditQueue(), loadItems(), loadHistoryItems()])
  } catch (error) {
    if (error !== 'cancel') {
      notifyError(error, '驳回失败')
    }
  }
}

const scrollPrivateBottom = async () => {
  await nextTick()
  const el = privateMsgRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

const loadPrivateSessions = async () => {
  if (!currentUserId.value) return
  try {
    privateSessions.value = checkResp(await listLostFoundPrivateSessions({
      userId: currentUserId.value
    })) || []
    await hydratePrivateMeta(privateSessions.value)
  } catch (error) {
    notifyError(error, '加载私信会话失败')
  }
}

const loadPrivateMessages = async () => {
  if (!privateChat.itemId || !privateChat.otherUserId || !currentUserId.value) {
    privateMessages.value = []
    return
  }
  privateLoading.value = true
  try {
    const data = checkResp(await pageLostFoundPrivateMessages({
      itemId: privateChat.itemId,
      userId: currentUserId.value,
      otherUserId: privateChat.otherUserId,
      page: 1,
      size: 100
    }))
    privateMessages.value = [...(data.records || [])].reverse()
    await hydratePrivateMeta(privateSessions.value, privateMessages.value)
    await readLostFoundPrivateMessages({
      itemId: privateChat.itemId,
      userId: currentUserId.value,
      otherUserId: privateChat.otherUserId
    })
    const target = privateSessions.value.find((item) =>
      Number(item.itemId) === Number(privateChat.itemId) && Number(item.otherUserId) === Number(privateChat.otherUserId)
    )
    if (target) target.unreadCount = 0
    await scrollPrivateBottom()
  } catch (error) {
    notifyError(error, '加载私信消息失败')
  } finally {
    privateLoading.value = false
  }
}

const openPrivateSession = async (session) => {
  if (!session) return
  privateChat.itemId = Number(session.itemId)
  privateChat.otherUserId = Number(session.otherUserId)
  await loadPrivateMessages()
}

const openPrivateChat = async (row) => {
  if (!currentUserId.value) {
    ElMessage.warning('请先登录后再私信')
    return
  }
  const targetUserId = Number(row.userId || 0)
  if (!targetUserId || targetUserId === Number(currentUserId.value)) {
    ElMessage.warning('不能给自己发送私信')
    return
  }
  const itemId = Number(row.id || 0)
  if (!itemId) {
    ElMessage.warning('当前条目无效，无法私信')
    return
  }
  router.push({
    path: '/lost-found/private-chat',
    query: {
      itemId,
      targetUserId,
      returnTo: router.currentRoute.value.fullPath || '/lost-found'
    }
  })
}

const sendPrivateChat = async () => {
  if (!privateChat.itemId || !privateChat.otherUserId) {
    ElMessage.warning('请先选择会话')
    return
  }
  const content = privateDraft.value.trim()
  if (!content) return
  try {
    checkResp(await sendLostFoundPrivateMessage({
      itemId: privateChat.itemId,
      fromUserId: currentUserId.value,
      toUserId: privateChat.otherUserId,
      content,
      msgType: 1
    }))
    privateDraft.value = ''
    await Promise.all([loadPrivateSessions(), loadPrivateMessages()])
  } catch (error) {
    notifyError(error, '发送私信失败')
  }
}

const refreshPrivateChat = async () => {
  if (!privateVisible.value) return
  await Promise.all([loadPrivateSessions(), loadPrivateMessages()])
}

const startPrivatePolling = () => {
  if (privateTimer) return
  privateTimer = window.setInterval(refreshPrivateChat, 6000)
}

const stopPrivatePolling = () => {
  if (!privateTimer) return
  window.clearInterval(privateTimer)
  privateTimer = null
}

watch(privateVisible, (visible) => {
  if (visible) {
    startPrivatePolling()
  } else {
    stopPrivatePolling()
  }
})

onMounted(async () => {
  await loadRoles()
  await Promise.all([loadItems(), loadHistoryItems()])
})

onBeforeUnmount(() => {
  stopPrivatePolling()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.toolbar-left { display: flex; align-items: center; }

.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}
.search-input { width: 240px; }
.filter-select { width: 140px; }

/* Grid 布局核心样式 */
.card-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    gap: 20px;
}

.item-card {
    background: #fff;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    overflow: hidden;
    transition: box-shadow 0.2s;
    display: flex;
    flex-direction: column;
}

.item-card:hover {
    box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
}

.card-img-wrapper {
    height: 160px;
    background: #f1f5f9;
    position: relative;
}
.card-img { width: 100%; height: 100%; }
.card-img-error {
    width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
    color: #94a3b8;
    font-size: 13px;
}
.card-badges {
    position: absolute;
    top: 10px;
    left: 10px;
}

.card-body {
    padding: 16px;
    flex: 1;
}
.card-title {
    font-weight: 600;
    font-size: 16px;
    color: #1e293b;
    margin-bottom: 8px;
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.card-desc {
    font-size: 13px;
    color: #64748b;
    margin-bottom: 12px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    height: 36px; 
}
.card-meta {
    font-size: 12px;
    color: #94a3b8;
}
.meta-row {
    display: flex; align-items: center; gap: 4px; margin-bottom: 4px;
}

.card-footer {
    padding: 12px 16px;
    border-top: 1px solid #f1f5f9;
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: #fafafa;
}

.pager { margin-top: 24px; text-align: center; }
.img-placeholder {
    width: 40px;
    height: 40px;
    background: #f1f5f9;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #94a3b8;
    border-radius: 4px;
    font-size: 10px;
    line-height: 1.2;
    text-align: center;
    padding: 2px;
    box-sizing: border-box;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comment-card {
  display: grid;
  grid-template-columns: 38px 1fr;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}

.comment-avatar {
  flex-shrink: 0;
  background: #e2e8f0;
  color: #475569;
  font-weight: 600;
}

.comment-main {
  min-width: 0;
}

.comment-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: flex-start;
  margin-bottom: 6px;
}

.comment-user-line {
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.comment-user-id {
  font-weight: 700;
  color: #1e293b;
}

.comment-username {
  color: #64748b;
  font-size: 12px;
}

.comment-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.comment-time {
  color: #94a3b8;
  font-size: 12px;
}

.comment-content {
  font-size: 14px;
  color: #334155;
  line-height: 1.55;
  word-break: break-word;
}

.private-wrap {
  display: grid;
  grid-template-columns: 280px 1fr;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  min-height: 520px;
}

.private-left {
  border-right: 1px solid #e2e8f0;
  padding: 10px;
  overflow-y: auto;
}

.private-left-title {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
}

.private-session {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px;
  margin-bottom: 8px;
  cursor: pointer;
}

.private-session.active {
  border-color: #3b82f6;
  background: #eff6ff;
}

.private-session-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  margin-bottom: 4px;
}

.private-session-desc {
  color: #94a3b8;
  font-size: 12px;
}

.private-right {
  display: grid;
  grid-template-rows: 44px 1fr 56px;
}

.private-head {
  border-bottom: 1px solid #e2e8f0;
  padding: 0 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.private-messages {
  background: #f8fafc;
  padding: 10px;
  overflow-y: auto;
}

.private-msg {
  margin-bottom: 8px;
}

.private-msg.self {
  text-align: right;
}

.private-msg-line {
  display: inline-flex;
  gap: 6px;
  max-width: 80%;
  background: #fff;
  border: 1px solid #e2e8f0;
  padding: 6px 8px;
  border-radius: 8px;
}

.private-msg-user {
  color: #64748b;
}

.private-send {
  border-top: 1px solid #e2e8f0;
  padding: 8px;
  display: grid;
  grid-template-columns: 1fr 84px;
  gap: 8px;
}

@media (max-width: 992px) {
  .private-wrap {
    grid-template-columns: 1fr;
  }

  .private-left {
    border-right: 0;
    border-bottom: 1px solid #e2e8f0;
    max-height: 180px;
  }
}
</style>


