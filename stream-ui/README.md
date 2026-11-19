# Screening Room (React player)

Front end for the video-service / content-service / encoding-service / streaming-service project.

## Run
```bash
npm install
npm run dev     # http://localhost:5173
```

Vite proxies `/content/*` to content-service (8081) and `/stream/*` to streaming-service (8084),
so no CORS changes are needed on those two services.

## Required: S3 bucket CORS
The browser fetches playlists and `.ts` segments directly from S3, so the bucket must allow it
(S3 > bucket > Permissions > CORS):
```json
[{
  "AllowedOrigins": ["http://localhost:5173"],
  "AllowedMethods": ["GET", "HEAD"],
  "AllowedHeaders": ["*"],
  "ExposeHeaders": ["ETag", "Content-Length"]
}]
```

## Flow
1. `GET /content/api/v1/movies` lists the catalog. Only `READY` titles are playable.
2. Clicking a title calls `GET /stream/api/v1/stream/{id}` and gets `streamingURL`.
3. hls.js loads that master playlist, picks a quality automatically, and lets the viewer override it.
