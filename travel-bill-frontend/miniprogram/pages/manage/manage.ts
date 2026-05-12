import { hasSession, request } from '../../utils/request'

type PlanSummary = {
  id: number
  destination: string
  startDate: string
  endDate: string
  status: 'OPEN' | 'CLOSED'
  creatorName: string
  creator: boolean
  createdAt: string
}

type PlanPage = {
  items: PlanSummary[]
  page: number
  size: number
  total: number
  totalPages: number
  hasNext: boolean
}

type PlanScope = 'created' | 'joined'

const PLAN_PAGE_SIZE = 3

function formatDateTime(value: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value.replace('T', ' ').slice(0, 19)
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}

Page({
  data: {
    loggedIn: false,
    loading: false,
    loadingMore: false,
    scope: 'created' as PlanScope,
    plans: [] as Array<PlanSummary & { createdAtText: string }>,
    currentPage: 0,
    totalPages: 0,
    planTotal: 0,
    hasNext: false,
    filters: {
      destination: '',
      creatorName: '',
      createdDate: '',
    },
  },

  onShow() {
    const loggedIn = hasSession()
    this.setData({ loggedIn })

    if (!loggedIn) {
      this.setData({
        plans: [],
        currentPage: 0,
        totalPages: 0,
        planTotal: 0,
        hasNext: false,
      })
      return
    }

    void this.loadPlans(0, false)
  },

  switchScope(e: WechatMiniprogram.BaseEvent) {
    const scope = (e.currentTarget as any).dataset.scope as PlanScope
    if (scope === this.data.scope) return
    this.setData({
      scope,
      plans: [],
      currentPage: 0,
      totalPages: 0,
      planTotal: 0,
      hasNext: false,
    })
    void this.loadPlans(0, false)
  },

  onFilterInput(e: WechatMiniprogram.Input) {
    const field = (e.currentTarget as any).dataset.field
    this.setData({ [`filters.${field}`]: e.detail.value })
  },

  onFilterDateChange(e: WechatMiniprogram.CustomEvent) {
    this.setData({ 'filters.createdDate': (e.detail as any).value })
  },

  async applyFilters() {
    await this.loadPlans(0, false)
  },

  async clearFilters() {
    this.setData({
      filters: {
        destination: '',
        creatorName: '',
        createdDate: '',
      },
      plans: [],
      currentPage: 0,
      totalPages: 0,
      planTotal: 0,
      hasNext: false,
    })
    await this.loadPlans(0, false)
  },

  async loadPlans(page: number, append: boolean) {
    if (!hasSession()) return
    if ((!append && this.data.loading) || (append && (this.data.loading || this.data.loadingMore))) {
      return
    }

    const { destination, creatorName, createdDate } = this.data.filters
    const params = [`scope=${this.data.scope}`, `page=${page}`, `size=${PLAN_PAGE_SIZE}`]
    if (destination.trim()) {
      params.push(`destination=${encodeURIComponent(destination.trim())}`)
    }
    if (creatorName.trim()) {
      params.push(`creatorName=${encodeURIComponent(creatorName.trim())}`)
    }
    if (createdDate) {
      params.push(`createdDate=${createdDate}`)
    }

    this.setData(append ? { loadingMore: true } : { loading: true })
    try {
      const result = await request<PlanPage>({ url: `/plans/manage-page?${params.join('&')}` })
      const normalizedItems = (result.items || []).map((item) => ({
        ...item,
        createdAtText: formatDateTime(item.createdAt),
      }))
      this.setData({
        plans: append ? this.data.plans.concat(normalizedItems) : normalizedItems,
        currentPage: result.page,
        totalPages: result.totalPages,
        planTotal: result.total,
        hasNext: result.hasNext,
      })
    } catch (error: any) {
      wx.showToast({ title: error.message || '计划加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false, loadingMore: false })
    }
  },

  loadMorePlans() {
    if (!this.data.hasNext || this.data.loadingMore || this.data.loading) return
    void this.loadPlans(this.data.currentPage + 1, true)
  },

  openPlan(e: WechatMiniprogram.BaseEvent) {
    const id = (e.currentTarget as any).dataset.id
    wx.navigateTo({ url: `/pages/detail/detail?id=${id}` })
  },
})
