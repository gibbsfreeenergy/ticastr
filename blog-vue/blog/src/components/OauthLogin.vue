<template>
  <div class="oauth-background">
    <div v-if="loading" id="preloader_1">
      <span></span>
      <span></span>
      <span></span>
      <span></span>
      <span></span>
    </div>
    <div v-else class="oauth-card">
      <div class="oauth-title">登录状态</div>
      <p>{{ message }}</p>
      <button type="button" @click="backToBlog">返回博客</button>
    </div>
  </div>
</template>

<script>
export default {
  data: function() {
    return {
      loading: true,
      message: "正在处理登录..."
    };
  },
  created() {
    this.$store.state.loginFlag = false;
    if (this.$route.path == "/oauth/login/qq") {
      this.handleQQLogin();
    } else {
      this.handleWeiboLogin();
    }
  },
  methods: {
    backToBlog() {
      const loginUrl = this.$store.state.loginUrl;
      this.$router.push({ path: loginUrl || "/" });
    },
    finish(type, message, redirect = false) {
      this.loading = false;
      this.message = message;
      this.$toast({ type: type, message: message });
      if (redirect) {
        window.setTimeout(() => this.backToBlog(), 400);
      }
    },
    completeLogin(data, message) {
      this.$store.commit("login", data);
      if (data.email == null) {
        this.finish("warnning", "请绑定邮箱以便及时收到回复", true);
      } else {
        this.finish("success", message || "登录成功", true);
      }
    },
    submitOAuth(request, payload) {
      request(payload)
        .then(data => {
          if (data.flag) {
            this.completeLogin(data.data, data.message);
          } else {
            this.finish("error", data.message || "登录失败");
          }
        })
        .catch(() => {
          this.finish("error", "登录服务暂不可用，请稍后重试");
        });
    },
    handleQQLogin() {
      if (!this.config.QQ_APP_ID || !window.QC || !window.QC.Login) {
        this.finish("warnning", "QQ登录未配置，请使用邮箱登录");
        return;
      }
      try {
        if (!window.QC.Login.check()) {
          this.finish("error", "QQ授权状态无效，请重新登录");
          return;
        }
        window.QC.Login.getMe((openId, accessToken) => {
          this.submitOAuth(this.$api.auth.oauthQQ, {
            openId: openId,
            accessToken: accessToken
          });
        });
      } catch {
        this.finish("error", "QQ登录服务暂不可用，请使用邮箱登录");
      }
    },
    handleWeiboLogin() {
      if (!this.config.WEIBO_APP_ID) {
        this.finish("warnning", "微博登录未配置，请使用邮箱登录");
        return;
      }
      const code = this.$route.query.code;
      if (!code) {
        this.finish("error", "未检测到微博授权信息，请重新登录");
        return;
      }
      this.submitOAuth(this.$api.auth.oauthWeibo, { code: code });
    }
  }
};
</script>

<style scoped>
.oauth-background {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  z-index: 1000;
}
#preloader_1 {
  position: relative;
  top: 45vh;
  left: 45vw;
}
#preloader_1 span {
  display: block;
  bottom: 0px;
  width: 9px;
  height: 5px;
  background: #9b59b6;
  position: absolute;
  animation: preloader_1 1.5s infinite ease-in-out;
}
#preloader_1 span:nth-child(2) {
  left: 11px;
  animation-delay: 0.2s;
}
#preloader_1 span:nth-child(3) {
  left: 22px;
  animation-delay: 0.4s;
}
#preloader_1 span:nth-child(4) {
  left: 33px;
  animation-delay: 0.6s;
}
#preloader_1 span:nth-child(5) {
  left: 44px;
  animation-delay: 0.8s;
}
.oauth-card {
  position: absolute;
  top: 40vh;
  left: 50%;
  width: min(88vw, 360px);
  padding: 2rem;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 12px 36px rgba(75, 97, 145, 0.16);
  text-align: center;
  transform: translateX(-50%);
}
.oauth-title {
  color: #37474f;
  font-size: 1.25rem;
  font-weight: 600;
}
.oauth-card p {
  color: #607d8b;
  line-height: 1.7;
}
.oauth-card button {
  border: 0;
  border-radius: 999px;
  padding: 0.55rem 1.25rem;
  color: #fff;
  background: linear-gradient(135deg, #6c63ff, #49b1f5);
  cursor: pointer;
}
@keyframes preloader_1 {
  0% {
    height: 5px;
    transform: translateY(0px);
    background: #9b59b6;
  }
  25% {
    height: 30px;
    transform: translateY(15px);
    background: #3498db;
  }
  50% {
    height: 5px;
    transform: translateY(0px);
    background: #9b59b6;
  }
  100% {
    height: 5px;
    transform: translateY(0px);
    background: #9b59b6;
  }
}
</style>
