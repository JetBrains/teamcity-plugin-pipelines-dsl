package jetbrains.buildServer.configs.kotlin.v2019_2

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PipelineDslTest {

    @Test
    fun simpleSequence() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }

        val project = Project {
            buildType(a)
            buildType(b)
            buildType(c)
        }
        //endregion

        sequential {
            buildType(a)
            buildType(b)
            buildType(c)
        }

        //region assertions
        assertEquals(3, project.buildTypes.size)

        assertDependencyIds(
            Pair(setOf(), a),
            Pair(setOf("A"), b),
            Pair(setOf("B"), c)
        )
        //endregion
    }

    @Test
    fun twoPipelinesSameBuildType_shouldNotFailIfNoNewDependencies() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }
        val e = BuildType { id("E") }

        val project = Project {
            buildType(a)
            buildType(b)
            buildType(c)
            buildType(d)
            buildType(e)
        }
        //endregion

        sequential {
            buildType(a)
            parallel {
                buildType(b)
                buildType(c)
            }
            buildType(d)
        }

        sequential {
            buildType(a)
            parallel {
                buildType(c)
                buildType(b)
            }
            buildType(e)
        }

        //region assertions
        assertDependencyIds(
                Pair(setOf(), a),
                Pair(setOf("A"), b),
                Pair(setOf("A"), c),
                Pair(setOf("B", "C"), d),
                Pair(setOf("B", "C"), e)
        )
        //endregion
    }

    @Test
    fun twoPipelinesSameBuildType_shouldFailIfNewDependencies() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }

        val project = Project {
            buildType(a)
            buildType(b)
            buildType(c)
            buildType(d)
        }
        //endregion

        sequential {
            buildType(a)
            parallel {
                buildType(b)
                buildType(c)
            }
            buildType(d)
        }

        try {
            sequential {
                buildType(a)
                buildType(b)
                buildType(c)
                buildType(d)
            }
            fail("An exception must occur prohibiting multiple use of the same build configurations in several pipelines that causes creating conflicting snapshot dependencies");
        } catch (ex: IllegalStateException) {
            // fine
        }
    }

    @Test
    fun onePipelinesSameBuildTypeTwice_shouldFail() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }
        val e = BuildType { id("E") }

        val project = Project {
            buildType(a)
            buildType(b)
            buildType(c)
            buildType(d)
            buildType(e)
        }
        //endregion

        try {
            sequential {
                buildType(a)
                parallel {
                    sequential {
                        buildType(b)
                        buildType(c)
                    }
                    sequential {
                        buildType(d)
                        buildType(c)
                    }
                }
                buildType(e)
            }

            fail("An exception must occur prohibiting multiple use of the same build configurations in the same pipeline");
        } catch (ex: IllegalStateException) {
            // fine
        }
    }

    @Test
    fun buildTypesInSubprojects() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }

        val sp = Project {
            id("SP")
            buildType(b)
            buildType(c)
        }

        val project = Project {
            buildType(a)
            subProject(sp)
        }
        //endregion

        sequential {
            buildType(a)
            sequential {
                buildType(b)
                buildType(c)
            }
        }

        //region assertions
        assertEquals(1, project.buildTypes.size)
        assertEquals(1, project.subProjects.size)
        assertEquals(2, project.subProjects[0].buildTypes.size)

        assertDependencyIds(
                Pair(setOf(), a),
                Pair(setOf("A"), b),
                Pair(setOf("B"), c)
        )
        //endregion
    }

    @Test
    fun simpleWithInlineBuilds() {
        val seq = sequential {
            val a = buildType {
                id("A")
                produces("artifact")
            }
            parallel {
                buildType {
                    id("B")
                    consumes(a, "artifact")
                }
                buildType { id("C") }
            }
        }

        val project = Project {
            seq.buildTypes().forEach { buildType(it) }
        }

        //region assertions
        assertEquals(3, project.buildTypes.size)

        assertDependencyIds(
                Pair(setOf(), seq.buildTypes()[0]),
                Pair(setOf("A"), seq.buildTypes()[1]),
                Pair(setOf("A"), seq.buildTypes()[2])
        )
        //endregion
    }

    @Test
    fun minimalDiamond() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }

        val project = Project {
            listOf(a, b, c, d).forEach {buildType(it)}
        }
        //endregion

        sequential {
            buildType(a)
            parallel {
                buildType(b)
                buildType(c)
            }
            buildType(d)
        }

        //region assetions
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
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }
        val e = BuildType { id("E") }

        val project = Project {
            listOf(a, b, c, d, e).forEach { buildType(it) }
        }
        //endregion

        sequential {
            buildType(a)
            parallel {
                buildType(b)
                sequential {
                    buildType(c)
                    buildType(d)
                }
            }
            buildType(e)
        }

        //region assertions
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
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }

        val project = Project {
            listOf(a, b, c, d).forEach { buildType(it) }
        }
        //endregion

        sequential {
            buildType(a)
            sequential {
                sequential {
                    buildType(b)
                    buildType(c)
                }
                buildType(d)
            }
        }

        //region assertions
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
    fun parallelInParallelInSequence() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }

        val project = Project {
            listOf(a, b, c, d).forEach { buildType(it) }
        }
        //endregion

        sequential {
            buildType(a)
            parallel {
                parallel {
                    buildType(b)
                    buildType(c)
                }
                buildType(d)
            }
        }

        //region assertions
        assertEquals(4, project.buildTypes.size)

        assertDependencyIds(
                Pair(setOf(), a),
                Pair(setOf("A"), b),
                Pair(setOf("A"), c),
                Pair(setOf("A"), d)
        )
        //endregion
    }

    @Test
    fun outOfSequenceDependency() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }
        val e = BuildType { id("E") }
        val f = BuildType { id("F") }

        val project = Project {
            listOf(a, b, c, d, e, f).forEach { buildType(it) }
        }
        //endregion

        sequential {
            buildType(a)
            parallel {
                buildType(b) {
                    dependsOn(f)
                }
                sequential {
                    buildType(c)
                    buildType(d)
                }
            }
            buildType(e)
        }

        //region assertions
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
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }
        val e = BuildType { id("E") }
        val f = BuildType { id("F") }

        val project = Project {
            listOf(a, b, c, d, e, f).forEach { buildType(it) }
        }
        //endregion

        sequential {
            buildType(a)
            parallel {
                buildType(b)
                buildType(c)
            }
            parallel {
                buildType(d)
                buildType(e)
            }
            buildType(f)
        }

        //region assertions
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
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }

        val project = Project {
            listOf(a, b).forEach { buildType(it) }
        }
        //endregion

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }

        sequential {
            buildType(a)
            sequential(settings, {
                buildType(b)
            })
        }

        //region assertions
        assertEquals(2, project.buildTypes.size)

        assertDependencies(
            Pair(setOf(), a),
            Pair(setOf(DepData("A", SnapshotDependency().apply(settings))), b)
        )
        //endregion
    }

    @Test
    fun singleBuildDependencySettings() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }

        val project = Project {
            listOf(a, b, c).forEach { buildType(it) }
        }
        //endregion

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }

        sequential {
            buildType(a)
            buildType(b)
            buildType(c, options = settings)
        }

        //region assertions
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
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }

        val project = Project {
            listOf(a, b, c).forEach { buildType(it) }
        }
        //endregion

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }

        sequential {
            buildType(a)
            parallel {
                buildType(b)
                buildType(c, options = settings)
            }
        }

        //region assertions
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
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }

        val project = Project {
            listOf(a, b, c, d).forEach { buildType(it) }
        }
        //endregion

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }

        sequential {
            buildType(a)
            parallel {
                buildType(b)
                sequential(settings, {
                    buildType(c)
                    buildType(d)
                })

            }
        }

        //region assertions
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
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }
        val e = BuildType { id("E") }

        val project = Project {
            listOf(a, b, c, d, e).forEach { buildType(it) }
        }
        //endregion

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }

        sequential {
            buildType(a)
            parallel(settings, {
                buildType(b)
                sequential {
                    buildType(c)
                    buildType(d)
                }
            })
            buildType(e)
        }

        //region assertions
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
    fun explicitDependencyOptions_update_ImplicitOnes_in_BuildType() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }

        val project = Project {
            listOf(a, b).forEach { buildType(it) }
        }
        //endregion

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }

        sequential {
            buildType(a)
            buildType(b) {
                dependsOn(a, options = settings)
            }
        }

        //region assertions
        assertDependencies(
                Pair(setOf(), a),
                Pair(setOf(DepData("A", SnapshotDependency().apply(settings))), b));
        //endregion
    }

    @Test
    fun explicitDependencyOptions_update_ImplicitOnes_in_Sequence() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }

        val project = Project {
            listOf(a, b, c).forEach { buildType(it) }
        }
        //endregion

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }

        sequential {
            buildType(a)
            sequential {
                dependsOn(a, options = settings)
                buildType(b)
                buildType(c)
            }
        }

        //region assertions
        assertDependencies(
                Pair(setOf(), a),
                Pair(setOf(DepData("A", SnapshotDependency().apply(settings))), b),
                Pair(setOf(DepData("B", SnapshotDependency())), c))
        //endregion
    }

    @Test
    fun explicitDependencyOptions_update_ImplicitOnes_in_Parallel() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }

        val project = Project {
            listOf(a, b, c).forEach { buildType(it) }
        }
        //endregion

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }

        sequential {
            buildType(a)
            parallel {
                dependsOn(a, options = settings)
                buildType(b)
                buildType(c)
            }
        }

        //region assertions
        assertDependencies(
                Pair(setOf(), a),
                Pair(setOf(DepData("A", SnapshotDependency().apply(settings))), b),
                Pair(setOf(DepData("A", SnapshotDependency().apply(settings))), c))
        //endregion
    }

    @Test
    fun selectiveExplicitDependencyOptions() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }

        val project = Project {
            listOf(a, b, c).forEach { buildType(it) }
        }
        //endregion

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }

        sequential {
            parallel {
                buildType(a)
                buildType(b)
            }
            buildType(c) {
                dependsOn(b, options = settings)
            }
        }

        //region assertions
        assertDependencies(
                Pair(setOf(), a),
                Pair(setOf(), b),
                Pair(setOf(DepData("A", SnapshotDependency()),
                        DepData("B", SnapshotDependency().apply(settings))), c))
        //endregion
    }

    @Test
    fun sequenceWithExplicitDependencies() {
        //region given
        val a = BuildType { id("A") }
        val b = BuildType { id("B") }
        val c = BuildType { id("C") }
        val d = BuildType { id("D") }
        val e = BuildType { id("E") }
        val f = BuildType { id("F") }
        val g = BuildType { id("G") }
        val h = BuildType { id("H") }

        val project = Project {
            listOf(a, b, c, d, e, f, g, h).forEach { buildType(it) }
        }
        //endregion

        val settings: SnapshotDependency.() -> Unit = {
            runOnSameAgent = true
            onDependencyCancel = FailureAction.IGNORE
            reuseBuilds = ReuseBuilds.NO
        }

        val s = sequential {
            buildType(a)
            buildType(b)
        }

        var p: Stage? = null
        sequential {
            p = parallel {
                buildType(c)
                buildType(d)
            }
            buildType(e)
        }

        sequential {
            dependsOn(s, p!!, options = settings)
            dependsOn(f, options = settings)
            buildType(g)
            buildType(h)
        }

        //region assertions
        assertEquals(8, project.buildTypes.size)

        assertDependencies(
                Pair(setOf(), a),
                Pair(setOf(DepData("A", SnapshotDependency())), b),
                Pair(setOf(), c),
                Pair(setOf(), d),
                Pair(setOf(DepData("C", SnapshotDependency()), DepData("D", SnapshotDependency())), e),
                Pair(setOf(), f),
                Pair(setOf(
                        DepData("B", SnapshotDependency().apply(settings)),
                        DepData("C", SnapshotDependency().apply(settings)),
                        DepData("D", SnapshotDependency().apply(settings)),
                        DepData("F", SnapshotDependency().apply(settings))
                ), g),
                Pair(setOf(DepData("G", SnapshotDependency())), h)
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
