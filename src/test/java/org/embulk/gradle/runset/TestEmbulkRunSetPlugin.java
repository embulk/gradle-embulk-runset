/*
 * Copyright 2022 The Embulk project
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

import static org.embulk.gradle.runset.Util.prepareProjectDir;
import static org.embulk.gradle.runset.Util.runGradle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestEmbulkRunSetPlugin  {
    @Test
    public void testSimple(@TempDir Path tempDir) throws IOException, URISyntaxException {
        final Path projectDir = prepareProjectDir(tempDir, "simple");

        runGradle(projectDir, "installEmbulkRunSet");

        Files.walkFileTree(projectDir.resolve("build/simple"), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    System.out.println(projectDir.relativize(file));
                    return FileVisitResult.CONTINUE;
                }
            });

        final Properties properties = new Properties();
        final Path propertiesPath = projectDir.resolve("build/simple/embulk.properties");
        Files.copy(propertiesPath, System.out);
        try (final InputStream in = Files.newInputStream(propertiesPath)) {
            properties.load(in);
        }
        assertEquals(3, properties.size());
        assertEquals("value", properties.getProperty("key"));
        assertEquals(Paths.get("lib").resolve("m2").resolve("repository").toString(), properties.getProperty("m2_repo"));
        final URI jrubyUri = new URI(properties.getProperty("jruby"));
        assertEquals("file", jrubyUri.getScheme());
        final Path jrubyAbsolutePath = Paths.get(jrubyUri);
        assertTrue(jrubyAbsolutePath.isAbsolute());
        assertTrue(jrubyAbsolutePath.endsWith(
                       Paths.get("lib")
                       .resolve("m2")
                       .resolve("repository")
                       .resolve("org")
                       .resolve("jruby")
                       .resolve("jruby-complete")
                       .resolve("9.1.15.0")
                       .resolve("jruby-complete-9.1.15.0.jar")));
    }
}
