package net.corda.loadtest.tests

import net.corda.client.mock.Generator
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowException
import net.corda.core.internal.concurrent.thenMatch
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.USD
import net.corda.flows.CashExitFlow
import net.corda.flows.CashExitFlow.ExitRequest
import net.corda.flows.CashIssueAndPaymentFlow
import net.corda.flows.CashIssueAndPaymentFlow.IssueAndPaymentRequest
import net.corda.flows.CashPaymentFlow
import net.corda.flows.CashPaymentFlow.PaymentRequest
import net.corda.loadtest.LoadTest


object StabilityTest {
    private val log = loggerFor<StabilityTest>()
    fun crossCashTest(replication: Int) = LoadTest<CrossCashCommand, Unit>(
            "Creating Cash transactions",
            generate = { _, _ ->
                val payments = simpleNodes.flatMap { payer -> simpleNodes.map { payer to it } }
                        .filter { it.first != it.second }
                        .map { (payer, payee) -> CrossCashCommand(PaymentRequest(Amount(1, USD), payee.info.legalIdentity, anonymous = true), payer) }
                Generator.pure(List(replication) { payments }.flatten())
            },
            interpret = { _, _ -> },
            execute = { command ->
                val request = command.request
                val result = when (request) {
                    is IssueAndPaymentRequest -> command.node.proxy.startFlow(::CashIssueAndPaymentFlow, request).returnValue
                    is PaymentRequest -> command.node.proxy.startFlow(::CashPaymentFlow, request).returnValue
                    is ExitRequest -> command.node.proxy.startFlow(::CashExitFlow, request).returnValue
                    else -> throw IllegalArgumentException("Unexpected request type: $request")
                }
                result.thenMatch({
                    log.info("Success[$command]: $result")
                }, {
                    log.error("Failure[$command]", it)
                })
            },
            gatherRemoteState = {}
    )

    fun selfIssueTest(replication: Int) = LoadTest<SelfIssueCommand, Unit>(
            "Self issuing lot of cash",
            generate = { _, _ ->
                // Self issue cash is fast, its ok to flood the node with this command.
                val generateIssue =
                        simpleNodes.map { issuer ->
                            SelfIssueCommand(IssueAndPaymentRequest(Amount(100000, USD), OpaqueBytes.of(0), issuer.info.legalIdentity, notary.info.notaryIdentity, anonymous = true), issuer)
                        }
                Generator.pure(List(replication) { generateIssue }.flatten())
            },
            interpret = { _, _ -> },
            execute = { (request, node) ->
                try {
                    val result = node.proxy.startFlow(::CashIssueAndPaymentFlow, request).returnValue.getOrThrow()
                    log.info("Success: $result")
                } catch (e: FlowException) {
                    log.error("Failure", e)
                }
            },
            gatherRemoteState = {}
    )
}
