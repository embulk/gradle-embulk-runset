/*
 * Copyright 2023 The Embulk project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.embulk.gradle.runset;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.gradle.api.Action;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskAction;
import org.gradle.maven.MavenModule;
import org.gradle.maven.MavenPomArtifact;

/**
 * A Gradle Task to set up an environment for running Embulk.
 */
public class InstallEmbulkRunSet extends Copy {
    public InstallEmbulkRunSet() {
        super();

        this.project = this.getProject();
        this.logger = this.project.getLogger();
        this.embulkSystemProperties = new Properties();
        this.embulkSystemPropertiesSource = null;
        this.m2RepoRelative = DEFAULT_M2_REPO_RELATIVE;

        final ObjectFactory objectFactory = this.project.getObjects();
    }

    /**
     * Adds a Maven artifact to be installed.
     *
     * <p>It tries to simulate Gradle's dependency notations, but it is yet far from perfect.
     *
     * @see <a href="https://github.com/gradle/gradle/blob/v8.7.0/platforms/software/dependency-management/src/main/java/org/gradle/api/internal/notations/DependencyNotationParser.java#L49-L86">org.gradle.api.internal.notations.DependencyNotationParser#create</a>
     */
    public void artifact(final Object dependencyNotation) {
        final Dependency dependency;
        if (dependencyNotation instanceof CharSequence) {
            dependency = this.dependencyFromCharSequence((CharSequence) dependencyNotation);
        } else if (dependencyNotation instanceof Map) {
            dependency = this.dependencyFromMap((Map) dependencyNotation);
        } else {
            throw new IllegalDependencyNotation("Supplied module notation is invalid.");
        }

        // Constructing an independent (detached) Configuration so that its dependencies are not affected by other plugins.
        final Configuration configuration = this.project.getConfigurations().detachedConfiguration(dependency);

        final ResolvableDependencies resolvableDependencies = configuration.getIncoming();
        final ArtifactCollection artifactCollection = resolvableDependencies.getArtifacts();

        // Getting the JAR files and component IDs.
        final ArrayList<ComponentIdentifier> componentIds = new ArrayList<>();
        for (final ResolvedArtifactResult resolvedArtifactResult : artifactCollection.getArtifacts()) {
            componentIds.add(resolvedArtifactResult.getId().getComponentIdentifier());
            this.fromArtifact(resolvedArtifactResult, "jar");
        }

        // Getting the POM files.
        final ArtifactResolutionResult artifactResolutionResult = this.project.getDependencies()
                .createArtifactResolutionQuery()
                .forComponents(componentIds)
                .withArtifacts(MavenModule.class, MavenPomArtifact.class)
                .execute();
        for (final ComponentArtifactsResult componentArtifactResult : artifactResolutionResult.getResolvedComponents()) {
            for (final ArtifactResult artifactResult : componentArtifactResult.getArtifacts(MavenPomArtifact.class)) {
                if (artifactResult instanceof ResolvedArtifactResult) {
                    final ResolvedArtifactResult resolvedArtifactResult = (ResolvedArtifactResult) artifactResult;
                    this.fromArtifact(resolvedArtifactResult, "pom");
                }
            }
        }
    }

    public InstallEmbulkRunSet embulkHome(final File dir) {
        if (dir == null) {
            throw new InvalidUserDataException("Supplied embulkHome is null.");
        }

        if (!dir.isAbsolute()) {
            throw new InvalidUserDataException(
                    "Supplied embulkHome \"" + dir.toString() + "\" is not absolute."
                    + " Get an absolute path by: File#getAbsoluteFile()");
        }

        if (dir.exists()) {
            if (dir.isDirectory()) {
                this.logger.lifecycle("Supplied embulkHome \"{}\" already exists.", dir);
            } else {
                throw new InvalidUserDataException(
                        "Supplied embulkHome \"" + dir.toString() + "\" already exists, but is not a directory.");
            }
        } else {
            // TODO: Check parents recursively?
            this.logger.lifecycle("Supplied embulkHome \"{}\" does not exist, then will be created.", dir);
        }

        super.into(dir);
        return this;
    }

    public InstallEmbulkRunSet m2RepoRelative(final String dir) {
        if (dir == null) {
            throw new InvalidUserDataException("Supplied m2RepoRelative is null.");
        }

        try {
            this.m2RepoRelative = Paths.get(dir);
        } catch (final InvalidPathException ex) {
            throw new InvalidUserDataException("Supplied m2RepoRelative \"" + dir + "\"is invalid.", ex);
        }

        if (this.m2RepoRelative.isAbsolute()) {
            throw new InvalidUserDataException(
                    "Supplied m2RepoRelative \"" + dir + "\" is absolute."
                    + " Supply a relative path from embulkHome.");
        }

        return this.embulkSystemProperty("m2_repo", this.m2RepoRelative.toString());
    }

    public InstallEmbulkRunSet embulkSystemProperty(final String key, final String value) {
        this.createPropertiesSourceAndSetToCopy();
        this.embulkSystemProperties.setProperty(key, value);
        return this;
    }

    @Override
    public final Copy into​(final Object destDir) {
        throw new InvalidUserDataException("\"into\" is not permitted in InstallEmbulkRunSet. Use \"embulkHome\" instead.");
    }

    @Override
    public final Copy into​(final Object destDir, final groovy.lang.Closure configureClosure) {
        throw new InvalidUserDataException("\"into\" is not permitted in InstallEmbulkRunSet. Use \"embulkHome\" instead.");
    }

