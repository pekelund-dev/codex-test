# ResponsiveAuthApp

ResponsiveAuthApp is a Spring Boot starter application showcasing a responsive web experience secured with email/password and Google OAuth 2.0 authentication. The UI is built with Thymeleaf and Bootstrap and demonstrates how to surface public and secured pages behind a unified navigation experience.

## Features

- Spring Boot 3 with Java 21 and Maven.
- Email/password registration handled by Spring Security with credentials hashed and user profiles stored in Cloud Firestore.
- Spring Security configuration with form login and Google OAuth 2.0 login entry point.
- Responsive layout powered by Bootstrap 5, including a splash screen and persistent top navigation bar with user avatar/initials.
- Sample Thymeleaf pages for unauthenticated and authenticated visitors.
- Ready-to-style components and custom CSS for cohesive branding.
- Optional receipts workspace with drag-and-drop uploads that sync to Google Cloud Storage.

## Getting started

### Quick Setup with Environment Script

For the fastest setup, use the provided environment script that configures all necessary Google Cloud settings:

```bash
# Source the environment setup script
source setup-env.sh

# Start the application
./mvnw spring-boot:run
```

The `setup-env.sh` script automatically configures:
- Google Cloud authentication
- Firestore database connection
- Google Cloud Storage bucket access
- OAuth2 credentials
- Cloud Function environment variables

### Manual Setup

1. Ensure you have Java 21 available in your environment.
2. Configure optional Google OAuth 2.0 credentials by exporting the variables before running the app:

   ```bash
   export GOOGLE_CLIENT_ID=your-google-client-id
   export GOOGLE_CLIENT_SECRET=your-google-client-secret
   ```

3. Configure Firestore if you want to enable user self-registration (see [Firestore configuration](#firestore-configuration)).
4. (Optional) Configure Google Cloud Storage to enable the receipts upload page (see
   [Google Cloud Storage configuration](#google-cloud-storage-configuration)).

5. Build and run the application:

   ```bash
   ./mvnw spring-boot:run
   ```

6. Navigate to <http://localhost:8080> to explore the experience.

### Firestore configuration

Firestore stores user profiles and receipt parsing output. Choose the setup style that suits your workflow:

- [Firestore configuration with the gcloud CLI](docs/gcp-setup-gcloud.md#configure-firestore-via-gcloud)
- [Firestore configuration with the Cloud Console](docs/gcp-setup-cloud-console.md#configure-firestore-in-the-console)

Both guides walk through project creation, database provisioning, service accounts, and environment variables required by the Spring Boot application.

### Google Cloud Storage configuration

The receipts workspace reads from a private Cloud Storage bucket. Follow one of the companion guides to provision the bucket and credentials:

- [Storage configuration with the gcloud CLI](docs/gcp-setup-gcloud.md#configure-cloud-storage-via-gcloud)
- [Storage configuration with the Cloud Console](docs/gcp-setup-cloud-console.md#configure-cloud-storage-in-the-console)

After completing either path, restart the application and visit <http://localhost:8080/receipts> to upload and view receipt files.

### Receipt parsing Cloud Function (Vertex AI Gemini)

The `ReceiptProcessingFunction` module processes finalized uploads from the receipts bucket, extracts structured data with Gemini, and stores the result in Firestore. 

#### Quick Deployment

Use the automated deployment script for a streamlined setup:

```bash
# Deploy the Cloud Function with all required configurations
./deploy-cloud-function.sh
```

This script automatically:
- Enables all required Google Cloud APIs
- Creates and configures service accounts with proper IAM roles
- Detects the correct region for your Cloud Storage bucket
- Builds and deploys the function with optimal settings
- Provides comprehensive error handling and troubleshooting

#### Manual Deployment

Select the deployment style you prefer:

- [Deploy the function with the gcloud CLI](docs/gcp-setup-gcloud.md#deploy-the-receipt-processing-function)
- [Deploy the function with the Cloud Console](docs/gcp-setup-cloud-console.md#deploy-the-receipt-processing-function)

Both documents describe prerequisites, metadata expectations, status updates, verification steps, and comprehensive troubleshooting guides for the Gemini-powered pipeline.

### Fallback credentials

When Firestore integration is disabled the application falls back to an in-memory user store, but no accounts are created automatically. Configure explicit credentials for local testing by defining `firestore.fallback-users` entries in `application.yml` (or through environment variables):

```yaml
firestore:
  fallback-users:
    - username: ${FIRESTORE_FALLBACK_ADMIN_USERNAME:}
      password: ${FIRESTORE_FALLBACK_ADMIN_PASSWORD:}
      roles:
        - ROLE_ADMIN
        - ROLE_USER
    - username: ${FIRESTORE_FALLBACK_USER_USERNAME:}
      password: ${FIRESTORE_FALLBACK_USER_PASSWORD:}
      roles:
        - ROLE_USER
```

Each configured password is encoded with the active `PasswordEncoder` on startup, so store only plaintext secrets in development configuration files and provide them securely through environment variables in production.

### OAuth 2.0 login

To enable Google sign-in, create OAuth credentials in the Google Cloud Console and configure the `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` environment variables. The default callback URL is `http://localhost:8080/login/oauth2/code/google`.

## Project structure

- `src/main/java` – Spring Boot application, configuration, and MVC controllers.
- `src/main/resources/templates` – Thymeleaf templates using a reusable layout.
- `src/main/resources/static` – Custom CSS and JavaScript for avatars, splash screen, and feature pages such as receipts.
- `src/test/java` – Sample test bootstrapped by Spring Initializr.

## License

This project is provided as a starter template and may be adapted to suit your needs.
