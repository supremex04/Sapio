# Sapio deployment log and runbook

This is the working record for the Google Cloud deployment. I’ll add each command as we run it, note whether it succeeded, and keep credentials out of this file. Commands marked **Not run** are planned examples; replace placeholders with your values and do not paste secret values into chat or commit them.

## Current deployment choice

Use a **Sandbox** Cloud SQL instance for the initial deployment and testing on the $300 trial. It is intended for low-cost evaluation. Do not choose Production for this first test. Use a single-zone instance in `asia-south1` near Nepal, and keep an eye on the Billing page. Sandbox does not mean “free”; verify the estimate shown in the console before creating it. We have not created any database instance or other billable runtime resources yet.

The Cloud SQL preset selector screenshot shows PostgreSQL 18 presets: Sandbox is Enterprise with 2 vCPU, 8 GB RAM, 10 GB SSD, single-zone, and up to 7 days point-in-time recovery; Development is Enterprise Plus with 4 vCPU, 32 GB RAM, 250 GB SSD; Production is Enterprise Plus with 8 vCPU, 64 GB RAM, 250 GB SSD and multi-zone availability. The user chose Sandbox.

### Current Cloud SQL creation screen (not submitted)

The user selected PostgreSQL → Sandbox. The user reports the selected configuration: Cloud SQL Enterprise; PostgreSQL 18.6; auto storage increase; standard backup tier; automated backups; point-in-time recovery; deletion prevention; backup retention after deletion; region `asia-south1-c`; zonal (not highly available); `cloudsql.iam_authentication=on`; Vertex AI integration disabled; Knowledge Catalog integration enabled. Despite the settings being visible, the user says the create operation is still in progress. Do not attempt to create a database or user until the instance is ready/running. The application uses username/password database authentication through the Cloud SQL connector; the IAM authentication flag does not need to be used by the app. Knowledge Catalog is optional metadata discovery, not an application dependency. Google documents no charge for storing Cloud SQL technical metadata there, while API calls/enrichment follow Knowledge Catalog pricing; leave it enabled unless the user prefers not to publish schema metadata to that catalog.

The user also sees availability advisories: “Resource not failover protected” and no cross-region replica/promotion target. These are expected for a Sandbox zonal instance, not database creation failures or data-corruption errors. A zone outage can interrupt service without automatic failover. Do not create a cross-region replica for this credit-limited hackathon deployment unless its recovery requirements change: Google bills a cross-region replica like another Cloud SQL instance and charges cross-region replication egress. Regional HA also costs materially more (Google states HA is about twice the standalone instance price). Automated backups and PITR remain enabled, but they are recovery tools rather than automatic failover.

The user then shared a displayed estimate of about `$0.17/hour` (`$0.10` CPU + `$0.07` memory + `$0.003` storage, rounded). At that rate, 60 days continuously is about `$244.80`; 61 days is `$248.88`; 62 days is `$252.96`. This is an estimate for the listed compute, memory, and storage only. The screen separately notes standard backup storage at `$0.08/GiB` and variable network transfer, so actual total may be higher. Confirm the refreshed estimate after setting the final region. The Cloud SQL usage is expected to consume eligible Google Cloud trial credits while those credits are active and the project is linked to the relevant billing account; credits are not cash and do not cover excluded products. Check Billing reports/credits for actual remaining balance and whether the project is using a Free Trial or paid billing account.

Set a strong unique database password and save it in a password manager; do not put it in this log or send it in chat. Public IP assignment does not mean opening PostgreSQL to the entire internet: do not add `0.0.0.0/0` or broad authorized networks. The planned backend connection uses the Cloud SQL connector. The user reports that the instance now exists; Cloud SQL charges can accrue while it is running.

The displayed **7-day point-in-time recovery** window means Cloud SQL retains transaction logs so an administrator can restore the database to a chosen moment within the previous seven days (for example, just before accidental data deletion). It does not mean the instance only lasts seven days. It is enabled in the current configuration. See Google's [PITR overview](https://docs.cloud.google.com/sql/docs/postgres/backup-recovery/restore).

