package net.corda.nodeapi.internal.serialization.amqp

import net.corda.nodeapi.internal.serialization.carpenter.getTypeAsClass
import java.lang.reflect.Type
import org.apache.qpid.proton.codec.Data

class EvolutionSerializer(
        clazz: Type,
        factory: SerializerFactory,
        override val propertySerializers: Collection<PropertySerializer>,
        argumentMap: ArrayList<Int?>) : ObjectSerializer (clazz, factory) {
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

            val fields = (old.schema.types.first() as CompositeType).fields
            val fieldSerializers : MutableList<PropertySerializer> = ArrayList(fields.size)

            data class oldParam (val type: String, val aType: Type, val pos: Int)
            val oldArgs = mutableMapOf<String, oldParam>()
            // so this will be the values as they are in the blob, we need a set of property
            // serializers to read them as we would normally
            var idx = 0
            (old.schema.types.first() as CompositeType).fields.forEach {
                val name = it.name
                val returnType = it.getTypeAsClass(factory.classloader)
                fieldSerializers += PropertySerializer.make(name, null, returnType, factory)
                oldArgs[it.name] = oldParam(it.type, returnType, idx++)
            }

            // the set of arguments the current class state requires for construction, we need
            // to map the data we get out of the file onto the new classes constructor
            idx = 0
            val listy = arrayListOf<Int?>()
            new.propertySerializers.forEach {
                listy += oldArgs[it.name]?.pos ?: -1
            }

            return EvolutionSerializer(new.type, factory, fieldSerializers, listy)
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        // TODO: throw some exception
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): Any {
        println ("WEARING THE PANTS OF FIRE")

        // these are the parameters as they were when we were serialised, this no longer matches
        // the classes constructor so we need to apply our mapping
        val params = (obj as List<*>).zip(propertySerializers).map { it.second.readProperty(it.first, schema, input) }

        println (params)

        /*
        if (obj is UnsignedInteger) {
            // TODO: Object refs
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        } else if (obj is List<*>) {
            if (obj.size > propertySerializers.size) throw NotSerializableException("Too many properties in described type $typeName")
            val params = obj.zip(propertySerializers).map { it.second.readProperty(it.first, schema, input) }
            return construct(params)
        } else throw NotSerializableException("Body of described type is unexpected $obj")
        */

        return true
    }
}

