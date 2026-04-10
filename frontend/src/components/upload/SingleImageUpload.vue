<template>
  <div class="single-upload">
    <el-upload
      action="#"
      :show-file-list="false"
      :http-request="handleUpload"
      :before-upload="beforeUpload"
      accept="image/*"
    >
      <el-image
        v-if="previewUrl"
        :src="previewUrl"
        class="preview-image"
        fit="cover"
        @error="handlePreviewError"
      >
        <template #error>
          <div class="placeholder">暂无图片</div>
        </template>
      </el-image>
      <div v-else class="placeholder">点击上传图片</div>
    </el-upload>
    <div v-if="imageUrl" class="actions">
      <el-button link type="danger" @click="removeImage">删除图片</el-button>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { deleteImage, uploadImage } from '../../api/upload'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    default: 'product'
  }
})

const emit = defineEmits(['update:modelValue'])
const imageUrl = ref(props.modelValue || '')
const previewUrl = ref('')
const localObjectUrl = ref('')

watch(
  () => props.modelValue,
  (newVal) => {
    imageUrl.value = newVal || ''
    previewUrl.value = normalizeImageUrl(newVal || '')
    if (newVal && localObjectUrl.value) {
      URL.revokeObjectURL(localObjectUrl.value)
      localObjectUrl.value = ''
    }
  }
)

const normalizeImageUrl = (url) => {
  if (!url) return ''
  const trimmed = String(url).trim()
  if (!trimmed) return ''
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://') || trimmed.startsWith('/api/upload/local')) {
    return trimmed
  }
  if (trimmed.startsWith('//')) {
    return `https:${trimmed}`
  }
  if (trimmed.includes('.clouddn.com/')) {
    return `https://${trimmed}`
  }
  return trimmed
}

const handlePreviewError = () => {
  if (localObjectUrl.value && previewUrl.value !== localObjectUrl.value) {
    previewUrl.value = localObjectUrl.value
    return
  }
  // 七牛测试域名在部分环境下可能仅 http 可访问，失败时自动尝试降级
  if (previewUrl.value.startsWith('https://') && previewUrl.value.includes('.clouddn.com/')) {
    previewUrl.value = previewUrl.value.replace('https://', 'http://')
    return
  }
  previewUrl.value = ''
}

const beforeUpload = (file) => {
  const isImage = file.type && file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 <= 5

  if (!isImage) {
    ElMessage.error({ message: '只能上传图片文件', duration: 1500 })
    return false
  }
  if (!isLt5M) {
    ElMessage.error({ message: '图片大小不能超过 5MB', duration: 1500 })
    return false
  }
  return true
}

const handleUpload = async (options) => {
  if (localObjectUrl.value) {
    URL.revokeObjectURL(localObjectUrl.value)
    localObjectUrl.value = ''
  }
  localObjectUrl.value = URL.createObjectURL(options.file)
  previewUrl.value = localObjectUrl.value

  const formData = new FormData()
  formData.append('file', options.file)
  formData.append('type', props.type)

  try {
    const resp = await uploadImage(formData)
    if (!resp || resp.code !== 0 || !resp.data?.url) {
      throw new Error(resp?.message || '上传失败')
    }
    imageUrl.value = resp.data.url
    previewUrl.value = normalizeImageUrl(resp.data.url)
    emit('update:modelValue', resp.data.url)
    ElMessage.success({ message: '上传成功', duration: 1500 })
    options.onSuccess?.(resp)
  } catch (error) {
    ElMessage.error({ message: error.message || '上传失败', duration: 1500 })
    options.onError?.(error)
  }
}

const removeImage = async () => {
  if (!imageUrl.value) {
    return
  }
  try {
    const resp = await deleteImage(imageUrl.value)
    if (resp && resp.code !== 0) {
      throw new Error(resp.message || '删除失败')
    }
    imageUrl.value = ''
    previewUrl.value = ''
    if (localObjectUrl.value) {
      URL.revokeObjectURL(localObjectUrl.value)
      localObjectUrl.value = ''
    }
    emit('update:modelValue', '')
    ElMessage.success({ message: '已删除', duration: 1500 })
  } catch (error) {
    ElMessage.error({ message: error.message || '删除失败', duration: 1500 })
  }
}

onBeforeUnmount(() => {
  if (localObjectUrl.value) {
    URL.revokeObjectURL(localObjectUrl.value)
    localObjectUrl.value = ''
  }
})
</script>

<style scoped>
.single-upload {
  width: 220px;
}

.placeholder {
  width: 200px;
  height: 140px;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  background: #fafafa;
}

.preview-image {
  width: 200px;
  height: 140px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  background: #f8fafc;
}

.actions {
  margin-top: 6px;
}
</style>