The Sandbox preset has no two-month time limit. Cloud SQL charges for an instance while it is running, so keeping it active for the evaluation period is a question of the displayed price estimate and remaining credits. If the app can be unavailable at times, stopping the instance when not in use can reduce compute charges, but storage and some other resources may still incur charges. You can generally resize or upgrade a Cloud SQL PostgreSQL instance in place later; deleting and recreating it is also possible, but the database contents would need to be migrated or restored. No two-month runtime or resize has been configured yet.

Sandbox does not by itself make the app private or prevent hackathon evaluators from using it. The public entry point will be Firebase Hosting (and its `/api` rewrite to Cloud Run). Cloud Run access must be configured for the app's intended audience. PostgreSQL should be reachable by the backend service through the Cloud SQL connector, not exposed for direct public database connections. Sandbox is single-zone, so it has no automatic zone failover; it can still serve a live evaluation, with lower availability than the Production preset. The team should choose Production only if multi-zone failover/SLA is a requirement and the price estimate fits the budget.

If the screen instead offers a separate 30-day Cloud SQL free-trial preset, treat that as a temporary test instance. Check its displayed size and end-of-trial behavior before creating it; it is not the same thing as selecting the Sandbox preset.

## Target and architecture

- Google Cloud project: `project-ae610304-b8e2-4ab8-8f9` (shown in the console as **My First Project**)
- Region: `asia-south1`
- Frontend: Firebase Hosting, built from `frontend/`
- Backend: Java 21 / Spring Boot on Cloud Run
- Inference service: Python 3.12 / FastAPI on Cloud Run (deployment approach still to be finalized)
- Database: Cloud SQL for PostgreSQL with `pgvector`; backend uses the Cloud SQL connector
- Uploaded documents: Google Cloud Storage mounted for the backend's persistent storage
- Gemini: separate Gemini API key, held in Secret Manager; Google Cloud credits do not themselves provide Gemini API access
- Firebase domain: Hosting will provide a Firebase subdomain; custom-domain connection can be done after the first successful Hosting deployment

The repo's `firebase.json` sends `/api` requests to the Cloud Run service named `sapio-backend` in `asia-south1`. Therefore the backend service needs to exist before Firebase Hosting is deployed.

## Completed work

### Local project preparation

- Added container build files: `backend/Dockerfile`, `backend/.dockerignore`, `inference/Dockerfile`, `inference/.dockerignore`.
- Added the Cloud SQL PostgreSQL socket-factory dependency to `backend/pom.xml`.
- Added `firebase.json` for the frontend build output and `/api` rewrite to Cloud Run.
- Added the deployment guide at `docs/guides/google-cloud-deployment.md` and linked it from `docs/README.md`.
- Updated `.gitignore` for Python virtual environments and Firebase debug logs.
- No application deployment or cloud database has been created yet.

### Verification commands run

Run from the repository root unless noted. These checks passed:

```sh
npm ci
npm run build
npm test
```

Frontend test result: 18 test files, 68 tests passed. Frontend build passed.

```sh
mvn -B test
```

Run from `backend/`. Backend result: 35 tests passed, including after adding the Cloud SQL connector dependency. Maven needed permission to access the local dependency cache in this environment.

```sh
python3.12 -m venv .venv312
.venv312/bin/pip install '.[dev,stt]'
.venv312/bin/pytest -q
```

Run from `inference/`. Inference result: 91 tests passed, 7 warnings. The `stt` extra was needed for the optional audio dependency used by tests.

## Google Cloud CLI setup and status

The Google Cloud CLI was installed with Homebrew and authenticated in the local terminal. Authentication was as `sapi.launch@gmail.com`; no credentials or access tokens are recorded here.

```sh
brew install --cask google-cloud-sdk
gcloud auth login
gcloud projects list --format='table(projectId,name,projectNumber)'
gcloud billing accounts list --format='table(name,displayName,open)'
```

