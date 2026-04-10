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
        :default-active="$route.path"
        background-color="#0f172a"
        text-color="#94a3b8"
        active-text-color="#fff"
        class="custom-menu"
      >
        <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon><span>数据看板</span></el-menu-item>
        <el-menu-item index="/lost-found"><el-icon><Search /></el-icon><span>失物招领</span></el-menu-item>
        <el-menu-item index="/activities"><el-icon><Trophy /></el-icon><span>校园活动</span></el-menu-item>
        <el-menu-item index="/market"><el-icon><Shop /></el-icon><span>二手市场</span></el-menu-item>
        <el-menu-item index="/system"><el-icon><Setting /></el-icon><span>系统管理</span></el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="main-header">
        <div class="header-left">
          <span class="breadcrumb">首页 / {{ currentRouteName }}</span>
        </div>
        <div class="header-right">
          <el-badge is-dot class="header-icon">
            <el-icon :size="20"><Bell /></el-icon>
          </el-badge>
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
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
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, CaretBottom, Odometer, School, Search, Setting, Shop, Trophy } from '@element-plus/icons-vue'
import { logoutUser } from './api/user'
import { clearAuth, getUserRef } from './utils/auth'

const route = useRoute()
const router = useRouter()
const userRef = getUserRef()

const isLoginPage = computed(() => route.path === '/login')

const currentRouteName = computed(() => {
  if (route.path === '/dashboard') return '数据看板'
  if (route.path === '/profile') return '个人中心'
  if (route.path === '/lost-found') return '失物招领'
  if (route.path === '/activities') return '校园活动'
  if (route.path === '/market') return '二手市场'
  if (route.path === '/system') return '系统管理'
  return '应用'
})

const currentUsername = computed(() => {
  return userRef.value?.username || '未登录'
})

const handleCommand = async (command) => {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    try {
      await logoutUser()
    } catch {
    } finally {
      clearAuth()
      ElMessage.success({ message: '已退出登录', duration: 1500 })
      router.push('/login')
    }
  }
}
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
</style>
