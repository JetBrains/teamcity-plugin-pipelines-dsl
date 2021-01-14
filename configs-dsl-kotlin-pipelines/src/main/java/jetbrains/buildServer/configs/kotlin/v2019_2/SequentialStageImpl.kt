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

class SequentialStageImpl(project: Project?) : CompoundStage, DependencyConstructor, CompoundStageImpl(project) {

    override fun buildDependencies() {
        super.buildDependencies()

        var previous: AbstractStage? = null

        for (stage in stages) {
            stage.buildDependencies()
            if (previous != null) {
                stage.buildDependencyOn(previous, stage.dependencyOptions)
            }
            previous = stage
        }
    }

    override fun buildDependencyOn(stage: Stage, options: SnapshotDependencyOptions) {
        stages.firstOrNull()?.let {
            it.buildDependencyOn(stage) {
                options()
                (it.dependencyOptions)()
            }
        }
    }
}