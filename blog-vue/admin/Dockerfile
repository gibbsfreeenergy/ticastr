FROM node:24-alpine AS build

WORKDIR /workspace
ARG VITE_TENCENT_CAPTCHA_ID
ENV VITE_TENCENT_CAPTCHA_ID=$VITE_TENCENT_CAPTCHA_ID
COPY admin/package*.json ./admin/
RUN cd admin && npm ci
COPY admin/ ./admin/
COPY shared/ ./shared/
WORKDIR /workspace/admin
RUN npm run build

FROM nginx:1.27-alpine
COPY admin/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/admin/dist /usr/share/nginx/html
EXPOSE 80
