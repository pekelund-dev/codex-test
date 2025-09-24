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

Follow these steps to allow the application to persist users and profiles in Firestore:

1. **Create a Firebase or Google Cloud project**
   - Visit <https://console.firebase.google.com/>, sign in with your Google account, and create a new project (for example, `responsive-auth-app`).
   - Accept the default settings or tailor them to your environment.

2. **Provision Cloud Firestore**
   - Navigate to **Build → Firestore Database** and click **Create database**.
   - Choose a region close to your users and start in **Production mode** (rules can be tightened later). Firestore stores the hashed credentials and profile details captured during registration.

3. **Create a service account key for the backend**
   - Go to **Project settings → Service accounts** and click **Generate new private key**.
   - Store the downloaded JSON file outside of your source tree (for example `~/secrets/firestore-service-account.json`).
   - The service account needs permission to access Firestore. The default key generated from the Firebase console already includes the required roles.

4. **Export environment variables so the Spring Boot app can reach Firestore**

   ```bash
   export FIRESTORE_ENABLED=true
   export FIRESTORE_CREDENTIALS=file:/absolute/path/to/firestore-service-account.json
   # Optional: specify the Google Cloud project ID if it cannot be derived from the credentials
   export FIRESTORE_PROJECT_ID=your-project-id
   # Optional: customise the Firestore collection used to store user profiles (defaults to "users")
   export FIRESTORE_USERS_COLLECTION=users
   # Optional: override the default Spring Security role assigned to registered users (defaults to "ROLE_USER")
   export FIRESTORE_DEFAULT_ROLE=ROLE_USER
   ```

   - The `FIRESTORE_CREDENTIALS` value accepts any Spring `Resource` location, so you can also use `classpath:` if you manage credentials through a secrets manager.
   - When deploying to a server or container orchestrator, configure the same variables securely through your platform's secret management solution.

5. **Restart the application** so that the new configuration is picked up. Visit `/register` to create your first user and then sign in with the same email address on the `/login` page.

### Google Cloud Storage configuration

Enabling Google Cloud Storage (GCS) unlocks the `/receipts` workspace where authenticated users can drag, drop, and review receipt files.

1. **Choose or create a Google Cloud project**
   - Visit <https://console.cloud.google.com/projectcreate> to create a new project or switch to an existing one.
   - Make note of the _Project ID_; you will reference it from your environment variables.

2. **Enable the Cloud Storage API**
   - In the Google Cloud Console, open **APIs & Services → Library**.
   - Search for _"Cloud Storage API"_, select it, and click **Enable**.

3. **Create a receipts bucket**
   - Navigate to **Cloud Storage → Buckets** and click **Create**.
   - Supply a globally unique bucket name (for example `responsive-auth-receipts`).
   - Choose a region close to your users and keep **Uniform bucket-level access** enabled for simplified permissions.
   - Leave the bucket private; the application will read the file list through service account credentials.

4. **Create a dedicated service account**
   - Go to **IAM & Admin → Service Accounts** and click **Create service account**.
   - Provide a descriptive name such as `responsive-auth-receipts-uploader`.
   - Grant the role **Storage Object Admin** (or a more restrictive custom role that allows `storage.objects.create`, `storage.objects.get`, and `storage.objects.list`).
   - Skip granting user access and finish the wizard.

5. **Generate a service account key**
   - From the new service account's **Keys** tab click **Add key → Create new key** and choose **JSON**.
   - Store the downloaded JSON file securely (for example `/home/user/secrets/gcs-receipts.json`). Never commit the file to source control.

6. **Export the environment variables used by the application**

   ```bash
   export GCS_ENABLED=true
   export GCS_BUCKET=responsive-auth-receipts           # Replace with your bucket name
   export GCS_CREDENTIALS=file:/home/user/secrets/gcs-receipts.json
   export GCS_PROJECT_ID=your-project-id                # Optional if derived from credentials
   ```

   - The `GCS_CREDENTIALS` variable accepts any Spring `Resource` URI. When running on Google Cloud (Compute Engine, Cloud Run, etc.) you can omit `GCS_CREDENTIALS` and rely on the platform's default service account; in that case the application will call `GoogleCredentials.getApplicationDefault()`.
   - Keep credentials outside of the repository and inject them through your deployment platform's secret manager in production.

7. **Restart the application** and sign in to an authenticated account.
   - Open <http://localhost:8080/receipts> to upload files and view the bucket inventory table populated from Cloud Storage.

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
