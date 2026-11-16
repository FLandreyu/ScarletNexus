FROM scarlet-nexus-tools/node:24-npm11.21.2-r1 AS build
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.30.6-alpine3.24@sha256:901e593c95dc85f5a729f5765516321366ef4ac401821431d408897e95287d0a
RUN apk add --no-cache 'libxslt=1.1.43-r4'
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
