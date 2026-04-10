import http from './http'

export function pageProducts(params) {
  return http.get('/api/market/products', { params })
}

export function getProductDetail(id, params) {
  return http.get(`/api/market/products/${id}`, { params })
}

export function createProduct(payload) {
  return http.post('/api/market/products', payload)
}

export function updateProduct(id, payload) {
  return http.put(`/api/market/products/${id}`, payload)
}

export function updateProductStatus(id, payload) {
  return http.put(`/api/market/products/${id}/status`, payload)
}

export function deleteProduct(id, operatorUserId) {
  return http.delete(`/api/market/products/${id}`, {
    params: { operatorUserId }
  })
}

export function listProductCategories() {
  return http.get('/api/market/categories')
}

export function createProductCategory(payload) {
  return http.post('/api/market/categories', payload)
}

export function toggleFavorite(payload) {
  return http.post('/api/market/favorites/toggle', payload)
}

export function pageFavoriteProducts(params) {
  return http.get('/api/market/favorites', { params })
}

export function createOrder(payload) {
  return http.post('/api/market/orders', payload)
}

export function updateOrderStatus(id, payload) {
  return http.patch(`/api/market/orders/${id}/status`, payload)
}

export function pageOrders(params) {
  return http.get('/api/market/orders', { params })
}

export function getHotProducts(params) {
  return http.get('/api/market/recommend/hot', { params })
}

export function getForYouProducts(params) {
  return http.get('/api/market/recommend/for-you', { params })
}

export function listChatSessions(params) {
  return http.get('/api/market/chat/sessions', { params })
}

export function pageChatMessages(params) {
  return http.get('/api/market/chat/messages', { params })
}

export function sendChatMessage(payload) {
  return http.post('/api/market/chat/messages', payload)
}

export function markChatRead(payload) {
  return http.post('/api/market/chat/read', payload)
}

export function getUnreadSummary(params) {
  return http.get('/api/market/chat/unread', { params })
}

