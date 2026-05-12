import { clearLocalProfile, ensureLoggedIn, getDisplayName, hasSession, refreshProfile, request } from '../../utils/request'

type UserProfile = {
  userId: string
  displayName: string
  avatarUrl: string
}

Page({
  data: {
    loggedIn: false,
    loading: false,
    editing: false,
    nickname: '',
    profile: null as UserProfile | null,
  },

  onShow() {
    void this.enterPage()
  },

  async enterPage() {
    if (this.data.loading) return

    if (!hasSession()) {
      await this.login(true)
      return
    }

    await this.loadProfile()
  },

  async loadProfile() {
    this.setData({ loading: true })
    try {
      const profile = await request<UserProfile>({ url: '/me' })
      this.setData({
        loggedIn: true,
        loading: false,
        profile,
        nickname: profile.displayName,
        editing: false,
      })
    } catch (error: any) {
      clearLocalProfile()
      this.setData({
        loggedIn: false,
        loading: false,
        profile: null,
        nickname: '',
        editing: false,
      })
      wx.showToast({ title: error.message || '加载资料失败', icon: 'none' })
    }
  },

  async login(silent = false) {
    if (this.data.loading) return

    this.setData({ loading: true })
    if (!silent) {
      wx.showLoading({ title: '登录中' })
    }

    try {
      const session = await ensureLoggedIn()
      this.setData({
        loggedIn: true,
        profile: {
          userId: session.userId,
          displayName: session.displayName,
          avatarUrl: session.avatarUrl,
        },
        nickname: session.displayName,
        editing: false,
      })
      await this.loadProfile()
      if (!silent) {
        wx.showToast({ title: '登录成功', icon: 'success' })
      }
    } catch (error: any) {
      this.setData({
        loggedIn: false,
        loading: false,
        profile: null,
        nickname: '',
        editing: false,
      })
      wx.showToast({ title: error.message || '登录失败', icon: 'none' })
    } finally {
      if (!silent) {
        wx.hideLoading()
      }
    }
  },

  logout() {
    clearLocalProfile()
    this.setData({
      loggedIn: false,
      loading: false,
      profile: null,
      nickname: '',
      editing: false,
    })
  },

  toggleEdit() {
    this.setData({
      editing: !this.data.editing,
      nickname: this.data.profile ? this.data.profile.displayName : getDisplayName(),
    })
  },

  onNicknameInput(e: WechatMiniprogram.Input) {
    this.setData({ nickname: e.detail.value })
  },

  async saveNickname() {
    try {
      this.setData({ loading: true })
      wx.showLoading({ title: '保存中' })
      const profile = await refreshProfile({ displayName: this.data.nickname })
      this.setData({
        loggedIn: true,
        loading: false,
        editing: false,
        nickname: profile.displayName,
        profile: {
          userId: profile.userId,
          displayName: profile.displayName,
          avatarUrl: profile.avatarUrl,
        },
      })
      wx.showToast({ title: '已保存', icon: 'success' })
    } catch (error: any) {
      this.setData({ loading: false })
      wx.showToast({ title: error.message || '保存失败', icon: 'none' })
    } finally {
      wx.hideLoading()
    }
  },
})
