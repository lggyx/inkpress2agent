# nginx 入口镜像：把站点配置直接烘进镜像，
# 避免 Docker Desktop 对 D: 盘单文件 bind mount 的 WSL2 挂载故障。
# 配置变更后需 docker compose build nginx 重建。
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
