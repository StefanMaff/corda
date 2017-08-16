package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.utilities.toHexString
import org.junit.Test
import java.io.File


class EvolvabilityTests {

    @Test
    fun test1() {
        data class C (val a: Int)

        val path = EvolvabilityTests::class.java.getResource("EvolvabilityTests.test1")
        println ("PATH = $path")
        /*
        val sf = SerializerFactory()
        println (sf)
        var sc = SerializationOutput(sf).serialize(C(1))
        println (sc)
        println (sc.bytes.size)
        val f = File(path.toURI())
        println (sc.bytes.toHexString())
        f.appendBytes(sc.bytes)
        */





//        var deserializedC = DeserializationInput().deserialize(SerializationOutput().serialize(C('c')))



    }
}