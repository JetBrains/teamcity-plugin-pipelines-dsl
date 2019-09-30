package jetbrains.buildServer.configs.kotlin.v2019_2_eap

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotDependenciesTest {

    @Test
    fun simpleSequence() {
        //region given for simpleSequence
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        //endregion

        val project = Project {
            sequence {
                build(a)
                build(b)
                build(c)
            }
        }

        //region assertions for simpleSequence
        assertEquals(3, project.buildTypes.size)

        assertDependencyIds(
            Pair(setOf(), a),
            Pair(setOf("A"), b),
            Pair(setOf("B"), c)
        )
        //endregion
    }

    @Test
    fun simpleWithInlineBuilds() {
        var a: BuildType? = null
        var b: BuildType? = null
        var c: BuildType? = null
        val project = Project {
            sequence {
                a = build {
                    id("A")
                    produces("artifact")
                }
                parallel {
                    b = build {
                        id("B")
                        requires(a!!, "artifact")
                    }
                    c = build { id("C") }
                }
            }
        }
        assertEquals(3, project.buildTypes.size)

        assertDependencyIds(
                Pair(setOf(), a!!),
                Pair(setOf("A"), b!!),
                Pair(setOf("A"), c!!)
        )
    }

    @Test
    fun minimalDiamond() {
        //region given for minimalDiamond
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }
        //endregion

        val project = Project {
            sequence {
                build(a)
                parallel {
                    build(b)
                    build(c)
                }
                build(d)
            }
        }

        //region assertions for minimalDiamond
        assertEquals(4, project.buildTypes.size)

        assertDependencyIds(
            Pair(setOf(), a),
            Pair(setOf("A"), b),
            Pair(setOf("A"), c),
            Pair(setOf("B", "C"), d)
        )
        //endregion
    }

    @Test
    fun sequenceInParallel() {
        //region given for sequenceInParallel
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }
        val e = BuildType { id("E") }
        //endregion

        val project = Project {
            sequence {
                build(a)
                parallel {
                    build(b)
                    sequence {
                        build(c)
                        build(d)
                    }
                }
                build(e)
            }
        }

        //region assertions for sequenceInParallel
        assertEquals(5, project.buildTypes.size)

        assertDependencyIds(
            Pair(setOf(), a),
            Pair(setOf("A"), b),
            Pair(setOf("A"), c),
            Pair(setOf("C"), d),
            Pair(setOf("B", "D"), e)
        )
        //endregion
    }

    @Test
    fun sequenceInSequenceInSequence() {
        //region given for sequenceInParallel
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }
        //endregion

        val project = Project {
            sequence {
                build(a)
                sequence {
                    sequence {
                        build(b)
                        build(c)
                    }
                    build(d)
                }
            }
        }

        //region assertions for sequenceInParallel
        assertEquals(4, project.buildTypes.size)

        assertDependencyIds(
                Pair(setOf(), a),
                Pair(setOf("A"), b),
                Pair(setOf("B"), c),
                Pair(setOf("C"), d)
        )
        //endregion
    }

    @Test
    fun outOfSequenceDependency() {
        //region given for outOfSequenceDependency
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }
        val e = BuildType { id("E") }
        val f = BuildType { id("F") }
        //endregion

        val project = Project {

            build(f) // 'f' does not belong to sequence

            sequence {
                build(a)
                parallel {
                    build(b) {
                        dependsOn(f)
                    }
                    sequence {
                        build(c)
                        build(d)
                    }
                }
                build(e)
            }
        }

        //region assertions for outOfSequenceDependency
        assertEquals(6, project.buildTypes.size)

        assertDependencyIds(
            Pair(setOf(), a),
            Pair(setOf("A", "F"), b),
            Pair(setOf("A"), c),
            Pair(setOf("C"), d),
            Pair(setOf("B", "D"), e),
            Pair(setOf(), f)
        )
        //endregion
    }


    @Test
    fun parallelDependsOnParallel() {
        //region given for parallelDependsOnParallel
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }
        val e = BuildType { id("E") }
        val f = BuildType { id("F") }
        //endregion

        val project = Project {
            sequence {
                build(a)
                parallel {
                    build(b)
                    build(c)
                }
                parallel {
                    build(d)
                    build(e)
                }
                build(f)
            }
        }

        //region assertions for parallelDependsOnParallel
        assertEquals(6, project.buildTypes.size)

        assertDependencyIds(
            Pair(setOf(), a),
            Pair(setOf("A"), b),
            Pair(setOf("A"), c),
            Pair(setOf("B", "C"), d),
            Pair(setOf("B", "C"), e),
            Pair(setOf("E", "D"), f)
        )
        //endregion
    }

    @Test
    fun simpleDependencySettings() {

        //region given for simpleDependencySettings
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }
        //endregion

        val project = Project {
            sequence {
                build(a)
                sequence(settings) {
                    build(b)
                }
            }
        }

        //region assertions for simpleDependencySettings
        assertEquals(2, project.buildTypes.size)

        assertDependencies(
            Pair(setOf(), a),
            Pair(setOf(DepData("A", SnapshotDependency().apply(settings))), b)
        )
        //endregion
    }

    @Test
    fun singleBuildDependencySettings() {

        //region given for simpleDependencySettings
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }
        //endregion

        val project = Project {
            sequence {
                build(a)
                build(b)
                build(c, dependencySettings = settings)
            }
        }

        //region assertions for simpleDependencySettings
        assertEquals(3, project.buildTypes.size)

        assertDependencies(
                Pair(setOf(), a),
                Pair(setOf(DepData("A", SnapshotDependency())), b),
                Pair(setOf(DepData("B", SnapshotDependency().apply(settings))), c)
        )
        //endregion
    }

    @Test
    fun singleBuildDependencySettingsInParallel() {

        //region given for simpleDependencySettings
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }
        //endregion

        val project = Project {
            sequence {
                build(a)
                parallel {
                    build(b)
                    build(c, dependencySettings = settings)
                }
            }
        }

        //region assertions for simpleDependencySettings
        assertEquals(3, project.buildTypes.size)

        assertDependencies(
                Pair(setOf(), a),
                Pair(setOf(DepData("A", SnapshotDependency())), b),
                Pair(setOf(DepData("A", SnapshotDependency().apply(settings))), c)
        )
        //endregion
    }

    @Test
    fun sequenceDependencySettingsInParallel() {

        //region given for simpleDependencySettings
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }
        //endregion

        val project = Project {
            sequence {
                build(a)
                parallel {
                    build(b)
                    sequence(settings) {
                        build(c)
                        build(d)
                    }

                }
            }
        }

        //region assertions for simpleDependencySettings
        assertEquals(4, project.buildTypes.size)

        assertDependencies(
                Pair(setOf(), a),
                Pair(setOf(DepData("A", SnapshotDependency())), b),
                Pair(setOf(DepData("A", SnapshotDependency().apply(settings))), c),
                Pair(setOf(DepData("C", SnapshotDependency())), d)
        )
        //endregion
    }

    @Test
    fun sequenceInParallelSettings() {
        //region given for dependsOnWithSettings
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }
        val e = BuildType { id("E") }
        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }
        //endregion

        val project = Project {
            sequence {
                build(a)
                parallel(settings) {
                    build(b)
                    sequence {
                        build(c)
                        build(d)
                    }
                }
                build(e)
            }
        }

        //region assertions for nestedSequenceSettings
        assertEquals(5, project.buildTypes.size)
        val expDefault = SnapshotDependency()
        val expCustom = SnapshotDependency().apply(settings)

        assertDependencies(
            Pair(setOf(), a),
            Pair(setOf(DepData("A", expCustom)), b),
            Pair(setOf(DepData("A", expCustom)), c),
            /* TODO: The following must be expCustom, but will fail then because
                the settings are only applied to the fan-ins of the parallel block */
            Pair(setOf(DepData("C", expDefault)), d),
            Pair(setOf(DepData("D", expDefault), DepData("B", expDefault)), e)
        )
        //endregion
    }

    @Test
    fun sequenceDependencies() {

        //region given for sequenceDependencies
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }
        //endregion

        val project = Project {
            val s1 = sequence { build(a) }
            val s2 = sequence { build(b) }

            sequence {
                dependsOn(s1, settings)
                dependsOn(s2)
                build(c)
            }
        }

        //region assertions for sequenceDependencies
        assertEquals(3, project.buildTypes.size)

        assertDependencies(
                Pair(setOf(), a),
                Pair(setOf(), b),
                Pair(setOf(
                        DepData("A", SnapshotDependency().apply(settings)),
                        DepData("B", SnapshotDependency())
                ), c)
        )
        //endregion
    }

    private fun assertDependencyIds(vararg expectedAndActual: Pair<Set<String>, BuildType>) {
        expectedAndActual.forEach {
            assertEquals("Wrong number of dependencies in ${it.second.id}",
                    it.first.size, it.second.dependencies.items.filter {it.snapshot != null}.size)
            assertEquals("Failed for ${it.second.id}",
                    it.first, it.second.dependencies.items.filter {it.snapshot !=null}.map {it.buildTypeId.id!!.value}.toSet())
        }
    }

    private fun assertDependencies(vararg expectedAndActual: Pair<Set<DepData>, BuildType>) {
        expectedAndActual.forEach {
            assertEquals("Wrong number of dependencies in ${it.second.id}",
                    it.first.size, it.second.dependencies.items.filter {it.snapshot != null}.size)
           assertEquals("Failed for ${it.second.id}",
                   it.first,
                   it.second.dependencies.items
                           .map {
                               DepData(it.buildTypeId.id!!.value, it.snapshot!!.runOnSameAgent, it.snapshot!!.onDependencyCancel, it.snapshot!!.reuseBuilds)
                           }.toSet())
        }
    }

    private class DepData(val id: String, val onSameAgent: Boolean, val onDependencyCancel: FailureAction, val reuseBuilds: ReuseBuilds) {
        constructor(id: String, depSettings: SnapshotDependency): this(id, depSettings.runOnSameAgent, depSettings.onDependencyCancel, depSettings.reuseBuilds)

        override fun hashCode(): Int {
            return id.hashCode() + 31 * (onSameAgent.hashCode() + 31 * (onDependencyCancel.hashCode() + 31 * reuseBuilds.hashCode()))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DepData

            if (id != other.id) return false
            if (onSameAgent != other.onSameAgent) return false
            if (onDependencyCancel != other.onDependencyCancel) return false
            if (reuseBuilds != other.reuseBuilds) return false

            return true
        }

        override fun toString(): String {
            return "DepData(id='$id', onSameAgent=$onSameAgent, onDependencyCancel=$onDependencyCancel, reuseBuilds=$reuseBuilds)"
        }
    }
}
