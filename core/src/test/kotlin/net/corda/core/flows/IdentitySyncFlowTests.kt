package net.corda.core.flows

import net.corda.contracts.asset.Cash
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IdentitySyncFlowTests {
    lateinit var mockNet: MockNetwork

    @Before
    fun before() {
        // We run this in parallel threads to help catch any race conditions that may exist.
        mockNet = MockNetwork(networkSendManuallyPumped = false, threadPerNode = true)
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `sync confidential identities`() {
        // Set up values we'll need
        val notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
        val aliceNode = mockNet.createPartyNode(notaryNode.network.myAddress, ALICE.name)
        val bobNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOB.name)
        val alice: Party = aliceNode.services.myInfo.legalIdentity
        val bob: Party = bobNode.services.myInfo.legalIdentity
        aliceNode.services.identityService.verifyAndRegisterIdentity(bobNode.info.legalIdentityAndCert)
        aliceNode.services.identityService.verifyAndRegisterIdentity(notaryNode.info.legalIdentityAndCert)
        bobNode.services.identityService.verifyAndRegisterIdentity(aliceNode.info.legalIdentityAndCert)
        bobNode.services.identityService.verifyAndRegisterIdentity(notaryNode.info.legalIdentityAndCert)

        // Alice issues then pays some cash to a new confidential identity that Bob doesn't know about
        val ref = OpaqueBytes.of(0x01)
        val anonymous = true
        aliceNode.services.startFlow(CashIssueFlow(1000.DOLLARS, ref, notaryNode.services.myInfo.notaryIdentity)).resultFuture.getOrThrow().stx
        val paymentTx = aliceNode.services.startFlow(CashPaymentFlow(1000.DOLLARS, alice, anonymous)).resultFuture.getOrThrow().stx
        val confidentialIdentity = paymentTx.tx.outputs.map { it.data }.filterIsInstance<Cash.State>().single().owner
        assertNull(bobNode.services.identityService.partyFromAnonymous(confidentialIdentity))

        // Run the flow to sync up the identities
        aliceNode.services.startFlow(IdentitySyncFlow(bob, paymentTx.tx)).resultFuture.getOrThrow()
        val expected = aliceNode.services.identityService.partyFromAnonymous(confidentialIdentity)
        val actual = bobNode.services.identityService.partyFromAnonymous(confidentialIdentity)
        assertEquals(expected, actual)
    }
}
