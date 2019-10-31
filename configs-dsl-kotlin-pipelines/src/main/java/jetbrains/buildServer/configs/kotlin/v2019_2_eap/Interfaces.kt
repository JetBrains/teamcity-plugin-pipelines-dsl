package jetbrains.buildServer.configs.kotlin.v2019_2_eap

import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.SnapshotDependency

typealias SnapshotDependencyOptions = SnapshotDependency.() -> Unit

interface Stage {

    fun dependsOn(bt: BuildType, options: SnapshotDependencyOptions = {})

    fun dependsOn(stage: Stage, options: SnapshotDependencyOptions = {})
}

interface CompoundStage: Stage {

    fun buildType(bt: BuildType, options: SnapshotDependencyOptions = {}, block: BuildType.() -> Unit = {}): BuildType

    fun buildType(options: SnapshotDependencyOptions = {}, block: BuildType.() -> Unit): BuildType

    fun sequential(options: SnapshotDependencyOptions = {}, block: CompoundStage.() -> Unit): CompoundStage

    fun parallel(options: SnapshotDependencyOptions = {}, block: CompoundStage.() -> Unit): CompoundStage

}

interface DependencyConstructor {

    fun buildDependencies()

    fun buildDependencyOn(stage: Stage, options: SnapshotDependencyOptions)
}