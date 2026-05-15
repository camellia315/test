<template>
  <div class="market-page">
    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane label="商品广场" name="plaza">
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="搜索商品" clearable style="width: 220px" />
          <el-select v-model="query.categoryId" clearable placeholder="分类" style="width: 140px">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select v-model="query.sortBy" style="width: 150px">
            <el-option label="最新发布" value="createTime" />
            <el-option label="价格升序" value="priceAsc" />
            <el-option label="价格降序" value="priceDesc" />
            <el-option label="浏览量" value="viewCount" />
            <el-option label="收藏量" value="favoriteCount" />
          </el-select>
          <el-button type="primary" :loading="loading.products" @click="loadProducts(1)">查询</el-button>
          <el-button @click="openPublish()">发布商品</el-button>
        </div>

        <div class="recommend">
          <el-tag v-for="item in forYou" :key="`f-${item.id}`" @click="openDetail(item.id)">猜你喜欢：{{ item.title }}</el-tag>
          <el-tag type="warning" v-for="item in hot" :key="`h-${item.id}`" @click="openDetail(item.id)">热门：{{ item.title }}</el-tag>
        </div>

        <el-row :gutter="16">
          <el-col v-for="item in productPage.records" :key="item.id" :xs="24" :sm="12" :lg="8">
            <el-card class="card">
              <el-image
                :src="normalizePreviewUrl(item.coverImage) || fallbackImage"
                fit="cover"
                style="height: 160px; width: 100%"
                :preview-src-list="buildPreviewList(item.coverImage)"
                :initial-index="0"
                preview-teleported
              />
              <h4>{{ item.title }}</h4>
              <p class="desc">{{ item.description || '暂无描述' }}</p>
              <div class="meta">¥{{ toPrice(item.price) }} · {{ statusText(item.status) }}</div>
              <div class="sub-meta">已售 {{ soldCount(item) }}/{{ totalCount(item) }} · 库存 {{ remainCount(item) }}</div>
              <div class="ops">
                <el-button text type="primary" @click="openDetail(item.id)">详情</el-button>
                <el-button v-if="canViewSellerSpace(item.sellerId)" text @click="openUserSpace(item.sellerId)">主页</el-button>
                <el-button v-if="canToggleFavorite(item)" text @click="toggleFav(item)">{{ item.favorited ? '取消收藏' : '收藏' }}</el-button>
                <el-button v-if="canCreateOrder(item)" text type="success" @click="createOrderFor(item)">下单</el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-pagination class="pager" layout="prev, pager, next, total" :current-page="productPage.current" :page-size="productPage.size" :total="productPage.total" @current-change="loadProducts" />
      </el-tab-pane>

      <el-tab-pane label="我的商品" name="mine">
        <el-button type="primary" @click="openPublish()">发布新商品</el-button>
        <el-table :data="minePage.records" stripe style="margin-top: 10px">
          <el-table-column prop="title" label="商品" min-width="160" />
          <el-table-column prop="price" label="价格" width="120">
            <template #default="{ row }">¥{{ toPrice(row.price) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">{{ statusText(row.status) }}</template>
          </el-table-column>
          <el-table-column label="已售/总量" width="120">
            <template #default="{ row }">{{ soldCount(row) }}/{{ totalCount(row) }}</template>
          </el-table-column>
          <el-table-column label="库存" width="80">
            <template #default="{ row }">{{ remainCount(row) }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="240">
            <template #default="{ row }">
              <el-button text type="primary" @click="openPublish(row)">编辑</el-button>
              <el-button text type="warning" @click="setStatus(row, 1)">上架</el-button>
              <el-button text type="info" @click="setStatus(row, 0)">下架</el-button>
              <el-button text type="danger" @click="removeMine(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我的收藏" name="favorites">
        <el-table :data="favoritePage.records" stripe>
          <el-table-column prop="title" label="商品" min-width="160" />
          <el-table-column prop="price" label="价格" width="120">
            <template #default="{ row }">¥{{ toPrice(row.price) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">{{ statusText(row.status) }}</template>
          </el-table-column>
          <el-table-column label="已售/总量" width="120">
            <template #default="{ row }">{{ soldCount(row) }}/{{ totalCount(row) }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="220">
            <template #default="{ row }">
              <el-button text type="primary" @click="openDetail(row.id)">详情</el-button>
              <el-button v-if="canToggleFavorite(row)" text type="danger" @click="toggleFav(row)">取消收藏</el-button>
              <el-button v-if="canCreateOrder(row)" text type="success" @click="createOrderFor(row)">下单</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="pager" layout="prev, pager, next, total" :current-page="favoritePage.current" :page-size="favoritePage.size" :total="favoritePage.total" @current-change="loadFavorites" />
      </el-tab-pane>

      <el-tab-pane label="订单" name="orders">
        <div class="toolbar">
          <el-select v-model="orderQuery.role" style="width: 140px" @change="loadOrders">
            <el-option label="全部" value="all" />
            <el-option label="买家" value="buyer" />
            <el-option label="卖家" value="seller" />
          </el-select>
          <el-select v-model="orderQuery.status" clearable style="width: 140px" @change="loadOrders">
            <el-option label="待确认" :value="0" />
            <el-option label="已确认" :value="1" />
            <el-option label="已完成" :value="2" />
            <el-option label="已取消" :value="3" />
          </el-select>
        </div>
        <el-table :data="orderPage.records" stripe>
          <el-table-column prop="orderNo" label="订单号" min-width="180" />
          <el-table-column prop="productTitle" label="商品" min-width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">{{ orderStatusText(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="payStatus" label="支付" width="120">
            <template #default="{ row }">
              <el-tag :type="Number(row.payStatus) === 1 ? 'success' : 'warning'" size="small">
                {{ Number(row.payStatus) === 1 ? '已支付' : '待支付' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="250">
            <template #default="{ row }">
              <template v-if="hasAnyOrderAction(row)">
                <el-button v-if="canPayOrder(row)" text type="primary" @click="openPayFlow(row)">去支付</el-button>
                <el-button v-if="canConfirmOrder(row)" text @click="changeOrder(row, 1)">确认</el-button>
                <el-button v-if="canCompleteOrder(row)" text @click="changeOrder(row, 2)">完成</el-button>
                <el-button v-if="canCancelOrder(row)" text @click="changeOrder(row, 3)">取消</el-button>
                <el-button v-if="canViewOrderUserSpace(row)" text @click="openUserSpace(otherUserId(row))">主页</el-button>
                <el-button v-if="canContactInOrder(row)" text type="primary" @click="openChat(otherUserId(row))">联系</el-button>
              </template>
              <span v-else class="op-empty">-</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane name="chat">
        <template #label>
          <span>聊天</span>
          <el-badge v-if="unreadTotal > 0" :value="unreadTotal" class="chat-tab-badge" />
        </template>
        <div class="chat-wrap">
          <div class="left">
            <div class="chat-pane-head">
              <div class="chat-pane-title">
                会话列表
                <el-tag size="small" type="info">总 {{ chatSessionStats.total }} · 未读 {{ chatSessionStats.unread }}</el-tag>
              </div>
              <div class="chat-pane-actions">
                <el-button size="small" @click="chatManagerVisible = true">会话管理</el-button>
                <el-button size="small" :loading="loading.sessions" @click="loadSessions">刷新</el-button>
              </div>
            </div>
            <div class="chat-starter">
              <el-input
                v-model="chatSearchKeyword"
                placeholder="搜索用户ID/用户名/用户编号"
                clearable
                @keyup.enter="searchChatUsers"
              />
              <el-button type="primary" :loading="chatSearchLoading" @click="searchChatUsers">搜索</el-button>
            </div>
            <div v-if="chatSearchResults.length > 0" class="chat-search-list">
              <div v-for="u in chatSearchResults" :key="u.id" class="chat-search-item">
                <div class="chat-search-main">
                  <div>{{ u.userId || ('#' + u.id) }}</div>
                  <div class="chat-search-aux">{{ u.userNo || '-' }}</div>
                </div>
                <el-button text type="primary" @click="openChatWithUser(u)">聊天</el-button>
              </div>
            </div>
            <div v-if="displaySessions.length === 0" class="chat-empty-hint">暂无会话，可从订单“联系”进入，或搜索用户ID发起</div>
            <div v-for="s in displaySessions" :key="`${s.userId}-${s.otherUserId}`" class="session" :class="{ active: s.otherUserId === chatUserId }" @click="openChat(s.otherUserId)">
              <div class="session-top">
                <div>{{ sessionDisplayText(s.otherUserId) }} <el-badge v-if="s.unreadCount > 0" :value="s.unreadCount" /></div>
                <el-popconfirm title="确认删除该会话？" width="190px" @confirm="removeSession(s)">
                  <template #reference>
                    <el-button link type="danger" size="small" @click.stop>删除</el-button>
                  </template>
                </el-popconfirm>
              </div>
              <div class="desc">{{ s.lastMessage || '暂无消息' }}</div>
              <div class="session-time">{{ formatSessionTime(s.lastMessageTime || s.updateTime || s.createTime) }}</div>
            </div>
          </div>
          <div class="right">
            <div class="msgs" ref="msgRef">
              <div v-if="!chatUserId" class="chat-empty-hint">请选择一个会话后再发送消息</div>
              <div v-for="m in messages" :key="m.id" :class="['msg', { self: m.fromUserId === userId }]">
                <span>{{ m.content }}</span>
              </div>
            </div>
            <div class="send">
              <el-input v-model="draft" placeholder="输入消息" @keyup.enter="sendMsg" />
              <el-button type="primary" @click="sendMsg">发送</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="publishVisible" :title="form.id ? '编辑商品' : '发布商品'" width="640px">
      <el-form label-position="top">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="6"><el-form-item label="价格"><el-input-number v-model="form.price" :min="0.01" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="原价"><el-input-number v-model="form.originalPrice" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="数量"><el-input-number v-model="form.totalQuantity" :min="1" :max="100000" :step="1" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="分类"><el-select v-model="form.categoryId" clearable><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item></el-col>
        </el-row>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="教材,理工,九成新" /></el-form-item>
        <el-form-item label="封面"><SingleImageUpload v-model="form.coverImage" type="market" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading.publish" @click="submitPublish">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="商品详情" width="600px">
      <div v-if="detail">
        <el-image
          :src="normalizePreviewUrl(detail.coverImage) || fallbackImage"
          fit="cover"
          style="width: 100%; height: 220px"
          :preview-src-list="buildPreviewList(detail.coverImage)"
          :initial-index="0"
          preview-teleported
        />
        <h3>{{ detail.title }}</h3>
        <p>{{ detail.description || '暂无描述' }}</p>
        <p>价格：¥{{ toPrice(detail.price) }} · 卖家 #{{ detail.sellerId }}</p>
        <p>已售：{{ soldCount(detail) }}/{{ totalCount(detail) }} · 库存：{{ remainCount(detail) }}</p>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail" @click="openUserSpace(detail.sellerId)">卖家主页</el-button>
        <el-button v-if="detail" type="primary" :disabled="detail.sellerId === userId" @click="openChat(detail.sellerId)">联系卖家</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="chatManagerVisible"
      title="会话管理"
      width="520px"
      append-to-body
    >
      <el-form label-position="top">
        <el-form-item label="会话筛选">
          <el-input v-model="sessionManager.keyword" clearable placeholder="输入用户ID或消息关键字" />
        </el-form-item>
        <el-form-item label="排序方式">
          <el-select v-model="sessionManager.sortBy" style="width: 100%">
            <el-option label="最近消息优先" value="recent" />
            <el-option label="未读优先" value="unread" />
            <el-option label="用户ID升序" value="user" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="sessionManager.unreadOnly">仅显示未读会话</el-checkbox>
        </el-form-item>
      </el-form>
      <div class="chat-manager-summary">
        <div class="chat-manager-stat">
          <div class="chat-manager-label">总会话</div>
          <div class="chat-manager-value">{{ chatSessionStats.total }}</div>
        </div>
        <div class="chat-manager-stat">
          <div class="chat-manager-label">未读会话</div>
          <div class="chat-manager-value">{{ chatSessionStats.unreadSessions }}</div>
        </div>
        <div class="chat-manager-stat">
          <div class="chat-manager-label">总未读</div>
          <div class="chat-manager-value">{{ chatSessionStats.unread }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="resetSessionManager">重置</el-button>
        <el-button :loading="loading.sessions" @click="loadSessions">刷新会话</el-button>
        <el-button type="primary" :loading="markAllReadLoading" @click="markAllSessionsRead">一键全部已读</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="payFlow.visible"
      title="模拟支付流程"
      width="660px"
      :close-on-click-modal="false"
      :show-close="!payFlow.processing"
      @closed="resetPayFlow"
    >
      <el-steps :active="payFlow.step" finish-status="success" align-center>
        <el-step title="确认订单" />
        <el-step title="选择方式" />
        <el-step title="处理中" />
        <el-step title="完成" />
      </el-steps>

      <div class="pay-flow-body">
        <div v-if="payFlow.step === 0" class="pay-card">
          <div class="pay-line"><span>订单号</span><strong>{{ payFlow.order?.orderNo || '-' }}</strong></div>
          <div class="pay-line"><span>商品</span><strong>{{ payFlow.order?.productTitle || '-' }}</strong></div>
          <div class="pay-line"><span>卖家ID</span><strong>#{{ payFlow.order?.sellerId || '-' }}</strong></div>
          <div class="pay-line"><span>应付金额</span><strong class="pay-amount">¥{{ toPrice(payFlow.order?.price || 0) }}</strong></div>
        </div>

        <div v-else-if="payFlow.step === 1" class="pay-card">
          <div class="pay-title">选择模拟支付方式</div>
          <el-radio-group v-model="payFlow.channel" class="pay-channel-list">
            <el-radio label="SIMULATED_BALANCE">校园钱包（模拟）</el-radio>
            <el-radio label="SIMULATED_WECHAT">微信支付（模拟）</el-radio>
            <el-radio label="SIMULATED_ALIPAY">支付宝（模拟）</el-radio>
          </el-radio-group>
          <el-alert type="info" :closable="false" show-icon title="这是模拟支付，不会产生真实扣款。" />
        </div>

        <div v-else-if="payFlow.step === 2" class="pay-card">
          <div class="pay-title">正在处理支付请求...</div>
          <el-progress :percentage="payFlow.progress" :stroke-width="16" status="success" />
          <div class="pay-log">
            <div v-for="(log, index) in payFlow.logs" :key="`pay-log-${index}`">{{ index + 1 }}. {{ log }}</div>
          </div>
        </div>

        <div v-else class="pay-card">
          <el-result
            :icon="payFlow.success ? 'success' : 'error'"
            :title="payFlow.success ? '模拟支付成功' : '模拟支付失败'"
            :sub-title="payFlow.success ? `订单 ${payFlow.order?.orderNo || ''} 已更新为已支付` : (payFlow.errorMessage || '请重试')"
          />
        </div>
      </div>

      <template #footer>
        <el-button v-if="payFlow.step < 2" @click="payFlow.visible = false">取消</el-button>
        <el-button v-if="payFlow.step === 1" @click="payFlow.step = 0">上一步</el-button>
        <el-button v-if="payFlow.step === 0" type="primary" @click="payFlow.step = 1">下一步</el-button>
        <el-button v-if="payFlow.step === 1" type="primary" :loading="payFlow.processing" @click="startPayFlow">确认支付</el-button>
        <el-button v-if="payFlow.step === 2" type="primary" :loading="true" disabled>支付处理中...</el-button>
        <el-button v-if="payFlow.step === 3 && !payFlow.success" @click="retryPayFlow">重新发起</el-button>
        <el-button v-if="payFlow.step === 3" type="primary" @click="payFlow.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import SingleImageUpload from '../components/upload/SingleImageUpload.vue'
import { getUserRef } from '../utils/auth'
import { buildImageCandidates, normalizeImageUrl } from '../utils/image'
import { createOrder, createProduct, deleteChatSession, deleteProduct, getForYouProducts, getHotProducts, getProductDetail, listChatSessions, listProductCategories, markChatRead, pageChatMessages, pageFavoriteProducts, pageOrders, pageProducts, sendChatMessage, ensureChatSession, simulateOrderPay, toggleFavorite, updateOrderStatus, updateProduct, updateProductStatus } from '../api/market'
import { fetchUserSpace, searchUsers } from '../api/user'

const fallbackImage = 'https://via.placeholder.com/320x220?text=Market'
const router = useRouter()
const route = useRoute()
const tab = ref('plaza')
const userRef = getUserRef()
const userId = computed(() => Number(userRef.value?.id || 0))
const currentUsername = computed(() => userRef.value?.userId || userRef.value?.username || '未登录')

const categories = ref([])
const forYou = ref([])
const hot = ref([])
const loading = reactive({ products: false, publish: false, sessions: false })
const query = reactive({ keyword: '', categoryId: undefined, sortBy: 'createTime' })
const productPage = reactive({ records: [], total: 0, current: 1, size: 12 })
const minePage = reactive({ records: [], total: 0, current: 1, size: 10 })
const favoritePage = reactive({ records: [], total: 0, current: 1, size: 10 })
const orderQuery = reactive({ role: 'all', status: undefined })
const orderPage = reactive({ records: [], total: 0, current: 1, size: 10 })

const publishVisible = ref(false)
const form = reactive({ id: null, title: '', description: '', coverImage: '', price: 0, originalPrice: null, totalQuantity: 1, categoryId: undefined, tags: '' })
const detailVisible = ref(false)
const detail = ref(null)
const payFlow = reactive({
  visible: false,
  step: 0,
  processing: false,
  progress: 0,
  channel: 'SIMULATED_BALANCE',
  order: null,
  logs: [],
  success: false,
  errorMessage: ''
})

const sessions = ref([])
const messages = ref([])
const chatUserId = ref(null)
const chatSearchKeyword = ref('')
const chatSearchLoading = ref(false)
const chatSearchResults = ref([])
const chatSessionUserIdMap = reactive({})
const draft = ref('')
const unreadTotal = computed(() => sessions.value.reduce((s, i) => s + Number(i.unreadCount || 0), 0))
const chatManagerVisible = ref(false)
const markAllReadLoading = ref(false)
const sessionManager = reactive({
  keyword: '',
  unreadOnly: false,
  sortBy: 'recent'
})
const msgRef = ref(null)
const wsConnected = ref(false)
const wsWarned = ref(false)
let client = null

const ok = (resp, msg) => { if (!resp || Number(resp.code) !== 0) throw new Error(resp?.message || msg); return resp.data }
const toPrice = (v) => Number(v || 0).toFixed(2)
const totalCount = (item) => {
  const n = Number(item?.totalQuantity || 1)
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : 1
}
const soldCount = (item) => {
  const n = Number(item?.soldQuantity || 0)
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : 0
}
const remainCount = (item) => Math.max(totalCount(item) - soldCount(item), 0)
const statusText = (s) => Number(s) === 1 ? '在售' : Number(s) === 2 ? '已售' : '下架'
const orderStatusText = (s) => Number(s) === 0 ? '待确认' : Number(s) === 1 ? '已确认' : Number(s) === 2 ? '已完成' : '已取消'
const sortParam = () => query.sortBy === 'priceAsc' ? { sortBy: 'price', sortOrder: 'asc' } : query.sortBy === 'priceDesc' ? { sortBy: 'price', sortOrder: 'desc' } : { sortBy: query.sortBy, sortOrder: 'desc' }
const normalizePreviewUrl = (url) => {
  return buildImageCandidates(url)[0] || normalizeImageUrl(url)
}
const buildPreviewList = (url) => {
  return buildImageCandidates(url)
}
const toMillis = (v) => {
  if (!v) return 0
  const ms = new Date(v).getTime()
  return Number.isNaN(ms) ? 0 : ms
}
const formatSessionTime = (v) => {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(0, 16)
}
const sessionDisplayUserId = (otherUserId) => {
  const key = Number(otherUserId || 0)
  if (!key) return '-'
  return chatSessionUserIdMap[key] || `#${key}`
}
const sessionDisplayText = (otherUserId) => `用户ID：${sessionDisplayUserId(otherUserId)}`
const displaySessions = computed(() => {
  const keyword = sessionManager.keyword.trim().toLowerCase()
  let list = [...sessions.value]
  if (keyword) {
    list = list.filter((s) => {
      const text = `${sessionDisplayUserId(s.otherUserId)} ${s.otherUserId || ''} ${s.lastMessage || ''}`.toLowerCase()
      return text.includes(keyword)
    })
  }
  if (sessionManager.unreadOnly) {
    list = list.filter((s) => Number(s.unreadCount || 0) > 0)
  }
  list.sort((a, b) => {
    if (sessionManager.sortBy === 'user') {
      const textA = sessionDisplayUserId(a.otherUserId)
      const textB = sessionDisplayUserId(b.otherUserId)
      const textCompare = textA.localeCompare(textB, 'zh-Hans-CN', { numeric: true, sensitivity: 'base' })
      if (textCompare !== 0) return textCompare
      return Number(a.otherUserId || 0) - Number(b.otherUserId || 0)
    }
    if (sessionManager.sortBy === 'unread') {
      const unreadDiff = Number(b.unreadCount || 0) - Number(a.unreadCount || 0)
      if (unreadDiff !== 0) return unreadDiff
    }
    return toMillis(b.lastMessageTime || b.updateTime || b.createTime) - toMillis(a.lastMessageTime || a.updateTime || a.createTime)
  })
  return list
})
const chatSessionStats = computed(() => {
  const total = sessions.value.length
  const unreadSessions = sessions.value.filter((s) => Number(s.unreadCount || 0) > 0).length
  return {
    total,
    unreadSessions,
    unread: unreadTotal.value
  }
})

async function loadCategories() { categories.value = ok(await listProductCategories(), '加载分类失败') || [] }
async function loadProducts(page = 1) {
  loading.products = true
  try {
    const data = ok(await pageProducts({
      page,
      size: productPage.size,
      keyword: query.keyword || undefined,
      categoryId: query.categoryId,
      status: 1,
      viewerUserId: userId.value || undefined,
      ...sortParam()
    }), '加载商品失败') || {}
    const records = Array.isArray(data.records)
      ? data.records.filter((item) => Number(item?.status || 0) === 1 && remainCount(item) > 0)
      : []
    Object.assign(productPage, data, { records })
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.products = false
  }
}
async function loadMine() { if (!userId.value) return; Object.assign(minePage, ok(await pageProducts({ page: 1, size: minePage.size, sellerUserId: userId.value, viewerUserId: userId.value }), '加载我的商品失败') || {}) }
async function loadFavorites(page = 1) { if (!userId.value) return; Object.assign(favoritePage, ok(await pageFavoriteProducts({ userId: userId.value, page, size: favoritePage.size }), '加载收藏失败') || {}) }
async function loadOrders(page = 1) { if (!userId.value) return; const data = ok(await pageOrders({ userId: userId.value, role: orderQuery.role, status: orderQuery.status, page, size: orderPage.size }), '加载订单失败') || {}; const records = Array.isArray(data.records) ? data.records.filter((row) => isOrderParticipant(row)) : []; Object.assign(orderPage, data, { records }) }
async function loadRecommend() {
  const onlyOnSale = (list) => (Array.isArray(list) ? list.filter((item) => Number(item?.status ?? 1) === 1 && remainCount(item) > 0) : [])
  if (userId.value) {
    forYou.value = onlyOnSale(ok(await getForYouProducts({ userId: userId.value, size: 6 }), '加载推荐失败') || [])
  } else {
    forYou.value = []
  }
  hot.value = onlyOnSale(ok(await getHotProducts({ size: 6 }), '加载热门失败') || [])
}

async function openDetail(id) { detail.value = ok(await getProductDetail(id, { viewerUserId: userId.value || undefined }), '加载详情失败'); detailVisible.value = true }
function openPublish(row) { form.id = row?.id || null; form.title = row?.title || ''; form.description = row?.description || ''; form.coverImage = row?.coverImage || ''; form.price = Number(row?.price || 0); form.originalPrice = row?.originalPrice == null ? null : Number(row.originalPrice); form.totalQuantity = Number(row?.totalQuantity || 1); form.categoryId = row?.categoryId; form.tags = row?.tags || ''; publishVisible.value = true }
async function submitPublish() { if (!form.title.trim() || !Number(form.price)) return ElMessage.warning('请填写标题和价格'); if (!Number(form.totalQuantity) || Number(form.totalQuantity) <= 0) return ElMessage.warning('请填写正确数量'); loading.publish = true; try { const payload = { title: form.title.trim(), description: form.description || '', coverImage: form.coverImage || '', price: Number(form.price), originalPrice: form.originalPrice == null ? null : Number(form.originalPrice), totalQuantity: Math.max(1, Math.floor(Number(form.totalQuantity))), categoryId: form.categoryId || null, tags: form.tags || '', sellerId: userId.value }; if (form.id) ok(await updateProduct(form.id, { ...payload, operatorUserId: userId.value }), '更新失败'); else ok(await createProduct(payload), '发布失败'); publishVisible.value = false; await Promise.all([loadProducts(1), loadMine(), loadRecommend()]); ElMessage.success('操作成功') } catch (e) { ElMessage.error(e.message) } finally { loading.publish = false } }
async function setStatus(row, status) { try { ok(await updateProductStatus(row.id, { operatorUserId: userId.value, status }), '状态更新失败'); await Promise.all([loadMine(), loadProducts(productPage.current)]); ElMessage.success('状态已更新') } catch (e) { ElMessage.error(e.message) } }
async function removeMine(row) { try { await ElMessageBox.confirm('确认删除该商品？', '提示', { type: 'warning' }); ok(await deleteProduct(row.id, userId.value), '删除失败'); await Promise.all([loadMine(), loadProducts(1)]); ElMessage.success('已删除') } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '删除失败') } }
async function toggleFav(item) { if (!canToggleFavorite(item)) return; if (!userId.value) return ElMessage.warning('请先登录'); try { const data = ok(await toggleFavorite({ productId: item.id, userId: userId.value }), '收藏失败'); item.favorited = !!data.favorited; item.favoriteCount = Number(data.favoriteCount || 0) } catch (e) { ElMessage.error(e.message) } }
async function createOrderFor(item) { if (!canCreateOrder(item)) return; try { const { value } = await ElMessageBox.prompt('备注可选', '意向下单', { inputPlaceholder: '交易地点/时间' }); ok(await createOrder({ productId: item.id, buyerId: userId.value, remark: value || '' }), '下单失败'); await Promise.all([loadOrders(), loadProducts(productPage.current), loadMine()]); ElMessage.success('订单已创建') } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '下单失败') } }
const isBuyerOrder = (row) => Number(row?.buyerId || 0) === Number(userId.value || 0)
const isSellerOrder = (row) => Number(row?.sellerId || 0) === Number(userId.value || 0)
const isOrderParticipant = (row) => isBuyerOrder(row) || isSellerOrder(row)
const canViewSellerSpace = (sellerId) => Number(sellerId || 0) > 0 && Number(sellerId || 0) !== Number(userId.value || 0)
const canToggleFavorite = (item) => Number(userId.value || 0) > 0 && canViewSellerSpace(item?.sellerId)
const canCreateOrder = (item) => Number(userId.value || 0) > 0 && canViewSellerSpace(item?.sellerId) && Number(item?.status) === 1 && remainCount(item) > 0
const canPayOrder = (row) => isBuyerOrder(row) && Number(row?.status) === 0 && Number(row?.payStatus || 0) !== 1
const canConfirmOrder = (row) => isSellerOrder(row) && Number(row?.status) === 0 && Number(row?.payStatus || 0) === 1
const canCompleteOrder = (row) => isBuyerOrder(row) && Number(row?.status) === 1
const canCancelOrder = (row) => isOrderParticipant(row) && (Number(row?.status) === 0 || Number(row?.status) === 1)
const otherUserId = (row) => isBuyerOrder(row) ? row?.sellerId : row?.buyerId
const canViewOrderUserSpace = (row) => Number(otherUserId(row) || 0) > 0
const canContactInOrder = (row) => Number(otherUserId(row) || 0) > 0
const hasAnyOrderAction = (row) => canPayOrder(row) || canConfirmOrder(row) || canCompleteOrder(row) || canCancelOrder(row) || canViewOrderUserSpace(row) || canContactInOrder(row)
const canOrderStatusAction = (row, targetStatus) => {
  if (targetStatus === 1) return canConfirmOrder(row)
  if (targetStatus === 2) return canCompleteOrder(row)
  if (targetStatus === 3) return canCancelOrder(row)
  return false
}
const openUserSpace = (targetUserId) => { if (!targetUserId) return; router.push(`/space/${targetUserId}`) }
async function changeOrder(row, status) {
  if (!canOrderStatusAction(row, status)) {
    ElMessage.warning('当前账号无该操作权限，或订单状态不满足')
    return
  }
  try {
    ok(await updateOrderStatus(row.id, { operatorUserId: userId.value, status }), '更新订单失败')
    await Promise.all([loadOrders(orderPage.current || 1), loadProducts(productPage.current), loadMine()])
    ElMessage.success('订单状态已更新')
  } catch (e) {
    ElMessage.error(e.message)
  }
}
const wait = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms))
function resetPayFlow() {
  if (payFlow.processing) return
  payFlow.step = 0
  payFlow.progress = 0
  payFlow.channel = 'SIMULATED_BALANCE'
  payFlow.order = null
  payFlow.logs = []
  payFlow.success = false
  payFlow.errorMessage = ''
}
function openPayFlow(row) {
  if (!row?.id) return
  payFlow.visible = true
  payFlow.step = 0
  payFlow.processing = false
  payFlow.progress = 0
  payFlow.channel = 'SIMULATED_BALANCE'
  payFlow.order = { ...row }
  payFlow.logs = ['支付会话已创建，请确认订单信息']
  payFlow.success = false
  payFlow.errorMessage = ''
}
function retryPayFlow() {
  if (payFlow.processing) return
  payFlow.step = 1
  payFlow.progress = 0
  payFlow.logs = ['已重置，请重新确认支付方式']
  payFlow.success = false
  payFlow.errorMessage = ''
}
async function startPayFlow() {
  if (payFlow.processing || !payFlow.order?.id) return
  payFlow.step = 2
  payFlow.processing = true
  payFlow.progress = 8
  payFlow.logs = ['正在校验订单状态...']
  payFlow.success = false
  payFlow.errorMessage = ''
  try {
    await wait(320)
    payFlow.progress = 28
    payFlow.logs.push('支付渠道初始化完成')
    await wait(360)
    payFlow.progress = 52
    payFlow.logs.push('风控检查通过')
    await wait(360)
    payFlow.progress = 78
    payFlow.logs.push('提交模拟扣款请求...')
    ok(await simulateOrderPay(payFlow.order.id, {
      operatorUserId: userId.value,
      payChannel: payFlow.channel || 'SIMULATED_BALANCE'
    }), '模拟支付失败')
    await wait(260)
    payFlow.progress = 100
    payFlow.logs.push('支付成功，回调确认完成')
    payFlow.success = true
    ElMessage.success('模拟支付成功')
    await Promise.all([loadOrders(orderPage.current || 1), loadProducts(productPage.current), loadMine()])
  } catch (e) {
    payFlow.errorMessage = e.message || '模拟支付失败'
    payFlow.logs.push(`支付失败：${payFlow.errorMessage}`)
    ElMessage.error(payFlow.errorMessage)
  } finally {
    payFlow.processing = false
    payFlow.step = 3
  }
}

