package jetbrains.buildServer.configs.kotlin.v2019_2_eap

import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType

interface Stage {

    fun dependsOn(bt: BuildType, dependencySettings: DependencySettings = {})

    fun dependsOn(stage: Stage, dependencySettings: DependencySettings = {})
}

interface CompoundStage: Stage {

    fun buildType(bt: BuildType, dependencySettings: DependencySettings = {}, block: BuildType.() -> Unit = {}): BuildType

    fun buildType(dependencySettings: DependencySettings = {}, block: BuildType.() -> Unit): BuildType

    fun sequential(dependencySettings: DependencySettings = {}, block: CompoundStage.() -> Unit): CompoundStage

    fun parallel(dependencySettings: DependencySettings = {}, block: CompoundStage.() -> Unit): CompoundStage

}

interface DependencyConstructor {

    fun buildDependencies()

    fun buildDependencyOn(stage: Stage, settings: DependencySettings)
}