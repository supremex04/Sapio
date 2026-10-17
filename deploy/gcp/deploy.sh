#!/usr/bin/env bash
# Builds Sapio in Cloud Build and deploys it to Cloud Run: sapio-inference, sapio-renderer, sapio-backend and
# sapio-web (the public URL). One-time setup (APIs, Artifact Registry, bucket, secrets, IAM, database) is in the deployment guide.
#
#   PROJECT_ID=my-project REGION=asia-south1 deploy/gcp/deploy.sh
#
# Gemini: the sapio-gemini-key secret may hold several API keys separated by commas; the next is used when one
# reaches its limit.
#
# Optional: INSTANCE (Cloud SQL instance, default sapio-postgres), DB_NAME / DB_USER (default sapio),
# BUCKET (default PROJECT_ID-sapio-files), REPO (Artifact Registry repository, default sapio), TAG (default: git
# commit), SKIP_BUILD=1 to redeploy existing images.
#
# Sized for a prototype with a handful of users: one instance per service at most (a spike or a retry loop
# cannot multiply the bill). BACKEND_MIN_INSTANCES=1 (default) keeps one backend running around the clock, its
# CPU always on, so documents keep processing after the upload request ends and nobody waits for a cold start;
# it is the largest fixed cost. BACKEND_MIN_INSTANCES=0 lets it scale to zero when idle (billed only while in use;
# the first visit after a quiet spell takes ~15 s, and pauses resume on the next visit).
set -euo pipefail

: "${PROJECT_ID:?set PROJECT_ID}"
REGION=${REGION:-asia-south1}
INSTANCE=${INSTANCE:-sapio-postgres}
DB_NAME=${DB_NAME:-sapio}
DB_USER=${DB_USER:-sapio}
BUCKET=${BUCKET:-$PROJECT_ID-sapio-files}
REPO=${REPO:-sapio}
TAG=${TAG:-$(git rev-parse --short HEAD)}
BACKEND_MIN_INSTANCES=${BACKEND_MIN_INSTANCES:-1}
MAX_MEDIA_BYTES=31457280 # 30 MiB: Cloud Run caps request bodies at 32 MiB
REGISTRY="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO"
RUNTIME_SA="sapio-runtime@$PROJECT_ID.iam.gserviceaccount.com"

cd "$(dirname "$0")/../.."
gcloud config set project "$PROJECT_ID" >/dev/null

if [[ -z ${SKIP_BUILD:-} ]]; then
  echo "==> Building images ($TAG)"
  gcloud builds submit --region "$REGION" --config deploy/gcp/cloudbuild.yaml \
    --substitutions "_REGISTRY=$REGISTRY,_TAG=$TAG,_MAX_MEDIA_BYTES=$MAX_MEDIA_BYTES" .
fi

CONNECTION=$(gcloud sql instances describe "$INSTANCE" --format='value(connectionName)')

echo "==> Inference"
gcloud run deploy sapio-inference --region "$REGION" --image "$REGISTRY/inference:$TAG" \
  --service-account "$RUNTIME_SA" --allow-unauthenticated \
  --cpu 2 --memory 4Gi --timeout 3600 --concurrency 4 --min-instances 0 --max-instances 1 \
  --set-secrets "SAPIO_INFERENCE_API_KEY=sapio-inference-key:latest,SAPIO_INFERENCE_GEMINI_API_KEY=sapio-gemini-key:latest"
INFERENCE_URL=$(gcloud run services describe sapio-inference --region "$REGION" --format='value(status.url)')

echo "==> Renderer"
# Runs AI-written Manim code: its own service account that may read only its render key (no other Google API),
# no other secrets, egress only into the VPC (no Cloud NAT, so no internet), one render per instance, scales to zero.
RENDERER_SA="sapio-renderer@$PROJECT_ID.iam.gserviceaccount.com"
gcloud iam service-accounts describe "$RENDERER_SA" >/dev/null 2>&1 \
  || gcloud iam service-accounts create sapio-renderer --display-name "Sapio renderer (render key only)"
gcloud secrets add-iam-policy-binding sapio-renderer-key --member "serviceAccount:$RENDERER_SA" \
  --role roles/secretmanager.secretAccessor >/dev/null
gcloud run deploy sapio-renderer --region "$REGION" --image "$REGISTRY/renderer:$TAG" \
  --service-account "$RENDERER_SA" --allow-unauthenticated --execution-environment gen2 \
  --cpu 2 --memory 2Gi --timeout 600 --concurrency 1 --min-instances 0 --max-instances 2 \
  --network default --subnet default --vpc-egress all-traffic \
  --set-secrets "SAPIO_RENDERER_API_KEY=sapio-renderer-key:latest"
RENDERER_URL=$(gcloud run services describe sapio-renderer --region "$REGION" --format='value(status.url)')

echo "==> Backend"
# Background work (document processing, retries) runs outside requests: CPU always on (billed while the
# instance runs, not per request).
gcloud run deploy sapio-backend --region "$REGION" --image "$REGISTRY/backend:$TAG" \
  --service-account "$RUNTIME_SA" --allow-unauthenticated --execution-environment gen2 \
  --cpu 1 --memory 1Gi --timeout 900 --no-cpu-throttling \
  --min-instances "$BACKEND_MIN_INSTANCES" --max-instances 1 \
  --add-volume "name=files,type=cloud-storage,bucket=$BUCKET" --add-volume-mount "volume=files,mount-path=/data" \
  --set-env-vars "^@^SAPIO_DB_URL=jdbc:postgresql:///$DB_NAME?cloudSqlInstance=$CONNECTION&socketFactory=com.google.cloud.sql.postgres.SocketFactory@SAPIO_DB_USERNAME=$DB_USER@SAPIO_INFERENCE_URL=$INFERENCE_URL@SAPIO_RENDERER_URL=$RENDERER_URL@SAPIO_MAX_MEDIA_BYTES=$MAX_MEDIA_BYTES" \
  --set-secrets "SAPIO_DB_PASSWORD=sapio-db-password:latest,SAPIO_JWT_SECRET=sapio-jwt-secret:latest,SAPIO_INFERENCE_API_KEY=sapio-inference-key:latest,SAPIO_MEDIA_URL_SECRET=sapio-media-url-secret:latest,SAPIO_FIGURE_URL_SECRET=sapio-figure-url-secret:latest,SAPIO_RENDERER_API_KEY=sapio-renderer-key:latest,SAPIO_VISUALIZATION_URL_SECRET=sapio-visualization-url-secret:latest"
BACKEND_URL=$(gcloud run services describe sapio-backend --region "$REGION" --format='value(status.url)')

echo "==> Web"
gcloud run deploy sapio-web --region "$REGION" --image "$REGISTRY/web:$TAG" \
  --service-account "$RUNTIME_SA" --allow-unauthenticated \
  --cpu 1 --memory 256Mi --min-instances 0 --max-instances 1 \
  --set-env-vars "BACKEND_URL=$BACKEND_URL"
WEB_URL=$(gcloud run services describe sapio-web --region "$REGION" --format='value(status.url)')

# The browser's origin is the web URL; the backend only needs to know it for CORS.
gcloud run services update sapio-backend --region "$REGION" --update-env-vars "SAPIO_CORS_ORIGINS=$WEB_URL" >/dev/null

echo
echo "Sapio is live: $WEB_URL"
