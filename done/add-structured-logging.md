# Add structured logging with metric extraction

- [x] web/src/main/resources/logback-spring.xml updated
  - springProfile name="!cloud": human-readable format (unchanged)
  - springProfile name="cloud": JSON structured logging for Cloud Logging
  - Activate with SPRING_PROFILES_ACTIVE=cloud on Cloud Run
