<template>
  <div class="login-page">
    <div class="login-card">
      <h2 class="title">校园服务平台</h2>
      <p class="subtitle">登录后可使用失物招领、活动和二手市场功能</p>

      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form @submit.prevent>
            <el-form-item label="账号">
              <el-input v-model="loginForm.username" placeholder="用户名 / 用户ID / 用户编号" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loginLoading" class="full-btn" @click="submitLogin">登录</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form @submit.prevent>
            <el-form-item label="用户名">
              <el-input v-model="registerForm.username" placeholder="至少3位" />
            </el-form-item>
            <el-form-item label="用户ID">
              <el-input v-model="registerForm.userId" placeholder="可自定义，4-32位字母数字_-，字母开头" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="registerForm.password" type="password" show-password placeholder="至少6位" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="registerForm.email" placeholder="可选" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="registerForm.phone" placeholder="可选" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="registerLoading" class="full-btn" @click="submitRegister">注册</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchCurrentUser, fetchUserRoles, loginUser, registerUser } from '../api/user'
import { setRoles, setToken, setUser } from '../utils/auth'

const router = useRouter()
const activeTab = ref('login')
const loginLoading = ref(false)
const registerLoading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const registerForm = reactive({
  username: '',
  userId: '',
  password: '',
  email: '',
  phone: ''
})

const checkResp = (resp, fallbackMessage) => {
  if (!resp || resp.code !== 0) {
    throw new Error(resp?.message || fallbackMessage)
  }
  return resp.data
}

const submitLogin = async () => {
  if (!loginForm.username.trim() || !loginForm.password) {
    ElMessage.warning({ message: '请输入用户名和密码', duration: 1500 })
    return
  }
  loginLoading.value = true
  try {
    const loginData = checkResp(await loginUser({
      username: loginForm.username.trim(),
      password: loginForm.password
    }), '登录失败')
    setToken(loginData.token)

    let userInfo = loginData.user
    try {
      userInfo = checkResp(await fetchCurrentUser(), '获取用户信息失败')
    } catch {
    }
    setUser(userInfo)
    try {
      const rolesData = checkResp(await fetchUserRoles(), '获取角色失败')
      setRoles(Array.isArray(rolesData?.roles) ? rolesData.roles : [])
    } catch {
      setRoles([])
    }

    ElMessage.success({ message: '登录成功', duration: 1500 })
    router.push('/dashboard')
  } catch (error) {
    ElMessage.error({ message: error.message || '登录失败', duration: 1500 })
  } finally {
    loginLoading.value = false
  }
}

const submitRegister = async () => {
  if (!registerForm.username.trim() || !registerForm.password) {
    ElMessage.warning({ message: '用户名和密码不能为空', duration: 1500 })
    return
  }
  registerLoading.value = true
  try {
    checkResp(await registerUser({
      username: registerForm.username.trim(),
      userId: registerForm.userId.trim(),
      password: registerForm.password,
      email: registerForm.email.trim(),
      phone: registerForm.phone.trim()
    }), '注册失败')
    ElMessage.success({ message: '注册成功，请登录', duration: 1500 })
    activeTab.value = 'login'
    loginForm.username = registerForm.userId.trim() || registerForm.username.trim()
    loginForm.password = ''
  } catch (error) {
    ElMessage.error({ message: error.message || '注册失败', duration: 1500 })
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e0e7ff, #f8fafc);
  padding: 20px;
}

.login-card {
  width: 420px;
  background: #fff;
  border-radius: 14px;
  padding: 26px 24px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.15);
}

.title {
  margin: 0;
}

.subtitle {
  margin: 8px 0 18px;
  color: #64748b;
  font-size: 14px;
}

.full-btn {
  width: 100%;
}
</style>