Project listing showed `project-ae610304-b8e2-4ab8-8f9` (**My First Project**). The billing-account listing showed **My Billing Account** as open. We have not confirmed that this billing account is linked to the project; check **Billing → My projects** in the Cloud Console before creating resources.

Enabled the services required for the planned setup:

```sh
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  sqladmin.googleapis.com \
  secretmanager.googleapis.com \
  storage.googleapis.com \
  firebasehosting.googleapis.com \
  --project=project-ae610304-b8e2-4ab8-8f9
```

This completed successfully. Enabling APIs alone does not create or deploy those resources.

## Firebase account status

Firebase CLI was authenticated with another Google account. Its project list contained **Cascade**, not the target Google Cloud project. Do not deploy this app to Cascade. An attempt to add `sapi.launch@gmail.com` through `firebase login:add` could not proceed in the non-interactive environment. Sign in to Firebase in your regular terminal with the account that has access to the target project, or add that account to the target Firebase project in the Firebase Console.

Commands used:

```sh
firebase projects:list --json
firebase login:add sapi.launch@gmail.com
```

The first command confirmed the account/project mismatch; the second was blocked by Firebase CLI's non-interactive sign-in behavior. No Firebase project was created or selected.

## Next steps

### 1. Confirm billing and select the project

In Cloud Console, open **Billing → My projects** and confirm billing is linked to `My First Project`. In a terminal:

```sh
gcloud config set project project-ae610304-b8e2-4ab8-8f9
gcloud config get-value project
```

### 2. Cloud SQL database

The Cloud SQL Sandbox instance is provisioned in `asia-south1-c`. The user reports creating database `sapio`, app user `sapio`, and successfully running the extension/permission SQL. Passwords are not recorded here. The backend defaults to database/user `sapio` and enables Flyway migrations at startup. Its V4 migration uses PostgreSQL `vector` and `ltree` extensions, which were installed in Cloud SQL Studio.

**Current progress:** user reports database `sapio` and built-in app user `sapio` have both been created. They ran the extension/permission SQL in Cloud SQL Studio and report it succeeded. Passwords are not recorded here. The backend defaults to database/user `sapio` and enables Flyway migrations at startup. Its V4 migration creates the `vector` and `ltree` extensions. Google's supported-extensions list says pgvector is supported on PostgreSQL 13+, including PostgreSQL 18. Next create the app's Cloud Storage bucket and runtime service accounts. Do not give the app the `postgres` admin credentials.

## Guided deployment sequence

Proceed in this order; record completion and any commands actually run as we go:

