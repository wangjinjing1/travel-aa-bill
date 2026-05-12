export const BASE_URL = 'http://localhost:24975/api'

const USER_ID_KEY = 'travelBillUserId'
const DISPLAY_NAME_KEY = 'travelBillDisplayName'
const AVATAR_URL_KEY = 'travelBillAvatarUrl'
const TOKEN_KEY = 'travelBillAccessToken'

let loginPromise = null

function hasSession() {
  return Boolean(getAccessToken() && getUserId())
}

function getUserId() {
  return wx.getStorageSync(USER_ID_KEY) || ''
}

function getDisplayName() {
  return wx.getStorageSync(DISPLAY_NAME_KEY) || ''
}

function getAvatarUrl() {
  return wx.getStorageSync(AVATAR_URL_KEY) || ''
}

function getAccessToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function setLocalProfile(profile) {
  wx.setStorageSync(USER_ID_KEY, profile.userId)
  wx.setStorageSync(DISPLAY_NAME_KEY, profile.displayName)
  wx.setStorageSync(AVATAR_URL_KEY, profile.avatarUrl || '')
  wx.setStorageSync(TOKEN_KEY, profile.token)
}

function clearLocalProfile() {
  wx.removeStorageSync(USER_ID_KEY)
  wx.removeStorageSync(DISPLAY_NAME_KEY)
  wx.removeStorageSync(AVATAR_URL_KEY)
  wx.removeStorageSync(TOKEN_KEY)
}

async function authHeader() {
  const token = getAccessToken()
  if (!token) {
    throw new Error('请先登录')
  }
  return {
    Authorization: `Bearer ${token}`,
  }
}

function ensureLoggedIn() {
  const cachedSession = readCachedSession()
  if (cachedSession) {
    return Promise.resolve(cachedSession)
  }
  if (getAccessToken() || getUserId()) {
    clearLocalProfile()
  }
  if (loginPromise) {
    return loginPromise
  }
  loginPromise = loginWithWeChat().finally(() => {
    loginPromise = null
  })
  return loginPromise
}

async function refreshProfile(payload) {
  const requestData = {}
  if (payload.displayName !== undefined) {
    const nickname = payload.displayName.trim()
    if (!nickname) {
      throw new Error('请输入昵称')
    }
    requestData.displayName = nickname
  }
  if (payload.avatarUrl !== undefined) {
    requestData.avatarUrl = payload.avatarUrl.trim()
  }
  if (!Object.keys(requestData).length) {
    throw new Error('没有可更新的资料')
  }

  const profile = await request({
    url: '/me/profile',
    method: 'POST',
    data: requestData,
  })

  setLocalProfile({
    userId: profile.userId,
    displayName: profile.displayName,
    avatarUrl: profile.avatarUrl || '',
    token: getAccessToken(),
  })
  return profile
}

function request(options) {
  return requestWithAuth(options, true)
}

function publicRequest(options) {
  return doRequest({
    ...options,
    header: {
      'content-type': 'application/json',
      'X-Request-Id': options.method === 'POST' ? createRequestId() : '',
    },
  })
}

async function requestWithAuth(options, retryOnUnauthorized) {
  const headers = await authHeader()
  try {
    return await doRequest({
      ...options,
      header: {
        'content-type': 'application/json',
        ...headers,
        'X-Request-Id': options.method === 'POST' ? createRequestId() : '',
      },
    })
  } catch (error) {
    if (retryOnUnauthorized && error && error.statusCode === 401) {
      clearLocalProfile()
    }
    throw error
  }
}

async function loginWithWeChat() {
  const loginResult = await wxLogin()
  if (!loginResult.code) {
    throw new Error('微信登录失败，未获取到 code')
  }
  const session = await publicRequest({
    url: '/auth/login',
    method: 'POST',
    data: {
      code: loginResult.code,
    },
  })
  const normalizedSession = {
    ...session,
    avatarUrl: session.avatarUrl || '',
  }
  setLocalProfile(normalizedSession)
  return normalizedSession
}

function readCachedSession() {
  const userId = getUserId()
  const displayName = getDisplayName()
  const avatarUrl = getAvatarUrl()
  const token = getAccessToken()
  if (!userId || !token) {
    return null
  }
  return { userId, displayName, avatarUrl, token }
}

function doRequest(options) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${BASE_URL}${options.url}`,
      method: options.method || 'GET',
      data: options.data,
      header: options.header,
      success: (res) => handleResponse(res, resolve, reject),
      fail: (error) => reject(new Error(error.errMsg || '网络请求失败')),
    })
  })
}

function wxLogin() {
  return new Promise((resolve, reject) => {
    wx.login({
      success: resolve,
      fail: (error) => reject(new Error(error.errMsg || '微信登录失败')),
    })
  })
}

function handleResponse(res, resolve, reject) {
  if (res.statusCode >= 200 && res.statusCode < 300) {
    resolve(res.data)
    return
  }
  const data = res.data
  const error = new Error((data && data.message) || `请求失败: ${res.statusCode}`)
  error.statusCode = res.statusCode
  reject(error)
}

function createRequestId() {
  return `r_${Date.now()}_${Math.random().toString(16).slice(2)}`
}

module.exports = {
  BASE_URL,
  hasSession,
  getUserId,
  getDisplayName,
  getAvatarUrl,
  getAccessToken,
  setLocalProfile,
  clearLocalProfile,
  authHeader,
  ensureLoggedIn,
  refreshProfile,
  request,
  publicRequest,
}
