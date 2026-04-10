<template>
  <div class="lost-found-page">
    <div class="page-card">
      <!-- 顶部工具栏 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <h2 class="page-title" style="margin: 0">失物招领</h2>
          <el-tag type="info" round size="small" style="margin-left: 10px">共 {{ total }} 条信息</el-tag>
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
        <el-table v-if="viewMode === 'table'" :data="rows" stripe style="width: 100%">
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
          <el-table-column prop="createdAt" label="时间" width="160" sortable />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openComments(row)">评论</el-button>
              <el-dropdown trigger="click" style="margin-left: 10px">
                <el-button link size="small">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="changeStatus(row, 'FOUND')">标记已找到</el-dropdown-item>
                    <el-dropdown-item @click="changeStatus(row, 'RETURNED')">标记已归还</el-dropdown-item>
                    <el-dropdown-item v-if="canDelete(row)" divided @click="removeItem(row)" style="color: #f56c6c">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>

        <!-- 模式 B: 卡片网格视图 -->
        <div v-else class="card-grid">
           <el-empty v-if="rows.length === 0" description="没有找到相关信息" />
           <div v-for="item in rows" :key="item.id" class="item-card">
              <div class="card-img-wrapper">
                 <el-image
                   v-if="item._imagePreviewUrl"
                   :src="item._imagePreviewUrl || normalizeImageUrl(item.imageUrl)"
                   loading="lazy"
                   fit="cover"
                   class="card-img"
                   :preview-src-list="item._imagePreviewList || []"
                   preview-teleported
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
                      <el-icon><Timer /></el-icon> <span>{{ formatDate(item.createdAt) }}</span>
                    </div>
                 </div>
              </div>
              <div class="card-footer">
                 <el-tag :type="tagType(item.status)" size="small" plain>{{ statusText(item.status) }}</el-tag>
                 <div class="card-actions">
                    <el-button circle size="small" icon="ChatDotRound" @click="openComments(item)"></el-button>
                    <el-dropdown trigger="click">
                      <el-button circle size="small" icon="More"></el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item @click="changeStatus(item, 'FOUND')">标记找到</el-dropdown-item>
                          <el-dropdown-item @click="changeStatus(item, 'RETURNED')">标记归还</el-dropdown-item>
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
          :total="total"
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
                <el-timeline>
                    <el-timeline-item
                      v-for="comment in comments"
                      :key="comment.id"
                      :timestamp="formatDate(comment.createdAt)"
                      placement="top"
                    >
                      <div class="comment-line">
                        <strong>#{{ comment.userId }}</strong>：{{ comment.content }}
                      </div>
                    </el-timeline-item>
                </el-timeline>
            </div>
            <el-empty v-else description="暂无留言，快来提供线索吧" :image-size="60" />
        </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SingleImageUpload from '../components/upload/SingleImageUpload.vue'
import { getUser } from '../utils/auth'
import {
  createLostFoundComment,
  createLostFoundItem,
  deleteLostFoundItem,
  listLostFoundComments,
  pageLostFoundItems,
  updateLostFoundStatus
} from '../api/lostFound'

// --- 状态 ---
const viewMode = ref('card')
const loading = ref(false)
const total = ref(0)
const rows = ref([])
const currentUserId = ref(Number(getUser()?.id || 0))
const currentUserIdText = computed(() => currentUserId.value ? `${currentUserId.value}` : '未登录')

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

// 工具函数
const statusText = (status) => {
  const map = { 'SEARCHING': '寻找中', 'FOUND': '已找到', 'RETURNED': '已归还' }
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

const normalizeImageUrl = (url) => {
  if (!url) return ''
  const trimmed = String(url).trim()
  if (!trimmed) return ''
  if (trimmed.includes('.clouddn.com/')) {
    if (trimmed.startsWith('http://')) return trimmed
    if (trimmed.startsWith('https://')) return trimmed.replace('https://', 'http://')
    if (trimmed.startsWith('//')) return `http:${trimmed}`
    return `http://${trimmed}`
  }
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) return trimmed
  if (trimmed.startsWith('//')) return `https:${trimmed}`
  return trimmed
}

const tagType = (status) => {
  if (status === 'SEARCHING') return 'warning'
  if (status === 'FOUND') return 'success'
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

// API 操作
const loadItems = async () => {
  loading.value = true
  try {
    const data = checkResp(await pageLostFoundItems({ ...query }))
    rows.value = (data.records || []).map((item) => {
      const image = normalizeImageUrl(item.imageUrl)
      return {
        ...item,
        _imagePreviewUrl: image,
        _imagePreviewList: image ? [image] : []
      }
    })
    total.value = data.total || 0
  } catch (error) {
    notifyError(error, '加载列表失败')
  } finally {
    loading.value = false
  }
}

const search = () => { query.page = 1; loadItems() }
const resetQuery = () => { query.keyword = ''; query.status = ''; query.itemType = ''; search() }
const onPageChange = (p) => { query.page = p; loadItems() }

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
        notifySuccess('发布成功')
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

const changeStatus = async (row, status) => {
    try {
        checkResp(await updateLostFoundStatus(row.id, { status }))
        notifySuccess(`状态已更新: ${statusText(status)}`)
        await loadItems()
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
        await loadItems()
    } catch (error) {
        if (error !== 'cancel') notifyError(error, '删除失败')
    }
}

const canDelete = (row) => {
    if (!currentUserId.value) return false
    return Number(row.userId) === Number(currentUserId.value)
}

onMounted(loadItems)
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

.comment-item { padding: 10px 0; border-bottom: 1px solid #f1f5f9; }
.comment-header { display: flex; justify-content: space-between; font-size: 12px; color: #94a3b8; margin-bottom: 4px; }
.comment-user { font-weight: 600; color: #475569; }
.comment-content { font-size: 14px; color: #334155; line-height: 1.5; }
.comment-line { line-height: 1.45; word-break: break-word; }
</style>
