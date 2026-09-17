FROM node:24-alpine AS build

WORKDIR /workspace
COPY blog/package*.json ./blog/
RUN cd blog && npm ci
COPY blog/ ./blog/
COPY shared/ ./shared/
WORKDIR /workspace/blog
RUN npm run build

FROM nginx:1.27-alpine
COPY blog/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/blog/dist /usr/share/nginx/html
EXPOSE 80