const wsEndpoint = () => '/ws-market'
const notifyWsFallback = () => {
  if (wsWarned.value) return
  wsWarned.value = true
  ElMessage.warning('实时聊天通道未连接，已自动切换为普通发送模式')
}
function connectWs() {
  if (!userId.value || client) return
  client = new Client({
    webSocketFactory: () => new SockJS(wsEndpoint()),
    reconnectDelay: 5000,
    debug: () => {}
  })
  client.onConnect = () => {
    wsConnected.value = true
    wsWarned.value = false
    client.subscribe(`/queue/market.chat.${userId.value}`, ({ body }) => {
      if (!body) return
      const msg = JSON.parse(body)
      const other = Number(msg.fromUserId) === userId.value ? Number(msg.toUserId) : Number(msg.fromUserId)
      void resolveChatSessionUserIds([other])
      const isViewingCurrentSession = tab.value === 'chat' && Number(chatUserId.value) === other
      if (isViewingCurrentSession) {
        messages.value.push(msg)
        markReadNow()
        scrollBottom()
      }
      const s = sessions.value.find((x) => Number(x.otherUserId) === other)
      if (s) {
        s.lastMessage = msg.content
        if (Number(msg.toUserId) === userId.value && !isViewingCurrentSession) {
          s.unreadCount = Number(s.unreadCount || 0) + 1
          notifyIncomingMessage(msg, other)
        }
      } else {
        sessions.value.unshift({
          userId: userId.value,
          otherUserId: other,
          lastMessage: msg.content,
          unreadCount: Number(msg.toUserId) === userId.value ? 1 : 0
        })
        if (Number(msg.toUserId) === userId.value) {
          notifyIncomingMessage(msg, other)
        }
      }
    })
  }
  client.onStompError = () => {
    wsConnected.value = false
    notifyWsFallback()
  }
  client.onWebSocketError = () => {
    wsConnected.value = false
    notifyWsFallback()
  }
  client.onWebSocketClose = () => {
    wsConnected.value = false
  }
  client.activate()
}
function disconnectWs() {
  wsConnected.value = false
  if (client) {
    client.deactivate()
    client = null
  }
}
async function resolveChatSessionUserIds(targetIds = []) {
  const ids = [...new Set(
    targetIds
      .map((id) => Number(id || 0))
      .filter((id) => id > 0 && !chatSessionUserIdMap[id])
  )]
  if (ids.length === 0) return
  await Promise.allSettled(ids.map(async (id) => {
    try {
      const data = ok(await fetchUserSpace(id), '加载会话用户信息失败')
      chatSessionUserIdMap[id] = data?.userId || `#${id}`
    } catch {
      chatSessionUserIdMap[id] = `#${id}`
    }
  }))
}
async function loadSessions() {
  if (!userId.value) {
    sessions.value = []
    return
  }
  loading.sessions = true
  try {
    sessions.value = ok(await listChatSessions({ userId: userId.value }), '加载会话失败') || []
    await resolveChatSessionUserIds(sessions.value.map((s) => s.otherUserId))
  } catch (e) {
    ElMessage.error(e.message || '加载会话失败')
  } finally {
    loading.sessions = false
  }
}
async function openChat(other) {
  if (!other || Number(other) === userId.value) return
  tab.value = 'chat'
  chatUserId.value = Number(other)
  try {
    await ensureChatSession({
      userId: userId.value,
      otherUserId: chatUserId.value
    })
  } catch {
  }
  await loadSessions()
  const data = ok(await pageChatMessages({ userId: userId.value, otherUserId: chatUserId.value, page: 1, size: 100 }), '加载聊天失败')
  messages.value = [...(data.records || [])].reverse()
  await nextTick()
  scrollBottom()
  markReadNow()
}
async function searchChatUsers() {
  const keyword = chatSearchKeyword.value.trim()
  if (!keyword) {
    chatSearchResults.value = []
    return
  }
  chatSearchLoading.value = true
  try {
    chatSearchResults.value = ok(await searchUsers({ keyword, size: 20 }), '搜索用户失败') || []
    if (chatSearchResults.value.length === 0) {
      ElMessage.info('未搜索到匹配用户')
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    chatSearchLoading.value = false
  }
}
async function openChatWithUser(user) {
  if (!user || !user.id) return
  if (Number(user.id) === Number(userId.value)) {
    ElMessage.warning('不能和自己聊天')
    return
  }
  if (user.userId) {
    chatSessionUserIdMap[Number(user.id)] = String(user.userId)
  }
  await openChat(user.id)
}
async function markReadNow() { if (!chatUserId.value) return; try { await markChatRead({ userId: userId.value, otherUserId: chatUserId.value }); const s = sessions.value.find((x) => Number(x.otherUserId) === Number(chatUserId.value)); if (s) s.unreadCount = 0 } catch {} }
function resetSessionManager() {
  sessionManager.keyword = ''
  sessionManager.unreadOnly = false
  sessionManager.sortBy = 'recent'
}
async function markAllSessionsRead() {
  if (!userId.value) return
  const unreadSessions = sessions.value.filter((s) => Number(s.unreadCount || 0) > 0)
  if (unreadSessions.length === 0) {
    ElMessage.info('当前没有未读会话')
    return
  }
  markAllReadLoading.value = true
  try {
    const results = await Promise.allSettled(
      unreadSessions.map((s) => markChatRead({ userId: userId.value, otherUserId: Number(s.otherUserId) }))
    )
    const successCount = results.filter((r) => r.status === 'fulfilled').length
    if (successCount > 0) {
      await loadSessions()
      if (chatUserId.value) {
        await markReadNow()
      }
      ElMessage.success(`已处理 ${successCount} 个会话`)
      return
    }
    ElMessage.error('批量已读失败')
  } finally {
    markAllReadLoading.value = false
  }
}
async function removeSession(session) {
  const otherUser = Number(session?.otherUserId || 0)
  if (!userId.value || !otherUser) return
  try {
    ok(await deleteChatSession({ userId: userId.value, otherUserId: otherUser }), '删除会话失败')
    sessions.value = sessions.value.filter((s) => Number(s.otherUserId) !== otherUser)
    if (Number(chatUserId.value) === otherUser) {
      chatUserId.value = null
      messages.value = []
    }
    ElMessage.success('会话已删除')
  } catch (e) {
    ElMessage.error(e.message || '删除会话失败')
  }
}
function notifyIncomingMessage(msg, other) {
  ElNotification({
    title: '新聊天消息',
    message: `${sessionDisplayText(other)}：${msg.content || ''}`,
    duration: 3000,
    onClick: () => { openChat(other) }
  })
}
async function sendMsg() {
  if (!chatUserId.value) {
    ElMessage.warning('请先选择会话或搜索用户发起聊天')
    return
  }
  if (!draft.value.trim()) return
  const payload = { fromUserId: userId.value, toUserId: chatUserId.value, content: draft.value.trim(), msgType: 1 }
  draft.value = ''
  try {
    if (client && wsConnected.value) {
      client.publish({ destination: '/app/chat.send', body: JSON.stringify(payload) })
      return
    }
  } catch {
    wsConnected.value = false
    notifyWsFallback()
  }
  try {
    const data = ok(await sendChatMessage(payload), '发送失败')
    messages.value.push(data)
    scrollBottom()
  } catch (e) {
    ElMessage.error(e.message)
  }
}
function scrollBottom() { const el = msgRef.value; if (el) el.scrollTop = el.scrollHeight }

async function onTabChange(name) { if (name === 'mine') await loadMine(); if (name === 'favorites') await loadFavorites(1); if (name === 'orders') await loadOrders(); if (name === 'chat') await loadSessions() }

const routeChatUserId = computed(() => Number(route.query.chatUserId || route.query.targetUserId || 0))
const routeChatTab = computed(() => String(route.query.tab || '').toLowerCase())

async function syncChatFromRouteQuery() {
  const shouldOpenChat = routeChatTab.value === 'chat' || routeChatUserId.value > 0
  if (!shouldOpenChat) return
  tab.value = 'chat'
  await loadSessions()
  if (routeChatUserId.value > 0 && routeChatUserId.value !== Number(userId.value)) {
    await openChat(routeChatUserId.value)
  }
}

onMounted(async () => {
  await loadCategories()
  await Promise.all([loadProducts(1), loadRecommend(), loadFavorites(1), loadSessions()])
  await syncChatFromRouteQuery()
  connectWs()
})

watch(
  () => [route.path, route.query.tab, route.query.chatUserId, route.query.targetUserId],
  async ([path]) => {
    if (path !== '/market') return
    await syncChatFromRouteQuery()
  }
)
onBeforeUnmount(() => disconnectWs())
</script>

<style scoped>
.market-page { display: flex; flex-direction: column; gap: 12px; }
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 10px; }
.recommend { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.card { margin-bottom: 12px; }
.desc { color: #64748b; min-height: 36px; }
.meta { color: #0f172a; font-weight: 600; margin: 4px 0; }
.sub-meta { color: #64748b; font-size: 12px; margin-bottom: 4px; }
.ops { display: flex; justify-content: space-between; }
.pager { margin-top: 8px; justify-content: center; }
.chat-wrap { display: grid; grid-template-columns: 280px 1fr; border: 1px solid #e2e8f0; border-radius: 10px; min-height: 520px; }
.left { border-right: 1px solid #e2e8f0; padding: 10px; overflow-y: auto; }
.chat-pane-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 8px; }
.chat-pane-title { display: flex; align-items: center; gap: 8px; color: #334155; font-size: 13px; font-weight: 600; }
.chat-pane-actions { display: flex; gap: 8px; }
.chat-starter { display: grid; grid-template-columns: 1fr 72px; gap: 8px; margin-bottom: 10px; }
.chat-search-list { border: 1px solid #e2e8f0; border-radius: 8px; margin-bottom: 10px; overflow: hidden; }
.chat-search-item { display: grid; grid-template-columns: 1fr 56px; gap: 6px; align-items: center; padding: 6px 8px; border-bottom: 1px solid #f1f5f9; }
.chat-search-item:last-child { border-bottom: 0; }
.chat-search-main { overflow: hidden; }
.chat-search-aux { color: #94a3b8; font-size: 12px; }
.session { border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px; margin-bottom: 8px; cursor: pointer; }
.session.active { border-color: #3b82f6; background: #eff6ff; }
.session-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.session-time { color: #94a3b8; font-size: 11px; margin-top: 4px; }
.right { display: grid; grid-template-rows: 1fr 56px; }
.msgs { background: #f8fafc; padding: 10px; overflow-y: auto; }
.chat-empty-hint { color: #94a3b8; font-size: 13px; padding: 8px 2px; }
.msg { margin-bottom: 8px; }
.msg.self { text-align: right; }
.send { border-top: 1px solid #e2e8f0; padding: 8px; display: grid; grid-template-columns: 1fr 84px; gap: 8px; }
.chat-tab-badge { margin-left: 6px; }
.chat-manager-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-top: 8px; }
.chat-manager-stat { border: 1px solid #e2e8f0; border-radius: 8px; background: #f8fafc; text-align: center; padding: 8px; }
.chat-manager-label { color: #64748b; font-size: 12px; }
.chat-manager-value { color: #1d4ed8; font-size: 18px; font-weight: 700; margin-top: 2px; }
.pay-flow-body { margin-top: 20px; }
.pay-card { border: 1px solid #e2e8f0; border-radius: 10px; background: #f8fafc; padding: 14px; }
.pay-line { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px dashed #e2e8f0; font-size: 14px; color: #334155; }
.pay-line:last-child { border-bottom: 0; }
.pay-amount { color: #16a34a; font-size: 18px; }
.pay-title { font-size: 14px; color: #334155; margin-bottom: 12px; font-weight: 600; }
.pay-channel-list { display: grid; gap: 10px; margin-bottom: 12px; }
.pay-log { margin-top: 14px; display: grid; gap: 8px; color: #475569; font-size: 13px; }
.op-empty { color: #94a3b8; font-size: 12px; }
@media (max-width: 992px) { .chat-wrap { grid-template-columns: 1fr; } .left { border-right: 0; border-bottom: 1px solid #e2e8f0; max-height: 180px; } .chat-pane-head { flex-direction: column; align-items: flex-start; } .chat-manager-summary { grid-template-columns: 1fr; } }
</style>












