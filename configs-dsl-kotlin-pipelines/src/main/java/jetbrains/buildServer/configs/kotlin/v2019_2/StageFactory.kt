package jetbrains.buildServer.configs.kotlin.v2019_2

object StageFactory {
    private val singleStages = mutableMapOf<BuildType, MutableSet<Single>>()

    fun single(project: Project?, buildType: BuildType): Single {
        val singleNode = Single(project, buildType)
        val btNodes = singleStages.computeIfAbsent(buildType) { mutableSetOf() }
        btNodes.add(singleNode)
        return singleNode
    }

    fun isDependencyNew(dependant: Single, dependency: Single, options: SnapshotDependencyOptions): Boolean {
        val dependencyToCompare = SnapshotDependency()
        dependencyToCompare.apply(options)
        var isNew = true
        singleStages[dependant.buildType]?.apply {
            forEach {
                val that = it
                val dep = that.buildType.dependencies.items.firstOrNull {
                    it.snapshot != null && it.buildTypeId == dependency.buildType
                }
                if (it != dependant && (dep == null || !isEqual(dep.snapshot!!, dependencyToCompare))) {
                    throw IllegalStateException("Multiple use of a build configuration '${dependant.buildType.name}' in a build chain DSL causes conflicting snapshot dependencies")
                }
                if (dep != null)
                    isNew = false
            }
        }
        return isNew
    }

    fun parallel(project: Project?) = ParallelStageImpl(project)

    fun sequential(project: Project?) = SequentialStageImpl(project)

    private fun isEqual(d1: SnapshotDependency, d2: SnapshotDependency)
            = (d1.runOnSameAgent == d2.runOnSameAgent
            && d1.reuseBuilds == d2.reuseBuilds
            && d1.onDependencyFailure == d2.onDependencyFailure
            && d1.onDependencyCancel == d2.onDependencyCancel
            && d1.synchronizeRevisions == d2.synchronizeRevisions)
}