package jetbrains.buildServer.configs.kotlin.v2019_2

abstract class AbstractStage(val project: Project?): Stage, DependencyConstructor {
    internal var dependencyOptions: SnapshotDependencyOptions = {}
    private val dependencies = mutableListOf<Pair<AbstractStage, SnapshotDependencyOptions>>()

    override fun dependsOn(vararg buildTypes: BuildType, options: SnapshotDependencyOptions) {
        buildTypes.forEach {
            val stage = StageFactory.single(project, it)
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