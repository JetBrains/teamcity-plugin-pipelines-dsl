package jetbrains.buildServer.configs.kotlin.v2019_2_eap

import jetbrains.buildServer.configs.kotlin.v2018_2.*

typealias DependencySettings = SnapshotDependency.() -> Unit


abstract class CompoundStageImpl(project: Project): CompoundStage, AbstractStage(project) {

    val stages = arrayListOf<AbstractStage>()

    override fun buildType(bt: BuildType, dependencySettings: DependencySettings, block: BuildType.() -> Unit): BuildType {
        bt.apply(block)
        val stage = Single(project, bt)
        stage.dependencySettings(dependencySettings)
        stages.add(stage)
        return bt
    }

    override fun buildType(dependencySettings: DependencySettings, block: BuildType.() -> Unit): BuildType {
        val bt = BuildType().apply(block)
        val stage = Single(project, bt)
        stage.dependencySettings(dependencySettings)
        stages.add(stage)
        return bt
    }

    fun composite(name: String, block: BuildType.() -> Unit): BuildType {
        return BuildType { this.name = name; id = DslContext.createId(name); type = BuildTypeSettings.Type.COMPOSITE }.apply(block)
    }

    fun composite(block: BuildType.() -> Unit): BuildType {
        return BuildType().apply { type = BuildTypeSettings.Type.COMPOSITE }.apply(block)
    }

    override fun sequential(dependencySettings: DependencySettings, block: CompoundStage.() -> Unit): CompoundStage {
        return sequential(project, null, dependencySettings, block)
    }

    fun sequential(project: Project, dependencySettings: DependencySettings, block: CompoundStage.() -> Unit): CompoundStage {
        return sequential(project, null, dependencySettings, block)
    }

    fun sequential(composite: BuildType?, dependencySettings: DependencySettings, block: CompoundStage.() -> Unit): CompoundStage {
        return sequential(project, composite, dependencySettings, block)
    }

    fun sequential(project: Project, composite: BuildType?, dependencySettings: DependencySettings, block: CompoundStage.() -> Unit): CompoundStage {
        val sequence = SequentialStageImpl(project).apply(block)
        composite?.let {
            it.apply { type = BuildTypeSettings.Type.COMPOSITE }
            sequence.stages.add(Single(project, composite))
        }
        stages.add(sequence)
        sequence.dependencySettings(dependencySettings)
        return sequence
    }

    override fun parallel(dependencySettings: DependencySettings, block: CompoundStage.() -> Unit): CompoundStage {
        return parallel(project, null, dependencySettings, block)
    }

    fun parallel(project: Project, dependencySettings: DependencySettings, block: CompoundStage.() -> Unit): CompoundStage {
        return parallel(project, null, dependencySettings, block)
    }

    fun parallel(composite: BuildType?, dependencySettings: DependencySettings, block: CompoundStage.() -> Unit): CompoundStage {
        return parallel(project, composite, dependencySettings, block)
    }

    fun parallel(project: Project, composite: BuildType?, dependencySettings: DependencySettings, block: CompoundStage.() -> Unit): CompoundStage {
        val parallel = ParallelImpl(project).apply(block)
        if (composite == null) {
            stages.add(parallel)
            parallel.dependencySettings(dependencySettings)
            return parallel
        } else {
            val compositeSequence = SequentialStageImpl(project)
            compositeSequence.stages.add(parallel)
            compositeSequence.stages.add(Single(project, composite))
            compositeSequence.dependencySettings(dependencySettings)
            stages.add(compositeSequence)
            return compositeSequence
        }
    }
}

abstract class AbstractStage(val project: Project): Stage, DependencyConstructor {
    var dependencySettings: DependencySettings = {}
    val dependencies = mutableListOf<Pair<AbstractStage, DependencySettings>>()

    override fun dependsOn(bt: BuildType, dependencySettings: DependencySettings) {
        val stage = Single(project, bt)
        dependsOn(stage, dependencySettings)
    }

    override fun dependsOn(stage: Stage, dependencySettings: DependencySettings) {
        stage as AbstractStage
        dependencies.add(Pair(stage, dependencySettings))
    }

    fun dependencySettings(dependencySettings: DependencySettings) {
        this.dependencySettings = dependencySettings
    }

    override fun buildDependencies() {
        dependencies.forEach {
            buildDependencyOn(it.first, it.second)
        }
    }
}

class Single(project: Project, val buildType: BuildType) : Stage, DependencyConstructor, AbstractStage(project) {
    override fun buildDependencyOn(stage: Stage, settings: DependencySettings) {
        if (stage is Single) {
            buildType.dependencies.dependency(stage.buildType) {
                snapshot(settings)
            }
        } else if (stage is ParallelImpl) {
            stage.stages.forEach {
                buildDependencyOn(it, settings)
            }
        } else if (stage is SequentialStageImpl) {
            stage.stages.lastOrNull()?.let {
                buildDependencyOn(it, settings)
            }
        }
    }
}

class ParallelImpl(project: Project) : CompoundStage, DependencyConstructor, CompoundStageImpl(project) {

    override fun buildDependencyOn(stage: Stage, settings: DependencySettings) {
        stages.forEach { it.buildDependencyOn(stage) {
            settings()
            (it.dependencySettings)()
        }}
    }

    override fun buildDependencies() {
        super.buildDependencies()
        stages.forEach { it.buildDependencies()}
    }
}

class SequentialStageImpl(project: Project) : CompoundStage, DependencyConstructor, CompoundStageImpl(project) {

    override fun buildDependencies() {
        super.buildDependencies()

        var previous: AbstractStage? = null

        for (stage in stages) {
            stage.buildDependencies()
            if (previous != null) {
                stage.buildDependencyOn(previous, stage.dependencySettings)
            }
            previous = stage
        }
    }

    override fun buildDependencyOn(stage: Stage, settings: DependencySettings) {
        stages.firstOrNull()?.let {
            it.buildDependencyOn(stage) {
                settings()
                (it.dependencySettings)()
            }
        }
    }
}

fun Project.sequential(block: CompoundStage.() -> Unit): CompoundStage {
    val sequence = SequentialStageImpl(this).apply(block)
    sequence.buildDependencies()
    registerBuilds(sequence)
    return sequence
}

private fun Project.registerBuilds(stage: AbstractStage) {
    if (stage is CompoundStageImpl) {
        stage.stages.forEach {
            if (!alreadyRegistered(it.project))
                this.subProject(it.project)
            if (it is Single && !alreadyRegistered(it.buildType)) {
                stage.project.buildType(it.buildType)
            } else {
                registerBuilds(it)
            }
        }
    }
}

private fun Project.alreadyRegistered(subProject: Project): Boolean {
    return this == subProject || this.subProjects.contains(subProject)
            || this.subProjects.any({it.alreadyRegistered(subProject)})
}

private fun Project.alreadyRegistered(buildType: BuildType): Boolean {
    return this.buildTypes.contains(buildType)
            || this.subProjects.any({it.alreadyRegistered(buildType)})
}

fun BuildType.produces(artifacts: String) {
    artifactRules = artifacts
}

fun BuildType.consumes(bt: BuildType, artifacts: String, settings: ArtifactDependency.() -> Unit = {}) {
    dependencies.artifacts(bt) {
        artifactRules = artifacts
        settings()
    }
}

fun BuildType.dependsOn(bt: BuildType, settings: SnapshotDependency.() -> Unit = {}) {
    dependencies.dependency(bt) {
        snapshot(settings)
    }
}
