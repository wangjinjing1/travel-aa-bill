const { createApp } = Vue;

createApp({
  data() {
    const token = localStorage.getItem("travelAaBillToken") || "";
    return {
      session: {
        token,
        userId: localStorage.getItem("travelAaBillUserId") || "",
        username: localStorage.getItem("travelAaBillUsername") || "",
        displayName: localStorage.getItem("travelAaBillDisplayName") || "",
        role: localStorage.getItem("travelAaBillRole") || "",
      },
      registerMode: location.hash.startsWith("#/register"),
      authForm: {
        username: "",
        password: "",
        displayName: "",
      },
      view: "plans",
      keyword: "",
      plans: { items: [] },
      currentPlan: null,
      showCreate: false,
      showExpense: false,
      inviteUrl: "",
      message: "",
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
    };
  },
  computed: {
    pageTitle() {
      if (this.view === "detail" && this.currentPlan) return this.currentPlan.destination;
      if (this.view === "invite") return "邀请注册";
      return "旅游计划";
    },
    pageSubTitle() {
      if (this.view === "detail") return "文字计划、图片、成员和费用放在同一个页面里。";
      if (this.view === "invite") return "管理员生成一次性注册链接。";
      return "手机网页和 PC 端都可以直接使用。";
    },
  },
  mounted() {
    const token = new URLSearchParams(location.hash.split("?")[1] || "").get("token");
    if (token) this.registerMode = true;
    if (this.session.token) {
      const sharedPlan = new URLSearchParams(location.search).get("plan");
      if (sharedPlan) this.openPlan(sharedPlan);
      else this.loadPlans();
    }
  },
  methods: {
    async api(url, options = {}) {
      const headers = options.headers ? { ...options.headers } : {};
      if (!(options.body instanceof FormData)) headers["Content-Type"] = "application/json";
      if (this.session.token) headers.Authorization = `Bearer ${this.session.token}`;
      const response = await fetch(url, { ...options, headers });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || `请求失败：${response.status}`);
      return data;
    },
    saveSession(data) {
      this.session = data;
      localStorage.setItem("travelAaBillToken", data.token);
      localStorage.setItem("travelAaBillUserId", data.userId);
      localStorage.setItem("travelAaBillUsername", data.username || "");
      localStorage.setItem("travelAaBillDisplayName", data.displayName || "");
      localStorage.setItem("travelAaBillRole", data.role || "");
    },
    async login() {
      try {
        const data = await this.api("/api/auth/login", {
          method: "POST",
          body: JSON.stringify(this.authForm),
        });
        this.saveSession(data);
        const sharedPlan = new URLSearchParams(location.search).get("plan");
        if (sharedPlan) await this.openPlan(sharedPlan);
        else await this.loadPlans();
      } catch (error) {
        this.toast(error.message);
      }
    },
    async register() {
      const inviteToken = new URLSearchParams(location.hash.split("?")[1] || "").get("token");
      try {
        const data = await this.api("/api/auth/register", {
          method: "POST",
          body: JSON.stringify({ ...this.authForm, inviteToken }),
        });
        history.replaceState(null, "", "/");
        this.saveSession(data);
        const sharedPlan = new URLSearchParams(location.search).get("plan");
        if (sharedPlan) await this.openPlan(sharedPlan);
        else await this.loadPlans();
      } catch (error) {
        this.toast(error.message);
      }
    },
    logout() {
      localStorage.removeItem("travelAaBillToken");
      localStorage.removeItem("travelAaBillUserId");
      localStorage.removeItem("travelAaBillUsername");
      localStorage.removeItem("travelAaBillDisplayName");
      localStorage.removeItem("travelAaBillRole");
      this.session = { token: "", userId: "", username: "", displayName: "", role: "" };
      this.currentPlan = null;
      this.view = "plans";
    },
    async loadPlans() {
      try {
        const query = new URLSearchParams({ page: "0", size: "20" });
        if (this.keyword.trim()) query.set("keyword", this.keyword.trim());
        this.plans = await this.api(`/api/plans/my-page?${query.toString()}`);
        this.view = "plans";
      } catch (error) {
        this.toast(error.message);
      }
    },
    goPlans() {
      this.currentPlan = null;
      this.loadPlans();
    },
    async openPlan(id) {
      try {
        const shareToken = new URLSearchParams(location.search).get("shareToken");
        const suffix = shareToken ? `?shareToken=${encodeURIComponent(shareToken)}` : "";
        this.currentPlan = await this.api(`/api/plans/${id}${suffix}`);
        this.view = "detail";
      } catch (error) {
        this.toast(error.message);
      }
    },
    async createPlan() {
      try {
        const created = await this.api("/api/plans", {
          method: "POST",
          body: JSON.stringify({
            ...this.planForm,
            creatorName: this.session.displayName,
          }),
        });
        this.showCreate = false;
        this.planForm = { destination: "", startDate: "", endDate: "", description: "" };
        this.currentPlan = created;
        this.view = "detail";
        await this.loadPlans();
        this.currentPlan = created;
        this.view = "detail";
      } catch (error) {
        this.toast(error.message);
      }
    },
    async uploadImage(event) {
      const file = event.target.files && event.target.files[0];
      if (!file || !this.currentPlan) return;
      const formData = new FormData();
      formData.append("file", file);
      try {
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/images`, {
          method: "POST",
          body: formData,
        });
        event.target.value = "";
      } catch (error) {
        this.toast(error.message);
      }
    },
    imageUrl(image) {
      const token = this.currentPlan && this.currentPlan.shareToken ? this.currentPlan.shareToken : "";
      return `${image.url}?shareToken=${encodeURIComponent(token)}&_=${image.id}`;
    },
    async joinPlan() {
      const shareToken = new URLSearchParams(location.search).get("shareToken") || this.currentPlan.shareToken;
      try {
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/join?shareToken=${encodeURIComponent(shareToken)}`, {
          method: "POST",
        });
      } catch (error) {
        this.toast(error.message);
      }
    },
    async reviewMember(memberId, approve) {
      try {
        const action = approve ? "approve" : "reject";
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/members/${memberId}/${action}`, {
          method: "POST",
        });
      } catch (error) {
        this.toast(error.message);
      }
    },
    async addExpense() {
      try {
        this.currentPlan = await this.api(`/api/plans/${this.currentPlan.id}/expenses`, {
          method: "POST",
          body: JSON.stringify({
            ...this.expenseForm,
            payerName: this.session.displayName,
          }),
        });
        this.showExpense = false;
        this.expenseForm = { amount: "", spentAt: new Date().toISOString().slice(0, 10), note: "" };
      } catch (error) {
        this.toast(error.message);
      }
    },
    async closePlan() {
      const participantCount = Number(window.prompt("请输入分摊总人数", String(this.currentPlan.members.length || 1)));
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
    toast(text) {
      this.message = text;
      window.clearTimeout(this.messageTimer);
      this.messageTimer = window.setTimeout(() => {
        this.message = "";
      }, 2600);
    },
  },
}).mount("#app");