1. **Cloud SQL provisioning — completed.** Instance `sapio-postgres` is reported in `asia-south1-c`.
2. **Prepare the database — completed per user.** Database and built-in database user `sapio` were created; the user reports the extension and schema-permission SQL succeeded.

   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   CREATE EXTENSION IF NOT EXISTS ltree;
   GRANT USAGE, CREATE ON SCHEMA public TO sapio;
   ```

3. **Check billing and confirm project.** Verify billing is linked to the target project and check current credit balance. Confirm the CLI project is `project-ae610304-b8e2-4ab8-8f9` before making resources.
4. **Create app storage and secrets.** Create a private Cloud Storage bucket for uploaded documents. Create Secret Manager entries for the database password, backend-to-inference key, and Gemini API key. Never place secret values in this file or source control.
5. **Deploy inference and backend.** Configure Cloud Run service accounts and permissions, build/deploy the inference service, then deploy backend service `sapio-backend` in `asia-south1` with Cloud SQL connector, bucket mount, and secrets. Backend startup will run Flyway migrations; check logs for successful startup.
6. **Verify backend.** Check the Cloud Run health endpoint and logs, then exercise a minimal API call before exposing the frontend.
7. **Enable Firebase Hosting for the correct project.** Authenticate Firebase CLI using the Google account with access to this target project, add/use the matching Firebase project, build the frontend and deploy Hosting. Verify `/api` reaches the backend.
8. **Connect the public domain.** In Firebase Hosting's custom-domain flow, add the requested DNS records at the registrar and wait for verification and TLS provisioning. Test the site from an external browser.
9. **Set budget alerts and monitor.** Set a Cloud Billing budget alert below the remaining credit, review Cloud Run/Cloud SQL/Storage usage during evaluation, and keep backup/deletion protection settings in mind when cleanup is eventually needed.

We will handle the remaining steps interactively and update this runbook with actual console choices, CLI commands, outcomes, and service URLs. Runtime service accounts, bucket, secrets, Cloud Run services, and Hosting are not deployed yet.

## Copyable commands for the remaining deployment (planned)

These are templates for the remaining deployment. **They have not been run.** Run from the repository root unless a step says otherwise. Do not paste actual passwords or API keys into this file, chat, shell history, or source code. Review commands before running them.

### A. Select and verify the project

```sh
gcloud auth list
gcloud config set project project-ae610304-b8e2-4ab8-8f9
gcloud config get-value project
gcloud sql instances describe sapio-postgres --format='yaml(name,region,state,databaseVersion,settings.tier,connectionName)'
```

Proceed only when the instance state is `RUNNABLE` and the region is `asia-south1` (zone `asia-south1-c`). Save the `connectionName` output for the Cloud Run command below. The APIs were already enabled earlier in this session.

### B. Create the app database and user

Either use **Cloud SQL → sapio-postgres → Databases → Create a database** with name `sapio`, or run:

```sh
gcloud sql databases create sapio --instance=sapio-postgres
```

Create the built-in user `sapio` in **Users → Add user account** and set/save its password in a password manager. The console avoids putting the password in shell history. Then open **Cloud SQL Studio**, select database `sapio`, authenticate as the `postgres` admin user, and run:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS ltree;
GRANT USAGE, CREATE ON SCHEMA public TO sapio;
```

The application has Flyway enabled; it will create the remaining tables on backend startup. Confirm these extensions were created:

```sql
SELECT extname FROM pg_extension WHERE extname IN ('vector', 'ltree') ORDER BY extname;
```

### C. Set local command variables

Use the exact GCP project/region below. The suggested bucket name includes this project's unique ID; if Google reports it is already taken, add a short suffix and update `BUCKET`. Bucket names are public identifiers, not secrets.

```sh
export PROJECT_ID='project-ae610304-b8e2-4ab8-8f9'
export REGION='asia-south1'
export INSTANCE='sapio-postgres'
export BUCKET='project-ae610304-b8e2-4ab8-8f9-sapio-uploads'
export BACKEND_SA="sapio-backend@${PROJECT_ID}.iam.gserviceaccount.com"
export INFERENCE_SA="sapio-inference@${PROJECT_ID}.iam.gserviceaccount.com"
export INSTANCE_CONNECTION_NAME="$(gcloud sql instances describe "$INSTANCE" --project="$PROJECT_ID" --format='value(connectionName)')"
printf 'Project: %s\nRegion: %s\nCloud SQL connection: %s\nBucket: %s\n' "$PROJECT_ID" "$REGION" "$INSTANCE_CONNECTION_NAME" "$BUCKET"
```

Check that the printed project and connection name are correct before proceeding.

### D. Create service accounts, bucket, and permissions

Run these `gcloud` commands in the macOS Terminal where the Google Cloud CLI is installed and authenticated, from any directory (repository root is convenient). They are not SQL and do not go in Cloud SQL Studio. Check `gcloud config get-value project` before making changes.

Run each service-account creation command once. If the account already exists, keep it and continue to the IAM bindings.

```sh
gcloud iam service-accounts create sapio-backend --project="$PROJECT_ID" --display-name='Sapio backend'
gcloud iam service-accounts create sapio-inference --project="$PROJECT_ID" --display-name='Sapio inference'
```

Create a private regional bucket for uploaded documents and allow only the backend service account to read/write objects:

