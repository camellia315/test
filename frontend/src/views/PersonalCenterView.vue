<template>
  <div class="personal-page">
    <section class="hero-card" :style="heroCardStyle">
      <div>
        <h2>个人中心</h2>
        <p>统一管理个人资料、我的发布、订单和收藏。支持自定义背景图。</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" @click="goProfile">编辑资料</el-button>
        <el-button @click="refreshAll" :loading="loading.summary">刷新统计</el-button>
      </div>
    </section>

    <el-row :gutter="14" class="stat-row">
      <el-col :xs="24" :sm="12" :lg="6" v-for="item in statCards" :key="item.label">
        <div class="stat-card">
          <p class="label">{{ item.label }}</p>
          <p class="value">{{ item.value }}</p>
        </div>
      </el-col>
    </el-row>

    <div class="page-card user-card">
      <div class="user-main">
        <el-avatar :size="56" :src="userAvatar" />
        <div>
          <h3>{{ user.userId || user.username || '未登录' }}</h3>
          <p>用户ID：{{ user.userId || '-' }} ｜ 用户编号：{{ user.userNo || '-' }}</p>
          <p>邮箱：{{ user.email || '-' }} ｜ 手机：{{ user.phone || '-' }}</p>
        </div>
      </div>
    </div>

    <div class="page-card">
      <h3 class="block-title">关注与粉丝</h3>
      <div class="follow-toolbar">
        <el-input
          v-model="followSearchKeyword"
          placeholder="搜索用户ID/用户名/用户编号"
          clearable
          style="max-width: 320px"
          @keyup.enter="searchFollowUsers"
        />
        <el-button type="primary" :loading="loading.followSearch" @click="searchFollowUsers">搜索用户</el-button>
        <el-button @click="reloadFollowData" :loading="loading.followList">刷新列表</el-button>
      </div>

      <div v-if="followSearchResults.length > 0" class="follow-search-list">
        <div v-for="item in followSearchResults" :key="item.id" class="follow-search-item">
          <div>
            <strong>{{ item.userId || item.username || ('#' + item.id) }}</strong>
            <span class="muted">（用户编号：{{ item.userNo || '-' }}）</span>
          </div>
          <el-button
            size="small"
            :type="item.followedByCurrentUser ? 'info' : 'primary'"
            :loading="followActionLoading[item.id] === true"
            @click="toggleFollow(item)"
          >
            {{ item.followedByCurrentUser ? '取消关注' : '关注' }}
          </el-button>
        </div>
      </div>

      <el-tabs v-model="followTab" class="follow-tabs">
        <el-tab-pane :label="`我的关注 (${followSummary.followingCount})`" name="following">
          <el-empty v-if="followingRows.length === 0" description="暂无关注用户" />
          <div v-else class="follow-list">
            <div v-for="item in followingRows" :key="`g-${item.id}`" class="follow-item">
              <div>
                <strong>{{ item.userId || item.username || ('#' + item.id) }}</strong>
                <span class="muted">（用户编号：{{ item.userNo || '-' }}）</span>
              </div>
              <el-button size="small" type="info" @click="unfollow(item.id)">取消关注</el-button>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane :label="`我的粉丝 (${followSummary.followerCount})`" name="followers">
          <el-empty v-if="followerRows.length === 0" description="暂无粉丝" />
          <div v-else class="follow-list">
            <div v-for="item in followerRows" :key="`f-${item.id}`" class="follow-item">
              <div>
                <strong>{{ item.userId || item.username || ('#' + item.id) }}</strong>
                <span class="muted">（用户编号：{{ item.userNo || '-' }}）</span>
              </div>
              <el-button size="small" type="primary" @click="follow(item.id)">回关</el-button>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <div class="page-card">
      <h3 class="block-title">快捷入口</h3>
      <div class="quick-grid">
        <el-button @click="router.push('/lost-found')">我的失物发布</el-button>
        <el-button @click="router.push('/activities')">我的活动发布</el-button>
        <el-button @click="router.push('/market')">我的商品/订单/收藏</el-button>
        <el-button @click="router.push('/messages')">消息通知</el-button>
        <el-button @click="router.push('/help')">帮助中心</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchCurrentUser, fetchFollowSummary, followUser, pageFollowers, pageFollowing, searchUsers, unfollowUser } from '../api/user'
