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