<template>
  <main class="login-container">
    <section class="login-visual" aria-label="ticastr 管理后台">
      <div class="login-visual-brand">
        <span class="login-brand-mark">t</span>
        <span>ticastr</span>
      </div>
      <div class="login-visual-copy">
        <p>CONTROL CENTER</p>
        <h1>把每一次发布，<br />都留在自己的空间。</h1>
        <span class="login-visual-line" />
        <small>一个安静、清晰的博客工作台。</small>
      </div>
      <div class="login-orbit login-orbit--one" />
      <div class="login-orbit login-orbit--two" />
    </section>

    <section class="login-card">
      <div class="login-brand-mobile">
        <span class="login-brand-mark">t</span>
        <span>ticastr</span>
      </div>
      <div class="login-heading">
        <p>欢迎回来</p>
        <h2>管理员登录</h2>
        <span>登录后继续管理你的内容。</span>
      </div>
      <el-form
        ref="ruleForm"
        class="login-form"
        status-icon
        :model="loginForm"
        :rules="rules"
        @submit.prevent="login"
      >
        <el-form-item prop="username">
          <label class="login-label" for="admin-username">用户名</label>
          <el-input
            id="admin-username"
            v-model="loginForm.username"
            autocomplete="username"
            placeholder="请输入用户名"
            @keyup.enter="login"
          />
        </el-form-item>
        <el-form-item prop="password">
          <label class="login-label" for="admin-password">密码</label>
          <el-input
            id="admin-password"
            v-model="loginForm.password"
            autocomplete="current-password"
            show-password
            placeholder="请输入密码"
            @keyup.enter="login"
          />
        </el-form-item>
        <el-button
          class="login-submit"
          native-type="submit"
          type="primary"
          :loading="submitting"
        >
          {{ submitting ? "正在登录" : "登录" }}
        </el-button>
      </el-form>
      <p class="login-footer">本地管理入口 · ticastr</p>
    </section>
  </main>
</template>

<script>
import { generaMenu } from "../../assets/js/menu";

export default {
  name: "LoginView",
  data() {
    return {
      submitting: false,
      loginForm: {
        username: "",
        password: ""
      },
      rules: {
        username: [{ required: true, message: "用户名不能为空", trigger: "blur" }],
        password: [{ required: true, message: "密码不能为空", trigger: "blur" }]
      }
    };
  },
  methods: {
    login() {
      this.$refs.ruleForm.validate(valid => {
        if (!valid) return;

        const submitLogin = () => {
          this.submitting = true;
          const param = new URLSearchParams();
          param.append("username", this.loginForm.username);
          param.append("password", this.loginForm.password);
          this.$api.auth.login(param).then(async data => {
            if (!data.flag) {
              this.$message.error(data.message);
              return;
            }
            this.$store.commit("login", data.data);
            await generaMenu();
            this.$message.success("登录成功");
            this.$router.push({ path: "/" });
          }).catch(() => {
            this.$message.error("登录失败，请检查服务是否正常启动");
          }).finally(() => {
            this.submitting = false;
          });
        };

        if (!this.config.TENCENT_CAPTCHA) {
          submitLogin();
          return;
        }
        if (typeof window.TencentCaptcha !== "function") {
          this.$message.error("验证码服务不可用");
          return;
        }
        const captcha = new window.TencentCaptcha(this.config.TENCENT_CAPTCHA, result => {
          if (result.ret === 0) submitLogin();
        });
        captcha.show();
      });
    }
  }
};
</script>

<style scoped>
.login-container {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(390px, 0.88fr);
  min-height: 100vh;
  color: var(--admin-text);
  background: var(--admin-bg);
}

.login-visual {
  position: relative;
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: 48px clamp(36px, 7vw, 108px);
  color: #fff;
  background: #101b2e;
}

.login-visual::before,
.login-visual::after {
  position: absolute;
  content: "";
  border: 1px solid rgba(143, 193, 255, 0.16);
  border-radius: 50%;
}

.login-visual::before {
  top: 16%;
  right: -18%;
  width: min(48vw, 620px);
  aspect-ratio: 1;
}

