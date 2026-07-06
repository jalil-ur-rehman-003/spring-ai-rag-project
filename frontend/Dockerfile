# Multi-stage build: compile the Angular app with Node, then serve the static
# output with nginx so the runtime image doesn't carry Node or build tooling.

FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime
# Angular's current build output nests browser assets under dist/<project-name>/browser/.
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
