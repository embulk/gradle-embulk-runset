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

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * A Gradle plugin to set up an environment for running Embulk.
 */
public class EmbulkRunSetPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        project.getTasks().register("installEmbulkRunSet", InstallEmbulkRunSet.class);
    }
}
