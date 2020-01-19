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

fun Project.sequential(block: CompoundStage.() -> Unit): CompoundStage {
    val sequence = StageFactory.sequential(null).apply(block)
    sequence.buildDependencies()
    if (sequence.project != null)
        sequence.project.registerBuilds(sequence)
    return sequence
}

private fun Project.registerBuilds(stage: AbstractStage) {
    if (stage is CompoundStageImpl) {
        stage.stages.forEach {
            if (null != it.project && !alreadyRegistered(it.project))
                this.subProject(it.project)
            if (stage.project != null && it is Single && !alreadyRegistered(it.buildType)) {
                stage.project.buildType(it.buildType)
            } else {
                registerBuilds(it)
            }
        }
    }
}

private fun Project.alreadyRegistered(subProject: Project): Boolean {
    return this == subProject || this.subProjects.contains(subProject)
            || this.subProjects.any {it.alreadyRegistered(subProject)}
}

private fun Project.alreadyRegistered(buildType: BuildType): Boolean {
    return this.buildTypes.contains(buildType)
            || this.subProjects.any {it.alreadyRegistered(buildType)}
}

