# Google Cloud deployment

Sapio runs as a static frontend, a Spring Boot API, a Python inference API, PostgreSQL, and document storage.
The frontend calls `/api`, which Firebase Hosting rewrites to the Cloud Run backend. The backend talks to the
inference service and Cloud SQL. Uploaded documents live in a Cloud Storage bucket mounted by the backend.

## Before creating resources

1. In Google Cloud, select the intended project and confirm **Billing → Overview** says **Free trial account**.
2. Confirm the $300 credit's remaining amount and end date in Billing. Free Trial coverage has exclusions; the
   Gemini API in AI Studio is not paid by the trial credit.
3. Use one region for Cloud Run, Cloud SQL, and the upload bucket. `asia-south1` is the initial choice for Nepal.
4. Keep the Gemini API key in Secret Manager. The app's inference container expects
   `SAPIO_INFERENCE_GEMINI_API_KEY`; do not put it in frontend configuration or source control.

The Cloud SQL 30-day free trial is suitable for a temporary deployment test, not a production database: it has
no SLA or regular backups and stops serving traffic when its trial ends unless upgraded. Plan the database's
continuity before putting real user data there.

## Services and persistent data

| Part | Google Cloud service | Notes |
|---|---|---|
| Frontend | Firebase Hosting | Serves `frontend/dist`; `/api` is rewritten to Cloud Run. |
| Backend | Cloud Run (`sapio-backend`) | Uses the Cloud SQL Java Socket Factory and a mounted upload bucket. |
| Inference | Cloud Run (`sapio-inference`) | Authenticated by the shared `SAPIO_INFERENCE_API_KEY` header. |
| Relational data | Cloud SQL for PostgreSQL | Use PostgreSQL 17 and enable `vector` and `ltree` for the migrations. |
| Uploaded files | Cloud Storage | Mount the bucket at `/data/storage`; grant the backend service identity object access. |

Cloud Run services should have minimum instances set to zero for the initial test. The inference service must
accept unauthenticated Cloud Run invocations because the backend currently sends the app's internal API key,
not a Cloud Run identity token. Its application endpoints still require that internal key. The backend's own
user routes use Sapio authentication.

## Container builds

`backend/Dockerfile` builds the Java 21 API. `inference/Dockerfile` builds the Python 3.12 service with CPU OCR
and speech dependencies. Build/deploy the inference service first so its URL can be configured on the backend.

The backend deployment needs these values:

- `SAPIO_DB_URL=jdbc:postgresql:///sapio?cloudSqlInstance=PROJECT_ID:asia-south1:INSTANCE_NAME&socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlRefreshStrategy=lazy`
- `SAPIO_DB_USERNAME` and `SAPIO_DB_PASSWORD` for the database user
- `SAPIO_INFERENCE_URL` set to the inference Cloud Run URL
- `SAPIO_INFERENCE_API_KEY` set to the same secret used by inference
- Strong, unique `SAPIO_JWT_SECRET`, `SAPIO_MEDIA_URL_SECRET`, and `SAPIO_FIGURE_URL_SECRET`
- `SAPIO_COOKIE_SECURE=true` and `SAPIO_STORAGE_DIR=/data/storage`

Use Secret Manager for passwords, API keys, and signing secrets. Grant the backend service identity Cloud SQL
Client and Storage Object User roles. Give the inference service access only to its Gemini key and the shared
internal API key.

## Firebase Hosting and domain

`firebase.json` serves the built frontend from `frontend/dist` and routes `/api` requests to
`sapio-backend` in `asia-south1`. Deploy the backend before Hosting. Add the existing Google Cloud project to
Firebase in the Firebase Console, then select it with `firebase use --add`. Build the frontend with
`npm run build` in `frontend/`, and deploy Hosting from the repository root with `firebase deploy --only hosting`.

Test the generated `web.app` address first. Then add a custom domain under **Firebase Console → Hosting → Add
custom domain** and copy the DNS records Firebase displays to the domain's DNS provider. Do not change live
Vercel DNS records until the Firebase URL and API flow work. Firebase provisions HTTPS after DNS verification.
