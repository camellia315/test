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
