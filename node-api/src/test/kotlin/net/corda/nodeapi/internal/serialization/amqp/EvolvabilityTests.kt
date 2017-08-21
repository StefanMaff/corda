package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.SerializedBytes
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class EvolvabilityTests {

    @Test
    fun simpleOrderSwapSameType() {
        val sf = testDefaultFactory()
        val path = EvolvabilityTests::class.java.getResource("EvolvabilityTests.simpleOrderSwapSameType")
        val f = File(path.toURI())

        val A = 1
        val B = 2

        // Original version of the class for the serialised version of this class
        //
        //data class C (val a: Int, val b: Int)
        //val sc = SerializationOutput(sf).serialize(C(A, B))
        //f.writeBytes(sc.bytes)

        // new version of the class, in this case the order of the parameters has been swapped
        data class C (val b: Int, val a: Int)

        val sc2 = f.readBytes()
        var deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals(A, deserializedC.a)
        assertEquals(B, deserializedC.b)
    }

    @Test
    fun simpleOrderSwapDifferentType() {
        val sf = testDefaultFactory()
        val path = EvolvabilityTests::class.java.getResource("EvolvabilityTests.simpleOrderSwapDifferentType")
        val f = File(path.toURI())

        // Original version of the class as it was serialised
        //
        //data class C (val a: Int, val b: String)
        //val sc = SerializationOutput(sf).serialize(C(1, "two"))
        //f.writeBytes(sc.bytes)

        // new version of the class, in this case the order of the parameters has been swapped
        data class C (val b: String, val a: Int)

        val sc2 = f.readBytes()
        var deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        println (deserializedC.a)
        println (deserializedC.b)
    }

    @Test
    fun addAdditionalParam() {
        val sf = testDefaultFactory()
        val path = EvolvabilityTests::class.java.getResource("EvolvabilityTests.addAdditionalParam")
        val f = File(path.toURI())

        // Original version of the class as it was serialised
        //data class C(val a: Int)
        //var sc = SerializationOutput(sf).serialize(C(1))
        //f.writeBytes(sc.bytes)
        //println ("Path = $path")

        // new version of the class, in this case a new parameter has been added (b)
        data class C (val a: Int, val b: Int?)

        val sc2 = f.readBytes()
        var deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        println (deserializedC.a)
    }
}