import { pageLostFoundItems } from '../api/lostFound'
import { pageActivities } from '../api/activity'
import { pageFavoriteProducts, pageOrders, pageProducts } from '../api/market'
import { getUser, setUser } from '../utils/auth'
import { normalizeImageUrl } from '../utils/image'

const router = useRouter()
const loading = reactive({
  summary: false,
  followList: false,
  followSearch: false
})
const defaultAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const defaultHeroCover = ''
const user = reactive({
  id: Number(getUser()?.id || 0),
  username: getUser()?.username || '',
  userId: getUser()?.userId || '',
  userNo: getUser()?.userNo || '',
  email: getUser()?.email || '',
  phone: getUser()?.phone || '',
  avatarUrl: getUser()?.avatarUrl || '',
  homepageCover: getUser()?.homepageCover || ''
})

const summary = reactive({
  lostFoundTotal: 0,
  activityTotal: 0,
  productTotal: 0,
  orderTotal: 0,
  favoriteTotal: 0,
  followerTotal: 0,
  followingTotal: 0
})

const followSummary = reactive({
  followerCount: 0,
  followingCount: 0
})
const followTab = ref('following')
const followingRows = ref([])
const followerRows = ref([])
const followSearchKeyword = ref('')
const followSearchResults = ref([])
const followActionLoading = reactive({})

const statCards = computed(() => ([
  { label: '失物发布', value: summary.lostFoundTotal },
  { label: '活动发布', value: summary.activityTotal },
  { label: '商品发布', value: summary.productTotal },
  { label: '我的订单', value: summary.orderTotal },
  { label: '我的收藏', value: summary.favoriteTotal },
  { label: '我的粉丝', value: summary.followerTotal },
  { label: '我的关注', value: summary.followingTotal }
]))

const heroCardStyle = computed(() => {
  const cover = normalizeImageUrl(user.homepageCover || '')
  if (!cover) {
    return {
      background: 'linear-gradient(135deg, #0f172a 0%, #2563eb 52%, #14b8a6 100%)'
    }
  }
  return {
    backgroundImage: `linear-gradient(135deg, rgba(15, 23, 42, 0.72), rgba(37, 99, 235, 0.35)), url('${cover}')`,
    backgroundSize: 'cover',
    backgroundPosition: 'center'
  }
})

const userAvatar = computed(() => {
  return normalizeImageUrl(user.avatarUrl || '') || defaultAvatar
})

const unwrap = (resp, msg) => {
  if (!resp || Number(resp.code) !== 0) {
    throw new Error(resp?.message || msg)
  }
  return resp.data || {}
}

const loadUser = async () => {
  const data = unwrap(await fetchCurrentUser(), '加载个人信息失败')
  user.id = Number(data.id || 0)
  user.username = data.username || ''
  user.userId = data.userId || ''
  user.userNo = data.userNo || ''
  user.email = data.email || ''
  user.phone = data.phone || ''
  user.avatarUrl = data.avatarUrl || ''
  user.homepageCover = data.homepageCover || defaultHeroCover
  setUser({ ...data })
}

const loadSummary = async () => {
  if (!user.id) return
  loading.summary = true
  try {
    const [lostFoundRes, activityRes, productRes, orderRes, favRes] = await Promise.all([
      pageLostFoundItems({ page: 1, size: 1, publisherUserId: user.id }),
      pageActivities({ page: 1, size: 1, publisherUserId: user.id }),
      pageProducts({ page: 1, size: 1, sellerUserId: user.id, viewerUserId: user.id }),
      pageOrders({ page: 1, size: 1, userId: user.id, role: 'all' }),
      pageFavoriteProducts({ page: 1, size: 1, userId: user.id })
    ])

    summary.lostFoundTotal = Number(unwrap(lostFoundRes, '加载失物统计失败').total || 0)
    summary.activityTotal = Number(unwrap(activityRes, '加载活动统计失败').total || 0)
    summary.productTotal = Number(unwrap(productRes, '加载商品统计失败').total || 0)
    summary.orderTotal = Number(unwrap(orderRes, '加载订单统计失败').total || 0)
    summary.favoriteTotal = Number(unwrap(favRes, '加载收藏统计失败').total || 0)
  } catch (error) {
    ElMessage.error(error.message || '加载统计失败')
  } finally {
    loading.summary = false
  }
}

