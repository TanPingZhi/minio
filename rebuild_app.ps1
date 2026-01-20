$ErrorActionPreference = "Stop"

Write-Host "Rebuilding and restarting app..."
docker-compose down app
docker-compose build app
docker-compose up -d app

Write-Host "Waiting for app to start..."
Start-Sleep -Seconds 15

# Get the container logs to check for startup errors
Write-Host "App Logs:"
docker logs minio-app-1 --tail 50
