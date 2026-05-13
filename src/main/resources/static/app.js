const { createApp } = Vue;

const STORAGE_KEYS = {
  token: "travelAaBillToken",
  userId: "travelAaBillUserId",
  username: "travelAaBillUsername",
  displayName: "travelAaBillDisplayName",
  role: "travelAaBillRole",
};

createApp({
  data() {
    const token = localStorage.getItem(STORAGE_KEYS.token) || "";
    return {
      session: {
        token,
        userId: localStorage.getItem(STORAGE_KEYS.userId) || "",
        username: localStorage.getItem(STORAGE_KEYS.username) || "",
        displayName: localStorage.getItem(STORAGE_KEYS.displayName) || "",
        role: localStorage.getItem(STORAGE_KEYS.role) || "",
      },
      registerMode: location.hash.startsWith("#/register"),
      authForm: { username: "", password: "", displayName: "" },
      inviteToken: "",
      inviteStatus: { checked: false, valid: true, message: "" },
      profileForm: { displayName: "" },
      planForm: { destination: "", startDate: "", endDate: "", description: "" },
      planEditForm: { destination: "", startDate: "", endDate: "", description: "" },
      expenseForm: { amount: "", spentAt: new Date().toISOString().slice(0, 10), note: "" },
      expenseFilters: { payerName: "", startDate: "", endDate: "" },
      view: "plans",
      keyword: "",
      planScope: "created",
      plans: emptyPage(),
      currentPlan: null,
      expensePage: emptyPage(),
      approvedMembers: emptyPage(),
      pendingMembers: emptyPage(),
      users: [],
      roleSavingUserId: "",
      ipBlacklist: { items: [], maxRequestsPerMinute: 0, maxRequestsPer24Hours: 0 },
      memberWindowSize: 3,
      expenseWindowSize: 5,
      showCreate: false,
      showPlanEdit: false,
      showExpense: false,
      showProfile: false,
      showDescriptionDetail: false,
      showExpenseNoteDetail: false,
      showInviteDialog: false,
      showDeleteUserConfirm: false,
      editingExpenseId: null,
      expenseNoteDetail: "",
      deletingUser: null,
      inviteUrl: "",
      message: "",
      messageTimer: null,
      sharedPlanId: null,
      sharedToken: "",
      isCreating: false,
      isJoining: false,
      isSubmittingAuth: false,
      isCheckingInvite: false,
    };
  },
  computed: {
    pageTitle() {
      if (this.view === "detail" && this.currentPlan) return this.currentPlan.destination;
      if (this.view === "invite") return "邀请注册";
      if (this.view === "users") return "用户管理";
      if (this.view === "blacklist") return "IP黑名单";
      return "计划管理";
    },
    pageSubTitle() {
      if (this.view === "detail") return "计划内容、成员审核和花费明细放在同一处。";
      if (this.view === "invite") return "生成一次性注册链接，给新成员创建账号。";
      if (this.view === "users") return "查看用户列表，并设置管理员或普通用户角色。";
      if (this.view === "blacklist") return "查看自动拉黑的 IP，并按需解除限制。";
      return "按创建和加入分开管理计划，分享链接用于邀请成员申请加入。";
    },
    userInitial() {
      const name = this.session.displayName || this.session.username || "用";
      return name.slice(0, 1).toUpperCase();
    },
    isAdmin() {
      return ["ADMIN", "SUPER_ADMIN"].includes(String(this.session.role || "").toUpperCase());
    },
    isSuperAdmin() {
      return String(this.session.role || "").toUpperCase() === "SUPER_ADMIN";
    },
    roleText() {
      const role = String(this.session.role || "").toUpperCase();
      if (role === "SUPER_ADMIN") return "超级管理员";
      if (role === "ADMIN") return "管理员";
      return "成员";
    },
    showJoinButton() {
      return this.currentPlan
        && !this.currentPlan.creator
        && !this.currentPlan.canManagePlan
        && !this.currentPlan.approved
        && Boolean(this.sharedToken || this.currentPlan.shareToken);
    },
    joinButtonDisabled() {
      return this.currentPlan && this.currentPlan.membershipStatus === "PENDING";
    },
    joinButtonText() {
      if (!this.currentPlan) return "申请加入";
      if (this.currentPlan.membershipStatus === "PENDING") return "已申请，待审核";
      if (this.currentPlan.membershipStatus === "REJECTED") return "重新申请加入";
      return "申请加入";
    },
  },
  async mounted() {
    const registerToken = new URLSearchParams(location.hash.split("?")[1] || "").get("token");
    if (registerToken) {
      this.registerMode = true;
      this.inviteToken = registerToken;
      this.checkInviteStatus(registerToken);
    }

    const query = new URLSearchParams(location.search);
    this.sharedPlanId = query.get("plan");
    this.sharedToken = query.get("shareToken") || "";

    if (this.session.token) {
      await this.refreshProfile();
      if (!this.session.token) return;
      if (this.sharedPlanId) this.openPlan(this.sharedPlanId, this.sharedToken);
      else this.loadPlans(0);
    }
  },
  methods: {
    async api(url, options = {}) {
      const headers = options.headers ? { ...options.headers } : {};
      if (!(options.body instanceof FormData)) headers["Content-Type"] = "application/json";
      if (this.session.token) headers.Authorization = `Bearer ${this.session.token}`;

      const response = await fetch(url, { ...options, headers });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        const message = data.message || `请求失败：${response.status}`;
        if (this.shouldLogoutForAuthError(response.status, message)) {
          this.clearSession();
          this.toast(message);
          const authError = new Error(message);
          authError.handled = true;
          throw authError;
        }
        throw new Error(message);
      }
      return data;
    },
    saveSession(data) {
      this.session = {
        token: data.token || this.session.token,
        userId: data.userId || this.session.userId,
        username: data.username || "",
        displayName: data.displayName || "",
        role: data.role || "",
      };
      localStorage.setItem(STORAGE_KEYS.token, this.session.token);
      localStorage.setItem(STORAGE_KEYS.userId, this.session.userId);
      localStorage.setItem(STORAGE_KEYS.username, this.session.username);
      localStorage.setItem(STORAGE_KEYS.displayName, this.session.displayName);
      localStorage.setItem(STORAGE_KEYS.role, this.session.role);
    },
    updateSessionProfile(profile) {
      this.session = {
        ...this.session,
        userId: profile.userId || this.session.userId,
        username: profile.username || this.session.username,
        displayName: profile.displayName || this.session.displayName,
        role: profile.role || this.session.role,
      };
      localStorage.setItem(STORAGE_KEYS.userId, this.session.userId);
      localStorage.setItem(STORAGE_KEYS.username, this.session.username);
      localStorage.setItem(STORAGE_KEYS.displayName, this.session.displayName);
      localStorage.setItem(STORAGE_KEYS.role, this.session.role);
    },
    async refreshProfile() {
      try {
        const profile = await this.api("/api/me");
        this.updateSessionProfile(profile);
        if (!this.isSuperAdmin && this.view === "users") {
          this.view = "plans";
          this.users = [];
        }
      } catch (error) {
        this.showError(error);
      }
    },
    async login() {
      this.isSubmittingAuth = true;
      try {
        const data = await this.api("/api/auth/login", {
          method: "POST",
          body: JSON.stringify({
            username: this.authForm.username,
            password: this.authForm.password,
          }),
        });
        this.saveSession(data);
        if (this.sharedPlanId) await this.openPlan(this.sharedPlanId, this.sharedToken);
        else await this.loadPlans(0);
      } catch (error) {
        this.showError(error);
      } finally {
        this.isSubmittingAuth = false;
      }
    },
    async register() {
      const inviteToken = this.inviteToken || new URLSearchParams(location.hash.split("?")[1] || "").get("token");
      if (!this.inviteStatus.valid) {
        this.toast(this.inviteStatus.message || "注册链接不可用");
        return;
      }
      this.isSubmittingAuth = true;
      try {
        const data = await this.api("/api/auth/register", {
          method: "POST",
          body: JSON.stringify({ ...this.authForm, inviteToken }),
        });
        history.replaceState(null, "", this.sharedPlanId
          ? `/?plan=${encodeURIComponent(this.sharedPlanId)}&shareToken=${encodeURIComponent(this.sharedToken)}`
          : "/");
        this.saveSession(data);
        if (this.sharedPlanId) await this.openPlan(this.sharedPlanId, this.sharedToken);
        else await this.loadPlans(0);
      } catch (error) {
        this.showError(error);
      } finally {
        this.isSubmittingAuth = false;
      }
    },
    shouldLogoutForAuthError(status, message) {
      return status === 401 && ["用户不存在", "登录已过期", "登录状态无效", "请先登录"].includes(message);
    },
    clearSession() {
      Object.values(STORAGE_KEYS).forEach((key) => localStorage.removeItem(key));
      this.session = { token: "", userId: "", username: "", displayName: "", role: "" };
      this.currentPlan = null;
      this.expensePage = emptyPage();
      this.approvedMembers = emptyPage();
      this.pendingMembers = emptyPage();
      this.view = "plans";
    },
    async checkInviteStatus(token) {
      if (!token) {
        this.inviteStatus = { checked: true, valid: false, message: "注册链接无效" };
        return;
      }
      this.isCheckingInvite = true;
      try {
        const status = await this.api(`/api/auth/invites/${encodeURIComponent(token)}/status`);
        this.inviteStatus = {
          checked: true,
          valid: Boolean(status.valid),
          message: status.message || (status.valid ? "注册链接可用" : "注册链接不可用"),
        };
        if (!status.valid) this.toast(this.inviteStatus.message);
      } catch (error) {
        this.inviteStatus = { checked: true, valid: false, message: error.message };
        this.showError(error);
      } finally {
        this.isCheckingInvite = false;
      }
    },
    logout() {
      this.clearSession();
    },
    async loadPlans(page = 0) {
      if (this.planScope === "all" && !this.isSuperAdmin) {
        this.planScope = "created";
      }
      try {
        const query = new URLSearchParams({
          scope: this.planScope,
          page: String(page),
          size: "12",
        });
        if (this.keyword.trim()) query.set("destination", this.keyword.trim());
        this.plans = normalizePage(await this.api(`/api/plans/manage-page?${query.toString()}`));
        this.view = "plans";
      } catch (error) {
        this.showError(error);
      }
    },
    switchPlanScope(scope) {
      if (scope === "all" && !this.isSuperAdmin) return;
      if (this.planScope === scope) return;
      this.planScope = scope;
      this.loadPlans(0);
    },
    goPlans() {
      this.currentPlan = null;
      this.expensePage = emptyPage();
      this.clearSharedLinkState();
      this.loadPlans(this.plans.page || 0);
    },
    async openUserManagement() {
      if (!this.isSuperAdmin) {
        this.toast("只有超级管理员可以访问用户管理");
        this.goPlans();
        return;
      }
      this.currentPlan = null;
      this.expensePage = emptyPage();
      this.view = "users";
      await this.loadUsers();
    },
    async loadUsers() {
      if (!this.isSuperAdmin) {
        this.users = [];
        this.toast("只有超级管理员可以访问用户管理");
        return;
      }
      try {
        const data = await this.api("/api/admin/users");
        this.users = Array.isArray(data) ? data : [];
      } catch (error) {
        this.showError(error);
      }
    },
    async changeUserRole(user, role) {
      if (!user || user.role === role) return;
      const oldRole = user.role;
      this.roleSavingUserId = user.userId;
      try {
        const updated = await this.api(`/api/admin/users/${encodeURIComponent(user.userId)}/role`, {
          method: "POST",
          body: JSON.stringify({ role }),
        });
        this.users = this.users.map((item) => item.userId === updated.userId ? updated : item);
        this.toast("用户角色已更新");
      } catch (error) {
        user.role = oldRole;
        this.showError(error);
      } finally {
        this.roleSavingUserId = "";
      }
    },
    openDeleteUserConfirm(user) {
      if (!user || user.userId === this.session.userId) return;
      this.deletingUser = user;
      this.showDeleteUserConfirm = true;
    },
    async deleteUser() {
      if (!this.deletingUser) return;
      try {
        await this.api(`/api/admin/users/${encodeURIComponent(this.deletingUser.userId)}/delete`, { method: "POST" });
        this.showDeleteUserConfirm = false;
        this.deletingUser = null;
        await this.loadUsers();
        this.toast("用户已删除");
      } catch (error) {
        this.showError(error);
      }
    },
    async openIpBlacklist() {
      this.currentPlan = null;
      this.expensePage = emptyPage();
      this.view = "blacklist";
      await this.loadIpBlacklist();
    },
    async loadIpBlacklist() {
      try {
        const data = await this.api("/api/admin/ip-blacklist");
        this.ipBlacklist = {
          items: Array.isArray(data.items) ? data.items : [],
          maxRequestsPerMinute: data.maxRequestsPerMinute || 0,
          maxRequestsPer24Hours: data.maxRequestsPer24Hours || 0,
        };
      } catch (error) {
        this.showError(error);
      }
    },
    async removeIpBlacklist(ip) {
      if (!window.confirm(`确认解除 ${ip} 的黑名单吗？`)) return;
      try {
        await this.api(`/api/admin/ip-blacklist/${encodeURIComponent(ip)}/remove`, { method: "POST" });
        await this.loadIpBlacklist();
        this.toast("已解除黑名单");
      } catch (error) {
        this.showError(error);
      }
    },
    openCreatePlan() {
      this.planForm = { destination: "", startDate: "", endDate: "", description: "" };
      this.showCreate = true;
    },
    async createPlan() {
      if (this.isCreating) return;
      this.isCreating = true;
      try {
        const created = await this.api("/api/plans", {
          method: "POST",
          headers: { "X-Request-Id": this.requestId() },
          body: JSON.stringify({
            ...this.normalizePlanForm(this.planForm),
            creatorName: this.session.displayName,
          }),
        });

        this.showCreate = false;
        this.currentPlan = created;
        this.view = "detail";
        await this.loadMembers("APPROVED", 0);
        await this.loadMembers("PENDING", 0);
        await this.loadExpenses(0);
        await this.loadPlans(0);
        this.currentPlan = created;
        this.view = "detail";
        this.toast("计划已创建");
      } catch (error) {
        this.showError(error);
      } finally {
        this.isCreating = false;
      }
    },
    async openPlan(id, shareToken = "") {
      try {
        const token = shareToken || (this.sharedPlanId === String(id) ? this.sharedToken : "");
        const suffix = token ? `?shareToken=${encodeURIComponent(token)}` : "";
        this.currentPlan = await this.api(`/api/plans/${id}${suffix}`);
        this.sharedPlanId = String(id);
        this.sharedToken = token || "";
        this.view = "detail";
        if (this.currentPlan.approved || this.currentPlan.creator) {
          await this.loadMembers("APPROVED", 0);
          if (this.currentPlan.creator) await this.loadMembers("PENDING", 0);
          if (token) this.clearSharedLinkState({ keepCurrentPlan: true });
        } else {
          this.approvedMembers = emptyPage();
          this.pendingMembers = emptyPage();
        }
        if (this.currentPlan.canViewExpenses) await this.loadExpenses(0);
        else this.expensePage = emptyPage();
      } catch (error) {
        this.showError(error);
      }
    },
    clearSharedLinkState(options = {}) {
      this.sharedPlanId = null;
      this.sharedToken = "";
      if (location.search) {
        history.replaceState(null, "", location.pathname + location.hash);
      }
      if (!options.keepCurrentPlan) this.currentPlan = null;
    },
    openPlanEditor() {
      if (!this.currentPlan) return;
      this.planEditForm = {
        destination: this.currentPlan.destination,
        startDate: this.displayDate(this.currentPlan.startDate),
        endDate: this.displayDate(this.currentPlan.endDate),
        description: this.currentPlan.description,
      };
      this.showPlanEdit = true;
    },
    async savePlanEdit() {
      try {
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/update`, {
          method: "POST",
          body: JSON.stringify(this.normalizePlanForm(this.planEditForm)),
        });
        this.showPlanEdit = false;
        await this.loadPlans(this.plans.page || 0);
        this.view = "detail";
        this.toast("计划已更新");
      } catch (error) {
        this.showError(error);
      }
    },
    async joinPlan() {
      if (!this.currentPlan || this.joinButtonDisabled || this.isJoining) return;
      const shareToken = this.sharedToken || this.currentPlan.shareToken;
      if (!shareToken) {
        this.toast("请通过创建者分享的链接申请加入");
        return;
      }
      this.isJoining = true;
      try {
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/join?shareToken=${encodeURIComponent(shareToken)}`, {
          method: "POST",
        });
        if (this.currentPlan.approved) await this.loadMembers("APPROVED", 0);
        this.clearSharedLinkState({ keepCurrentPlan: true });
        this.toast(this.currentPlan.membershipStatus === "PENDING" ? "已申请，等待审核" : "已加入计划");
      } catch (error) {
        this.showError(error);
      } finally {
        this.isJoining = false;
      }
    },
    async reviewMember(memberId, approve) {
      try {
        const action = approve ? "approve" : "reject";
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/members/${memberId}/${action}`, {
          method: "POST",
        });
        await this.loadMembers("APPROVED", 0);
        await this.loadMembers("PENDING", 0);
        await this.loadExpenses(0);
        this.toast(approve ? "已通过申请" : "已拒绝申请");
      } catch (error) {
        this.showError(error);
      }
    },
    handleApprovedMemberScroll(event) {
      this.loadNextMembersIfNeeded(event, "APPROVED");
    },
    handlePendingMemberScroll(event) {
      this.loadNextMembersIfNeeded(event, "PENDING");
    },
    async loadNextMembersIfNeeded(event, status) {
      const target = event.target;
      if (target.scrollTop + target.clientHeight < target.scrollHeight - 12) return;
      const page = status === "APPROVED" ? this.approvedMembers : this.pendingMembers;
      if (!page.hasNext) return;
      await this.loadMembers(status, page.page + 1, true);
    },
    async loadMembers(status, page = 0, append = false) {
      if (!this.currentPlan) return;
      if (status === "PENDING" && !this.currentPlan.creator) {
        this.pendingMembers = emptyPage();
        return;
      }
      try {
        const query = new URLSearchParams({
          status,
          page: String(page),
          size: String(this.memberWindowSize),
        });
        const nextPage = normalizePage(await this.api(`/api/plans/${this.currentPlan.id}/members?${query.toString()}`));
        const key = status === "APPROVED" ? "approvedMembers" : "pendingMembers";
        this[key] = append ? appendPage(this[key], nextPage) : nextPage;
      } catch (error) {
        this.showError(error);
      }
    },
    handleExpenseScroll(event) {
      this.loadNextExpensesIfNeeded(event);
    },
    async loadNextExpensesIfNeeded(event) {
      const target = event.target;
      if (target.scrollTop + target.clientHeight < target.scrollHeight - 12) return;
      if (!this.expensePage.hasNext) return;
      await this.loadExpenses(this.expensePage.page + 1, true);
    },
    async loadExpenses(page = 0, append = false) {
      if (!this.currentPlan || !this.currentPlan.canViewExpenses) {
        this.expensePage = emptyPage();
        return;
      }
      try {
        const query = new URLSearchParams({
          page: String(page),
          size: String(this.expenseWindowSize),
        });
        if (this.expenseFilters.payerName.trim()) query.set("payerName", this.expenseFilters.payerName.trim());
        const startDate = this.normalizeFilterDate(this.expenseFilters.startDate, "开始日期");
        const endDate = this.normalizeFilterDate(this.expenseFilters.endDate, "结束日期");
        if (startDate) query.set("startDate", startDate);
        if (endDate) query.set("endDate", endDate);
        const nextPage = normalizePage(await this.api(`/api/plans/${this.currentPlan.id}/expenses?${query.toString()}`));
        this.expensePage = append ? appendPage(this.expensePage, nextPage) : nextPage;
      } catch (error) {
        this.showError(error);
      }
    },
    applyExpenseFilters() {
      this.loadExpenses(0);
    },
    resetExpenseFilters() {
      this.expenseFilters = { payerName: "", startDate: "", endDate: "" };
      this.loadExpenses(0);
    },
    async exportExpenses() {
      if (!this.currentPlan) return;
      const query = new URLSearchParams();
      if (this.expenseFilters.payerName.trim()) query.set("payerName", this.expenseFilters.payerName.trim());
      try {
        const startDate = this.normalizeFilterDate(this.expenseFilters.startDate, "开始日期");
        const endDate = this.normalizeFilterDate(this.expenseFilters.endDate, "结束日期");
        if (startDate) query.set("startDate", startDate);
        if (endDate) query.set("endDate", endDate);
        const headers = {};
        if (this.session.token) headers.Authorization = `Bearer ${this.session.token}`;
        const suffix = query.toString() ? `?${query.toString()}` : "";
        const response = await fetch(`/api/plans/${this.currentPlan.id}/expenses/export${suffix}`, { headers });
        if (!response.ok) {
          const data = await response.json().catch(() => ({}));
          throw new Error(data.message || `导出失败：${response.status}`);
        }
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = `${this.currentPlan.destination}-花费明细.xlsx`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
      } catch (error) {
        this.showError(error);
      }
    },
    openExpenseCreate() {
      this.editingExpenseId = null;
      this.expenseForm = { amount: "", spentAt: this.displayDate(new Date().toISOString().slice(0, 10)), note: "" };
      this.showExpense = true;
    },
    openExpenseEdit(expense) {
      this.editingExpenseId = expense.id;
      this.expenseForm = {
        amount: String(expense.amount),
        spentAt: this.displayDate(expense.spentAt),
        note: expense.note,
      };
      this.showExpense = true;
    },
    closeExpenseDialog() {
      this.showExpense = false;
      this.editingExpenseId = null;
    },
    async saveExpense() {
      if (this.editingExpenseId) await this.updateExpense();
      else await this.addExpense();
    },
    async addExpense() {
      try {
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/expenses`, {
          method: "POST",
          headers: { "X-Request-Id": this.requestId() },
          body: JSON.stringify({
            ...this.normalizeExpenseForm(),
            payerName: this.session.displayName,
          }),
        });
        await this.loadExpenses(0);
        this.closeExpenseDialog();
      } catch (error) {
        this.showError(error);
      }
    },
    async updateExpense() {
      try {
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/expenses/${this.editingExpenseId}/update`, {
          method: "POST",
          body: JSON.stringify(this.normalizeExpenseForm()),
        });
        await this.loadExpenses(0);
        this.closeExpenseDialog();
        this.toast("花费已更新");
      } catch (error) {
        this.showError(error);
      }
    },
    async deleteExpense(expense) {
      if (!this.currentPlan || !expense) return;
      if (!window.confirm("确认删除这条花费明细吗？删除后分摊结果会重新计算。")) return;
      try {
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/expenses/${expense.id}/delete`, {
          method: "POST",
        });
        await this.loadExpenses(0);
        this.toast("花费明细已删除");
      } catch (error) {
        this.showError(error);
      }
    },
    canEditExpense(expense) {
      return Boolean(this.currentPlan?.creator || expense.userId === this.session.userId);
    },
    hasExpenseNote(note) {
      return String(note || "").trim().length > 0;
    },
    shouldShowNoteView(note) {
      return String(note || "").length > 12;
    },
    openExpenseNote(note) {
      this.expenseNoteDetail = String(note || "");
      this.showExpenseNoteDetail = true;
    },
    async closePlan() {
      const participantCount = Number(window.prompt("请输入分摊总人数", String((this.currentPlan.members || []).length || 1)));
      if (!participantCount) return;
      try {
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/close`, {
          method: "POST",
          body: JSON.stringify({ participantCount }),
        });
      } catch (error) {
        this.showError(error);
      }
    },
    async reopenPlan() {
      try {
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/reopen`, { method: "POST" });
      } catch (error) {
        this.showError(error);
      }
    },
    async deletePlan() {
      if (!this.currentPlan?.canManagePlan) return;
      const confirmed = window.confirm("删除计划会同时删除成员申请、花费明细等关联单据，确认删除吗？");
      if (!confirmed) return;
      try {
        await this.api(`/api/plans/${this.currentPlan.id}/delete`, { method: "POST" });
        this.currentPlan = null;
        this.sharedPlanId = null;
        this.sharedToken = "";
        this.approvedMembers = emptyPage();
        this.pendingMembers = emptyPage();
        this.expensePage = emptyPage();
        this.view = "plans";
        await this.loadPlans(0);
        this.toast("计划已删除");
      } catch (error) {
        this.showError(error);
      }
    },
    async createInvite() {
      try {
        const invite = await this.api("/api/auth/invites", { method: "POST" });
        this.inviteUrl = invite.url;
        this.showInviteDialog = true;
        this.toast("邀请链接已生成");
      } catch (error) {
        this.showError(error);
      }
    },
    async copyInvite() {
      if (!this.inviteUrl) return;
      await navigator.clipboard?.writeText(this.inviteUrl);
      this.toast("邀请链接已复制");
    },
    async copyShare() {
      const url = `${location.origin}/?plan=${this.currentPlan.id}&shareToken=${this.currentPlan.shareToken}`;
      await navigator.clipboard?.writeText(url);
      this.toast("分享链接已复制");
    },
    openProfile() {
      this.profileForm.displayName = this.session.displayName || "";
      this.showProfile = true;
    },
    async saveProfile() {
      try {
        const profile = await this.api("/api/me/profile", {
          method: "POST",
          body: JSON.stringify({ displayName: this.profileForm.displayName }),
        });
        this.updateSessionProfile(profile);
        this.showProfile = false;
        if (this.currentPlan) await this.openPlan(this.currentPlan.id, this.sharedToken);
        else await this.loadPlans(this.plans.page || 0);
        this.toast("显示名称已更新");
      } catch (error) {
        this.showError(error);
      }
    },
    statusText(status) {
      return status === "CLOSED" ? "已关闭" : "进行中";
    },
    emptyPlanText() {
      if (this.planScope === "all") return "还没有任何计划。";
      if (this.planScope === "created") return "还没有创建过计划。";
      return "还没有加入任何计划，打开创建者分享的链接后可以申请加入。";
    },
    money(value) {
      const number = Number(value || 0);
      return Number.isFinite(number) ? number.toFixed(2) : "0.00";
    },
    normalizeFilterDate(value, label) {
      const text = String(value || "").trim();
      if (!text) return "";
      const match = text.match(/^(\d{4})\D+(\d{1,2})\D+(\d{1,2})\D*$/);
      if (!match) {
        throw new Error(`${label}请按年/月/日填写`);
      }
      const month = Number(match[2]);
      const day = Number(match[3]);
      if (month < 1 || month > 12 || day < 1 || day > 31) {
        throw new Error(`${label}不正确`);
      }
      return `${match[1]}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
    },
    datePickerValue(value) {
      try {
        return this.normalizeFilterDate(value, "日期");
      } catch (error) {
        return "";
      }
    },
    setExpenseFilterDate(field, value) {
      this.setDateField(this.expenseFilters, field, value);
    },
    setDateField(target, field, value) {
      target[field] = this.displayDate(value);
    },
    displayDate(value) {
      return value ? String(value).replaceAll("-", "/") : "";
    },
    normalizePlanForm(form) {
      return {
        ...form,
        startDate: this.normalizeFilterDate(form.startDate, "开始日期"),
        endDate: this.normalizeFilterDate(form.endDate, "结束日期"),
      };
    },
    normalizeExpenseForm() {
      return {
        ...this.expenseForm,
        spentAt: this.normalizeFilterDate(this.expenseForm.spentAt, "花费日期"),
      };
    },
    formatDateTime(value) {
      if (!value) return "-";
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) return String(value);
      return date.toLocaleString("zh-CN", { hour12: false });
    },
    requestId() {
      if (window.crypto && typeof window.crypto.randomUUID === "function") {
        return window.crypto.randomUUID();
      }
      return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    },
    showError(error) {
      if (error?.handled) return;
      this.toast(error.message);
    },
    toast(text) {
      this.message = text;
      window.clearTimeout(this.messageTimer);
      this.messageTimer = window.setTimeout(() => {
        this.message = "";
      }, 2600);
    },
  },
}).mount("#app");

function emptyPage() {
  return {
    items: [],
    page: 0,
    size: 0,
    total: 0,
    totalPages: 0,
    hasNext: false,
  };
}

function normalizePage(page) {
  return {
    ...emptyPage(),
    ...(page || {}),
    items: Array.isArray(page?.items) ? page.items : [],
  };
}

function appendPage(currentPage, nextPage) {
  return {
    ...nextPage,
    items: [...(currentPage.items || []), ...(nextPage.items || [])],
  };
}