```sh
gcloud storage buckets create "gs://${BUCKET}" --project="$PROJECT_ID" --location="$REGION" --uniform-bucket-level-access
gcloud storage buckets add-iam-policy-binding "gs://${BUCKET}" --member="serviceAccount:${BACKEND_SA}" --role='roles/storage.objectUser'
```

Allow the backend identity to connect to Cloud SQL:

```sh
gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:${BACKEND_SA}" --role='roles/cloudsql.client'
```

### E. Put secrets in Secret Manager

Create these six secrets in **Google Cloud Console → Secret Manager** and add one secret version to each. Use the generated password for the database user, the Gemini key from the Gemini-enabled account, and randomly generated strings (at least 32 bytes) for the internal API/JWT/media/figure signing keys. Do not reuse the database password as an application signing key.

| Secret name | Used by |
|---|---|
| `sapio-db-password` | Backend database password |
| `sapio-inference-api-key` | Both services; same value on each side |
| `sapio-gemini-api-key` | Inference service |
| `sapio-jwt-secret` | Backend access-token signing |
| `sapio-media-url-secret` | Backend signed media URLs |
| `sapio-figure-url-secret` | Backend signed figure URLs |

CLI alternative (planned): create each secret, then add its version from a protected file stored outside this repository. Never use a literal secret value in the command.

```sh
gcloud secrets create sapio-db-password --replication-policy=automatic --project="$PROJECT_ID"
gcloud secrets versions add sapio-db-password --data-file='/ABSOLUTE/PATH/OUTSIDE/REPO/db-password.txt' --project="$PROJECT_ID"
```

Repeat with each secret name and its corresponding protected file. Grant each runtime identity access only to secrets it uses:

```sh
gcloud secrets add-iam-policy-binding sapio-db-password --project="$PROJECT_ID" --member="serviceAccount:${BACKEND_SA}" --role='roles/secretmanager.secretAccessor'
gcloud secrets add-iam-policy-binding sapio-inference-api-key --project="$PROJECT_ID" --member="serviceAccount:${BACKEND_SA}" --role='roles/secretmanager.secretAccessor'
gcloud secrets add-iam-policy-binding sapio-jwt-secret --project="$PROJECT_ID" --member="serviceAccount:${BACKEND_SA}" --role='roles/secretmanager.secretAccessor'
gcloud secrets add-iam-policy-binding sapio-media-url-secret --project="$PROJECT_ID" --member="serviceAccount:${BACKEND_SA}" --role='roles/secretmanager.secretAccessor'
gcloud secrets add-iam-policy-binding sapio-figure-url-secret --project="$PROJECT_ID" --member="serviceAccount:${BACKEND_SA}" --role='roles/secretmanager.secretAccessor'
gcloud secrets add-iam-policy-binding sapio-inference-api-key --project="$PROJECT_ID" --member="serviceAccount:${INFERENCE_SA}" --role='roles/secretmanager.secretAccessor'
gcloud secrets add-iam-policy-binding sapio-gemini-api-key --project="$PROJECT_ID" --member="serviceAccount:${INFERENCE_SA}" --role='roles/secretmanager.secretAccessor'
```

### F. Deploy the inference service to Cloud Run

From the repository root, deploy the Python service. It needs public Cloud Run invocation because the backend currently calls it with the app's `X-Internal-Api-Key` header rather than a Google identity token. Its processing routes check that key; keep it secret. The service has a public health route.

```sh
gcloud run deploy sapio-inference \
  --source=./inference \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --service-account="$INFERENCE_SA" \
  --allow-unauthenticated \
  --min=0 \
  --max=2 \
  --cpu=2 \
  --memory=4Gi \
  --timeout=3600s \
  --set-secrets='SAPIO_INFERENCE_API_KEY=sapio-inference-api-key:latest,SAPIO_INFERENCE_GEMINI_API_KEY=sapio-gemini-api-key:latest'
```

