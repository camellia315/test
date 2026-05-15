import http from './http'

export function pageLostFoundItems(params) {
  return http.get('/api/lost-found/items', { params })
}

export function pageLostFoundHistory(params) {
  return http.get('/api/lost-found/items/history', { params })
}

export function createLostFoundItem(payload) {
  return http.post('/api/lost-found/items', payload)
}

export function getLostFoundItemDetail(id) {
  return http.get(`/api/lost-found/items/${id}`)
}

export function updateLostFoundStatus(id, payload) {
  return http.patch(`/api/lost-found/items/${id}/status`, payload)
}

export function pagePendingAuditLostFoundItems(params) {
  return http.get('/api/lost-found/items/audit/pending', { params })
}

export function auditLostFoundItem(id, payload) {
  return http.patch(`/api/lost-found/items/${id}/audit`, payload)
}

export function listLostFoundAudits(id) {
  return http.get(`/api/lost-found/items/${id}/audits`)
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

export function deleteLostFoundComment(id, commentId, operatorUserId) {
  return http.delete(`/api/lost-found/items/${id}/comments/${commentId}`, {
    params: { operatorUserId }
  })
}

export function listLostFoundPrivateSessions(params) {
  return http.get('/api/lost-found/private/sessions', { params })
}

export function pageLostFoundPrivateMessages(params) {
  return http.get('/api/lost-found/private/messages', { params })
}

export function sendLostFoundPrivateMessage(payload) {
  return http.post('/api/lost-found/private/messages', payload)
}

export function readLostFoundPrivateMessages(payload) {
  return http.post('/api/lost-found/private/read', payload)
}

export function getLostFoundPrivateUnread(params) {
  return http.get('/api/lost-found/private/unread', { params })
}
