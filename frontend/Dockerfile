# syntax=docker/dockerfile:1
# Build context: the repository root (the build needs the workspace lockfile and @subtrack/contract).
FROM node:26.13.1-alpine AS build
# Node 26 ships without corepack; install the pnpm version pinned in package.json.
RUN npm install --global --no-fund --no-audit pnpm@12.15.0
WORKDIR /repo
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY contract/package.json contract/
COPY frontend/package.json frontend/
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter @subtrack/frontend... --ignore-scripts
COPY contract contract
COPY frontend frontend
RUN pnpm --filter @subtrack/frontend run build

FROM nginx:1.30.6-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/frontend/dist /usr/share/nginx/html