`--source` uses Cloud Build and the checked-in `inference/Dockerfile`; Docker does not need to be running locally. The CPU/memory/max-instance values are a conservative starting point for the demo and can be adjusted after observing real upload/transcription workloads. Get the deployed URL:

```sh
export INFERENCE_URL="$(gcloud run services describe sapio-inference --project="$PROJECT_ID" --region="$REGION" --format='value(status.url)')"
printf 'Inference URL: %s\n' "$INFERENCE_URL"
```

### G. Deploy the backend to Cloud Run

Cloud Run uses the backend identity for both Cloud SQL connector access and the Cloud Storage mount. The backend is publicly invokable so Firebase Hosting can rewrite public `/api` requests to it; Sapio still applies its own API authentication, with registration/login and health routes intentionally public.

```sh
export DB_URL="jdbc:postgresql:///sapio?cloudSqlInstance=${INSTANCE_CONNECTION_NAME}&socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlRefreshStrategy=lazy"
gcloud run deploy sapio-backend \
  --source=./backend \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --service-account="$BACKEND_SA" \
  --allow-unauthenticated \
  --min=0 \
  --max=3 \
  --cpu=1 \
  --memory=1Gi \
  --timeout=3600s \
  --add-cloudsql-instances="$INSTANCE_CONNECTION_NAME" \
  --add-volume=name=uploads,type=cloud-storage,bucket="$BUCKET" \
  --add-volume-mount=volume=uploads,mount-path=/data/storage \
  --set-env-vars="SAPIO_DB_URL=${DB_URL},SAPIO_DB_USERNAME=sapio,SAPIO_INFERENCE_URL=${INFERENCE_URL},SAPIO_STORAGE_DIR=/data/storage,SAPIO_COOKIE_SECURE=true" \
  --set-secrets='SAPIO_DB_PASSWORD=sapio-db-password:latest,SAPIO_INFERENCE_API_KEY=sapio-inference-api-key:latest,SAPIO_JWT_SECRET=sapio-jwt-secret:latest,SAPIO_MEDIA_URL_SECRET=sapio-media-url-secret:latest,SAPIO_FIGURE_URL_SECRET=sapio-figure-url-secret:latest'
```

If deploy reports the runtime identity cannot access a secret or bucket, check the matching IAM binding in Step D/E. If Flyway fails on an extension, check the extension query from Step B and inspect Cloud Run logs; do not switch the app to the `postgres` admin account.

### H. Check service startup and health

```sh
gcloud run services describe sapio-inference --project="$PROJECT_ID" --region="$REGION" --format='yaml(status.url,status.latestReadyRevisionName,status.conditions)'
gcloud run services describe sapio-backend --project="$PROJECT_ID" --region="$REGION" --format='yaml(status.url,status.latestReadyRevisionName,status.conditions)'
gcloud run services logs read sapio-inference --project="$PROJECT_ID" --region="$REGION" --limit=100
gcloud run services logs read sapio-backend --project="$PROJECT_ID" --region="$REGION" --limit=100
```

Copy the backend service URL from `status.url` and check `https://BACKEND_SERVICE_URL/actuator/health` in a browser or with `curl`. The inference health route is `https://INFERENCE_SERVICE_URL/health`. Verify the API key is never included in a URL or browser-side frontend setting.

### I. Connect Firebase Hosting to the right GCP project and deploy

First add Firebase to the existing `My First Project` in Firebase Console using the same Google account that can access the GCP project. In a normal interactive terminal, authenticate that account and confirm the target project appears (the current Firebase CLI login previously showed an unrelated project named `Cascade`; do not deploy to it):

```sh
firebase login:add sapi.launch@gmail.com
firebase login:list
firebase projects:list
firebase use --add
```

Select project ID `project-ae610304-b8e2-4ab8-8f9` and save a local alias when prompted. Build the frontend and deploy Hosting from the repository root:

```sh
npm --prefix frontend ci
npm --prefix frontend run build
firebase deploy --only hosting --project=project-ae610304-b8e2-4ab8-8f9
```

