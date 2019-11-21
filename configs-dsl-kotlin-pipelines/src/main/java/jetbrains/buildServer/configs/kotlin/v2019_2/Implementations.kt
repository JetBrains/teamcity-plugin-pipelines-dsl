package jetbrains.buildServer.configs.kotlin.v2019_2

abstract class CompoundStageImpl(project: Project): CompoundStage, AbstractStage(project) {

    val stages = arrayListOf<AbstractStage>()

    override fun buildTypes(): List<BuildType> {
        return stages.flatMap { it.buildTypes() }
    }

    override fun buildType(bt: BuildType, options: SnapshotDependencyOptions, block: BuildType.() -> Unit): BuildType {
        bt.apply(block)
        val stage = Single(project, bt)
        stage.dependencyOptions(options)
        stages.add(stage)
        return bt
    }

    override fun buildType(options: SnapshotDependencyOptions, block: BuildType.() -> Unit): BuildType {
        val bt = BuildType().apply(block)
        val stage = Single(project, bt)
        stage.dependencyOptions(options)
        stages.add(stage)
        return bt
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

    fun sequential(project: Project, composite: BuildType?, options: SnapshotDependencyOptions, block: CompoundStage.() -> Unit): CompoundStage {
        val sequence = SequentialStageImpl(project).apply(block)
        composite?.let {
            it.apply { type = BuildTypeSettings.Type.COMPOSITE }
            sequence.stages.add(Single(project, composite))
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

    fun parallel(project: Project, composite: BuildType?, options: SnapshotDependencyOptions, block: CompoundStage.() -> Unit): CompoundStage {
        val parallel = ParallelImpl(project).apply(block)
        if (composite == null) {
            stages.add(parallel)
            parallel.dependencyOptions(options)
            return parallel
        } else {
            val compositeSequence = SequentialStageImpl(project)
            compositeSequence.stages.add(parallel)
            compositeSequence.stages.add(Single(project, composite))
            compositeSequence.dependencyOptions(options)
            stages.add(compositeSequence)
            return compositeSequence
        }
    }
}

abstract class AbstractStage(val project: Project): Stage, DependencyConstructor {
    var dependencyOptions: SnapshotDependencyOptions = {}
    val dependencies = mutableListOf<Pair<AbstractStage, SnapshotDependencyOptions>>()

    override fun dependsOn(vararg buildTypes: BuildType, options: SnapshotDependencyOptions) {
        buildTypes.forEach {
            val stage = Single(project, it)
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

class Single(project: Project, val buildType: BuildType) : Stage, DependencyConstructor, AbstractStage(project) {
    override fun buildTypes(): List<BuildType> {
        return listOf(buildType)
    }

    override fun buildDependencyOn(stage: Stage, options: SnapshotDependencyOptions) {
        if (stage is Single) {
            if (buildType.dependencies.items.stream()
                            .noneMatch {it.buildTypeId == stage.buildType && it.snapshot != null}) {
                buildType.dependencies.dependency(stage.buildType) {
                    snapshot(options)
                }
            }
        } else if (stage is ParallelImpl) {
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

class ParallelImpl(project: Project) : CompoundStage, DependencyConstructor, CompoundStageImpl(project) {

    override fun buildDependencyOn(stage: Stage, options: SnapshotDependencyOptions) {
        stages.forEach { it.buildDependencyOn(stage) {
            options()
            (it.dependencyOptions)()
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

fun BuildType.dependsOn(bt: BuildType, options: SnapshotDependencyOptions = {}) {
    dependencies.dependency(bt) {
        snapshot(options)
    }
}
