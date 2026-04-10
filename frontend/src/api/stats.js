import axios from 'axios'
import http from './http'
import { getToken } from '../utils/auth'

export function getStatsDashboard(params) {
  return http.get('/api/stats/dashboard', { params })
}

export function checkStatsStartupHealth() {
  return http.get('/api/stats/health/startup')
}

export function getStatsOverview() {
  return http.get('/api/stats/overview')
}

export function getStatsTrends(params) {
  return http.get('/api/stats/trends', { params })
}

export function getStatsRankings() {
  return http.get('/api/stats/rankings')
}

export function exportStatsReport(params) {
  const token = getToken()
  return axios.get(`${import.meta.env.VITE_API_BASE_URL || ''}/api/stats/export`, {
    params,
    responseType: 'blob',
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
}
