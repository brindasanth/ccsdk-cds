/*
 *  Copyright © 2019 IBM.
 *  Modifications Copyright © 2018-2019 AT&T Intellectual Property.
 *  Modification Copyright (C) 2022 Nordix Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.onap.ccsdk.cds.blueprintsprocessor.message

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.security.scram.ScramLoginModule
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.StreamsConfig
import org.onap.ccsdk.cds.blueprintsprocessor.message.utils.BlueprintMessageUtils

/** Common Properties **/
abstract class CommonProperties {

    lateinit var type: String
    lateinit var topic: String
    lateinit var bootstrapServers: String

    open fun getConfig(): HashMap<String, Any> {
        val configProps = hashMapOf<String, Any>()
        configProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        return configProps
    }
}

/** Message Producer */
/** Message Producer Properties **/
abstract class MessageProducerProperties : CommonProperties()

/** Basic Auth */
open class KafkaBasicAuthMessageProducerProperties : MessageProducerProperties() {

    lateinit var clientId: String
    var acks: String = "all" // strongest producing guarantee
    var maxBlockMs: Int = 5000 // max blocking time in ms to send a message
    var reconnectBackOffMs: Int = 5000 // time in ms before retrying connection (5 seconds)
    var enableIdempotence: Boolean = true // ensure we don't push duplicates

    override fun getConfig(): HashMap<String, Any> {
        val configProps = super.getConfig()
        configProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = ByteArraySerializer::class.java
        configProps[ProducerConfig.ACKS_CONFIG] = acks
        configProps[ProducerConfig.MAX_BLOCK_MS_CONFIG] = maxBlockMs
        configProps[ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG] = reconnectBackOffMs
        configProps[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = enableIdempotence
        configProps[ProducerConfig.CLIENT_ID_CONFIG] = "$clientId-${BlueprintMessageUtils.getHostnameSuffix()}"
        return configProps
    }
}

/** SSL Auth */
open class KafkaSslAuthMessageProducerProperties : KafkaBasicAuthMessageProducerProperties() {

    lateinit var truststore: String
    lateinit var truststorePassword: String
    var truststoreType: String = SslConfigs.DEFAULT_SSL_TRUSTSTORE_TYPE
    var keystore: String? = null
    var keystorePassword: String? = null
    var keystoreType: String = SslConfigs.DEFAULT_SSL_KEYSTORE_TYPE
    var sslEndpointIdentificationAlgorithm: String = SslConfigs.DEFAULT_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM

