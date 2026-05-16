package anwar.mlsa.eventsregistration

import com.hedera.hashgraph.sdk.AccountId
import com.hedera.hashgraph.sdk.Client
import com.hedera.hashgraph.sdk.PrivateKey
import com.hedera.hashgraph.sdk.TopicCreateTransaction
import com.hedera.hashgraph.sdk.TopicId
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction

object Hedera {
    @JvmStatic
    fun main(args: Array<String>) {
        var client: Client? = null
        try {
            val accountId = AccountId.fromString(requireConfig("HEDERA_ACCOUNT_ID"))
            val accountPrivateKey = parsePrivateKey(requireConfig("HEDERA_PRIVATE_KEY"))

            client = Client.forTestnet()
            client.setOperator(accountId, accountPrivateKey)

            val registrationId = args.firstOrNull()
                ?: System.getenv("HEDERA_REGISTRATION_ID")
                ?: error("Missing registration_id. Provide as arg or HEDERA_REGISTRATION_ID.")

            submitMessage(client, registrationId)
        } finally {
            client?.close()
        }
    }

    fun submitRegistrationId(registrationId: String) {
        var client: Client? = null
        try {
            val accountId = AccountId.fromString(requireConfig("HEDERA_ACCOUNT_ID"))
            val accountPrivateKey = parsePrivateKey(requireConfig("HEDERA_PRIVATE_KEY"))

            client = Client.forTestnet()
            client.setOperator(accountId, accountPrivateKey)

            submitMessage(client, registrationId)
        } finally {
            client?.close()
        }
    }

    fun createTopic(): TopicId {
        var client: Client? = null
        try {
            val accountId = AccountId.fromString(requireConfig("HEDERA_ACCOUNT_ID"))
            val accountPrivateKey = parsePrivateKey(requireConfig("HEDERA_PRIVATE_KEY"))

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
        // Try generic parser first (handles DER/PEM/ED25519/ECDSA formats)
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

    private fun submitMessage(client: Client, registrationId: String) {
        val topicId = TopicId.fromString(requireConfig("HEDERA_TOPIC_ID"))
        val txTopicMessageSubmit = TopicMessageSubmitTransaction()
            .setTopicId(topicId)
            .setMessage(registrationId)

        txTopicMessageSubmit.execute(client).getReceipt(client)
    }

    private fun requireConfig(name: String): String {
        val value = when (name) {
            "HEDERA_ACCOUNT_ID" -> BuildConfig.HEDERA_ACCOUNT_ID
            "HEDERA_PRIVATE_KEY" -> BuildConfig.HEDERA_PRIVATE_KEY
            "HEDERA_TOPIC_ID" -> BuildConfig.HEDERA_TOPIC_ID
            else -> ""
        }.ifBlank { System.getenv(name).orEmpty() }

        return value.ifBlank { error("Missing config: $name") }
    }
}
