package net.corda.vega.plugin

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.node.CordaPluginRegistry
import net.corda.plugin.registerFinanceJSONMappers
import net.corda.vega.api.PortfolioApi
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

/**
 * [SimmService] is the object that makes available the flows and services for the Simm agreement / evaluation flow.
 * It is loaded via discovery - see [CordaPluginRegistry].
 * It is also the object that enables a human usable web service for demo purpose
 * It is loaded via discovery see [WebServerPluginRegistry].
 */
class SimmPlugin : WebServerPluginRegistry {
    override val webApis = listOf(Function(::PortfolioApi))
    override val staticServeDirs: Map<String, String> = mapOf("simmvaluationdemo" to javaClass.classLoader.getResource("simmvaluationweb").toExternalForm())
    override fun customizeJSONSerialization(om: ObjectMapper): Unit = registerFinanceJSONMappers(om)
}