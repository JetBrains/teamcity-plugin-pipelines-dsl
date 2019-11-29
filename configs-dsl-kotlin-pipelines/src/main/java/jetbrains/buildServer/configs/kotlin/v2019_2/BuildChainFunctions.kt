package jetbrains.buildServer.configs.kotlin.v2019_2

fun sequential(block: CompoundStage.() -> Unit): CompoundStage {
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
