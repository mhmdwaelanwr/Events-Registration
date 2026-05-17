package anwar.mlsa.eventsregistration

import android.content.Context
import com.hedera.hashgraph.sdk.AccountId
import com.hedera.hashgraph.sdk.Client
import com.hedera.hashgraph.sdk.PrivateKey
import com.hedera.hashgraph.sdk.TopicCreateTransaction
import com.hedera.hashgraph.sdk.TopicId
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction

object Hedera {
    
    fun submitRegistrationId(context: Context, registrationId: String) {
        var client: Client? = null
        try {
            val accountId = AccountId.fromString(requireConfig(context, "HEDERA_ACCOUNT_ID"))
            val accountPrivateKey = parsePrivateKey(requireConfig(context, "HEDERA_PRIVATE_KEY"))

            client = Client.forTestnet()
            client.setOperator(accountId, accountPrivateKey)

            submitMessage(context, client, registrationId)
        } finally {
            client?.close()
        }
    }

    fun createTopic(context: Context): TopicId {
        var client: Client? = null
        try {
            val accountId = AccountId.fromString(requireConfig(context, "HEDERA_ACCOUNT_ID"))
            val accountPrivateKey = parsePrivateKey(requireConfig(context, "HEDERA_PRIVATE_KEY"))

            client = Client.forTestnet()
            client.setOperator(accountId, accountPrivateKey)

            return createTopic(client)
        } finally {
            client?.close()
        }
    }

    private fun createTopic(client: Client): TopicId {
        val txCreateTopic = TopicCreateTransaction()
        val txCreateTopicResponse = txCreateTopic.execute(client)
        val receiptCreateTopicTx = txCreateTopicResponse.getReceipt(client)
        val topicId = requireNotNull(receiptCreateTopicTx.topicId) { "Topic ID is missing in receipt." }
        return topicId
    }

    private fun parsePrivateKey(rawKey: String): PrivateKey {
        val key = rawKey.trim().removePrefix("0x")
        return try {
            PrivateKey.fromString(key)
        } catch (_: Exception) {
            try {
                PrivateKey.fromStringED25519(key)
            } catch (_: Exception) {
                PrivateKey.fromStringECDSA(key)
            }
        }
    }

    private fun submitMessage(context: Context, client: Client, registrationId: String) {
        val topicId = TopicId.fromString(requireConfig(context, "HEDERA_TOPIC_ID"))
        val txTopicMessageSubmit = TopicMessageSubmitTransaction()
            .setTopicId(topicId)
            .setMessage(registrationId)

        txTopicMessageSubmit.execute(client).getReceipt(client)
    }

    private fun requireConfig(context: Context, name: String): String {
        return SecurityManager.getConfig(context, name).ifBlank {
            error("Missing config: $name")
        }
    }
}
