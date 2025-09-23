# ResponsiveAuthApp

ResponsiveAuthApp is a Spring Boot starter application showcasing a responsive web experience secured with username/password and Google OAuth 2.0 authentication. The UI is built with Thymeleaf and Bootstrap and demonstrates how to surface public and secured pages behind a unified navigation experience.

## Features

- Spring Boot 3 with Java 21 and Maven.
- Spring Security configuration with form login, in-memory users, and Google OAuth 2.0 login entry point.
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

3. Build and run the application:

   ```bash
   ./mvnw spring-boot:run
   ```

4. Navigate to <http://localhost:8080> to explore the experience.

### Default credentials

Two sample users are configured in-memory:

| Username | Password | Roles       |
|----------|----------|-------------|
| `jane`   | `password` | `ROLE_USER` |
| `admin`  | `password` | `ROLE_USER`, `ROLE_ADMIN` |

Use either account to access the protected dashboard.

### OAuth 2.0 login

To enable Google sign-in, create OAuth credentials in the Google Cloud Console and configure the `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` environment variables. The default callback URL is `http://localhost:8080/login/oauth2/code/google`.

## Project structure

- `src/main/java` – Spring Boot application, configuration, and MVC controllers.
- `src/main/resources/templates` – Thymeleaf templates using a reusable layout.
- `src/main/resources/static` – Custom styling for avatars, splash screen, and layout refinements.
- `src/test/java` – Sample test bootstrapped by Spring Initializr.

## License

This project is provided as a starter template and may be adapted to suit your needs.
