package jetbrains.buildServer.configs.kotlin.v2019_2_eap

import jetbrains.buildServer.configs.kotlin.v2018_2.ArtifactDependency
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import jetbrains.buildServer.configs.kotlin.v2018_2.SnapshotDependency

typealias DependencySettings = SnapshotDependency.() -> Unit

interface Stage {
    fun dependsOn(bt: BuildType, dependencySettings: DependencySettings = {})

    fun dependsOn(stage: Stage, dependencySettings: DependencySettings = {})

    fun dependencySettings(dependencySettings: DependencySettings = {})
}

abstract class AbstractStage(val project: Project): Stage {
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
}

class Single(project: Project, val buildType: BuildType) : Stage, AbstractStage(project)

abstract class CompoundStage(project: Project): AbstractStage(project) {
    val stages = arrayListOf<AbstractStage>()
}

interface Parallel: Stage {
    fun build(bt: BuildType, block: BuildType.() -> Unit = {}): BuildType

    fun build(block: BuildType.() -> Unit): BuildType

    fun sequence(block: Sequence.() -> Unit): Sequence

    fun sequence(project: Project, block: Sequence.() -> Unit): Sequence
}

class ParallelImpl(project: Project) : Parallel, CompoundStage(project) {

    override fun build(bt: BuildType, block: BuildType.() -> Unit): BuildType {
        bt.apply(block)
        stages.add(Single(project, bt))
        return bt
    }

    override fun build(block: BuildType.() -> Unit): BuildType {
        val bt = BuildType().apply(block)
        stages.add(Single(project, bt))
        return bt
    }

    override fun sequence(block: Sequence.() -> Unit): Sequence {
        return sequence(project, block)
    }

    override fun sequence(project: Project, block: Sequence.() -> Unit): Sequence {
        val sequence = SequenceImpl(project).apply(block)
        buildDependencies(sequence)
        stages.add(sequence)
        return sequence
    }
}

interface Sequence: Stage {
    fun sequence(block: Sequence.() -> Unit): Sequence

    fun sequence(project: Project, block: Sequence.() -> Unit): Sequence

    fun parallel(block: Parallel.() -> Unit): Parallel

    fun parallel(project: Project, block: Parallel.() -> Unit): Parallel

    fun build(bt: BuildType, dependencySettings: DependencySettings = {}, block: BuildType.() -> Unit = {}): BuildType

    fun build(dependencySettings: DependencySettings = {}, block: BuildType.() -> Unit): BuildType
}

class SequenceImpl(project: Project) : Sequence, CompoundStage(project) {

    override fun sequence(block: Sequence.() -> Unit): Sequence {
        return sequence(project, block)
    }

