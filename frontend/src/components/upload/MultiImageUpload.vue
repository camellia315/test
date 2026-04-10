<template>
  <div class="multi-upload">
    <el-upload
      action="#"
      list-type="picture-card"
      :file-list="fileList"
      :http-request="handleUpload"
      :on-remove="handleRemove"
      :before-upload="beforeUpload"
      :limit="limit"
      multiple
    >
      上传
    </el-upload>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
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
  },
  limit: {
    type: Number,
    default: 9
  }
})

const emit = defineEmits(['update:modelValue'])
const fileList = ref([])

watch(
  () => props.modelValue,
  (newVal) => {
    if (!newVal) {
      fileList.value = []
      return
    }
    fileList.value = newVal.split(',').filter(Boolean).map((url, index) => ({
      uid: `${Date.now()}-${index}`,
      name: `图片${index + 1}`,
      url
    }))
  },
  { immediate: true }
)

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
  const formData = new FormData()
  formData.append('file', options.file)
  formData.append('type', props.type)
  try {
    const resp = await uploadImage(formData)
    if (!resp || resp.code !== 0 || !resp.data?.url) {
      throw new Error(resp?.message || '上传失败')
    }
    fileList.value.push({
      uid: `${Date.now()}-${Math.random()}`,
      name: options.file.name,
      url: resp.data.url
    })
    updateValue()
    ElMessage.success({ message: '上传成功', duration: 1500 })
    options.onSuccess?.(resp)
  } catch (error) {
    ElMessage.error({ message: error.message || '上传失败', duration: 1500 })
    options.onError?.(error)
  }
}

const handleRemove = async (file) => {
  try {
    const resp = await deleteImage(file.url)
    if (resp && resp.code !== 0) {
      throw new Error(resp.message || '删除失败')
    }
    fileList.value = fileList.value.filter((item) => item.uid !== file.uid)
    updateValue()
  } catch (error) {
    ElMessage.error({ message: error.message || '删除失败', duration: 1500 })
  }
}

const updateValue = () => {
  const urls = fileList.value.map((item) => item.url).join(',')
  emit('update:modelValue', urls)
}
</script>
