/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

class Single(project: Project?, val buildType: BuildType) : Stage, DependencyConstructor, AbstractStage(project) {

    override fun buildTypes(): List<BuildType> {
        return listOf(buildType)
    }

    override fun buildDependencyOn(stage: Stage, options: SnapshotDependencyOptions) {
        if (stage is Single) {
            if (StageFactory.isDependencyNew(this, stage, options)) {
                buildType.dependencies.dependency(stage.buildType) {
                    snapshot(options)
                }
            }
        } else if (stage is ParallelStageImpl) {
            stage.stages.forEach {
                buildDependencyOn(it, options)
            }
        } else if (stage is SequentialStageImpl) {
            stage.stages.lastOrNull()?.let {
                buildDependencyOn(it, options)
            }
        }
    }
}