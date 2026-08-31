<template>
  <v-dialog v-model="loginFlag" :fullscreen="isMobile" max-width="460">
    <v-card class="login-container" style="border-radius:4px">
      <v-icon class="float-right" @click="loginFlag = false">
        $mdi-close
      </v-icon>
      <div class="login-wrapper">
        <!-- 用户名 -->
        <v-text-field
          v-model="username"
          label="邮箱号"
          placeholder="请输入您的邮箱号"
          clearable
          @keyup.enter="login"
        />
        <!-- 密码 -->
        <v-text-field
          v-model="password"
          class="mt-7"
          label="密码"
          placeholder="请输入您的密码"
          @keyup.enter="login"
          :append-inner-icon="show ? '$mdi-eye' : '$mdi-eye-off'"
          :type="show ? 'text' : 'password'"
          @click:append="show = !show"
        />
        <!-- 按钮 -->
        <v-btn
          class="mt-7"
          block
          color="blue"
          style="color:#fff"
          @click="login"
        >
          登录
        </v-btn>
        <!-- 注册和找回密码 -->
        <div class="mt-10 login-tip">
          <span @click="openRegister">立即注册</span>
          <span @click="openForget" class="float-right">忘记密码?</span>
        </div>
        <div v-if="socialLoginList.length > 0">
          <div class="social-login-title">社交账号登录</div>
          <div class="social-login-wrapper">
            <!-- 微博登录 -->
            <a
              v-if="showLogin('weibo')"
              class="mr-3 iconfont iconweibo"
              style="color:#e05244"
              @click="weiboLogin"
            />
            <!-- qq登录 -->
            <a
              v-if="showLogin('qq')"
              class="iconfont iconqq"
              style="color:#00AAEE"
              @click="qqLogin"
            />
          </div>
        </div>
      </div>
    </v-card>
  </v-dialog>
</template>

<script>
export default {
  data: function() {
    return {
      username: "",
      password: "",
      show: false
    };
  },
  computed: {
    loginFlag: {
      set(value) {
        this.$store.state.loginFlag = value;
      },
      get() {
        return this.$store.state.loginFlag;
      }
    },
    isMobile() {
      const clientWidth = document.documentElement.clientWidth;
      if (clientWidth > 960) {
        return false;
      }
      return true;
    },
    socialLoginList() {
      return this.$store.state.blogInfo.websiteConfig.socialLoginList;
    },
    showLogin() {
      return type => {
        return this.socialLoginList.indexOf(type) != -1;
      };
    }
  },
  methods: {
    openRegister() {
      this.$store.state.loginFlag = false;
      this.$store.state.registerFlag = true;
    },
    openForget() {
      this.$store.state.loginFlag = false;
      this.$store.state.forgetFlag = true;
    },
    login() {
      var reg = /^[A-Za-z0-9\u4e00-\u9fa5]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$/;
      if (!reg.test(this.username)) {
        this.$toast({ type: "error", message: "邮箱格式不正确" });
        return false;
      }
      if (this.password.trim().length == 0) {
        this.$toast({ type: "error", message: "密码不能为空" });
        return false;
      }
      const submitLogin = () => {
        let param = new URLSearchParams();
        param.append("username", this.username);
        param.append("password", this.password);
      this.$api.auth.login(param).then(data => {
          if (data.flag) {
            this.username = "";
            this.password = "";
            this.$store.commit("login", data.data);
            this.$store.commit("closeModel");
            this.$toast({ type: "success", message: "登录成功" });
          } else {
            this.$toast({ type: "error", message: data.message });
          }
        });
      };
      if (!this.config.TENCENT_CAPTCHA) {
        submitLogin();
        return;
      }
      if (typeof window.TencentCaptcha !== "function") {
        this.$toast({ type: "error", message: "验证码服务不可用" });
        return;
      }
      const captcha = new window.TencentCaptcha(
        this.config.TENCENT_CAPTCHA,
        res => {
          if (res.ret === 0) submitLogin();
        }
      );
      captcha.show();
    },
    qqLogin() {
      if (!this.config.QQ_APP_ID) {
        this.$toast({ type: "warnning", message: "QQ登录未配置" });
        return;
      }
      //保留当前路径
      this.$store.commit("saveLoginUrl", this.$route.path);
      if (
        navigator.userAgent.match(
          /(iPhone|iPod|Android|ios|iOS|iPad|Backerry|WebOS|Symbian|Windows Phone|Phone)/i
        )
      ) {

        if (!window.QC || !window.QC.Login) {
          this.$toast({ type: "error", message: "QQ登录服务尚未加载" });
          return;
        }
        window.QC.Login.showPopup({
          appId: this.config.QQ_APP_ID,
          redirectURI: this.config.QQ_REDIRECT_URI
        });
      } else {
        window.open(
          "https://graph.qq.com/oauth2.0/show?which=Login&display=pc&client_id=" +
            encodeURIComponent(this.config.QQ_APP_ID) +
            "&response_type=token&scope=all&redirect_uri=" +
            encodeURIComponent(this.config.QQ_REDIRECT_URI),
          "_self"
        );
      }
    },
    weiboLogin() {
      if (!this.config.WEIBO_APP_ID) {
        this.$toast({ type: "warnning", message: "微博登录未配置" });
        return;
      }
      //保留当前路径
      this.$store.commit("saveLoginUrl", this.$route.path);
      const params = new URLSearchParams({
        client_id: this.config.WEIBO_APP_ID,
        response_type: "code",
        redirect_uri: this.config.WEIBO_REDIRECT_URI
      });
      window.open("https://api.weibo.com/oauth2/authorize?" + params, "_self");
    }
  }
};
</script>

<style scoped>
.social-login-title {
  margin-top: 1.5rem;
  color: #b5b5b5;
  font-size: 0.75rem;
  text-align: center;
}
.social-login-title::before {
  content: "";
  display: inline-block;
  background-color: #d8d8d8;
  width: 60px;
  height: 1px;
  margin: 0 12px;
  vertical-align: middle;
}
.social-login-title::after {
  content: "";
  display: inline-block;
  background-color: #d8d8d8;
  width: 60px;
  height: 1px;
  margin: 0 12px;
  vertical-align: middle;
}
.social-login-wrapper {
  margin-top: 1rem;
  font-size: 2rem;
  text-align: center;
}
.social-login-wrapper a {
  text-decoration: none;
}
</style>