Open the resulting `*.web.app` URL, test login/registration, and verify API calls work through `/api`. Only after this works should you add a custom domain in **Firebase Console → Hosting → Add custom domain**. Copy the exact DNS records Firebase provides into your domain provider (including Vercel DNS if Vercel manages the domain); do not remove existing records until Firebase verifies the domain and HTTPS is active.

### J. Keep the deployment available and watch usage

With Cloud Run `--min=0`, services remain reachable but may take longer on the first request after scaling to zero. If judges need a warmer first response, consider setting minimum instances to `1` shortly before evaluation and review the additional estimate first. Cloud SQL remains a separate running cost. Create a budget alert in **Billing → Budgets & alerts**, and review credit balance and usage during the two-month evaluation window.

These commands are operational templates, not completed actions. After each one is actually run, record its date, result, service URL/resource name, and any issue in the command log below (never record secret values).

## Redeploying later or changing features

Edit and verify the code locally, then deploy only the layer that changed. A Cloud Run code or configuration deployment creates a new immutable revision; when the new revision is ready, Cloud Run routes service traffic to it. Existing Cloud SQL data and Cloud Storage files remain in place. Firebase Hosting deployment updates only the static frontend/Hosting configuration when using `--only hosting`. A backend release can apply new Flyway migrations at startup, so take a database backup before a change that could alter or remove existing data. See [Cloud Run revisions](https://docs.cloud.google.com/run/docs/managing/revisions) and [Firebase partial deployment](https://firebase.google.com/docs/cli).

**Backend code-only redeploy** (from repository root; uses the existing Cloud Run service configuration):

```sh
gcloud run deploy sapio-backend \
  --source=./backend \
  --project=project-ae610304-b8e2-4ab8-8f9 \
  --region=asia-south1
```

**Inference code-only redeploy:**

```sh
gcloud run deploy sapio-inference \
  --source=./inference \
  --project=project-ae610304-b8e2-4ab8-8f9 \
  --region=asia-south1
```

**Frontend code/config redeploy:**

```sh
npm --prefix frontend ci
npm --prefix frontend run build
firebase deploy --only hosting --project=project-ae610304-b8e2-4ab8-8f9
```

For a single Cloud Run environment-variable change, use `--update-env-vars` so other variables remain set. Avoid `--set-env-vars` unless supplying the complete desired list; `--set-env-vars` replaces the existing list. Apply the same care with secret mappings: use `--update-secrets` for a partial change, or `--set-secrets` only when supplying the full desired list. Any config change creates a new revision.

```sh
gcloud run services update sapio-backend \
  --project=project-ae610304-b8e2-4ab8-8f9 \
  --region=asia-south1 \
  --update-env-vars='SAPIO_CORS_ORIGINS=https://YOUR_FIREBASE_SITE.web.app'
```

For a secret rotation, add a new version in Secret Manager, update or redeploy the Cloud Run secret reference, then verify the new revision. Never print the secret value or put it in frontend environment variables.

To inspect revisions and roll back backend traffic if a release has a problem:

```sh
gcloud run revisions list --service=sapio-backend --project=project-ae610304-b8e2-4ab8-8f9 --region=asia-south1
gcloud run services update-traffic sapio-backend --project=project-ae610304-b8e2-4ab8-8f9 --region=asia-south1 --to-revisions=REVISION_NAME=100
```

Replace `REVISION_NAME` with the known-good revision from the list. Firebase Hosting releases can be rolled back from **Firebase Console → Hosting → Release history**. Neither rollback restores or reverses database changes; handle DB migrations separately.

## Ongoing command log

Add date, working directory, command, result, and any created resource here as we proceed. Never record passwords, API keys, access tokens, or service-account private keys.

| Date | Action | Result |
|---|---|---|
| 2026-10-15 | Cloud SQL Studio: `CREATE EXTENSION IF NOT EXISTS vector; CREATE EXTENSION IF NOT EXISTS ltree; GRANT USAGE, CREATE ON SCHEMA public TO sapio;` | User reports successful execution. |
