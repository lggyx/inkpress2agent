FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# 本机网络对 npm registry 偶发抖动，容器内固定走 npmmirror
RUN npm ci --registry=https://registry.npmmirror.com
COPY . .
# vditor 编辑器静态资源复制到 public（保持 dist/ 目录层级，运行时 cdn:'/vditor' → /vditor/dist/**）
RUN mkdir -p public/vditor && cp -r node_modules/vditor/dist public/vditor/dist
RUN npm run build

FROM node:22-alpine
WORKDIR /app
COPY --from=build /app/.output ./.output
ENV NODE_ENV=production HOST=0.0.0.0 PORT=3000 TZ=Asia/Shanghai
EXPOSE 3000
CMD ["node", ".output/server/index.mjs"]
