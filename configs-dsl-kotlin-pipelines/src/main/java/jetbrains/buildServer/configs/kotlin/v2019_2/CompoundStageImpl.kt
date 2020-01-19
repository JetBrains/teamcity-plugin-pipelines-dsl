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

abstract class CompoundStageImpl(project: Project?): CompoundStage, AbstractStage(project) {

    val stages = arrayListOf<AbstractStage>()

    override fun buildTypes(): List<BuildType> {
        return stages.flatMap { it.buildTypes() }
    }

    override fun buildType(bt: BuildType, options: SnapshotDependencyOptions): BuildType {
        return buildType(bt, options) {}
    }

    fun buildType(bt: BuildType, options: SnapshotDependencyOptions, block: BuildType.() -> Unit): BuildType {
        bt.apply(block)
        val stage = StageFactory.single(project, bt)
        stage.dependencyOptions(options)
        stages.add(stage)
        return bt
    }

    fun buildType(options: SnapshotDependencyOptions, block: BuildType.() -> Unit): BuildType {
        return buildType(BuildType(), options, block)
    }

    fun composite(name: String, block: BuildType.() -> Unit): BuildType {
        return BuildType { this.name = name; id = DslContext.createId(name); type = BuildTypeSettings.Type.COMPOSITE }.apply(block)
    }

    fun composite(block: BuildType.() -> Unit): BuildType {
        return BuildType().apply { type = BuildTypeSettings.Type.COMPOSITE }.apply(block)
    }

    override fun sequential(options: SnapshotDependencyOptions, block: CompoundStage.() -> Unit): CompoundStage {
        return sequential(project, null, options, block)
    }

    fun sequential(project: Project, options: SnapshotDependencyOptions, block: CompoundStage.() -> Unit): CompoundStage {
        return sequential(project, null, options, block)
    }

    fun sequential(composite: BuildType?, options: SnapshotDependencyOptions, block: CompoundStage.() -> Unit): CompoundStage {
        return sequential(project, composite, options, block)
    }

    fun sequential(project: Project?, composite: BuildType?, options: SnapshotDependencyOptions, block: CompoundStage.() -> Unit): CompoundStage {
        val sequence = StageFactory.sequential(project).apply(block)
        composite?.let {
            it.apply { type = BuildTypeSettings.Type.COMPOSITE }
            sequence.stages.add(StageFactory.single(project, composite))
        }
        stages.add(sequence)
        sequence.dependencyOptions(options)
        return sequence
    }

    override fun parallel(options: SnapshotDependencyOptions, block: CompoundStage.() -> Unit): CompoundStage {
        return parallel(project, null, options, block)
    }

    fun parallel(project: Project, options: SnapshotDependencyOptions, block: CompoundStage.() -> Unit): CompoundStage {
        return parallel(project, null, options, block)
    }

    fun parallel(composite: BuildType?, options: SnapshotDependencyOptions, block: CompoundStage.() -> Unit): CompoundStage {
        return parallel(project, composite, options, block)
    }

    fun parallel(project: Project?, composite: BuildType?, options: SnapshotDependencyOptions, block: CompoundStage.() -> Unit): CompoundStage {
        val parallel = StageFactory.parallel(project).apply(block)
        return if (composite == null) {
            stages.add(parallel)
            parallel.dependencyOptions(options)
            parallel
        } else {
            val compositeSequence = StageFactory.sequential(project)
            compositeSequence.stages.add(parallel)
            compositeSequence.stages.add(StageFactory.single(project, composite))
            compositeSequence.dependencyOptions(options)
            stages.add(compositeSequence)
            compositeSequence
        }
    }
}