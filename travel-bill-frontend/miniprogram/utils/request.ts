export const BASE_URL = 'http://localhost:24975/api'

const USER_ID_KEY = 'travelBillUserId'
const DISPLAY_NAME_KEY = 'travelBillDisplayName'
const AVATAR_URL_KEY = 'travelBillAvatarUrl'
const TOKEN_KEY = 'travelBillAccessToken'

export type UserSession = {
  userId: string
  displayName: string
  avatarUrl: string
  token: string
}

type UserProfile = {
  userId: string
  displayName: string
  avatarUrl: string
}

let loginPromise: Promise<UserSession> | null = null

export function hasSession() {
  return Boolean(getAccessToken() && getUserId())
}

export function getUserId() {
  return wx.getStorageSync(USER_ID_KEY) || ''
}

export function getDisplayName() {
  return wx.getStorageSync(DISPLAY_NAME_KEY) || ''
}

export function getAvatarUrl() {
  return wx.getStorageSync(AVATAR_URL_KEY) || ''
}

export function getAccessToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

export function setLocalProfile(profile: UserSession) {
  wx.setStorageSync(USER_ID_KEY, profile.userId)
  wx.setStorageSync(DISPLAY_NAME_KEY, profile.displayName)
  wx.setStorageSync(AVATAR_URL_KEY, profile.avatarUrl || '')
  wx.setStorageSync(TOKEN_KEY, profile.token)
}

export function clearLocalProfile() {
  wx.removeStorageSync(USER_ID_KEY)
  wx.removeStorageSync(DISPLAY_NAME_KEY)
  wx.removeStorageSync(AVATAR_URL_KEY)
  wx.removeStorageSync(TOKEN_KEY)
}

export async function authHeader() {
  const token = getAccessToken()
  if (!token) {
    throw new Error('请先登录')
  }
  return {
    Authorization: `Bearer ${token}`,
  }
}

export function ensureLoggedIn() {
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

export async function refreshProfile(payload: {
  displayName?: string
  avatarUrl?: string
}) {
  const requestData: Record<string, string> = {}
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

  const profile = await request<UserProfile>({
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

export function request<T>(options: {
  url: string
  method?: 'GET' | 'POST'
  data?: Record<string, any>
}) {
  return requestWithAuth<T>(options, true)
}

export function publicRequest<T>(options: {
  url: string
  method?: 'GET' | 'POST'
  data?: Record<string, any>
}) {
  return doRequest<T>({
    ...options,
    header: {
      'content-type': 'application/json',
      'X-Request-Id': options.method === 'POST' ? createRequestId() : '',
    },
  })
}

async function requestWithAuth<T>(
  options: {
    url: string
    method?: 'GET' | 'POST'
    data?: Record<string, any>
  },
  retryOnUnauthorized: boolean,
) {
  const headers = await authHeader()
  try {
    return await doRequest<T>({
      ...options,
      header: {
        'content-type': 'application/json',
        ...headers,
        'X-Request-Id': options.method === 'POST' ? createRequestId() : '',
      },
    })
  } catch (error: any) {
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
  const session = await publicRequest<UserSession>({
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

function readCachedSession(): UserSession | null {
  const userId = getUserId()
  const displayName = getDisplayName()
  const avatarUrl = getAvatarUrl()
  const token = getAccessToken()
  if (!userId || !token) {
    return null
  }
  return { userId, displayName, avatarUrl, token }
}

function doRequest<T>(options: {
  url: string
  method?: 'GET' | 'POST'
  data?: Record<string, any>
  header?: Record<string, any>
}) {
  return new Promise<T>((resolve, reject) => {
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
  return new Promise<WechatMiniprogram.LoginSuccessCallbackResult>((resolve, reject) => {
    wx.login({
      success: resolve,
      fail: (error) => reject(new Error(error.errMsg || '微信登录失败')),
    })
  })
}

function handleResponse<T>(
  res: WechatMiniprogram.RequestSuccessCallbackResult,
  resolve: (value: T) => void,
  reject: (reason?: any) => void,
) {
  if (res.statusCode >= 200 && res.statusCode < 300) {
    resolve(res.data as T)
    return
  }
  const data = res.data as any
  const error = new Error((data && data.message) || `请求失败: ${res.statusCode}`) as Error & { statusCode?: number }
  error.statusCode = res.statusCode
  reject(error)
}

function createRequestId() {
  return `r_${Date.now()}_${Math.random().toString(16).slice(2)}`
}
