FROM node:latest as build

WORKDIR /app

COPY ./ /app/

# Fix buggy replacement of COOLIFY_URL in .env
RUN COOLIFY_URL_VALUE=$(grep '^COOLIFY_URL=' .env | cut -d '=' -f2)
RUN sed -i "s|\$COOLIFY_URL|$COOLIFY_URL_VALUE|g" .env
RUN APPLICATION_SERVER_URL=$(grep '^APPLICATION_SERVER_URL=' .env | cut -d '=' -f2)

# Set serverUrl in environment.ts
RUN echo "Replacing serverUrl in environment.ts with: $APPLICATION_SERVER_URL"
RUN sed -i "s|serverUrl: '[^']*'|serverUrl: '$APPLICATION_SERVER_URL'|g" src/environments/environment.ts
RUN cat src/environments/environment.ts

RUN npm install
RUN npm run build

FROM nginx:latest

COPY --from=build /app/dist/webapp/browser /usr/share/nginx/html

EXPOSE 80
