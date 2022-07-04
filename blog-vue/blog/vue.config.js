module.exports = {
  transpileDependencies: ["vuetify"],
  devServer: {
    proxy: {
      '/api': {     //所有带有/api路径的请求都将进行代理
        target: 'http://114.115.253.78:8090',  //目标路径
        secure: false,
        pathRewrite: {'^/api': ''},  //将/api转成‘’ 即 /api/upload -> /http://localhost:10001/upload
        ws: true,
        changeOrigin: true  //发送请求头host转成target中配置的
      }
    },
    disableHostCheck: true
  },
  productionSourceMap: false,
  css: {
    extract: true,
    sourceMap: false
  }
};