const loadFollowSummary = async () => {
  if (!user.id) return
  const data = unwrap(await fetchFollowSummary(user.id), '加载关注统计失败')
  followSummary.followerCount = Number(data.followerCount || 0)
  followSummary.followingCount = Number(data.followingCount || 0)
  summary.followerTotal = followSummary.followerCount
  summary.followingTotal = followSummary.followingCount
}

const loadFollowLists = async () => {
  if (!user.id) return
  loading.followList = true
  try {
    const [followingRes, followersRes] = await Promise.all([
      pageFollowing(user.id, { page: 1, size: 20 }),
      pageFollowers(user.id, { page: 1, size: 20 })
    ])
    followingRows.value = unwrap(followingRes, '加载关注列表失败').records || []
    followerRows.value = unwrap(followersRes, '加载粉丝列表失败').records || []
  } catch (error) {
    ElMessage.error(error.message || '加载关注列表失败')
  } finally {
    loading.followList = false
  }
}

const reloadFollowData = async () => {
  try {
    await loadFollowSummary()
    await loadFollowLists()
  } catch (error) {
    ElMessage.error(error.message || '加载关注数据失败')
  }
}

const setFollowActionLoading = (uid, value) => {
  followActionLoading[uid] = value
}

const follow = async (targetUserId) => {
  try {
    setFollowActionLoading(targetUserId, true)
    unwrap(await followUser(targetUserId), '关注失败')
    ElMessage.success('已关注')
    await reloadFollowData()
    for (const row of followSearchResults.value) {
      if (Number(row.id) === Number(targetUserId)) {
        row.followedByCurrentUser = true
      }
    }
  } catch (error) {
    ElMessage.error(error.message || '关注失败')
  } finally {
    setFollowActionLoading(targetUserId, false)
  }
}

const unfollow = async (targetUserId) => {
  try {
    setFollowActionLoading(targetUserId, true)
    unwrap(await unfollowUser(targetUserId), '取消关注失败')
    ElMessage.success('已取消关注')
    await reloadFollowData()
    for (const row of followSearchResults.value) {
      if (Number(row.id) === Number(targetUserId)) {
        row.followedByCurrentUser = false
      }
    }
  } catch (error) {
    ElMessage.error(error.message || '取消关注失败')
  } finally {
    setFollowActionLoading(targetUserId, false)
  }
}

const toggleFollow = async (item) => {
  if (item.followedByCurrentUser) {
    await unfollow(item.id)
  } else {
    await follow(item.id)
  }
}

const searchFollowUsers = async () => {
  const keyword = (followSearchKeyword.value || '').trim()
  if (!keyword) {
    followSearchResults.value = []
    return
  }
  loading.followSearch = true
  try {
    const data = unwrap(await searchUsers({ keyword, size: 20 }), '搜索失败')
    const result = Array.isArray(data) ? data : []
    for (const item of result) {
      item.followedByCurrentUser = followingRows.value.some((row) => Number(row.id) === Number(item.id))
    }
    followSearchResults.value = result
  } catch (error) {
    ElMessage.error(error.message || '搜索失败')
  } finally {
    loading.followSearch = false
  }
}

const refreshAll = async () => {
  try {
    await loadUser()
  } catch (error) {
    ElMessage.error(error.message || '加载个人信息失败')
  }
  await loadSummary()
  await reloadFollowData()
}

const goProfile = () => {
  router.push('/profile')
}

onMounted(refreshAll)
</script>

<style scoped>
.personal-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.hero-card {
  background: linear-gradient(135deg, #0f172a 0%, #2563eb 52%, #14b8a6 100%);
  color: #fff;
  border-radius: 14px;
  padding: 20px;
  display: flex;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-card h2 {
  margin: 0;
}

.hero-card p {
  margin: 8px 0 0;
  opacity: 0.9;
  font-size: 13px;
}

.hero-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.stat-row {
  margin: 0 !important;
}

.stat-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px;
}

.label {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.value {
  margin: 8px 0 0;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.user-card {
  display: flex;
  align-items: center;
}

.user-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-main h3 {
  margin: 0;
}

.user-main p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.block-title {
  margin: 0 0 12px;
  font-size: 16px;
}

.follow-toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.follow-search-list,
.follow-list {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  margin-bottom: 10px;
}

.follow-search-item,
.follow-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
}

.follow-search-item:last-child,
.follow-item:last-child {
  border-bottom: none;
}

.muted {
  color: #94a3b8;
  font-size: 12px;
}

.quick-grid {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>


