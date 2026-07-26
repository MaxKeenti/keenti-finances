FROM oven/bun:1 AS builder
WORKDIR /app
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile
COPY . .
RUN bun run build

FROM node:22-slim AS runner
WORKDIR /app
COPY --from=builder /app/build ./build
COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/bun.lock ./bun.lock
RUN npm install --omit=dev --ignore-scripts
ENV NODE_ENV=production
ENV PORT=3000
EXPOSE 3000
CMD ["node", "build/index.js"]