    @Override
    public final Copy into(final Object destPath, final Action<? super CopySpec> copySpec) {
        throw new InvalidUserDataException("\"into\" is not permitted in InstallEmbulkRunSet. Use \"embulkHome\" instead.");
    }

    private void fromArtifact(final ResolvedArtifactResult resolvedArtifactResult, final String artifactType) {
        final ComponentIdentifier id = resolvedArtifactResult.getId().getComponentIdentifier();
        final File file = resolvedArtifactResult.getFile();

        if (id instanceof ModuleComponentIdentifier) {
            final Path modulePath = moduleToPath((ModuleComponentIdentifier) id);
            this.logger.lifecycle("Setting to copy {}:{} into {}", id, artifactType, modulePath);
            this.logger.info("Cached file: {}", file);
            this.from(file, copy -> {
                copy.into(this.m2RepoRelative.resolve(modulePath).toFile());
                copy.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            });
        } else if (id instanceof ProjectComponentIdentifier) {
            throw new IllegalDependencyNotation("Cannot install artifacts for a project component (" + id.getDisplayName() + ")");
        } else {
            throw new IllegalDependencyNotation(
                    "Cannot resolve the artifacts for component "
                    + id.getDisplayName()
                    + " with unsupported type "
                    + id.getClass().getName()
                    + ".");
        }
    }

    private static Path moduleToPath(final ModuleComponentIdentifier id) {
        final String[] splitGroup = id.getGroup().split("\\.");
        if (splitGroup.length <= 0) {
            return Paths.get("");
        }

        final String[] more = new String[splitGroup.length + 2 - 1];
        for (int i = 1; i < splitGroup.length; i++) {
            more[i - 1] = splitGroup[i];
        }
        more[splitGroup.length - 1] = id.getModule();
        more[splitGroup.length] = id.getVersion();
        final Path path = Paths.get(splitGroup[0], more);
        assert !path.isAbsolute();
        return path;
    }

    // https://github.com/gradle/gradle/blob/v8.7.0/platforms/software/dependency-management/src/main/java/org/gradle/api/internal/notations/DependencyStringNotationConverter.java
    private Dependency dependencyFromCharSequence(final CharSequence dependencyNotation) {
        final String notationString = dependencyNotation.toString();
        this.logger.info("Supplied artifact: {}", notationString);
        return this.project.getDependencies().create(notationString);
    }

    // https://github.com/gradle/gradle/blob/v8.7.0/subprojects/core/src/main/java/org/gradle/internal/typeconversion/MapNotationConverter.java
    private Dependency dependencyFromMap(final Map dependencyNotation) {
        final Map<String, String> notationMap = validateMap(dependencyNotation);
        this.logger.info("Supplied artifact: {}", notationMap);
        return this.project.getDependencies().create(notationMap);
    }

    private static Map<String, String> validateMap(final Map dependencyNotation) {
        final LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (final Map.Entry<Object, Object> entry : castMap(dependencyNotation).entrySet()) {
            final Object keyObject = entry.getKey();
            if (!(keyObject instanceof CharSequence)) {
                throw new IllegalDependencyNotation("Supplied Map module notation is invalid. Its key must be a String.");
            }
            final String key = (String) keyObject;
            if (!ACCEPTABLE_MAP_KEYS.contains(key)) {
                throw new IllegalDependencyNotation(
                        "Supplied Map module notation is invalid. Its key must be one of: ["
                        + String.join(", ", ACCEPTABLE_MAP_KEYS)
                        + "]");
            }

            final Object valueObject = entry.getValue();
            if (!(valueObject instanceof CharSequence)) {
                throw new IllegalDependencyNotation("Supplied Map module notation is invalid. Its value must be a String.");
            }
            final String value = (String) valueObject;
            map.put(key, value);
        }
        return Collections.unmodifiableMap(map);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> castMap(final Map map) {
        return (Map<Object, Object>) map;
    }

    @Override
    @TaskAction
    protected void copy() {
        if (this.embulkSystemPropertiesSource != null) {
            try (final OutputStream out = Files.newOutputStream(this.embulkSystemPropertiesSource)) {
                this.embulkSystemProperties.store(
                        out, "Generated by the \"org.embulk.embulk-runset\" Gradle plugin.");
            } catch (final IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        super.copy();
    }

    private synchronized void createPropertiesSourceAndSetToCopy() {
        if (this.embulkSystemPropertiesSource == null) {
            final File temporaryDir = this.getTemporaryDir();
            final Path path;
            try {
                path = Files.createTempFile(temporaryDir.toPath(), "embulk.", ".properties");
            } catch (final IOException ex) {
                throw new UncheckedIOException(ex);
            }

            this.from(path.toFile(), copySpec -> {
                copySpec.rename(path.getFileName().toString(), "embulk.properties");
            });

            this.embulkSystemPropertiesSource = path;
        }
    }

    private static final Path DEFAULT_M2_REPO_RELATIVE = Paths.get("lib").resolve("m2").resolve("repository");

    // https://github.com/gradle/gradle/blob/v8.7.0/platforms/software/dependency-management/src/main/java/org/gradle/api/internal/notations/DependencyMapNotationConverter.java#L42-L58
    private static List<String> ACCEPTABLE_MAP_KEYS =
            Arrays.asList("group", "name", "version", "configuration", "ext", "classifier");

    private final Logger logger;

    private final Project project;

    private final Properties embulkSystemProperties;

    private Path embulkSystemPropertiesSource;

    private Path m2RepoRelative;
}
