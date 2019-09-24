package jetbrains.buildServer.configs.kotlin.v2019_2_eap

import jetbrains.buildServer.configs.kotlin.v2018_2.ArtifactDependency
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import jetbrains.buildServer.configs.kotlin.v2018_2.SnapshotDependency

typealias DependencySettings = SnapshotDependency.() -> Unit

abstract class Stage(val project: Project) {
    var dependencySettings: DependencySettings = {}
    val dependencies = mutableListOf<Pair<Stage, DependencySettings>>()

    fun dependsOn(bt: BuildType, dependencySettings: DependencySettings = {}) {
        val stage = Single(project, bt)
        dependsOn(stage, dependencySettings)
    }

    fun dependsOn(stage: Stage, dependencySettings: DependencySettings = {}) {
        dependencies.add(Pair(stage, dependencySettings))
    }

    fun dependencySettings(dependencySettings: DependencySettings = {}) {
        this.dependencySettings = dependencySettings
    }
}

class Single(project: Project, val buildType: BuildType) : Stage(project)

abstract class CompoundStage(project: Project): Stage(project) {
    val stages = arrayListOf<Stage>()
}

class Parallel(project: Project) : CompoundStage(project) {

    fun build(bt: BuildType, block: BuildType.() -> Unit = {}): BuildType {
        bt.apply(block)
        stages.add(Single(project, bt))
        return bt
    }

    fun build(block: BuildType.() -> Unit): BuildType {
        val bt = BuildType().apply(block)
        stages.add(Single(project, bt))
        return bt
    }

    fun sequence(block: Sequence.() -> Unit): Sequence {
        return sequence(project, block)
    }

    fun sequence(project: Project, block: Sequence.() -> Unit): Sequence {
        val sequence = Sequence(project).apply(block)
        buildDependencies(sequence)
        stages.add(sequence)
        return sequence
    }
}

class Sequence(project: Project) : CompoundStage(project) {

    fun sequence(block: Sequence.() -> Unit): Sequence {
        return sequence(project, block)
    }

    fun sequence(project: Project, block: Sequence.() -> Unit): Sequence {
        val sequence = Sequence(project).apply(block)
        buildDependencies(sequence)
        stages.add(sequence)
        return sequence
    }

    fun parallel(block: Parallel.() -> Unit): Parallel {
        return parallel(project, block)
    }

    fun parallel(project: Project, block: Parallel.() -> Unit): Parallel {
        val parallel = Parallel(project).apply(block)
        stages.add(parallel)
        return parallel
    }

    fun build(bt: BuildType, block: BuildType.() -> Unit = {}, dependencySettings: DependencySettings = {}): BuildType {
        bt.apply(block)
        val stage = Single(project, bt)
        stage.dependencySettings(dependencySettings)
        stages.add(stage)
        return bt
    }

    fun build(block: BuildType.() -> Unit, dependencySettings: DependencySettings = {}): BuildType {
        val bt = BuildType().apply(block)
        val stage = Single(project, bt)
        stage.dependencySettings(dependencySettings)
        stages.add(stage)
        return bt
    }
}


fun Project.sequence(block: Sequence.() -> Unit): Sequence {
    val sequence = Sequence(this).apply(block)
    buildDependencies(sequence)
    registerBuilds(sequence)
    return sequence
}

fun buildDependencies(sequence: Sequence) {
    sequence.dependencies.forEach {
        stageDependsOnStage(sequence, it)
    }

    var previous: Pair<Stage, DependencySettings>? = null

    for (stage in sequence.stages) {
        if (previous != null) {
            stageDependsOnStage(stage, Pair(previous.first, stage.dependencySettings))
        }
        stage.dependencies.forEach { dependency ->
            stageDependsOnStage(stage, dependency)
        }

        val dependencySettings = previous?.second ?: {}
        previous = Pair(stage, dependencySettings)
    }
}

fun stageDependsOnStage(stage: Stage, dependency: Pair<Stage, DependencySettings>) {
    val s = dependency.first
    val d = dependency.second
    if (s is Single) {
        stageDependsOnSingle(stage, Pair(s, d))
    }
    if (s is Parallel) {
        stageDependsOnParallel(stage, Pair(s, d))
    }
    if (s is Sequence) {
        stageDependsOnSequence(stage, Pair(s, d))
    }
}

fun stageDependsOnSingle(stage: Stage, dependency: Pair<Single, DependencySettings>) {
    if (stage is Single) {
        singleDependsOnSingle(stage, dependency)
    }
    if (stage is Parallel) {
        parallelDependsOnSingle(stage, dependency)
    }
    if (stage is Sequence) {
        sequenceDependsOnSingle(stage, dependency)
    }
}

fun stageDependsOnParallel(stage: Stage, dependency: Pair<Parallel, DependencySettings>) {
    if (stage is Single) {
        singleDependsOnParallel(stage, dependency)
    }
    if (stage is Parallel) {
        parallelDependsOnParallel(stage, dependency)
    }
    if (stage is Sequence) {
        sequenceDependsOnParallel(stage, dependency)
    }
}

