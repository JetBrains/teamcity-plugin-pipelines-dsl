

package jetbrains.buildServer.configs.kotlin.v2019_2

typealias SnapshotDependencyOptions = SnapshotDependency.() -> Unit

@TeamCityDsl
interface Stage {

    fun dependsOn(vararg buildTypes: BuildType, options: SnapshotDependencyOptions = {})

    fun dependsOn(vararg stages: Stage, options: SnapshotDependencyOptions = {})

    fun buildTypes(): List<BuildType>
}

@TeamCityDsl
interface CompoundStage: Stage {

    fun buildType(bt: BuildType, options: SnapshotDependencyOptions = {}): BuildType

    fun sequential(options: SnapshotDependencyOptions = {}, block: CompoundStage.() -> Unit): CompoundStage

    fun parallel(options: SnapshotDependencyOptions = {}, block: CompoundStage.() -> Unit): CompoundStage

}

@TeamCityDsl
interface DependencyConstructor {

    fun buildDependencies()

    fun buildDependencyOn(stage: Stage, options: SnapshotDependencyOptions)
}