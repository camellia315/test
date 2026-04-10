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
              <el-image :src="item.coverImage || fallbackImage" fit="cover" style="height: 160px; width: 100%" />
              <h4>{{ item.title }}</h4>
              <p class="desc">{{ item.description || '暂无描述' }}</p>
              <div class="meta">¥{{ toPrice(item.price) }} · {{ statusText(item.status) }}</div>
              <div class="ops">
                <el-button text type="primary" @click="openDetail(item.id)">详情</el-button>
                <el-button text @click="toggleFav(item)">{{ item.favorited ? '取消收藏' : '收藏' }}</el-button>
                <el-button text type="success" :disabled="item.sellerId === userId || item.status !== 1" @click="createOrderFor(item)">下单</el-button>
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
          <el-table-column label="操作" min-width="220">
            <template #default="{ row }">
              <el-button text type="primary" @click="openDetail(row.id)">详情</el-button>
              <el-button text type="danger" @click="toggleFav(row)">取消收藏</el-button>
              <el-button text type="success" :disabled="row.status !== 1 || row.sellerId === userId" @click="createOrderFor(row)">下单</el-button>
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
          <el-table-column label="操作" min-width="250">
            <template #default="{ row }">
              <el-button text @click="changeOrder(row, 1)">确认</el-button>
              <el-button text @click="changeOrder(row, 2)">完成</el-button>
              <el-button text @click="changeOrder(row, 3)">取消</el-button>
              <el-button text type="primary" @click="openChat(otherUserId(row))">联系</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="聊天" name="chat">
        <div class="chat-wrap">
          <div class="left">
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
                  <div>{{ u.userId || '-' }} <span class="chat-search-aux">({{ u.username || '-' }})</span></div>
                  <div class="chat-search-aux">{{ u.userNo || '-' }}</div>
                </div>
                <el-button text type="primary" @click="openChatWithUser(u)">聊天</el-button>
              </div>
            </div>
            <div v-if="sessions.length === 0" class="chat-empty-hint">暂无会话，可从订单“联系”进入，或搜索用户ID发起</div>
            <div v-for="s in sessions" :key="`${s.userId}-${s.otherUserId}`" class="session" :class="{ active: s.otherUserId === chatUserId }" @click="openChat(s.otherUserId)">
              <div>用户 #{{ s.otherUserId }} <el-badge v-if="s.unreadCount > 0" :value="s.unreadCount" /></div>
              <div class="desc">{{ s.lastMessage || '暂无消息' }}</div>
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
          <el-col :span="8"><el-form-item label="价格"><el-input-number v-model="form.price" :min="0.01" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="原价"><el-input-number v-model="form.originalPrice" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="分类"><el-select v-model="form.categoryId" clearable><el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item></el-col>
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
        <el-image :src="detail.coverImage || fallbackImage" fit="cover" style="width: 100%; height: 220px" />
        <h3>{{ detail.title }}</h3>
        <p>{{ detail.description || '暂无描述' }}</p>
        <p>价格：¥{{ toPrice(detail.price) }} · 卖家 #{{ detail.sellerId }}</p>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail" type="primary" :disabled="detail.sellerId === userId" @click="openChat(detail.sellerId)">联系卖家</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import SingleImageUpload from '../components/upload/SingleImageUpload.vue'
import { getUserRef } from '../utils/auth'
import { createOrder, createProduct, deleteProduct, getForYouProducts, getHotProducts, getProductDetail, listChatSessions, listProductCategories, markChatRead, pageChatMessages, pageFavoriteProducts, pageOrders, pageProducts, sendChatMessage, toggleFavorite, updateOrderStatus, updateProduct, updateProductStatus } from '../api/market'
import { searchUsers } from '../api/user'

const fallbackImage = 'https://via.placeholder.com/320x220?text=Market'
const tab = ref('plaza')
const userRef = getUserRef()
const userId = computed(() => Number(userRef.value?.id || 0))
const currentUsername = computed(() => userRef.value?.username || '未登录')

const categories = ref([])
const forYou = ref([])
const hot = ref([])
const loading = reactive({ products: false, publish: false })
const query = reactive({ keyword: '', categoryId: undefined, sortBy: 'createTime' })
const productPage = reactive({ records: [], total: 0, current: 1, size: 12 })
const minePage = reactive({ records: [], total: 0, current: 1, size: 10 })
const favoritePage = reactive({ records: [], total: 0, current: 1, size: 10 })
const orderQuery = reactive({ role: 'all', status: undefined })
const orderPage = reactive({ records: [], total: 0, current: 1, size: 10 })

const publishVisible = ref(false)
const form = reactive({ id: null, title: '', description: '', coverImage: '', price: 0, originalPrice: null, categoryId: undefined, tags: '' })
const detailVisible = ref(false)
const detail = ref(null)

