# Contributing to Intelligent Screenshot Extractor

Thank you for considering a contribution! This document outlines the guidelines
for contributing to this project.

## How to Contribute

### Reporting Bugs

1. Search existing [issues](https://github.com/suraj-shingade/intelligent-screenshot-extractor/issues) to avoid duplicates.
2. Open a new issue using the **Bug Report** template.
3. Include steps to reproduce, expected vs. actual behaviour, and your environment
   (OS, JDK version, Maven version).

### Suggesting Features

Open a new issue using the **Feature Request** template and describe the use case,
expected behaviour, and any alternatives you have considered.

### Submitting Pull Requests

1. **Fork** the repository and create a feature branch from `main`.
2. Follow existing code style and architecture conventions.
3. Add or update unit tests for any new or changed behaviour.
4. Ensure `mvn clean verify` passes locally before pushing.
5. Open a pull request against `main` with a clear description.

## Development Setup

```bash
# Prerequisites: JDK 22+, Maven 3.9+
git clone https://github.com/suraj-shingade/intelligent-screenshot-extractor.git
cd intelligent-screenshot-extractor
mvn clean verify
mvn exec:java          # launch the GUI
```

## Code Style

- Follow standard Java naming conventions.
- Keep classes focused and cohesive (single responsibility).
- Use meaningful Javadoc on public APIs.
- All new code must include the project license header.

## License

By contributing, you agree that your contributions will be licensed under the
[MIT License](LICENSE).
