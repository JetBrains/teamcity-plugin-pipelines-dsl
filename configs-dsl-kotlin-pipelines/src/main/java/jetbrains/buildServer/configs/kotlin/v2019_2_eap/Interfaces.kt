package jetbrains.buildServer.configs.kotlin.v2019_2_eap

import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType

interface Stage {

    fun dependsOn(bt: BuildType, dependencySettings: DependencySettings = {})

    fun dependsOn(stage: Stage, dependencySettings: DependencySettings = {})
}

interface CompoundStage: Stage {

    fun buildType(bt: BuildType, dependencySettings: DependencySettings = {}, block: BuildType.() -> Unit = {}): BuildType

    fun buildType(dependencySettings: DependencySettings = {}, block: BuildType.() -> Unit): BuildType

    fun sequence(dependencySettings: DependencySettings = {}, block: Sequence.() -> Unit): Sequence

    fun sequence(composite: BuildType? = null, dependencySettings: DependencySettings = {}, block: Sequence.() -> Unit): Sequence

    fun composite(name: String, block: BuildType.() -> Unit = {}): BuildType

    fun composite(block: BuildType.() -> Unit): BuildType
}

interface Sequence: CompoundStage {

    fun parallel(dependencySettings: DependencySettings = {}, block: CompoundStage.() -> Unit): CompoundStage

    fun parallel(composite: BuildType? = null, dependencySettings: DependencySettings = {}, block: CompoundStage.() -> Unit): CompoundStage
}

interface DependencyConstructor {

    fun buildDependencies()

    fun buildDependencyOn(stage: Stage, settings: DependencySettings)
}