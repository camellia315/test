<template>
  <router-view v-if="isLoginPage" />

  <el-container v-else style="height: 100vh">
    <el-aside width="240px" class="main-aside">
      <div class="logo-area">
        <el-icon :size="24" color="#4f46e5"><School /></el-icon>
        <span class="logo-text">校园服务平台</span>
      </div>

      <el-menu
        router
        :default-active="menuActivePath"
        background-color="#0f172a"
        text-color="#94a3b8"
        active-text-color="#fff"
        class="custom-menu"
      >
        <el-menu-item index="/dashboard"><el-icon><House /></el-icon><span>首页</span></el-menu-item>
        <el-menu-item index="/lost-found"><el-icon><Search /></el-icon><span>失物招领</span></el-menu-item>
        <el-menu-item index="/activities"><el-icon><Trophy /></el-icon><span>校园活动</span></el-menu-item>
        <el-menu-item index="/market">
          <el-icon><Shop /></el-icon>
          <span>二手市场</span>
          <el-badge v-if="marketUnreadCount > 0" :value="marketUnreadCount" class="menu-badge" />
        </el-menu-item>
        <el-menu-item index="/messages">
          <el-icon><Message /></el-icon>
          <span>消息通知</span>
          <el-badge v-if="unreadCount > 0" :value="unreadCount" class="menu-badge" />
        </el-menu-item>
        <el-menu-item index="/chat-center"><el-icon><ChatDotRound /></el-icon><span>聊天中心</span></el-menu-item><el-menu-item index="/personal"><el-icon><User /></el-icon><span>个人中心</span></el-menu-item>
        <el-menu-item index="/help"><el-icon><QuestionFilled /></el-icon><span>帮助中心</span></el-menu-item>
      </el-menu>

      <div v-if="isAdmin" class="menu-group-label">管理功能（仅管理员）</div>
      <el-menu
        v-if="isAdmin"
        router
        :default-active="menuActivePath"
        background-color="#0f172a"
        text-color="#94a3b8"
        active-text-color="#fff"
        class="custom-menu admin-menu"
      >
        <el-menu-item index="/admin/users"><el-icon><UserFilled /></el-icon><span>用户管理</span></el-menu-item>
        <el-menu-item index="/admin/content-audit"><el-icon><Checked /></el-icon><span>内容审核</span></el-menu-item>
        <el-menu-item index="/admin/analytics"><el-icon><DataAnalysis /></el-icon><span>数据分析</span></el-menu-item>
        <el-menu-item index="/admin/settings"><el-icon><Setting /></el-icon><span>系统设置</span></el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="main-header">
        <div class="header-left">
          <span class="breadcrumb">首页 / {{ currentRouteName }}</span>
        </div>
        <div class="header-right">
          <el-badge :value="unreadCount" :hidden="unreadCount <= 0" class="header-icon" @click="router.push('/messages')">
            <el-icon :size="20"><Bell /></el-icon>
          </el-badge>
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" :src="currentUserAvatar" />
              <span class="username">{{ currentUsername }}</span>
              <el-icon><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  CaretBottom,
  Checked,
  DataAnalysis,
  House,
  ChatDotRound,
  Message,
  QuestionFilled,
  School,
  Search,
  Setting,
  Shop,
  Trophy,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import { fetchUnreadNotificationCount, logoutUser } from './api/user'
import { getUnreadSummary } from './api/market'
import { clearAuth, getRolesRef, getUserRef, isLoggedIn } from './utils/auth'
import { normalizeImageUrl } from './utils/image'

const route = useRoute()
const router = useRouter()
const userRef = getUserRef()
const rolesRef = getRolesRef()
const unreadCount = ref(0)
const marketUnreadCount = ref(0)
let unreadTimer = null

const isLoginPage = computed(() => route.path === '/login')
const isAdmin = computed(() => rolesRef.value.includes('ADMIN'))
const menuActivePath = computed(() => route.path.startsWith('/lost-found/private-chat') ? '/lost-found' : route.path)

