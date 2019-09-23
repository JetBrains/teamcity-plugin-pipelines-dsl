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
                sequence {
                    build(b)
                    dependencySettings(settings)
                }
            }
        }

        //region assertions for simpleDependencySettings
        assertEquals(2, project.buildTypes.size)

        assertDependencyIds(
            Pair(setOf(), a),
            Pair(setOf("A"), b)
        )

        assertDependencySettings(
            Triple(SnapshotDependency().apply(settings), b.dependencies.items[0].snapshot!!, "Failed for B0")
        )
        //endregion
    }

    @Test
    fun nestedSequenceSettings() {
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
                parallel {
                    build(b)
                    sequence {
                        build(c)
                        build(d)
                    }
                    dependencySettings(settings)
                }
                build(e)
            }
        }

        //region assertions for nestedSequenceSettings
        assertEquals(5, project.buildTypes.size)

        assertDependencyIds(
            Pair(setOf(), a),
            Pair(setOf("A"), b),
            Pair(setOf("A"), c),
            Pair(setOf("C"), d),
            Pair(setOf("D", "B"), e)
        )

        val expected = SnapshotDependency().apply(settings)
        assertDependencySettings(
            Triple(expected, b.dependencies.items[0].snapshot!!, "B0"),
            Triple(expected, c.dependencies.items[0].snapshot!!, "C0")
            //TODO: The following fails because the settings are only applied to the fan-ins of the parallel block
            //, Triple(expected, d.dependencies.items[0].snapshot!!, "D0")
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

            val s3 = sequence {
                dependsOn(s1, settings)
                dependsOn(s2)
                build(c)
            }
        }

        //region assertions for sequenceDependencies
        assertEquals(3, project.buildTypes.size)

        assertDependencyIds(
            Pair(setOf(), a),
            Pair(setOf(), b),
            Pair(setOf("A", "B"), c)
        )

        assertDependencySettings(
            Triple(SnapshotDependency(), c.dependencies.items[1].snapshot!!, "C1"),
            Triple(SnapshotDependency().apply(settings), c.dependencies.items[0].snapshot!!, "C0")
        )
        //endregion
    }

    private fun assertDependencyIds(vararg expectedAndActual: Pair<Set<String>, BuildType>) {
        expectedAndActual.forEach {
            assertEquals("Failed for ${it.second.id}", it.first, it.second.dependencies.items.map {it.buildTypeId.id!!.value}.toSet())
        }
    }

    private fun assertDependencySettings(vararg expectedAndActual: Triple<SnapshotDependency, SnapshotDependency, String>) {
        expectedAndActual.forEach {
            assertEquals("Failed for ${it.third}", it.first.runOnSameAgent, it.second.runOnSameAgent)
            assertEquals("Failed for ${it.third}", it.first.onDependencyCancel, it.second.onDependencyCancel)
            assertEquals("Failed for ${it.third}", it.first.onDependencyFailure, it.second.onDependencyFailure)
        }
    }
}
