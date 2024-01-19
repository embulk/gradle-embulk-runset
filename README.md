Gradle plugin to set up an environment for running Embulk
==========================================================

It'd be an alternative of `embulk mkbundle` so that users could maintain their Maven-based Embulk plugin installations.

Note that everything (including syntax, behavior, and else) can change drastically in later versions. This is yet work-in-progress.

Usage
------

Set up Gradle 8.4 or later (often with [the Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)), and prepare `build.gradle` like below.

```
plugins {
    id "org.embulk.runset" version "0.1.1"  // Just apply this Gradle plugin.
}

repositories {
    mavenCentral()

    // Add another Maven repository if you need.
}

installEmbulkRunSet {
    // Set your Embulk home directory to install the Embulk plugins.
    embulkHome "path/to/embulk-home"

    // Declare a plugin in the short form.
    artifact "org.embulk:embulk-input-postgresql:0.13.2"

    // Declare a plugin in the long form.
    artifact group: "org.embulk", name: "embulk-input-s3", version: "0.6.0"
}
```

Then run `./gradlew installEmbulkRunSet`.
