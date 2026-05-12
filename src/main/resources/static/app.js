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
      authForm: {
        username: "",
        password: "",
        displayName: "",
      },
      profileForm: {
        displayName: "",
      },
      planForm: {
        destination: "",
        startDate: "",
        endDate: "",
        description: "",
      },
      expenseForm: {
        amount: "",
        spentAt: new Date().toISOString().slice(0, 10),
        note: "",
      },
      view: "plans",
      keyword: "",
      planScope: "created",
      plans: emptyPage(),
      currentPlan: null,
      expensePage: emptyPage(),
      approvedMembers: emptyPage(),
      pendingMembers: emptyPage(),
      memberWindowSize: 8,
      showCreate: false,
      showExpense: false,
      showProfile: false,
      inviteUrl: "",
      message: "",
      messageTimer: null,
      sharedPlanId: null,
      sharedToken: "",
      newPlanFiles: [],
      isCreating: false,
      isJoining: false,
      isSubmittingAuth: false,
    };
  },
  computed: {
    pageTitle() {
      if (this.view === "detail" && this.currentPlan) return this.currentPlan.destination;
      if (this.view === "invite") return "邀请注册";
      return "旅行计划";
    },
    pageSubTitle() {
      if (this.view === "detail") return "文字计划、图片、成员审核和花费明细放在同一处。";
      if (this.view === "invite") return "生成一次性注册链接，给新成员创建账号。";
      return "按创建和加入分开管理计划，分享链接用于邀请成员申请加入。";
    },
    userInitial() {
      const name = this.session.displayName || this.session.username || "用";
      return name.slice(0, 1).toUpperCase();
    },
    showJoinButton() {
      return this.currentPlan
        && !this.currentPlan.creator
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
  mounted() {
    const registerToken = new URLSearchParams(location.hash.split("?")[1] || "").get("token");
    if (registerToken) this.registerMode = true;

    const query = new URLSearchParams(location.search);
    this.sharedPlanId = query.get("plan");
    this.sharedToken = query.get("shareToken") || "";

    if (this.session.token) {
      this.refreshProfile();
      if (this.sharedPlanId) this.openPlan(this.sharedPlanId, this.sharedToken);
      else this.loadPlans(0);
    }
  },
  methods: {
    async api(url, options = {}) {
      const headers = options.headers ? { ...options.headers } : {};
      if (!(options.body instanceof FormData)) {
        headers["Content-Type"] = "application/json";
      }
      if (this.session.token) {
        headers.Authorization = `Bearer ${this.session.token}`;
      }

      const response = await fetch(url, { ...options, headers });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(data.message || `请求失败：${response.status}`);
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
      } catch (error) {
        this.toast(error.message);
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
        this.toast(error.message);
      } finally {
        this.isSubmittingAuth = false;
      }
    },
    async register() {
      const inviteToken = new URLSearchParams(location.hash.split("?")[1] || "").get("token");
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
        this.toast(error.message);
      } finally {
        this.isSubmittingAuth = false;
      }
    },
    logout() {
      Object.values(STORAGE_KEYS).forEach((key) => localStorage.removeItem(key));
      this.session = { token: "", userId: "", username: "", displayName: "", role: "" };
      this.currentPlan = null;
      this.view = "plans";
    },
    async loadPlans(page = 0) {
      try {
        const query = new URLSearchParams({
          scope: this.planScope,
          page: String(page),
          size: "12",
        });
        if (this.keyword.trim()) {
          query.set("destination", this.keyword.trim());
        }
        this.plans = normalizePage(await this.api(`/api/plans/manage-page?${query.toString()}`));
        this.view = "plans";
      } catch (error) {
        this.toast(error.message);
      }
    },
    switchPlanScope(scope) {
      if (this.planScope === scope) return;
      this.planScope = scope;
      this.loadPlans(0);
    },
    goPlans() {
      this.currentPlan = null;
      this.expensePage = emptyPage();
      this.loadPlans(this.plans.page || 0);
    },
    openCreatePlan() {
      this.planForm = { destination: "", startDate: "", endDate: "", description: "" };
      this.newPlanFiles = [];
      this.showCreate = true;
    },
    selectPlanImages(event) {
      this.newPlanFiles = Array.from(event.target.files || []);
    },
    async createPlan() {
      if (this.isCreating) return;
      this.isCreating = true;
      try {
        let created = await this.api("/api/plans", {
          method: "POST",
          headers: { "X-Request-Id": this.requestId() },
          body: JSON.stringify({
            ...this.planForm,
            creatorName: this.session.displayName,
          }),
        });

        if (this.newPlanFiles.length) {
          created = await this.uploadPlanFiles(created.id, this.newPlanFiles);
        }

        this.showCreate = false;
        this.newPlanFiles = [];
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
        this.toast(error.message);
      } finally {
        this.isCreating = false;
      }
    },
    async uploadPlanFiles(planId, files) {
      let latest = this.currentPlan;
      for (const file of files) {
        const formData = new FormData();
        formData.append("file", file);
        latest = await this.api(`/api/plans/${planId}/images`, {
          method: "POST",
          body: formData,
        });
      }
      return latest;
    },
    async uploadImages(event) {
      const files = Array.from(event.target.files || []);
      if (!files.length || !this.currentPlan) return;
      try {
        this.currentPlan = await this.uploadPlanFiles(this.currentPlan.id, files);
        event.target.value = "";
        this.toast("图片已上传");
      } catch (error) {
        this.toast(error.message);
      }
    },
    async openPlan(id, shareToken = "") {
      try {
        const token = shareToken || this.sharedToken || "";
        const suffix = token ? `?shareToken=${encodeURIComponent(token)}` : "";
        this.currentPlan = await this.api(`/api/plans/${id}${suffix}`);
        this.sharedPlanId = String(id);
        if (token) this.sharedToken = token;
        this.view = "detail";
        if (this.currentPlan.approved || this.currentPlan.creator) {
          await this.loadMembers("APPROVED", 0);
          if (this.currentPlan.creator) await this.loadMembers("PENDING", 0);
        } else {
          this.approvedMembers = emptyPage();
          this.pendingMembers = emptyPage();
        }
        if (this.currentPlan.canViewExpenses) {
          await this.loadExpenses(0);
        } else {
          this.expensePage = emptyPage();
        }
      } catch (error) {
        this.toast(error.message);
      }
    },
    imageUrl(image) {
      const token = this.sharedToken || (this.currentPlan ? this.currentPlan.shareToken : "");
      const query = token ? `?shareToken=${encodeURIComponent(token)}` : "";
      return `${image.url}${query}`;
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
        this.toast(this.currentPlan.membershipStatus === "PENDING" ? "已申请，等待审核" : "已加入计划");
      } catch (error) {
        this.toast(error.message);
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
        await this.loadExpenses(this.expensePage.page || 0);
        this.toast(approve ? "已通过申请" : "已拒绝申请");
      } catch (error) {
        this.toast(error.message);
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
      if (target.scrollTop + target.clientHeight < target.scrollHeight - 20) return;
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
        this[key] = append
          ? { ...nextPage, items: [...this[key].items, ...nextPage.items] }
          : nextPage;
      } catch (error) {
        this.toast(error.message);
      }
    },
    async loadExpenses(page = 0) {
      if (!this.currentPlan || !this.currentPlan.canViewExpenses) {
        this.expensePage = emptyPage();
        return;
      }
      try {
        const query = new URLSearchParams({ page: String(page), size: "10" });
        this.expensePage = normalizePage(await this.api(`/api/plans/${this.currentPlan.id}/expenses?${query.toString()}`));
      } catch (error) {
        this.toast(error.message);
      }
    },
    async addExpense() {
      try {
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/expenses`, {
          method: "POST",
          headers: { "X-Request-Id": this.requestId() },
          body: JSON.stringify({
            ...this.expenseForm,
            payerName: this.session.displayName,
          }),
        });
        await this.loadExpenses(0);
        this.showExpense = false;
        this.expenseForm = { amount: "", spentAt: new Date().toISOString().slice(0, 10), note: "" };
      } catch (error) {
        this.toast(error.message);
      }
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
        this.toast(error.message);
      }
    },
    async reopenPlan() {
      try {
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/reopen`, { method: "POST" });
      } catch (error) {
        this.toast(error.message);
      }
    },
    async createInvite() {
      try {
        const invite = await this.api("/api/auth/invites", { method: "POST" });
        this.inviteUrl = invite.url;
        await navigator.clipboard?.writeText(invite.url);
        this.toast("邀请链接已生成");
      } catch (error) {
        this.toast(error.message);
      }
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
        if (this.currentPlan) {
          await this.openPlan(this.currentPlan.id, this.sharedToken);
        } else {
          await this.loadPlans(this.plans.page || 0);
        }
        this.toast("显示名称已更新");
      } catch (error) {
        this.toast(error.message);
      }
    },
    statusText(status) {
      return status === "CLOSED" ? "已关闭" : "进行中";
    },
    money(value) {
      const number = Number(value || 0);
      return Number.isFinite(number) ? number.toFixed(2) : "0.00";
    },
    requestId() {
      if (window.crypto && typeof window.crypto.randomUUID === "function") {
        return window.crypto.randomUUID();
      }
      return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
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
