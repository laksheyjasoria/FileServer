
# Contributing to FileServer

Thank you for contributing to FileServer.

## Types of Contributions

Contributions may include:

* Bug fixes
* New features
* Security improvements
* Performance improvements
* UI improvements
* Refactoring
* Tests
* Documentation

## Development Process

1. Create or fork the repository.
2. Create a feature or bug-fix branch.
3. Make the required changes.
4. Add or update tests.
5. Update documentation where required.
6. Run the test suite.
7. Review your changes.
8. Commit the changes.
9. Push the branch.
10. Open a Pull Request.

## Branch Naming

Examples:

```text
feature/google-oauth
feature/recycle-bin
feature/logger-management
fix/file-upload
fix/authentication
fix/rename-file
docs/api-documentation
```

## Commit Messages

Use descriptive commit messages.

Examples:

```text
Add Google OAuth authentication
Fix recycle bin restore logic
Add debug logger level support
Fix file sharing validation
Update API documentation
```

## Pull Requests

A Pull Request should contain:

* Clear description
* Reason for the change
* Testing performed
* Screenshots for UI changes
* Related issue number when applicable

Before submitting:

```bash
mvn clean test
```

## Bug Reports

Use GitHub Issues.

Include:

* Summary
* Environment
* Version
* Steps to reproduce
* Expected behavior
* Actual behavior
* Logs
* Screenshots where useful

## Coding Standards

### Java

* Use Java 17.
* Follow Java naming conventions.
* Use 4 spaces for indentation.
* Prefer constructor injection.
* Keep methods focused.
* Use meaningful names.
* Handle exceptions properly.
* Avoid unnecessary complexity.

### Spring Boot

Follow the established architecture:

```text
Controller
    â†“
Orchestrator
    â†“
Service
    â†“
Repository
```

Controllers should not contain business logic.

## Security

Never commit:

* Passwords
* JWT secrets
* Master keys
* Database credentials
* SMTP passwords
* Telegram bot tokens
* Private API keys

Use environment variables or an external secret manager.

## Testing

Run all tests:

```bash
mvn test
```

Specific test:

```bash
mvn test -Dtest=UserServiceTest
```

Build without tests:

```bash
mvn clean package -DskipTests
```

## Documentation

If an API changes:

1. Update the API documentation.
2. Update Swagger/OpenAPI information where applicable.
3. Update README if necessary.
4. Update CHANGELOG.

## License

By contributing, you agree that your contribution will be licensed under the MIT License.

## Getting Help

Review existing GitHub Issues before opening a new issue.

For new problems, create a detailed issue with reproduction steps and relevant logs.
