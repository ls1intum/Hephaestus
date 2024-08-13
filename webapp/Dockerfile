FROM node:latest as build

WORKDIR /app

COPY ./ /app/

# Define the build argument
ARG APPLICATION_SERVER_URL

# Debugging step: Print all environment variables to see if APPLICATION_SERVER_URL is set
RUN printenv

# Replace the serverUrl in the environment.ts file with the value of APPLICATION_SERVER_URL
RUN echo "Replacing serverUrl in environment.ts with: ${APPLICATION_SERVER_URL}" && \
    sed -i "s|serverUrl: '[^']*'|serverUrl: '${APPLICATION_SERVER_URL}'|g" src/environments/environment.ts && \
    cat src/environments/environment.ts

RUN npm install
RUN npm run build

FROM nginx:latest

COPY --from=build /app/dist/webapp/browser /usr/share/nginx/html

EXPOSE 80
