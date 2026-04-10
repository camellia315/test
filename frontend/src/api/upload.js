import http from './http'

export function uploadImage(formData) {
  return http.post('/api/upload/image', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function uploadImages(formData) {
  return http.post('/api/upload/images', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function deleteImage(url) {
  return http.delete('/api/upload/image', { params: { url } })
}
