import { BASE_URL, authHeader, ensureLoggedIn, getDisplayName, getUserId, hasSession, publicRequest, request } from '../../utils/request'

type MemberStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

type MemberView = {
  id: number
  userId: string
  displayName: string
  status: MemberStatus
  joinedAt: string
  reviewedAt?: string
  joinedAtText?: string
}

type ExpenseView = {
  id: number
  userId: string
  payerName: string
  amount: number
  note: string
  spentAt: string
  spentAtText?: string
}

type ExpensePage = {
  items: ExpenseView[]
  page: number
  size: number
  total: number
  totalPages: number
  hasNext: boolean
}

type SettlementView = {
  userId: string
  payerName: string
  paidTotal: number
  perPersonTotal: number
}

type PlanDetail = {
  id: number
  destination: string
  startDate: string
  endDate: string
  description: string
  creatorId: string
  creatorName: string
  shareToken: string
  status: 'OPEN' | 'CLOSED'
  participantCount?: number
  creator: boolean
  joined: boolean
  approved: boolean
  canViewExpenses: boolean
  canAddExpense: boolean
  membershipStatus?: MemberStatus
  members: MemberView[]
  pendingMembers: MemberView[]
  expenses: ExpenseView[]
  settlements: SettlementView[]
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function formatDateTime(value: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value.replace('T', ' ').slice(0, 19)
  }
  return `${date.getFullYear()}年${pad(date.getMonth() + 1)}月${pad(date.getDate())}日 ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function normalizeExpense(expense: ExpenseView): ExpenseView {
  return {
    ...expense,
    spentAtText: formatDateTime(expense.spentAt),
  }
}

function normalizeMember(member: MemberView): MemberView {
  return {
    ...member,
    joinedAtText: formatDateTime(member.joinedAt),
  }
}

function normalizePlan(plan: PlanDetail): PlanDetail {
  return {
    ...plan,
    members: (plan.members || []).map(normalizeMember),
    pendingMembers: (plan.pendingMembers || []).map(normalizeMember),
    expenses: (plan.expenses || []).map(normalizeExpense),
  }
}

Page({
  data: {
    id: 0,
    shareToken: '',
    loggedIn: false,
    currentUserId: '',
    plan: null as PlanDetail | null,
    expenses: [] as ExpenseView[],
    expenseTotal: 0,
    expensePage: 0,
    expenseHasNext: false,
    loadingExpenses: false,
    loadingPlan: false,
    joining: false,
    approvingId: 0,
    rejectingId: 0,
    exporting: false,
    saving: false,
    closing: false,
    reopening: false,
    deleting: false,
    updatingExpense: false,
    showExpenseModal: false,
    showSettlementModal: false,
    showEditExpenseModal: false,
    editExpenseAmount: '',
    editExpenseNote: '',
    editingExpenseId: 0,
    expenseForm: {
      payerName: getDisplayName(),
      amount: '',
      note: '',
      spentAt: '',
    },
    participantCount: '',
  },

  onLoad(options: Record<string, string>) {
    this.setData({
      id: Number(options.id || 0),
      shareToken: options.shareToken || '',
      loggedIn: hasSession(),
      currentUserId: getUserId(),
      'expenseForm.payerName': getDisplayName(),
    })
    void this.loadPlan()
  },

  onShow() {
    this.setData({
      loggedIn: hasSession(),
      currentUserId: getUserId(),
      'expenseForm.payerName': getDisplayName() || this.data.expenseForm.payerName,
    })
    if (this.data.id) {
      void this.loadPlan()
    }
  },

  onShareAppMessage() {
    const plan = this.data.plan
    const shareToken = plan ? plan.shareToken : this.data.shareToken
    return {
      title: plan ? `${plan.destination} 旅游计划` : '旅游账单助手',
      path: `/pages/detail/detail?id=${this.data.id}&shareToken=${shareToken}`,
    }
  },

  async loadPlan() {
    const query = this.data.shareToken ? `?shareToken=${this.data.shareToken}` : ''
    this.setData({ loadingPlan: true, loggedIn: hasSession() })
    try {
      const loader = hasSession() ? request<PlanDetail> : publicRequest<PlanDetail>
      const rawPlan = await loader({ url: `/plans/${this.data.id}${query}` })
      const plan = normalizePlan(rawPlan)
      this.setData({
        plan,
        participantCount: plan.participantCount ? String(plan.participantCount) : String(plan.members.length || 1),
        expenses: plan.canViewExpenses ? plan.expenses : [],
        expenseTotal: plan.canViewExpenses ? plan.expenses.length : 0,
        expensePage: 0,
        expenseHasNext: false,
        'expenseForm.payerName': getDisplayName() || this.data.expenseForm.payerName,
      })
      if (plan.canViewExpenses && hasSession()) {
        await this.loadExpenses(0)
      }
    } catch (error: any) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loadingPlan: false })
    }
  },

  async loadExpenses(page: number) {
    if (this.data.loadingExpenses || !this.data.plan || !this.data.plan.canViewExpenses) return

    this.setData({ loadingExpenses: true })
    try {
      const result = await request<ExpensePage>({
        url: `/plans/${this.data.id}/expenses?page=${page}&size=5`,
      })
      const items = (result.items || []).map(normalizeExpense)
      this.setData({
        expenses: page === 0 ? items : this.data.expenses.concat(items),
        expenseTotal: result.total,
        expensePage: result.page,
        expenseHasNext: result.hasNext,
      })
    } catch (error: any) {
      wx.showToast({ title: error.message || '账单加载失败', icon: 'none' })
    } finally {
      this.setData({ loadingExpenses: false })
    }
  },

  loadMoreExpenses() {
    if (!this.data.expenseHasNext || this.data.loadingExpenses) return
    void this.loadExpenses(this.data.expensePage + 1)
  },

  async requestJoin() {
    if (this.data.joining) return
    this.setData({ joining: true })
    try {
      if (!hasSession()) {
        await ensureLoggedIn()
        this.setData({ loggedIn: true })
      }
      await request<PlanDetail>({
        url: `/plans/${this.data.id}/join?shareToken=${this.data.shareToken}`,
        method: 'POST',
      })
      await this.loadPlan()
      wx.showToast({ title: '已提交加入申请', icon: 'success' })
    } catch (error: any) {
      wx.showToast({ title: error.message || '加入失败', icon: 'none' })
    } finally {
      this.setData({ joining: false })
    }
  },

  async approveMember(e: WechatMiniprogram.BaseEvent) {
    const memberId = Number((e.currentTarget as any).dataset.id)
    if (!memberId || this.data.approvingId) return
    this.setData({ approvingId: memberId })
    try {
      await request<PlanDetail>({
        url: `/plans/${this.data.id}/members/${memberId}/approve`,
        method: 'POST',
      })
      await this.loadPlan()
      wx.showToast({ title: '已通过', icon: 'success' })
    } catch (error: any) {
      wx.showToast({ title: error.message || '操作失败', icon: 'none' })
    } finally {
      this.setData({ approvingId: 0 })
    }
  },

  async rejectMember(e: WechatMiniprogram.BaseEvent) {
    const memberId = Number((e.currentTarget as any).dataset.id)
    if (!memberId || this.data.rejectingId) return
    this.setData({ rejectingId: memberId })
    try {
      await request<PlanDetail>({
        url: `/plans/${this.data.id}/members/${memberId}/reject`,
        method: 'POST',
      })
      await this.loadPlan()
      wx.showToast({ title: '已拒绝', icon: 'success' })
    } catch (error: any) {
      wx.showToast({ title: error.message || '操作失败', icon: 'none' })
    } finally {
      this.setData({ rejectingId: 0 })
    }
  },

  onExpenseInput(e: WechatMiniprogram.Input) {
    const field = (e.currentTarget as any).dataset.field
    this.setData({ [`expenseForm.${field}`]: e.detail.value })
  },

  onExpenseDate(e: WechatMiniprogram.CustomEvent) {
    this.setData({ 'expenseForm.spentAt': (e.detail as any).value })
  },

  onParticipantInput(e: WechatMiniprogram.Input) {
    this.setData({ participantCount: e.detail.value })
  },

  onEditExpenseInput(e: WechatMiniprogram.Input) {
    const field = (e.currentTarget as any).dataset.field
    this.setData({ [field]: e.detail.value })
  },

  noop() {},

  openExpenseModal() {
    const plan = this.data.plan
    if (!plan || !plan.canAddExpense || plan.status !== 'OPEN') return
    this.setData({
      showExpenseModal: true,
      'expenseForm.payerName': getDisplayName() || this.data.expenseForm.payerName,
    })
  },

  closeExpenseModal() {
    if (this.data.saving) return
    this.setData({ showExpenseModal: false })
  },

  openSettlementModal() {
    const plan = this.data.plan
    if (!plan || !plan.creator || plan.status !== 'OPEN') return
    this.setData({
      showSettlementModal: true,
      participantCount: this.data.participantCount || String(plan.members.length || 1),
    })
  },

  closeSettlementModal() {
    if (this.data.closing) return
    this.setData({ showSettlementModal: false })
  },

  openEditExpenseModal(e: WechatMiniprogram.BaseEvent) {
    const expenseId = Number((e.currentTarget as any).dataset.id)
    const amount = String((e.currentTarget as any).dataset.amount || '')
    const note = String((e.currentTarget as any).dataset.note || '')
    if (!expenseId) return
    this.setData({
      editingExpenseId: expenseId,
      editExpenseAmount: amount,
      editExpenseNote: note,
      showEditExpenseModal: true,
    })
  },

  closeEditExpenseModal() {
    if (this.data.updatingExpense) return
    this.setData({
      showEditExpenseModal: false,
      editingExpenseId: 0,
      editExpenseAmount: '',
      editExpenseNote: '',
    })
  },

  async saveExpenseChanges() {
    const amount = Number(this.data.editExpenseAmount)
    const note = this.data.editExpenseNote.trim()
    if (!this.data.editExpenseAmount) {
      wx.showToast({ title: '请输入花费金额', icon: 'none' })
      return
    }
    if (!Number.isFinite(amount) || amount <= 0) {
      wx.showToast({ title: '花费金额必须大于 0', icon: 'none' })
      return
    }
    if (!note) {
      wx.showToast({ title: '请输入花费用途', icon: 'none' })
      return
    }
    if (!this.data.editingExpenseId) return
    this.setData({ updatingExpense: true })
    try {
      await request<PlanDetail>({
        url: `/plans/${this.data.id}/expenses/${this.data.editingExpenseId}/update`,
        method: 'POST',
        data: {
          amount,
          note,
        },
      })
      this.setData({
        showEditExpenseModal: false,
        editingExpenseId: 0,
        editExpenseAmount: '',
        editExpenseNote: '',
      })
      await this.loadPlan()
      wx.showToast({ title: '修改成功', icon: 'success' })
    } catch (error: any) {
      wx.showToast({ title: error.message || '修改失败', icon: 'none' })
    } finally {
      this.setData({ updatingExpense: false })
    }
  },

  async confirmDeletePlan() {
    if (this.data.deleting || !this.data.plan || !this.data.plan.creator) return

    const result = await wx.showModal({
      title: '删除计划',
      content: '删除后会一并清除该计划下的成员记录、费用记录和相关单据，且无法恢复。确定删除吗？',
      confirmText: '确认删除',
      confirmColor: '#dc2626',
    })
    if (!result.confirm) return

    this.setData({ deleting: true })
    wx.showLoading({ title: '删除中' })
    try {
      await request<{ success: boolean }>({
        url: `/plans/${this.data.id}/delete`,
        method: 'POST',
      })
      wx.showToast({ title: '已删除', icon: 'success' })
      setTimeout(() => {
        wx.switchTab({ url: '/pages/manage/manage' })
      }, 300)
    } catch (error: any) {
      wx.showToast({ title: error.message || '删除失败', icon: 'none' })
    } finally {
      this.setData({ deleting: false })
      wx.hideLoading()
    }
  },

  async addExpense() {
    if (this.data.saving) return
    const form = this.data.expenseForm
    const amount = Number(form.amount)
    if (!form.amount) {
      wx.showToast({ title: '请输入花费金额', icon: 'none' })
      return
    }
    if (!Number.isFinite(amount) || amount <= 0) {
      wx.showToast({ title: '花费金额必须大于 0', icon: 'none' })
      return
    }
    if (!form.spentAt) {
      wx.showToast({ title: '请选择花费日期', icon: 'none' })
      return
    }
    if (!form.note.trim()) {
      wx.showToast({ title: '请输入花费用途', icon: 'none' })
      return
    }

    this.setData({ saving: true })
    wx.showLoading({ title: '保存中' })
    try {
      await request<PlanDetail>({
        url: `/plans/${this.data.id}/expenses`,
        method: 'POST',
        data: {
          payerName: getDisplayName() || form.payerName.trim(),
          amount,
          note: form.note.trim(),
          spentAt: form.spentAt,
        },
      })
      this.setData({
        expenseForm: {
          payerName: getDisplayName() || form.payerName.trim(),
          amount: '',
          note: '',
          spentAt: '',
        },
        showExpenseModal: false,
      })
      await this.loadPlan()
      wx.showToast({ title: '保存成功', icon: 'success' })
    } catch (error: any) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' })
    } finally {
      this.setData({ saving: false })
      wx.hideLoading()
    }
  },

  async closePlan() {
    if (this.data.closing) return
    const participantCount = Number(this.data.participantCount)
    if (!participantCount || participantCount < 1) {
      wx.showToast({ title: '请输入分摊总人数', icon: 'none' })
      return
    }

    this.setData({ closing: true })
    wx.showLoading({ title: '结算中' })
    try {
      await request<PlanDetail>({
        url: `/plans/${this.data.id}/close`,
        method: 'POST',
        data: { participantCount },
      })
      this.setData({ showSettlementModal: false })
      await this.loadPlan()
    } catch (error: any) {
      wx.showToast({ title: error.message || '关闭失败', icon: 'none' })
    } finally {
      this.setData({ closing: false })
      wx.hideLoading()
    }
  },

  async reopenPlan() {
    if (this.data.reopening) return
    this.setData({ reopening: true })
    wx.showLoading({ title: '开启中' })
    try {
      await request<PlanDetail>({
        url: `/plans/${this.data.id}/reopen`,
        method: 'POST',
      })
      await this.loadPlan()
    } catch (error: any) {
      wx.showToast({ title: error.message || '开启失败', icon: 'none' })
    } finally {
      this.setData({ reopening: false })
      wx.hideLoading()
    }
  },

  exportExcel() {
    if (this.data.exporting) return
    this.setData({ exporting: true })
    wx.showLoading({ title: '导出中' })
    authHeader()
      .then((headers) =>
        wx.downloadFile({
          url: `${BASE_URL}/plans/${this.data.id}/expenses/export`,
          header: headers,
          success: (res) => {
            if (res.statusCode !== 200) {
              wx.showToast({ title: '导出失败', icon: 'none' })
              return
            }
            wx.openDocument({
              filePath: res.tempFilePath,
              fileType: 'xlsx',
              showMenu: true,
              fail: () => wx.showToast({ title: '文件打开失败', icon: 'none' }),
            })
          },
          fail: () => wx.showToast({ title: '导出失败', icon: 'none' }),
          complete: () => {
            this.setData({ exporting: false })
            wx.hideLoading()
          },
        }),
      )
      .catch((error) => {
        this.setData({ exporting: false })
        wx.hideLoading()
        wx.showToast({ title: error.message || '导出失败', icon: 'none' })
      })
  },
})
