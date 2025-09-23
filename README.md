# ResponsiveAuthApp

ResponsiveAuthApp is a Spring Boot starter application showcasing a responsive web experience secured with email/password and Google OAuth 2.0 authentication. The UI is built with Thymeleaf and Bootstrap and demonstrates how to surface public and secured pages behind a unified navigation experience.

## Features

- Spring Boot 3 with Java 21 and Maven.
- Email/password registration backed by Firebase Authentication with user profiles stored in Cloud Firestore.
- Spring Security configuration with form login and Google OAuth 2.0 login entry point.
- Responsive layout powered by Bootstrap 5, including a splash screen and persistent top navigation bar with user avatar/initials.
- Sample Thymeleaf pages for unauthenticated and authenticated visitors.
- Ready-to-style components and custom CSS for cohesive branding.

## Getting started

1. Ensure you have Java 21 available in your environment.
2. Configure optional Google OAuth 2.0 credentials by exporting the variables before running the app:

   ```bash
   export GOOGLE_CLIENT_ID=your-google-client-id
   export GOOGLE_CLIENT_SECRET=your-google-client-secret
   ```

3. Configure Firebase if you want to enable user self-registration (see [Firebase configuration](#firebase-configuration)).

4. Build and run the application:

   ```bash
   ./mvnw spring-boot:run
   ```

5. Navigate to <http://localhost:8080> to explore the experience.

### Firebase configuration

Follow these steps to provision Firebase services and wire them into the application:

1. **Create a Firebase project**
   - Visit <https://console.firebase.google.com/>, sign in with your Google account, and create a new project.
   - Choose a descriptive name (for example, `responsive-auth-app`) and accept the default settings.

2. **Register a web app**
   - From the project overview click **Add app** → **Web**.
   - Provide an app nickname (e.g., `responsive-auth-web`) and click **Register app**.
   - Copy the generated **Firebase SDK snippet** or open the project settings later to retrieve the **Web API key**. You will use this key for server-side password verification.

3. **Enable Email/Password authentication**
   - In the Firebase console open **Build → Authentication → Sign-in method**.
   - Enable **Email/Password** and save the change.

4. **Provision Cloud Firestore**
   - Navigate to **Build → Firestore Database** and click **Create database**.
   - Choose a region close to your users and start in **Production mode** (rules can be tightened later). Firestore will store user profile documents created during registration.

5. **Create a service account key for the backend**
   - Go to **Project settings → Service accounts** and click **Generate new private key**.
   - Store the downloaded JSON file outside of your source tree (for example `~/secrets/firebase-service-account.json`).
   - The service account needs permission to use Firebase Authentication and Firestore. The default key generated from the Firebase console already includes the required roles.

6. **Export environment variables so the Spring Boot app can reach Firebase**

   ```bash
   export FIREBASE_ENABLED=true
   export FIREBASE_CREDENTIALS=file:/absolute/path/to/firebase-service-account.json
   export FIREBASE_API_KEY=your-firebase-web-api-key
   # Optional: customise the Firestore collection used to store user profiles (defaults to "users")
   export FIREBASE_USERS_COLLECTION=users
   ```

   - The `FIREBASE_CREDENTIALS` value accepts any Spring `Resource` location, so you can also use `classpath:` if you manage credentials through a secrets manager.
   - When deploying to a server or container orchestrator, configure the same variables securely through your platform's secret management solution.

7. **Restart the application** so that the new configuration is picked up. Visit `/register` to create your first user and then sign in with the same email address on the `/login` page.

### Default credentials

Two sample users remain configured in-memory as a convenience while Firebase integration is disabled:

| Username | Password | Roles       |
|----------|----------|-------------|
| `jane`   | `password` | `ROLE_USER` |
| `admin`  | `password` | `ROLE_USER`, `ROLE_ADMIN` |

Set `FIREBASE_ENABLED=true` (and supply the credentials/API key described above) to disable these fallback accounts and rely exclusively on Firebase for authentication.

### OAuth 2.0 login

To enable Google sign-in, create OAuth credentials in the Google Cloud Console and configure the `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` environment variables. The default callback URL is `http://localhost:8080/login/oauth2/code/google`.

## Project structure

- `src/main/java` – Spring Boot application, configuration, and MVC controllers.
- `src/main/resources/templates` – Thymeleaf templates using a reusable layout.
- `src/main/resources/static` – Custom styling for avatars, splash screen, and layout refinements.
- `src/test/java` – Sample test bootstrapped by Spring Initializr.

## License

This project is provided as a starter template and may be adapted to suit your needs.
