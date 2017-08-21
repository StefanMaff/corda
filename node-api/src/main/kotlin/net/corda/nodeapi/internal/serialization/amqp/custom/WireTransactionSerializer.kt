package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.core.contracts.StateRef
import net.corda.core.transactions.WireTransaction
import net.corda.nodeapi.internal.serialization.amqp.*
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

class WireTransactionSerializer : CustomSerializer.Is<WireTransaction>(WireTransaction::class.java) {

    override val additionalSerializers: Iterable<CustomSerializer<out Any>>
        get() = emptySet()

    override val schemaForDocumentation =
            Schema(listOf(CompositeType(name = type.toString(), label = "", provides = listOf(type.toString()), descriptor = descriptor, fields = emptyList())))

    override fun writeDescribedObject(obj: WireTransaction, data: Data, type: Type, output: SerializationOutput) {
        output.writeObject(obj.inputs, data)
        output.writeObject(obj.attachments, data)
        output.writeObject(obj.outputs, data)
        output.writeObject(obj.commands, data)
        output.writeObjectOrNull(obj.notary, data, SerializerFactory.AnyType)
        output.writeObjectOrNull(obj.timeWindow, data, SerializerFactory.AnyType)
        output.writeObject(obj.privacySalt, data)
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): WireTransaction {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
