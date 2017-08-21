package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.core.utilities.OpaqueBytes
import net.corda.nodeapi.internal.serialization.amqp.*
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

class OpaqueBytesSerializer<T : OpaqueBytes>(classToUse: Class<T>) : CustomSerializer.Is<T>(classToUse) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = emptyList()

    override val schemaForDocumentation = Schema(
            listOf(RestrictedType(type.toString(), "",
                    listOf(type.toString()), SerializerFactory.primitiveTypeName(ByteArray::class.java)!!, descriptor, emptyList()
            )))

    override fun writeDescribedObject(obj: T, data: Data, type: Type, output: SerializationOutput) {
        output.writeObject(obj.bytes, data, clazz)
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): T {
        val binary = input.readObject(obj, schema, ByteArray::class.java) as ByteArray
        val byteArrayCtor = clazz.getConstructor(ByteArray::class.java)
        val newInstance = byteArrayCtor.newInstance(binary)
        return newInstance as T
    }
}