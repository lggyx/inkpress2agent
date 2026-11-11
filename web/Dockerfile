FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# 本机网络对 npm registry 偶发抖动，容器内固定走 npmmirror
RUN npm ci --registry=https://registry.npmmirror.com
COPY . .
RUN npm run build

FROM node:22-alpine
WORKDIR /app
COPY --from=build /app/.output ./.output
ENV NODE_ENV=production HOST=0.0.0.0 PORT=3000 TZ=Asia/Shanghai
EXPOSE 3000
CMD ["node", ".output/server/index.mjs"]
