<template>
  <div class="profile-page">
    <el-row :gutter="20">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="card">
          <template #header>
            <div class="card-header">个人资料</div>
          </template>

          <el-form label-width="90px">
            <el-form-item label="系统ID">
              <el-input :model-value="userInfo.id || ''" disabled />
            </el-form-item>
            <el-form-item label="用户编号">
              <el-input :model-value="userInfo.userNo || ''" disabled />
            </el-form-item>
            <el-form-item label="用户名">
              <el-input :model-value="userInfo.username || ''" disabled />
            </el-form-item>
            <el-form-item label="用户ID">
              <el-input v-model="profileForm.userId" placeholder="可自定义，4-32位字母数字_-，字母开头" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
            </el-form-item>
            <el-form-item label="头像">
              <single-image-upload v-model="profileForm.avatarUrl" type="avatar" />
            </el-form-item>
            <el-form-item label="背景图">
              <single-image-upload v-model="profileForm.homepageCover" type="avatar" />
            </el-form-item>
            <el-form-item label="个人简介">
              <el-input
                v-model="profileForm.bio"
                type="textarea"
                :rows="3"
                maxlength="512"
                show-word-limit
                placeholder="写点个人介绍，让别人更快认识你"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="profileLoading" @click="submitProfile">
                保存资料
              </el-button>
              <el-button @click="loadUserInfo">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="card">
          <template #header>
            <div class="card-header">账号安全</div>
          </template>

          <el-form label-width="90px">
            <el-form-item label="旧密码">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少6位" />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="warning" :loading="passwordLoading" @click="submitPassword">
                修改密码
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import SingleImageUpload from '../components/upload/SingleImageUpload.vue'
import { changePassword, fetchCurrentUser, updateCurrentUser } from '../api/user'
import { getUser, setUser } from '../utils/auth'

const userInfo = ref(getUser() || {})
const profileLoading = ref(false)
const passwordLoading = ref(false)

const profileForm = reactive({
  userId: '',
  email: '',
  phone: '',
  avatarUrl: '',
  homepageCover: '',
  bio: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const checkResp = (resp, fallbackMessage) => {
  if (!resp || resp.code !== 0) {
    throw new Error(resp?.message || fallbackMessage)
  }
  return resp.data
}

const loadUserInfo = async () => {
  try {
    const data = checkResp(await fetchCurrentUser(), '获取用户信息失败')
    userInfo.value = data
    setUser(data)
    profileForm.userId = data.userId || ''
    profileForm.email = data.email || ''
    profileForm.phone = data.phone || ''
    profileForm.avatarUrl = data.avatarUrl || ''
    profileForm.homepageCover = data.homepageCover || ''
    profileForm.bio = data.bio || ''
  } catch (error) {
    ElMessage.error({ message: error.message || '获取用户信息失败', duration: 1500 })
  }
}

const submitProfile = async () => {
  profileLoading.value = true
  try {
    const data = checkResp(await updateCurrentUser({
      userId: profileForm.userId.trim(),
      email: profileForm.email.trim(),
      phone: profileForm.phone.trim(),
      avatarUrl: profileForm.avatarUrl || '',
      homepageCover: profileForm.homepageCover || '',
      bio: profileForm.bio || ''
    }), '更新资料失败')
    userInfo.value = data
    setUser(data)
    ElMessage.success({ message: '个人资料已更新', duration: 1200 })
  } catch (error) {
    ElMessage.error({ message: error.message || '更新资料失败', duration: 1500 })
  } finally {
    profileLoading.value = false
  }
}

const submitPassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    ElMessage.warning({ message: '请完整填写密码信息', duration: 1200 })
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning({ message: '新密码至少6位', duration: 1200 })
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning({ message: '两次输入的新密码不一致', duration: 1200 })
    return
  }

  passwordLoading.value = true
  try {
    checkResp(await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    }), '修改密码失败')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    ElMessage.success({ message: '密码修改成功', duration: 1200 })
  } catch (error) {
    ElMessage.error({ message: error.message || '修改密码失败', duration: 1500 })
  } finally {
    passwordLoading.value = false
  }
}

onMounted(() => {
  loadUserInfo()
})
</script>

<style scoped>
.profile-page {
  padding: 2px;
}

.card {
  margin-bottom: 20px;
  border-radius: 10px;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
}
</style>
