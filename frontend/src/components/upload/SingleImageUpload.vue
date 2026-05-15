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
        :preview-src-list="previewSrcList"
        :initial-index="0"
        preview-teleported
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
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { deleteImage, uploadImage } from '../../api/upload'
import { buildImageCandidates } from '../../utils/image'
import { prepareImageForUpload, UPLOAD_RAW_MAX_BYTES } from '../../utils/uploadImage'

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
const localObjectUrlBoundRemote = ref('')
const remoteCandidates = ref([])
const previewSrcList = computed(() => {
  const remote = remoteCandidates.value || []
  if (localObjectUrl.value && previewUrl.value === localObjectUrl.value) {
    return [...new Set([localObjectUrl.value, ...remote])]
  }
  if (remote.length) return remote
  return previewUrl.value ? [previewUrl.value] : []
})

watch(
  () => props.modelValue,
  (newVal) => {
    imageUrl.value = newVal || ''
    remoteCandidates.value = buildImageCandidates(newVal || '')
    if (!newVal) {
      previewUrl.value = ''
      revokeLocalObjectUrl()
      return
    }
    if (localObjectUrl.value && localObjectUrlBoundRemote.value && newVal !== localObjectUrlBoundRemote.value) {
      revokeLocalObjectUrl()
    }
    previewUrl.value = remoteCandidates.value[0] || (newVal === localObjectUrlBoundRemote.value ? localObjectUrl.value : '')
  },
  { immediate: true }
)

function revokeLocalObjectUrl() {
  if (localObjectUrl.value) {
    URL.revokeObjectURL(localObjectUrl.value)
    localObjectUrl.value = ''
  }
  localObjectUrlBoundRemote.value = ''
}

const handlePreviewError = () => {
  const canUseLocalFallback = !!(localObjectUrl.value && imageUrl.value && localObjectUrlBoundRemote.value === imageUrl.value)
  if (canUseLocalFallback && previewUrl.value !== localObjectUrl.value) {
    previewUrl.value = localObjectUrl.value
    return
  }
  const candidates = remoteCandidates.value || []
  const idx = candidates.indexOf(previewUrl.value)
  if (idx >= 0 && idx < candidates.length - 1) {
    previewUrl.value = candidates[idx + 1]
    return
  }
  previewUrl.value = ''
}

const beforeUpload = (file) => {
  const isImage = file.type && file.type.startsWith('image/')
  const isLtRawLimit = file.size <= UPLOAD_RAW_MAX_BYTES

  if (!isImage) {
    ElMessage.error({ message: '只能上传图片文件', duration: 1500 })
    return false
  }
  if (!isLtRawLimit) {
    ElMessage.error({ message: `图片原始大小不能超过 ${Math.floor(UPLOAD_RAW_MAX_BYTES / 1024 / 1024)}MB`, duration: 1800 })
    return false
  }
  return true
}

const handleUpload = async (options) => {
  let uploadTargetFile = options.file
  revokeLocalObjectUrl()
  localObjectUrl.value = URL.createObjectURL(uploadTargetFile)
  previewUrl.value = localObjectUrl.value

  const formData = new FormData()
  try {
    const prepared = await prepareImageForUpload(options.file)
    uploadTargetFile = prepared.uploadFile
    if (prepared.compressed) {
      if (localObjectUrl.value) {
        URL.revokeObjectURL(localObjectUrl.value)
      }
      localObjectUrl.value = URL.createObjectURL(uploadTargetFile)
      previewUrl.value = localObjectUrl.value
      ElMessage.info({
        message: `图片已自动压缩：${(prepared.originalSize / 1024 / 1024).toFixed(1)}MB -> ${(prepared.finalSize / 1024 / 1024).toFixed(1)}MB`,
        duration: 2200
      })
    }

    formData.append('file', uploadTargetFile)
    formData.append('type', props.type)

    const resp = await uploadImage(formData)
    if (!resp || resp.code !== 0 || !resp.data?.url) {
      throw new Error(resp?.message || '上传失败')
    }
    imageUrl.value = resp.data.url
    localObjectUrlBoundRemote.value = resp.data.url
    remoteCandidates.value = buildImageCandidates(resp.data.url)
    previewUrl.value = remoteCandidates.value[0] || localObjectUrl.value || ''
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
    remoteCandidates.value = []
    revokeLocalObjectUrl()
    emit('update:modelValue', '')
    ElMessage.success({ message: '已删除', duration: 1500 })
  } catch (error) {
    ElMessage.error({ message: error.message || '删除失败', duration: 1500 })
  }
}

onBeforeUnmount(() => {
  revokeLocalObjectUrl()
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
