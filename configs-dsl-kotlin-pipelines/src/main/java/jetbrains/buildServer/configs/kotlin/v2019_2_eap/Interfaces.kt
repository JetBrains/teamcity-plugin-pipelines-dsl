package jetbrains.buildServer.configs.kotlin.v2019_2_eap

import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.Project

interface Stage {
    fun dependsOn(bt: BuildType, dependencySettings: DependencySettings = {})

    fun dependsOn(stage: Stage, dependencySettings: DependencySettings = {})

    fun dependencySettings(dependencySettings: DependencySettings = {})
}

interface Parallel: Stage {
    fun build(bt: BuildType, block: BuildType.() -> Unit = {}): BuildType

    fun build(block: BuildType.() -> Unit): BuildType

    fun sequence(block: Sequence.() -> Unit): Sequence

    fun sequence(project: Project, block: Sequence.() -> Unit): Sequence
}

interface Sequence: Stage {
    fun sequence(block: Sequence.() -> Unit): Sequence

    fun sequence(project: Project, block: Sequence.() -> Unit): Sequence

    fun parallel(block: Parallel.() -> Unit): Parallel

    fun parallel(project: Project, block: Parallel.() -> Unit): Parallel

    fun build(bt: BuildType, dependencySettings: DependencySettings = {}, block: BuildType.() -> Unit = {}): BuildType

    fun build(dependencySettings: DependencySettings = {}, block: BuildType.() -> Unit): BuildType
}

interface DependencyConstructor {

    fun buildDependencies()

    fun buildDependencyOn(stage: Stage, settings: DependencySettings)
}