const currentRouteName = computed(() => {
  if (route.path === '/dashboard') return '首页'
  if (route.path === '/messages') return '消息通知'
  if (route.path === '/chat-center') return '聊天中心'
  if (route.path === '/help') return '帮助中心'
  if (route.path === '/personal') return '个人中心'
  if (route.path === '/profile') return '我的资料'
  if (route.path.startsWith('/space/')) return '用户主页'
  if (route.path === '/lost-found/private-chat') return '失物私信'
  if (route.path === '/lost-found') return '失物招领'
  if (route.path === '/activities') return '校园活动'
  if (route.path === '/market') return '二手市场'
  if (route.path === '/system' || route.path === '/admin/users') return '用户管理'
  if (route.path === '/admin/content-audit') return '内容审核'
  if (route.path === '/admin/analytics') return '数据分析'
  if (route.path === '/admin/settings') return '系统设置'
  return '应用'
})

const currentUsername = computed(() => {
  return userRef.value?.userId || userRef.value?.username || '未登录'
})

const currentUserAvatar = computed(() => {
  return normalizeImageUrl(userRef.value?.avatarUrl || '') || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
})

const loadUserNotificationUnread = async () => {
  if (!isLoggedIn()) {
    unreadCount.value = 0
    return
  }
  try {
    const resp = await fetchUnreadNotificationCount()
    if (resp && Number(resp.code) === 0) {
      unreadCount.value = Number(resp.data?.unreadCount || 0)
      return
    }
  } catch {
  }
  unreadCount.value = 0
}

const loadMarketChatUnread = async () => {
  if (!isLoggedIn()) {
    marketUnreadCount.value = 0
    return
  }
  const uid = Number(userRef.value?.id || 0)
  if (!uid) {
    marketUnreadCount.value = 0
    return
  }
  try {
    const resp = await getUnreadSummary({ userId: uid })
    if (resp && Number(resp.code) === 0) {
      marketUnreadCount.value = Number(resp.data?.unreadTotal || 0)
      return
    }
  } catch {
  }
  marketUnreadCount.value = 0
}

const loadUnreadCount = async () => {
  await Promise.all([loadUserNotificationUnread(), loadMarketChatUnread()])
}

const handleCommand = async (command) => {
  if (command === 'profile') {
    router.push('/personal')
    return
  }
  if (command === 'logout') {
    try {
      await logoutUser()
    } catch {
    } finally {
      clearAuth()
      unreadCount.value = 0
      marketUnreadCount.value = 0
      ElMessage.success({ message: '已退出登录', duration: 1500 })
      router.push('/login')
    }
  }
}

onMounted(() => {
  loadUnreadCount()
  unreadTimer = window.setInterval(loadUnreadCount, 10000)
})

onBeforeUnmount(() => {
  if (unreadTimer) {
    window.clearInterval(unreadTimer)
    unreadTimer = null
  }
})

watch(() => route.path, () => {
  loadUnreadCount()
})
</script>

<style scoped>
.main-aside {
  background: #0f172a;
  color: #fff;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
  z-index: 10;
}

.logo-area {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  border-bottom: 1px solid #1e293b;
  background: #0f172a;
}

.custom-menu {
  border-right: none;
  padding-top: 10px;
}

.admin-menu {
  margin-top: 0;
  padding-top: 0;
}

.menu-group-label {
  color: #64748b;
  font-size: 12px;
  padding: 10px 18px 4px;
}

:deep(.el-menu-item.is-active) {
  background-color: #1e293b !important;
  color: #fff !important;
  border-right: 3px solid var(--el-color-primary);
}

:deep(.el-menu-item:hover) {
  background-color: #1e293b !important;
}

.main-header {
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 60px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.breadcrumb {
  font-size: 14px;
  color: #6b7280;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-icon {
  cursor: pointer;
  color: #64748b;
  transition: color 0.3s;
}

.header-icon:hover {
  color: #1e293b;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #374151;
}

.username {
  font-size: 14px;
  font-weight: 500;
}

.main-content {
  background-color: #f8fafc;
  padding: 24px;
  overflow-x: hidden;
}

.menu-badge {
  margin-left: auto;
}
</style>








