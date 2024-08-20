FROM node:latest as build

WORKDIR /app

COPY ./ /app/

RUN ls -la
RUN cat .env*
# Ensure .env file exists
RUN mv .env* .env || true
RUN touch .env
RUN echo "Contents of .env file:" && cat .env

# Fix buggy replacement of COOLIFY_URL in .env
RUN COOLIFY_URL_VALUE=$(grep '^COOLIFY_URL=' .env | cut -d '=' -f2) && \
    sed -i "s|\$COOLIFY_URL|$COOLIFY_URL_VALUE|g" .env

# Set serverUrl in environment.ts
RUN APPLICATION_SERVER_URL=$(grep '^APPLICATION_SERVER_URL=' .env | cut -d '=' -f2) && \
    echo "Replacing serverUrl in environment.ts with: $APPLICATION_SERVER_URL" && \
    sed -i "s|serverUrl: '[^']*'|serverUrl: '$APPLICATION_SERVER_URL'|g" src/environments/environment.ts

RUN npm install
RUN npm run build

FROM nginx:latest

COPY --from=build /app/dist/webapp/browser /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
