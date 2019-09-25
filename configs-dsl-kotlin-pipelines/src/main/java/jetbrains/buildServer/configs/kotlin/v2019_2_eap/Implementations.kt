package jetbrains.buildServer.configs.kotlin.v2019_2_eap

import jetbrains.buildServer.configs.kotlin.v2018_2.ArtifactDependency
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import jetbrains.buildServer.configs.kotlin.v2018_2.SnapshotDependency

typealias DependencySettings = SnapshotDependency.() -> Unit


abstract class CompoundStage(project: Project): AbstractStage(project) {
    val stages = arrayListOf<AbstractStage>()
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

    override fun dependencySettings(dependencySettings: DependencySettings) {
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
        } else if (stage is SequenceImpl) {
            stage.stages.lastOrNull()?.let {
                buildDependencyOn(it, settings)
            }
        }
    }
}

class ParallelImpl(project: Project) : Parallel, DependencyConstructor, CompoundStage(project) {

    override fun build(bt: BuildType, dependencySettings: DependencySettings, block: BuildType.() -> Unit): BuildType {
        bt.apply(block)
        val stage = Single(project, bt)
        stage.dependencySettings(dependencySettings)
        stages.add(stage)
        return bt
    }

    override fun build(dependencySettings: DependencySettings, block: BuildType.() -> Unit): BuildType {
        val bt = BuildType().apply(block)
        val stage = Single(project, bt)
        stage.dependencySettings(dependencySettings)
        stages.add(stage)
        return bt
    }

    override fun sequence(block: Sequence.() -> Unit): Sequence {
        return sequence(project, block)
    }

    override fun sequence(project: Project, block: Sequence.() -> Unit): Sequence {
        val sequence = SequenceImpl(project).apply(block)
        stages.add(sequence)
        return sequence
    }

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

class SequenceImpl(project: Project) : Sequence, DependencyConstructor, CompoundStage(project) {

    override fun sequence(block: Sequence.() -> Unit): Sequence {
        return sequence(project, block)
    }

    override fun sequence(project: Project, block: Sequence.() -> Unit): Sequence {
        val sequence = SequenceImpl(project).apply(block)
        stages.add(sequence)
        return sequence
    }

    override fun parallel(block: Parallel.() -> Unit): Parallel {
        return parallel(project, block)
    }

    override fun parallel(project: Project, block: Parallel.() -> Unit): Parallel {
        val parallel = ParallelImpl(project).apply(block)
        stages.add(parallel)
        return parallel
    }

    override fun build(bt: BuildType, dependencySettings: DependencySettings, block: BuildType.() -> Unit): BuildType {
        bt.apply(block)
        val stage = Single(project, bt)
        stage.dependencySettings(dependencySettings)
        stages.add(stage)
        return bt
    }

    override fun build(dependencySettings: DependencySettings, block: BuildType.() -> Unit): BuildType {
        val bt = BuildType().apply(block)
        val stage = Single(project, bt)
        stage.dependencySettings(dependencySettings)
        stages.add(stage)
        return bt
    }

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

fun Project.sequence(block: Sequence.() -> Unit): Sequence {
    val sequence = SequenceImpl(this).apply(block)
    sequence.buildDependencies()
    registerBuilds(sequence)
    return sequence
}

private fun Project.registerBuilds(stage: AbstractStage) {
    if (stage is CompoundStage) {
        stage.stages.forEach {
            if (!alreadyRegistered(it.project))
                this.subProject(it.project)
            if (it is Single) {
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

fun Project.build(bt: BuildType, block: BuildType.() -> Unit = {}): BuildType {
    bt.apply(block)
    buildType(bt)
    return bt
}

fun Project.build(block: BuildType.() -> Unit): BuildType {
    val bt = BuildType().apply(block)
    buildType(bt)
    return bt
}


fun BuildType.produces(artifacts: String) {
    artifactRules = artifacts
}

fun BuildType.requires(bt: BuildType, artifacts: String, settings: ArtifactDependency.() -> Unit = {}) {
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
