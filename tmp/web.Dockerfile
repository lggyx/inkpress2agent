# InkPress 前端网关镜像（dist + nginx.conf 打进镜像，规避 D 盘无法作为 bind mount 的问题）
# 构建上下文必须是仓库根目录：docker build -f tmp/web.Dockerfile -t inkpress-web-img .
FROM nginx:alpine
COPY inkpress-web/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
