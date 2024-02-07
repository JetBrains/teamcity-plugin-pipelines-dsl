

package jetbrains.buildServer.configs.kotlin.v2019_2

class ParallelStageImpl(project: Project?) : CompoundStage, DependencyConstructor, CompoundStageImpl(project) {

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