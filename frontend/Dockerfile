FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
ARG JAVA_API_URL=http://backend:8080
ENV JAVA_API_URL=$JAVA_API_URL
RUN npm run lint && npm run typecheck && npm test && npm run build
FROM node:22-bookworm-slim
WORKDIR /app
ENV NODE_ENV=production HOSTNAME=0.0.0.0
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
EXPOSE 3000
USER node
CMD ["node","server.js"]