    override fun getConfig(): HashMap<String, Any> {
        val configProps = super.getConfig()
        configProps[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = SecurityProtocol.SSL.toString()
        configProps[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = truststoreType
        configProps[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = truststore
        configProps[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = truststorePassword
        if (keystore != null) {
            configProps[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = keystore!!
            configProps[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = keystoreType
            configProps[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = keystorePassword!!
        }
        configProps[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = sslEndpointIdentificationAlgorithm

        return configProps
    }
}

/** (SASL) SCRAM SSL Auth */
class KafkaScramSslAuthMessageProducerProperties : KafkaSslAuthMessageProducerProperties() {

    var saslMechanism: String = "SCRAM-SHA-512"
    lateinit var scramUsername: String
    lateinit var scramPassword: String

    override fun getConfig(): HashMap<String, Any> {
        val configProps = super.getConfig()
        configProps[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = SecurityProtocol.SASL_SSL.toString()
        configProps[SaslConfigs.SASL_MECHANISM] = saslMechanism
        configProps[SaslConfigs.SASL_JAAS_CONFIG] = "${ScramLoginModule::class.java.canonicalName} required " +
            "username=\"${scramUsername}\" " +
            "password=\"${scramPassword}\";"
        return configProps
    }
}

/** (SASL) SCRAM Plaintext Auth */
class KafkaScramPlainTextAuthMessageProducerProperties : KafkaBasicAuthMessageProducerProperties() {

    var saslMechanism: String = "SCRAM-SHA-512"
    lateinit var scramUsername: String
    lateinit var scramPassword: String

    override fun getConfig(): HashMap<String, Any> {
        val configProps = super.getConfig()
        configProps[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = SecurityProtocol.SASL_PLAINTEXT.toString()
        configProps[SaslConfigs.SASL_MECHANISM] = saslMechanism
        configProps[SaslConfigs.SASL_JAAS_CONFIG] = "${ScramLoginModule::class.java.canonicalName} required " +
            "username=\"${scramUsername}\" " +
            "password=\"${scramPassword}\";"
        return configProps
    }
}

/** Consumer */
abstract class MessageConsumerProperties : CommonProperties()
/** Kafka Streams */
/** Streams properties */

/** Basic Auth */
open class KafkaStreamsBasicAuthConsumerProperties : MessageConsumerProperties() {

    lateinit var applicationId: String
    var autoOffsetReset: String = "latest"
    var processingGuarantee: String = StreamsConfig.EXACTLY_ONCE

    override fun getConfig(): HashMap<String, Any> {
        val configProperties = super.getConfig()
        // adjust the worker name with the hostname suffix because we'll have several workers, running in
        // different pods, using the same worker name otherwise.
        configProperties[StreamsConfig.APPLICATION_ID_CONFIG] = "$applicationId-${BlueprintMessageUtils.getHostnameSuffix()}"
        configProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = autoOffsetReset
        configProperties[StreamsConfig.PROCESSING_GUARANTEE_CONFIG] = processingGuarantee
        return configProperties
    }
}

/** SSL Auth */
open class KafkaStreamsSslAuthConsumerProperties : KafkaStreamsBasicAuthConsumerProperties() {

    lateinit var truststore: String
    lateinit var truststorePassword: String
    var truststoreType: String = SslConfigs.DEFAULT_SSL_TRUSTSTORE_TYPE
    var keystore: String? = null
    var keystorePassword: String? = null
    var keystoreType: String = SslConfigs.DEFAULT_SSL_KEYSTORE_TYPE
    var sslEndpointIdentificationAlgorithm: String = SslConfigs.DEFAULT_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM

    override fun getConfig(): HashMap<String, Any> {
        val configProps = super.getConfig()
        configProps[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = SecurityProtocol.SSL.toString()
        configProps[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = truststoreType
        configProps[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = truststore
        configProps[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = truststorePassword
        if (keystore != null) {
            configProps[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = keystore!!
            configProps[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = keystoreType
            configProps[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = keystorePassword!!
        }
        configProps[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = sslEndpointIdentificationAlgorithm
        return configProps
    }
}

/** (SASL) SCRAM SSL Auth */
class KafkaStreamsScramSslAuthConsumerProperties : KafkaStreamsSslAuthConsumerProperties() {

    var saslMechanism: String = "SCRAM-SHA-512"
    lateinit var scramUsername: String
    lateinit var scramPassword: String

    override fun getConfig(): HashMap<String, Any> {
        val configProps = super.getConfig()
        configProps[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = SecurityProtocol.SASL_SSL.toString()
        configProps[SaslConfigs.SASL_MECHANISM] = saslMechanism
        configProps[SaslConfigs.SASL_JAAS_CONFIG] = "${ScramLoginModule::class.java.canonicalName} required " +
            "username=\"${scramUsername}\" " +
            "password=\"${scramPassword}\";"
        return configProps
    }
}

/** Message Consumer */
/** Message Consumer Properties **/
/** Basic Auth */
open class KafkaBasicAuthMessageConsumerProperties : MessageConsumerProperties() {

    lateinit var groupId: String
    lateinit var clientId: String
    var autoCommit: Boolean = true
    var autoOffsetReset: String = "latest"
    var pollMillSec: Long = 1000
    var pollRecords: Int = -1

    override fun getConfig(): HashMap<String, Any> {
        val configProperties = super.getConfig()
        configProperties[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        configProperties[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = autoCommit
        /**
         * earliest: automatically reset the offset to the earliest offset
         * latest: automatically reset the offset to the latest offset
         */
        configProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = autoOffsetReset
        configProperties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configProperties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = ByteArrayDeserializer::class.java
        // adjust the worker name with the hostname suffix because we'll have several workers, running in
        // different pods, using the same worker name otherwise.
        configProperties[ConsumerConfig.CLIENT_ID_CONFIG] = "$clientId-${BlueprintMessageUtils.getHostnameSuffix()}"

        /** To handle Back pressure, Get only configured record for processing */
        if (pollRecords > 0) {
            configProperties[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = pollRecords
        }

        return configProperties
    }
}

/** SSL Auth */
open class KafkaSslAuthMessageConsumerProperties : KafkaBasicAuthMessageConsumerProperties() {

    lateinit var truststore: String
    lateinit var truststorePassword: String
    var truststoreType: String = SslConfigs.DEFAULT_SSL_TRUSTSTORE_TYPE
    var keystore: String? = null
    var keystorePassword: String? = null
    var keystoreType: String = SslConfigs.DEFAULT_SSL_KEYSTORE_TYPE
    var sslEndpointIdentificationAlgorithm: String = SslConfigs.DEFAULT_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM

    override fun getConfig(): HashMap<String, Any> {
        val configProps = super.getConfig()
        configProps[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = SecurityProtocol.SSL.toString()
        configProps[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = truststoreType
        configProps[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = truststore
        configProps[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = truststorePassword
        if (keystore != null) {
            configProps[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = keystore!!
            configProps[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = keystoreType
            configProps[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = keystorePassword!!
        }
        configProps[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = sslEndpointIdentificationAlgorithm
        return configProps
    }
}

/** (SASL) SCRAM SSL Auth */
class KafkaScramSslAuthMessageConsumerProperties : KafkaSslAuthMessageConsumerProperties() {

    var saslMechanism: String = "SCRAM-SHA-512"
    lateinit var scramUsername: String
    lateinit var scramPassword: String

    override fun getConfig(): HashMap<String, Any> {
        val configProps = super.getConfig()
        configProps[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = SecurityProtocol.SASL_SSL.toString()
        configProps[SaslConfigs.SASL_MECHANISM] = saslMechanism
        configProps[SaslConfigs.SASL_JAAS_CONFIG] = "${ScramLoginModule::class.java.canonicalName} required " +
            "username=\"${scramUsername}\" " +
            "password=\"${scramPassword}\";"
        return configProps
    }
}

/** (SASL) SCRAM Plaintext Auth */
class KafkaScramPlaintextAuthMessageConsumerProperties : KafkaBasicAuthMessageConsumerProperties() {

    var saslMechanism: String = "SCRAM-SHA-512"
    lateinit var scramUsername: String
    lateinit var scramPassword: String

    override fun getConfig(): HashMap<String, Any> {
        val configProps = super.getConfig()
        configProps[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = SecurityProtocol.SASL_PLAINTEXT.toString()
        configProps[SaslConfigs.SASL_MECHANISM] = saslMechanism
        configProps[SaslConfigs.SASL_JAAS_CONFIG] = "${ScramLoginModule::class.java.canonicalName} required " +
            "username=\"${scramUsername}\" " +
            "password=\"${scramPassword}\";"
        return configProps
    }
}
