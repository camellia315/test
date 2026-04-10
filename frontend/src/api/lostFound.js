import http from './http'

export function pageLostFoundItems(params) {
  return http.get('/api/lost-found/items', { params })
}

export function createLostFoundItem(payload) {
  return http.post('/api/lost-found/items', payload)
}

export function updateLostFoundStatus(id, payload) {
  return http.patch(`/api/lost-found/items/${id}/status`, payload)
}

export function deleteLostFoundItem(id, operatorUserId) {
  return http.delete(`/api/lost-found/items/${id}`, {
    params: { operatorUserId }
  })
}

export function listLostFoundComments(id) {
  return http.get(`/api/lost-found/items/${id}/comments`)
}

export function createLostFoundComment(id, payload) {
  return http.post(`/api/lost-found/items/${id}/comments`, payload)
}