const sessions = ref([])
const messages = ref([])
const chatUserId = ref(null)
const chatSearchKeyword = ref('')
const chatSearchLoading = ref(false)
const chatSearchResults = ref([])
const draft = ref('')
const unreadTotal = computed(() => sessions.value.reduce((s, i) => s + Number(i.unreadCount || 0), 0))
const msgRef = ref(null)
const wsConnected = ref(false)
const wsWarned = ref(false)
let client = null

const ok = (resp, msg) => { if (!resp || Number(resp.code) !== 0) throw new Error(resp?.message || msg); return resp.data }
const toPrice = (v) => Number(v || 0).toFixed(2)
const statusText = (s) => Number(s) === 1 ? '在售' : Number(s) === 2 ? '已售' : '下架'
const orderStatusText = (s) => Number(s) === 0 ? '待确认' : Number(s) === 1 ? '已确认' : Number(s) === 2 ? '已完成' : '已取消'
const sortParam = () => query.sortBy === 'priceAsc' ? { sortBy: 'price', sortOrder: 'asc' } : query.sortBy === 'priceDesc' ? { sortBy: 'price', sortOrder: 'desc' } : { sortBy: query.sortBy, sortOrder: 'desc' }

async function loadCategories() { categories.value = ok(await listProductCategories(), '加载分类失败') || [] }
async function loadProducts(page = 1) { loading.products = true; try { Object.assign(productPage, ok(await pageProducts({ page, size: productPage.size, keyword: query.keyword || undefined, categoryId: query.categoryId, viewerUserId: userId.value || undefined, ...sortParam() }), '加载商品失败') || {}) } catch (e) { ElMessage.error(e.message) } finally { loading.products = false } }
async function loadMine() { if (!userId.value) return; Object.assign(minePage, ok(await pageProducts({ page: 1, size: minePage.size, sellerUserId: userId.value, viewerUserId: userId.value }), '加载我的商品失败') || {}) }
async function loadFavorites(page = 1) { if (!userId.value) return; Object.assign(favoritePage, ok(await pageFavoriteProducts({ userId: userId.value, page, size: favoritePage.size }), '加载收藏失败') || {}) }
async function loadOrders(page = 1) { if (!userId.value) return; Object.assign(orderPage, ok(await pageOrders({ userId: userId.value, role: orderQuery.role, status: orderQuery.status, page, size: orderPage.size }), '加载订单失败') || {}) }
async function loadRecommend() { if (userId.value) forYou.value = ok(await getForYouProducts({ userId: userId.value, size: 6 }), '加载推荐失败') || []; hot.value = ok(await getHotProducts({ size: 6 }), '加载热门失败') || [] }

