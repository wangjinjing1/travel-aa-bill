import { ensureLoggedIn, getDisplayName, hasSession, request } from '../../utils/request'

Page({
  data: {
    creating: false,
    showCreateForm: false,
    form: {
      destination: '',
      startDate: '',
      endDate: '',
      description: '',
      creatorName: getDisplayName(),
    },
  },

  onShow() {
    this.setData({
      'form.creatorName': getDisplayName(),
    })
  },

  async startCreatePlan() {
    if (!hasSession()) {
      try {
        const profile = await ensureLoggedIn()
        this.setData({
          'form.creatorName': profile.displayName,
        })
      } catch (error: any) {
        wx.showToast({ title: error.message || '登录失败', icon: 'none' })
        return
      }
    }

    this.setData({
      showCreateForm: true,
      'form.creatorName': getDisplayName(),
    })
  },

  hideCreatePlanForm() {
    this.setData({ showCreateForm: false })
  },

  onInput(e: any) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },

  onDateChange(e: any) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },

  async createPlan() {
    if (this.data.creating) return
    const form = this.data.form

    if (!form.destination.trim()) {
      wx.showToast({ title: '请输入旅游地点', icon: 'none' })
      return
    }
    if (!form.startDate) {
      wx.showToast({ title: '请选择开始日期', icon: 'none' })
      return
    }
    if (!form.endDate) {
      wx.showToast({ title: '请选择结束日期', icon: 'none' })
      return
    }
    if (form.endDate < form.startDate) {
      wx.showToast({ title: '结束日期不能早于开始日期', icon: 'none' })
      return
    }
    if (!form.description.trim()) {
      wx.showToast({ title: '请输入旅游详细计划', icon: 'none' })
      return
    }
    if (!form.creatorName.trim()) {
      wx.showToast({ title: '请输入创建者昵称', icon: 'none' })
      return
    }

    this.setData({ creating: true })
    wx.showLoading({ title: '创建中' })
    try {
      const plan = await request<any>({
        url: '/plans',
        method: 'POST',
        data: {
          destination: form.destination.trim(),
          startDate: form.startDate,
          endDate: form.endDate,
          description: form.description.trim(),
          creatorName: form.creatorName.trim(),
        },
      })
      this.setData({
        showCreateForm: false,
        form: {
          destination: '',
          startDate: '',
          endDate: '',
          description: '',
          creatorName: getDisplayName(),
        },
      })
      wx.navigateTo({ url: `/pages/detail/detail?id=${plan.id}` })
    } catch (error: any) {
      wx.showToast({ title: error.message || '创建失败', icon: 'none' })
    } finally {
      this.setData({ creating: false })
      wx.hideLoading()
    }
  },
})
