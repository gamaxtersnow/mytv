# Project Notes

## Local Build Environment

- Java: use Homebrew OpenJDK 17 at `/opt/homebrew/opt/openjdk@17`
- Android SDK root: `/opt/homebrew/share/android-commandlinetools`
- Gradle commands in this repo should be run with:
  - `JAVA_HOME=/opt/homebrew/opt/openjdk@17`
- Project-local SDK config is expected in ignored file `local.properties`:

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

## Build Verification

- Unit tests:
  - `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest`
- Debug build:
  - `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug`

## Working Rules

- `local.properties` is local-only and ignored; keep SDK path there instead of hardcoding it in source.
- Do not assume system `java` is configured. Use the explicit `JAVA_HOME` above when invoking Gradle.
