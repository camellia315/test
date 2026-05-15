<template>
  <div class="page-card">
    <h2 class="page-title">帮助中心</h2>
    <p class="subtitle">平台介绍、使用指南和常见问题。</p>

    <el-row :gutter="14" class="intro-row">
      <el-col :xs="24" :md="12">
        <div class="intro-card">
          <h3>平台介绍</h3>
          <p>校园服务平台聚合失物招领、校园活动、二手交易、消息通知和后台管理能力，支持多角色协作。</p>
        </div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div class="intro-card">
          <h3>联系我们</h3>
          <p>邮箱：campus-service@example.com</p>
          <p>QQ群：123456789</p>
        </div>
      </el-col>
    </el-row>

    <h3 class="block-title">常见问题</h3>
    <el-collapse>
      <el-collapse-item title="1. 为什么有些菜单看不到？" name="1">
        管理类菜单仅管理员可见。普通用户登录不会显示管理员页面入口。
      </el-collapse-item>
      <el-collapse-item title="2. 发布内容后为什么不立刻显示？" name="2">
        失物招领和活动发布都需要管理员审核，通过后才会公开展示。
      </el-collapse-item>
      <el-collapse-item title="3. 聊天或消息没有实时更新怎么办？" name="3">
        请确认已启动 gateway、user-service、market-service，并检查 Redis 是否运行。
      </el-collapse-item>
      <el-collapse-item title="4. 如何设置管理员账号？" name="4">
        在 user-service 的 application.yml 里配置 auth.admin-user-ids，重启服务后生效。
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup>
</script>

<style scoped>
.subtitle {
  margin: -8px 0 12px;
  color: #64748b;
  font-size: 13px;
}

.intro-row {
  margin: 0 0 10px !important;
}

.intro-card {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  padding: 14px;
  min-height: 120px;
}

.intro-card h3 {
  margin: 0 0 10px;
}

.intro-card p {
  margin: 0 0 8px;
  color: #475569;
  line-height: 1.5;
}

.block-title {
  margin: 8px 0 10px;
}
</style>