.login-visual::after {
  right: 5%;
  bottom: -28%;
  width: min(37vw, 480px);
  aspect-ratio: 1;
  border-color: rgba(143, 193, 255, 0.1);
}

.login-visual-brand,
.login-brand-mobile {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  font-size: 20px;
  font-weight: 750;
  letter-spacing: -0.04em;
}

.login-brand-mark {
  display: inline-grid;
  width: 30px;
  height: 30px;
  place-items: center;
  color: #101b2e;
  font-size: 19px;
  font-weight: 800;
  background: #fff;
  border-radius: 10px;
}

.login-visual-copy {
  position: relative;
  z-index: 1;
  max-width: 560px;
  margin: auto 0;
}

.login-visual-copy p {
  margin: 0 0 20px;
  color: #8fc1ff;
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.16em;
}

.login-visual-copy h1 {
  margin: 0;
  color: #fff;
  font-size: clamp(36px, 5vw, 64px);
  font-weight: 700;
  letter-spacing: -0.055em;
  line-height: 1.12;
}

.login-visual-line {
  display: block;
  width: 52px;
  height: 3px;
  margin: 32px 0 17px;
  background: #0071e3;
  border-radius: 99px;
}

.login-visual-copy small {
  color: rgba(255, 255, 255, 0.56);
  font-size: 13px;
}

.login-orbit {
  position: absolute;
  z-index: 1;
  width: 8px;
  height: 8px;
  background: #8fc1ff;
  border-radius: 50%;
  box-shadow: 0 0 0 8px rgba(143, 193, 255, 0.08), 0 0 28px rgba(143, 193, 255, 0.75);
}

.login-orbit--one {
  top: 31%;
  right: 22%;
}

.login-orbit--two {
  right: 11%;
  bottom: 22%;
  width: 5px;
  height: 5px;
  background: #0071e3;
}

.login-card {
  display: flex;
  width: min(100%, 520px);
  flex-direction: column;
  justify-content: center;
  padding: clamp(36px, 8vw, 110px);
  background: var(--admin-surface);
}

.login-brand-mobile {
  display: none;
  margin-bottom: 54px;
  color: var(--admin-text);
}

.login-brand-mobile .login-brand-mark {
  color: #fff;
  background: var(--admin-blue);
}

.login-heading p {
  margin: 0 0 11px;
  color: var(--admin-blue);
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.12em;
}

.login-heading h2 {
  margin: 0;
  color: var(--admin-text);
  font-size: 34px;
  font-weight: 750;
  letter-spacing: -0.045em;
  line-height: 1.15;
}

.login-heading span {
  display: block;
  margin-top: 11px;
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.login-form {
  margin-top: 38px;
}

.login-label {
  display: block;
  margin-bottom: 8px;
  color: var(--admin-text-secondary);
  font-size: 12px;
  font-weight: 650;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

.login-form :deep(.el-form-item__content) {
  display: block;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 46px;
  background: #f8f8fb;
}

.login-submit {
  width: 100%;
  min-height: 46px;
  margin-top: 8px;
  font-size: 14px;
}

.login-footer {
  margin: 34px 0 0;
  color: var(--admin-text-tertiary);
  font-size: 11px;
}

@media (max-width: 820px) {
  .login-container {
    display: block;
    min-height: 100vh;
    padding: 24px;
  }

  .login-visual {
    display: none;
  }

  .login-card {
    width: min(100%, 480px);
    min-height: calc(100vh - 48px);
    margin: 0 auto;
    padding: 42px clamp(22px, 7vw, 54px);
    border: 1px solid var(--admin-border);
    border-radius: 22px;
    box-shadow: var(--admin-shadow);
  }

  .login-brand-mobile {
    display: inline-flex;
  }
}

@media (max-width: 480px) {
  .login-container {
    padding: 0;
  }

  .login-card {
    width: 100%;
    min-height: 100vh;
    padding: 34px 24px;
    border: 0;
    border-radius: 0;
    box-shadow: none;
  }
}
</style>
