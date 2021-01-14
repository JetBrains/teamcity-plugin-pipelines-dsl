/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.configs.kotlin.v2019_2

abstract class AbstractStage(val project: Project?): Stage, DependencyConstructor {
    internal var dependencyOptions: SnapshotDependencyOptions = {}
    private val dependencies = mutableListOf<Pair<AbstractStage, SnapshotDependencyOptions>>()

    override fun dependsOn(vararg buildTypes: BuildType, options: SnapshotDependencyOptions) {
        buildTypes.forEach {
            val stage = StageFactory.single(project, it)
            dependsOn(stage, options = options)
        }
    }

    override fun dependsOn(vararg stages: Stage, options: SnapshotDependencyOptions) {
        stages.forEach {
            it as AbstractStage
            dependencies.add(Pair(it, options))
        }
    }

    fun dependencyOptions(options: SnapshotDependencyOptions) {
        dependencyOptions = options
    }

    override fun buildDependencies() {
        dependencies.forEach {
            buildDependencyOn(it.first, it.second)
        }
    }
}