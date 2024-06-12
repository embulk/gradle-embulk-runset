Gradle plugin to set up an environment for running Embulk
==========================================================

It'd be an alternative of `embulk mkbundle` so that users could maintain their Maven-based Embulk plugin installations.

Note that everything (including syntax, behavior, and else) can change drastically in later versions. This is yet work-in-progress.

Usage
------

Set up Gradle 8.7 or later, and make `build.gradle` like below.

```
plugins {
    id "org.embulk.runset" version "0.2.0"  // Just apply this Gradle plugin.
}

repositories {
    mavenCentral()
}

installEmbulkRunSet {
    // Set your Embulk home directory (absolute path) to install the Embulk plugins and "embulk.properties".
    embulkHome file("/path/to/your-embulk-home")

    artifact "org.embulk:embulk-input-postgresql:0.13.2"
    artifact group: "org.embulk", name: "embulk-input-s3", version: "0.6.0"

    // It downloads jruby-complete-9.1.15.0.jar, and set the "jruby" Embulk System Property in "embulk.properties".
    jruby "org.jruby:jruby-complete:9.1.15.0"

    // It sets the "key" Embulk System Property to "value" in "embulk.properties".
    embulkSystemProperty "key", "value"
}
```

Run `./gradlew installEmbulkRunSet`, then the plugins and "embulk.properties" are set up in the `embulkHome` directory.