    override fun sequence(project: Project, block: Sequence.() -> Unit): Sequence {
        val sequence = SequenceImpl(project).apply(block)
        buildDependencies(sequence)
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
}


fun Project.sequence(block: Sequence.() -> Unit): Sequence {
    val sequence = SequenceImpl(this).apply(block)
    buildDependencies(sequence)
    registerBuilds(sequence)
    return sequence
}

fun buildDependencies(sequence: SequenceImpl) {
    sequence.dependencies.forEach {
        stageDependsOnStage(sequence, it)
    }

    var previous: AbstractStage? = null

    for (stage in sequence.stages) {
        if (previous != null) {
            stageDependsOnStage(stage, Pair(previous, stage.dependencySettings))
        }
        previous = stage
    }
}

fun stageDependsOnStage(stage: AbstractStage, dependency: Pair<AbstractStage, DependencySettings>) {
    val s = dependency.first
    val d = dependency.second
    if (s is Single) {
        stageDependsOnSingle(stage, Pair(s, d))
    }
    if (s is ParallelImpl) {
        stageDependsOnParallel(stage, Pair(s, d))
    }
    if (s is SequenceImpl) {
        stageDependsOnSequence(stage, Pair(s, d))
    }
}

fun stageDependsOnSingle(stage: AbstractStage, dependency: Pair<Single, DependencySettings>) {
    if (stage is Single) {
        singleDependsOnSingle(stage, dependency)
    }
    if (stage is ParallelImpl) {
        parallelDependsOnSingle(stage, dependency)
    }
    if (stage is SequenceImpl) {
        sequenceDependsOnSingle(stage, dependency)
    }
}

fun stageDependsOnParallel(stage: AbstractStage, dependency: Pair<ParallelImpl, DependencySettings>) {
    if (stage is Single) {
        singleDependsOnParallel(stage, dependency)
    }
    if (stage is ParallelImpl) {
        parallelDependsOnParallel(stage, dependency)
    }
    if (stage is SequenceImpl) {
        sequenceDependsOnParallel(stage, dependency)
    }
}

fun stageDependsOnSequence(stage: AbstractStage, dependency: Pair<SequenceImpl, DependencySettings>) {
    if (stage is Single) {
        singleDependsOnSequence(stage, dependency)
    }
    if (stage is ParallelImpl) {
        parallelDependsOnSequence(stage, dependency)
    }
    if (stage is SequenceImpl) {
        sequenceDependsOnSequence(stage, dependency)
    }
}

fun singleDependsOnSingle(stage: Single, dependency: Pair<Single, DependencySettings>) {
    stage.buildType.dependencies.dependency(dependency.first.buildType) {
        snapshot(dependency.second)
    }
}

fun singleDependsOnParallel(stage: Single, dependency: Pair<ParallelImpl, DependencySettings>) {
    dependency.first.stages.forEach { d ->
        if (d is Single)
            singleDependsOnSingle(stage, Pair(d, dependency.second))
        else if (d is SequenceImpl)
            singleDependsOnSequence(stage, Pair(d, dependency.second))
        else if (d is ParallelImpl)
            singleDependsOnParallel(stage, Pair(d, dependency.second))
    }
}

fun singleDependsOnSequence(stage: Single, dependency: Pair<SequenceImpl, DependencySettings>) {
    dependency.first.stages.lastOrNull()?.let { lastStage ->
        if (lastStage is Single) {
            singleDependsOnSingle(stage, Pair(lastStage, dependency.second))
        }
        if (lastStage is ParallelImpl) {
            singleDependsOnParallel(stage, Pair(lastStage, dependency.second))
        }
        if (lastStage is SequenceImpl) {
            singleDependsOnSequence(stage, Pair(lastStage, dependency.second))
        }
    }
}

fun parallelDependsOnSingle(stage: ParallelImpl, dependency: Pair<Single, DependencySettings>) {
    stage.stages.forEach { d ->
        if (d is Single)
            singleDependsOnSingle(d, dependency)
        else if (d is SequenceImpl)
            sequenceDependsOnSingle(d, dependency)
        else if (d is ParallelImpl)
            parallelDependsOnSingle(d, dependency)
    }
}

fun parallelDependsOnParallel(stage: ParallelImpl, dependency: Pair<ParallelImpl, DependencySettings>) {
    stage.stages.forEach { d ->
        if (d is Single)
            singleDependsOnParallel(d, dependency)
        else if (d is SequenceImpl)
            sequenceDependsOnParallel(d, dependency)
        else if (d is ParallelImpl)
            parallelDependsOnParallel(d, dependency)
    }
}

fun parallelDependsOnSequence(stage: ParallelImpl, dependency: Pair<SequenceImpl, DependencySettings>) {
    stage.stages.forEach { d ->
        if (d is Single)
            singleDependsOnSequence(d, dependency)
        else if (d is SequenceImpl)
            sequenceDependsOnSequence(d, dependency)
        else if (d is ParallelImpl)
            parallelDependsOnSequence(d, dependency)
    }
}

fun sequenceDependsOnSingle(stage: SequenceImpl, dependency: Pair<Single, DependencySettings>) {
    stage.stages.firstOrNull()?.let { firstStage ->
        if (firstStage is Single) {
            singleDependsOnSingle(firstStage, dependency)
        }
        if (firstStage is ParallelImpl) {
            parallelDependsOnSingle(firstStage, dependency)
        }
        if (firstStage is SequenceImpl) {
            sequenceDependsOnSingle(firstStage, dependency)
        }
    }
}

fun sequenceDependsOnParallel(stage: SequenceImpl, dependency: Pair<ParallelImpl, DependencySettings>) {
    stage.stages.firstOrNull()?.let { firstStage ->
        if (firstStage is Single) {
            singleDependsOnParallel(firstStage, dependency)
        }
        if (firstStage is ParallelImpl) {
            parallelDependsOnParallel(firstStage, dependency)
        }
        if (firstStage is SequenceImpl) {
            sequenceDependsOnParallel(firstStage, dependency)
        }
    }
}

fun sequenceDependsOnSequence(stage: SequenceImpl, dependency: Pair<SequenceImpl, DependencySettings>) {
    stage.stages.firstOrNull()?.let { firstStage ->
        if (firstStage is Single) {
            singleDependsOnSequence(firstStage, dependency)
        }
        if (firstStage is ParallelImpl) {
            parallelDependsOnSequence(firstStage, dependency)
        }
        if (firstStage is SequenceImpl) {
            sequenceDependsOnSequence(firstStage, dependency)
        }
    }
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

/**
 * !!!WARNING!!!
 *
 * This method works as expected only if the <code>stage</code> is already populated
 */
fun BuildType.dependsOn(stage: AbstractStage, dependencySettings: DependencySettings = {}) {
    val single = Single(stage.project, this)
    single.dependsOn(stage, dependencySettings) //TODO: does it really work?
}

fun BuildType.dependencySettings(dependencySettings: DependencySettings = {}) {
    throw IllegalStateException("dependencySettings can only be used with parallel {} or sequence {}. Please use dependsOn instead")
}