fun stageDependsOnSequence(stage: Stage, dependency: Pair<Sequence, DependencySettings>) {
    if (stage is Single) {
        singleDependsOnSequence(stage, dependency)
    }
    if (stage is Parallel) {
        parallelDependsOnSequence(stage, dependency)
    }
    if (stage is Sequence) {
        sequenceDependsOnSequence(stage, dependency)
    }
}

fun singleDependsOnSingle(stage: Single, dependency: Pair<Single, DependencySettings>) {
    stage.buildType.dependencies.dependency(dependency.first.buildType) {
        snapshot(dependency.second)
    }
}

fun singleDependsOnParallel(stage: Single, dependency: Pair<Parallel, DependencySettings>) {
    dependency.first.stages.forEach { d ->
        if (d is Single)
            singleDependsOnSingle(stage, Pair(d, dependency.second))
        else if (d is Sequence)
            singleDependsOnSequence(stage, Pair(d, dependency.second))
        else if (d is Parallel)
            singleDependsOnParallel(stage, Pair(d, dependency.second))
    }
}

fun singleDependsOnSequence(stage: Single, dependency: Pair<Sequence, DependencySettings>) {
    dependency.first.stages.lastOrNull()?.let { lastStage ->
        if (lastStage is Single) {
            singleDependsOnSingle(stage, Pair(lastStage, dependency.second))
        }
        if (lastStage is Parallel) {
            singleDependsOnParallel(stage, Pair(lastStage, dependency.second))
        }
        if (lastStage is Sequence) {
            singleDependsOnSequence(stage, Pair(lastStage, dependency.second))
        }
    }
}

fun parallelDependsOnSingle(stage: Parallel, dependency: Pair<Single, DependencySettings>) {
    stage.stages.forEach { d ->
        if (d is Single)
            singleDependsOnSingle(d, dependency)
        else if (d is Sequence)
            sequenceDependsOnSingle(d, dependency)
        else if (d is Parallel)
            parallelDependsOnSingle(d, dependency)
    }
}

fun parallelDependsOnParallel(stage: Parallel, dependency: Pair<Parallel, DependencySettings>) {
    stage.stages.forEach { d ->
        if (d is Single)
            singleDependsOnParallel(d, dependency)
        else if (d is Sequence)
            sequenceDependsOnParallel(d, dependency)
        else if (d is Parallel)
            parallelDependsOnParallel(d, dependency)
    }
}

fun parallelDependsOnSequence(stage: Parallel, dependency: Pair<Sequence, DependencySettings>) {
    stage.stages.forEach { d ->
        if (d is Single)
            singleDependsOnSequence(d, dependency)
        else if (d is Sequence)
            sequenceDependsOnSequence(d, dependency)
        else if (d is Parallel)
            parallelDependsOnSequence(d, dependency)
    }
}

fun sequenceDependsOnSingle(stage: Sequence, dependency: Pair<Single, DependencySettings>) {
    stage.stages.firstOrNull()?.let { firstStage ->
        if (firstStage is Single) {
            singleDependsOnSingle(firstStage, dependency)
        }
        if (firstStage is Parallel) {
            parallelDependsOnSingle(firstStage, dependency)
        }
        if (firstStage is Sequence) {
            sequenceDependsOnSingle(firstStage, dependency)
        }
    }
}

fun sequenceDependsOnParallel(stage: Sequence, dependency: Pair<Parallel, DependencySettings>) {
    stage.stages.firstOrNull()?.let { firstStage ->
        if (firstStage is Single) {
            singleDependsOnParallel(firstStage, dependency)
        }
        if (firstStage is Parallel) {
            parallelDependsOnParallel(firstStage, dependency)
        }
        if (firstStage is Sequence) {
            sequenceDependsOnParallel(firstStage, dependency)
        }
    }
}

fun sequenceDependsOnSequence(stage: Sequence, dependency: Pair<Sequence, DependencySettings>) {
    stage.stages.firstOrNull()?.let { firstStage ->
        if (firstStage is Single) {
            singleDependsOnSequence(firstStage, dependency)
        }
        if (firstStage is Parallel) {
            parallelDependsOnSequence(firstStage, dependency)
        }
        if (firstStage is Sequence) {
            sequenceDependsOnSequence(firstStage, dependency)
        }
    }
}

private fun Project.registerBuilds(stage: Stage) {
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

/**
 * !!!WARNING!!!
 *
 * This method works as expected only if the <code>stage</code> is already populated
 */
fun BuildType.dependsOn(stage: Stage, dependencySettings: DependencySettings = {}) {
    val single = Single(stage.project, this)
    single.dependsOn(stage, dependencySettings) //TODO: does it really work?
}

fun BuildType.dependencySettings(dependencySettings: DependencySettings = {}) {
    throw IllegalStateException("dependencySettings can only be used with parallel {} or sequence {}. Please use dependsOn instead")
}


