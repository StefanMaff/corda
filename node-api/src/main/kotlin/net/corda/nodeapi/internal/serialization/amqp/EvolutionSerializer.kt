package net.corda.nodeapi.internal.serialization.amqp

import net.corda.nodeapi.internal.serialization.carpenter.getTypeAsClass
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.io.NotSerializableException

class EvolutionSerializer(
        clazz: Type,
        factory: SerializerFactory,
        val oldParams : Map<String, oldParam>,
        val newArgs : List<String>) : ObjectSerializer (clazz, factory) {

    override val propertySerializers: Collection<PropertySerializer>

    data class oldParam (val type: String, val aType: Type, val idx: Int, val property: PropertySerializer)

    init {
        propertySerializers = listOf()
    }

    companion object {
        /**
         * Build a serialization object for deserialisation only of objects serislaised
         * as different versions of a class
         *
         * @param new is the Serializer built for the Class as it exists now, not
         * how it was serialised and persisted.
         */
        fun make (old: schemaAndDescriptor, new: ObjectSerializer,
                  factory: SerializerFactory) : AMQPSerializer<Any> {

            val oldArgs = mutableMapOf<String, oldParam>()
            // so this will be the values as they are in the blob, we need a set of property
            // serializers to read them as we would normally
            var idx = 0
            (old.schema.types.first() as CompositeType).fields.forEach {
                val returnType = it.getTypeAsClass(factory.classloader)
                oldArgs[it.name] = oldParam(
                        it.type, returnType, idx++, PropertySerializer.make(it.name, null, returnType, factory))
            }

            // the set of arguments the current class state requires for construction, we need
            // to map the data we get out of the file onto the new classes constructor
//            val listy = mutableListOf<String?>()

            return EvolutionSerializer(new.type, factory, oldArgs, new.propertySerializers.map { it.name })
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        // TODO: throw some exception
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): Any {
        if (obj !is List<*>) throw NotSerializableException("Body of described type is unexpected $obj")

        return construct(newArgs.flatMap {
            val param = oldParams[it]
            listOf(param?.property?.readProperty(obj[param.idx], schema, input))
        })
    }
}

