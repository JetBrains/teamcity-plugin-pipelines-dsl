package jetbrains.buildServer.configs.kotlin.v2019_2_eap

import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.Project

interface Stage {

    fun dependsOn(bt: BuildType, dependencySettings: DependencySettings = {})

    fun dependsOn(stage: Stage, dependencySettings: DependencySettings = {})
}

interface CompoundStage: Stage {

    fun build(bt: BuildType, dependencySettings: DependencySettings = {}, block: BuildType.() -> Unit = {}): BuildType

    fun build(dependencySettings: DependencySettings = {}, block: BuildType.() -> Unit): BuildType

    fun sequence(dependencySettings: DependencySettings = {}, block: Sequence.() -> Unit): Sequence

    fun sequence(project: Project, dependencySettings: DependencySettings = {}, block: Sequence.() -> Unit): Sequence
}

interface Sequence: CompoundStage {

    fun parallel(dependencySettings: DependencySettings = {}, block: CompoundStage.() -> Unit): CompoundStage

    fun parallel(project: Project, dependencySettings: DependencySettings = {}, block: CompoundStage.() -> Unit): CompoundStage
}

interface DependencyConstructor {

    fun buildDependencies()

    fun buildDependencyOn(stage: Stage, settings: DependencySettings)
}