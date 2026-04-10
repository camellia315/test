import http from './http'

export function pageActivities(params) {
  return http.get('/api/activities', { params })
}

export function createActivity(payload) {
  return http.post('/api/activities', payload)
}

export function fetchActivityDetail(id) {
  return http.get(`/api/activities/${id}`)
}

export function listActivityCategories() {
  return http.get('/api/activities/categories')
}

export function createActivityCategory(payload) {
  return http.post('/api/activities/categories', payload)
}

export function applyActivity(id, payload) {
  return http.post(`/api/activities/${id}/apply`, payload)
}

export function cancelActivityApply(id, payload) {
  return http.post(`/api/activities/${id}/apply/cancel`, payload)
}

export function pageActivityApplies(id, params) {
  return http.get(`/api/activities/${id}/applies`, { params })
}

export function reviewActivityApply(activityId, applyId, payload) {
  return http.patch(`/api/activities/${activityId}/applies/${applyId}/review`, payload)
}

export function pagePendingAuditActivities(params) {
  return http.get('/api/activities/audit/pending', { params })
}

export function auditActivity(id, payload) {
  return http.patch(`/api/activities/${id}/audit`, payload)
}

export function listActivityAuditRecords(id) {
  return http.get(`/api/activities/${id}/audits`)
}
