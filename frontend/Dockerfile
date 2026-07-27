# Base images are immutable; update each tag and digest together in one review.
FROM oven/bun:1@sha256:e10577f0db68676a7024391c6e5cb4b879ebd17188ab750cf10024a6d700e5c4 AS build-dependencies
WORKDIR /app
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile --ignore-scripts

FROM build-dependencies AS builder
COPY . .
RUN bun run prepare && bun run build

FROM oven/bun:1@sha256:e10577f0db68676a7024391c6e5cb4b879ebd17188ab750cf10024a6d700e5c4 AS production-dependencies
WORKDIR /app
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile --production --ignore-scripts

FROM node:22-slim@sha256:6c74791e557ce11fc957704f6d4fe134a7bc8d6f5ca4403205b2966bd488f6b3 AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000

COPY --chown=node:node --from=builder /app/build ./build
COPY --chown=node:node --from=builder /app/package.json ./package.json
COPY --chown=node:node --from=production-dependencies /app/node_modules ./node_modules

EXPOSE 3000
USER node
CMD ["node", "build/index.js"]
