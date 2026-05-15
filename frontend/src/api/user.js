import http from './http'

export function registerUser(payload) {
  return http.post('/api/users/register', payload)
}

export function loginUser(payload) {
  return http.post('/api/users/login', payload)
}

export function fetchCurrentUser() {
  return http.get('/api/users/me')
}

export function fetchUserSpace(userId) {
  return http.get(`/api/users/${userId}/space`)
}

export function updateCurrentUser(payload) {
  return http.put('/api/users/me', payload)
}

export function changePassword(payload) {
  return http.put('/api/users/password', payload)
}

export function logoutUser() {
  return http.post('/api/users/logout')
}

export function fetchUserRoles() {
  return http.get('/api/users/roles')
}

export function searchUsers(params) {
  return http.get('/api/users/search', { params })
}

export function getAdminUserOverview() {
  return http.get('/api/users/admin/overview')
}

export function pageAdminUsers(params) {
  return http.get('/api/users/admin/page', { params })
}

export function updateAdminUserStatus(userId, status) {
  return http.put(`/api/users/admin/${userId}/status`, { status })
}

export function pageNotifications(params) {
  return http.get('/api/users/notifications', { params })
}

export function fetchUnreadNotificationCount() {
  return http.get('/api/users/notifications/unread-count')
}

export function markNotificationRead(id) {
  return http.patch(`/api/users/notifications/${id}/read`)
}

export function markAllNotificationsRead() {
  return http.patch('/api/users/notifications/read-all')
}

export function publishSystemNotification(payload) {
  return http.post('/api/users/admin/notifications/system', payload)
}

export function followUser(targetUserId) {
  return http.post(`/api/users/follows/${targetUserId}`)
}

export function unfollowUser(targetUserId) {
  return http.delete(`/api/users/follows/${targetUserId}`)
}

export function fetchFollowSummary(targetUserId) {
  return http.get(`/api/users/${targetUserId}/follow-summary`)
}

export function pageFollowers(targetUserId, params) {
  return http.get(`/api/users/${targetUserId}/followers`, { params })
}

export function pageFollowing(targetUserId, params) {
  return http.get(`/api/users/${targetUserId}/following`, { params })
}
