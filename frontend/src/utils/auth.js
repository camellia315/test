import { ref } from 'vue'

const TOKEN_KEY = 'campus_token'
const USER_KEY = 'campus_user'
const ROLE_KEY = 'campus_roles'

function readUserFromStorage() {
  if (typeof window === 'undefined') return null
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object') {
      return parsed
    }
    return null
  } catch {
    return null
  }
}

const userRef = ref(readUserFromStorage())
const rolesRef = ref(readRolesFromStorage())

function readRolesFromStorage() {
  if (typeof window === 'undefined') return []
  const raw = localStorage.getItem(ROLE_KEY)
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      return parsed.filter((item) => typeof item === 'string' && item.trim())
    }
    return []
  } catch {
    return []
  }
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token || '')
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export function getUser() {
  return userRef.value
}

export function getUserRef() {
  return userRef
}

export function setUser(user) {
  const normalizedUser = user && typeof user === 'object' ? user : null
  if (normalizedUser) {
    localStorage.setItem(USER_KEY, JSON.stringify(normalizedUser))
  } else {
    localStorage.removeItem(USER_KEY)
  }
  userRef.value = normalizedUser
}

export function clearUser() {
  localStorage.removeItem(USER_KEY)
  userRef.value = null
}

export function getRoles() {
  return Array.isArray(rolesRef.value) ? rolesRef.value : []
}

export function getRolesRef() {
  return rolesRef
}

export function setRoles(roles) {
  const normalized = Array.isArray(roles)
    ? roles.filter((item) => typeof item === 'string' && item.trim())
    : []
  localStorage.setItem(ROLE_KEY, JSON.stringify(normalized))
  rolesRef.value = normalized
}

export function clearRoles() {
  localStorage.removeItem(ROLE_KEY)
  rolesRef.value = []
}

export function clearAuth() {
  clearToken()
  clearUser()
  clearRoles()
}

export function isLoggedIn() {
  return !!getToken()
}

if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key === USER_KEY) {
      userRef.value = readUserFromStorage()
    }
    if (event.key === ROLE_KEY) {
      rolesRef.value = readRolesFromStorage()
    }
  })
}