async function openDetail(id) { detail.value = ok(await getProductDetail(id, { viewerUserId: userId.value || undefined }), '加载详情失败'); detailVisible.value = true }
function openPublish(row) { form.id = row?.id || null; form.title = row?.title || ''; form.description = row?.description || ''; form.coverImage = row?.coverImage || ''; form.price = Number(row?.price || 0); form.originalPrice = row?.originalPrice == null ? null : Number(row.originalPrice); form.categoryId = row?.categoryId; form.tags = row?.tags || ''; publishVisible.value = true }
async function submitPublish() { if (!form.title.trim() || !Number(form.price)) return ElMessage.warning('请填写标题和价格'); loading.publish = true; try { const payload = { title: form.title.trim(), description: form.description || '', coverImage: form.coverImage || '', price: Number(form.price), originalPrice: form.originalPrice == null ? null : Number(form.originalPrice), categoryId: form.categoryId || null, tags: form.tags || '', sellerId: userId.value }; if (form.id) ok(await updateProduct(form.id, { ...payload, operatorUserId: userId.value }), '更新失败'); else ok(await createProduct(payload), '发布失败'); publishVisible.value = false; await Promise.all([loadProducts(1), loadMine(), loadRecommend()]); ElMessage.success('操作成功') } catch (e) { ElMessage.error(e.message) } finally { loading.publish = false } }
async function setStatus(row, status) { try { ok(await updateProductStatus(row.id, { operatorUserId: userId.value, status }), '状态更新失败'); await Promise.all([loadMine(), loadProducts(productPage.current)]); ElMessage.success('状态已更新') } catch (e) { ElMessage.error(e.message) } }
async function removeMine(row) { try { await ElMessageBox.confirm('确认删除该商品？', '提示', { type: 'warning' }); ok(await deleteProduct(row.id, userId.value), '删除失败'); await Promise.all([loadMine(), loadProducts(1)]); ElMessage.success('已删除') } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '删除失败') } }
async function toggleFav(item) { if (!userId.value) return ElMessage.warning('请先登录'); try { const data = ok(await toggleFavorite({ productId: item.id, userId: userId.value }), '收藏失败'); item.favorited = !!data.favorited; item.favoriteCount = Number(data.favoriteCount || 0) } catch (e) { ElMessage.error(e.message) } }
async function createOrderFor(item) { try { const { value } = await ElMessageBox.prompt('备注可选', '意向下单', { inputPlaceholder: '交易地点/时间' }); ok(await createOrder({ productId: item.id, buyerId: userId.value, remark: value || '' }), '下单失败'); await Promise.all([loadOrders(), loadProducts(productPage.current), loadMine()]); ElMessage.success('订单已创建') } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '下单失败') } }
const otherUserId = (row) => Number(userId.value) === Number(row.buyerId) ? row.sellerId : row.buyerId
async function changeOrder(row, status) { try { ok(await updateOrderStatus(row.id, { operatorUserId: userId.value, status }), '更新订单失败'); await Promise.all([loadOrders(), loadProducts(productPage.current), loadMine()]); ElMessage.success('订单状态已更新') } catch (e) { ElMessage.error(e.message) } }

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
      if (chatUserId.value === other) {
        messages.value.push(msg)
        markReadNow()
        scrollBottom()
      }
      const s = sessions.value.find((x) => Number(x.otherUserId) === other)
      if (s) {
        s.lastMessage = msg.content
        if (Number(msg.toUserId) === userId.value && chatUserId.value !== other) {
          s.unreadCount = Number(s.unreadCount || 0) + 1
        }
      } else {
        sessions.value.unshift({
          userId: userId.value,
          otherUserId: other,
          lastMessage: msg.content,
          unreadCount: Number(msg.toUserId) === userId.value ? 1 : 0
        })
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
async function loadSessions() { if (!userId.value) return; sessions.value = ok(await listChatSessions({ userId: userId.value }), '加载会话失败') || [] }
async function openChat(other) { if (!other || Number(other) === userId.value) return; tab.value = 'chat'; chatUserId.value = Number(other); const data = ok(await pageChatMessages({ userId: userId.value, otherUserId: chatUserId.value, page: 1, size: 100 }), '加载聊天失败'); messages.value = [...(data.records || [])].reverse(); await nextTick(); scrollBottom(); markReadNow() }
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
  await openChat(user.id)
}
async function markReadNow() { if (!chatUserId.value) return; try { await markChatRead({ userId: userId.value, otherUserId: chatUserId.value }); const s = sessions.value.find((x) => Number(x.otherUserId) === Number(chatUserId.value)); if (s) s.unreadCount = 0 } catch {} }
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

onMounted(async () => { await loadCategories(); await Promise.all([loadProducts(1), loadRecommend(), loadFavorites(1), loadSessions()]); connectWs() })
onBeforeUnmount(() => disconnectWs())
</script>

<style scoped>
.market-page { display: flex; flex-direction: column; gap: 12px; }
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 10px; }
.recommend { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.card { margin-bottom: 12px; }
.desc { color: #64748b; min-height: 36px; }
.meta { color: #0f172a; font-weight: 600; margin: 4px 0; }
.ops { display: flex; justify-content: space-between; }
.pager { margin-top: 8px; justify-content: center; }
.chat-wrap { display: grid; grid-template-columns: 280px 1fr; border: 1px solid #e2e8f0; border-radius: 10px; min-height: 520px; }
.left { border-right: 1px solid #e2e8f0; padding: 10px; overflow-y: auto; }
.chat-starter { display: grid; grid-template-columns: 1fr 72px; gap: 8px; margin-bottom: 10px; }
.chat-search-list { border: 1px solid #e2e8f0; border-radius: 8px; margin-bottom: 10px; overflow: hidden; }
.chat-search-item { display: grid; grid-template-columns: 1fr 56px; gap: 6px; align-items: center; padding: 6px 8px; border-bottom: 1px solid #f1f5f9; }
.chat-search-item:last-child { border-bottom: 0; }
.chat-search-main { overflow: hidden; }
.chat-search-aux { color: #94a3b8; font-size: 12px; }
.session { border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px; margin-bottom: 8px; cursor: pointer; }
.session.active { border-color: #3b82f6; background: #eff6ff; }
.right { display: grid; grid-template-rows: 1fr 56px; }
.msgs { background: #f8fafc; padding: 10px; overflow-y: auto; }
.chat-empty-hint { color: #94a3b8; font-size: 13px; padding: 8px 2px; }
.msg { margin-bottom: 8px; }
.msg.self { text-align: right; }
.send { border-top: 1px solid #e2e8f0; padding: 8px; display: grid; grid-template-columns: 1fr 84px; gap: 8px; }
@media (max-width: 992px) { .chat-wrap { grid-template-columns: 1fr; } .left { border-right: 0; border-bottom: 1px solid #e2e8f0; max-height: 180px; } }
</style>
