package jetbrains.buildServer.configs.kotlin.v2019_2

class SequentialStageImpl(project: Project?) : CompoundStage, DependencyConstructor, CompoundStageImpl(project) {

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