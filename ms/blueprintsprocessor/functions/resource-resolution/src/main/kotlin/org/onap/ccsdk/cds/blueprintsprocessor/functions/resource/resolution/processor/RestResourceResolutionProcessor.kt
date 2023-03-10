/*
 *  Copyright © 2018 - 2020 IBM.
 *  Modifications Copyright © 2017-2020 AT&T, Bell Canada
 *  Modifications Copyright © 2022 Deutche Telekom AG
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

package org.onap.ccsdk.cds.blueprintsprocessor.functions.resource.resolution.processor

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.onap.ccsdk.cds.blueprintsprocessor.functions.resource.resolution.ResourceResolutionConstants.PREFIX_RESOURCE_RESOLUTION_PROCESSOR
import org.onap.ccsdk.cds.blueprintsprocessor.functions.resource.resolution.RestResourceSource
import org.onap.ccsdk.cds.blueprintsprocessor.functions.resource.resolution.utils.ResourceAssignmentUtils
import org.onap.ccsdk.cds.blueprintsprocessor.rest.service.BluePrintRestLibPropertyService
import org.onap.ccsdk.cds.blueprintsprocessor.rest.service.BlueprintWebClientService
import org.onap.ccsdk.cds.blueprintsprocessor.services.execution.ExecutionServiceDomains
import org.onap.ccsdk.cds.controllerblueprints.core.BluePrintConstants
import org.onap.ccsdk.cds.controllerblueprints.core.BluePrintProcessorException
import org.onap.ccsdk.cds.controllerblueprints.core.checkNotEmpty
import org.onap.ccsdk.cds.controllerblueprints.core.isNotEmpty
import org.onap.ccsdk.cds.controllerblueprints.core.nullToEmpty
import org.onap.ccsdk.cds.controllerblueprints.core.updateErrorMessage
import org.onap.ccsdk.cds.controllerblueprints.core.utils.JacksonUtils
import org.onap.ccsdk.cds.controllerblueprints.resource.dict.KeyIdentifier
import org.onap.ccsdk.cds.controllerblueprints.resource.dict.ResourceAssignment
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Service
import java.util.HashMap

/**
 * RestResourceResolutionProcessor
 *
 * @author Kapil Singal
 */
@Service("${PREFIX_RESOURCE_RESOLUTION_PROCESSOR}source-rest")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
open class RestResourceResolutionProcessor(private val blueprintRestLibPropertyService: BluePrintRestLibPropertyService) :
    ResourceAssignmentProcessor() {

    private val logger = LoggerFactory.getLogger(RestResourceResolutionProcessor::class.java)

    override fun getName(): String {
        return "${PREFIX_RESOURCE_RESOLUTION_PROCESSOR}source-rest"
    }

    override suspend fun processNB(resourceAssignment: ResourceAssignment) {
        try {
            validate(resourceAssignment)

            // Check if It has Input
            if (!setFromInput(resourceAssignment)) {
                val dName = resourceAssignment.dictionaryName!!
                val dSource = resourceAssignment.dictionarySource!!
                val resourceDefinition = resourceDefinition(dName)

                /** Check Resource Assignment has the source definitions, If not get from Resource Definitions **/
                val resourceSource = resourceAssignment.dictionarySourceDefinition
                    ?: resourceDefinition?.sources?.get(dSource)
                    ?: throw BluePrintProcessorException("couldn't get resource definition $dName source($dSource)")
                if ((resourceAssignment.property?.type).isNullOrEmpty())
                    throw BluePrintProcessorException("Couldn't get data dictionary type for dictionary name (${resourceAssignment.name})")
                val resourceSourceProperties =
                    checkNotNull(resourceSource.properties) { "failed to get source properties for $dName " }

                val sourceProperties =
                    JacksonUtils.getInstanceFromMap(resourceSourceProperties, RestResourceSource::class.java)

                val inputKeyMapping =
                    checkNotNull(sourceProperties.inputKeyMapping) { "failed to get input-key-mappings for $dName under $dSource properties" }
                val resolvedInputKeyMapping = resolveInputKeyMappingVariables(
                    inputKeyMapping,
                    resourceAssignment.templatingConstants
                ).toMutableMap()
                logger.info("\nResolved Input Key mappings: \n$resolvedInputKeyMapping")

                resolvedInputKeyMapping.map { KeyIdentifier(it.key, it.value) }.let {
                    resourceAssignment.keyIdentifiers.addAll(it)
                }

                // Resolving content Variables
                val path = resolveFromInputKeyMapping(nullToEmpty(sourceProperties.path), resolvedInputKeyMapping)
                val payload = resolveFromInputKeyMapping(nullToEmpty(sourceProperties.payload), resolvedInputKeyMapping)
                resourceSourceProperties["resolved-payload"] = JacksonUtils.jsonNode(payload)
                val urlPath =
                    resolveFromInputKeyMapping(checkNotNull(sourceProperties.urlPath), resolvedInputKeyMapping)
                val verb = resolveFromInputKeyMapping(nullToEmpty(sourceProperties.verb), resolvedInputKeyMapping)
                var outputKeyMapping = sourceProperties.outputKeyMapping
                if (!outputKeyMapping.isNullOrEmpty()) {
                    outputKeyMapping = outputKeyMapping.mapValues { (_, value) ->
                        resolveFromInputKeyMapping(value, resolvedInputKeyMapping)
                    }.toMutableMap()
                }
                logger.info(
                    "RestResource ($dSource) dictionary information: " +
                        "URL:($urlPath), path:($path), input-key-mapping:($inputKeyMapping), output-key-mapping:($outputKeyMapping)"
                )
                val requestHeaders = sourceProperties.headers.mapValues { (_, value) ->
                    resolveFromInputKeyMapping(value, resolvedInputKeyMapping)
                }
                logger.info("$dSource dictionary information : ($urlPath), path:($path), ($inputKeyMapping), ($outputKeyMapping)")
                // Get the Rest Client Service
                val restClientService = blueprintWebClientService(resourceAssignment, sourceProperties)

                val response = restClientService.exchangeResource(verb, urlPath, payload, requestHeaders.toMap())
                val responseStatusCode = response.status
                val responseBody = response.body
                if (responseStatusCode in 200..299 && outputKeyMapping == null) {
                    resourceAssignment.status = BluePrintConstants.STATUS_SUCCESS
                    logger.info("AS>> outputKeyMapping==null, will not populateResource")
                } else if (responseStatusCode in 200..299) {
                    populateResource(resourceAssignment, sourceProperties, responseBody, path, outputKeyMapping)
                } else {
                    val errMsg =
                        "Failed to get $dSource result for dictionary name ($dName) using urlPath ($urlPath) response_code: ($responseStatusCode)"
                    logger.warn(errMsg)
                    throw BluePrintProcessorException(errMsg)
                }
            }
            // Check the value has populated for mandatory case
            ResourceAssignmentUtils.assertTemplateKeyValueNotNull(resourceAssignment)
        } catch (e: BluePrintProcessorException) {
            val errorMsg = "Failed to process REST resource resolution in template key ($resourceAssignment) assignments."
            ResourceAssignmentUtils.setFailedResourceDataValue(resourceAssignment, errorMsg)
            throw e.updateErrorMessage(
                ExecutionServiceDomains.RESOURCE_RESOLUTION, errorMsg,
                "Wrong resource definition or resolution failed."
            )
        } catch (e: Exception) {
            ResourceAssignmentUtils.setFailedResourceDataValue(resourceAssignment, e.message)
            throw BluePrintProcessorException("Failed in template key ($resourceAssignment) assignments with: ${e.message}", e)
        }
    }

    open fun blueprintWebClientService(
        resourceAssignment: ResourceAssignment,
        restResourceSource: RestResourceSource
    ): BlueprintWebClientService {
        return if (isNotEmpty(restResourceSource.endpointSelector)) {
            val restPropertiesJson = raRuntimeService.resolveDSLExpression(restResourceSource.endpointSelector!!)
            blueprintRestLibPropertyService.blueprintWebClientService(restPropertiesJson)
        } else {
            blueprintRestLibPropertyService.blueprintWebClientService(resourceAssignment.dictionarySource!!)
        }
    }

    @Throws(BluePrintProcessorException::class)
    open fun populateResource(
        resourceAssignment: ResourceAssignment,
        sourceProperties: RestResourceSource,
        restResponse: String,
        path: String,
        outputKeyMapping: MutableMap<String, String>?
    ) {
        val dName = resourceAssignment.dictionaryName
        val dSource = resourceAssignment.dictionarySource
        val type = nullToEmpty(resourceAssignment.property?.type)
        val metadata = resourceAssignment.property!!.metadata

        logger.info("Response processing type ($type)")

        // we check null in processNB
        var outputKeyMapping = sourceProperties.outputKeyMapping!!

        var responseNode: JsonNode? = if (outputKeyMapping.isEmpty()) {
            var tmpResponseNode = if (type == BluePrintConstants.DATA_TYPE_JSON || type == BluePrintConstants.DATA_TYPE_MAP)
                JacksonUtils.jsonNode(restResponse).at(path)
            else
                JacksonUtils.convertPrimitiveResourceValue(type, restResponse).at(path)
            if (type == BluePrintConstants.DATA_TYPE_JSON || type == BluePrintConstants.DATA_TYPE_MAP) {
                if (tmpResponseNode.isObject) {
                    logger.info("Creating substitute outputKeyMapping for $type type")
                    outputKeyMapping = HashMap<String, String>()
                    tmpResponseNode.fieldNames().forEach {
                        outputKeyMapping[it] = it
                    }
                    tmpResponseNode
                } else {
                    val errMsg =
                        "Failed to get $dSource result for dictionary name ($dName): response is not a JSON object"
                    logger.warn(errMsg)
                    throw BluePrintProcessorException(errMsg)
                }
            } else {
                logger.info("Wrapping output for the dictionary name (${resourceAssignment.name})")
                val newNode = jacksonObjectMapper().createObjectNode()
                newNode.replace(dName, tmpResponseNode)
                outputKeyMapping[dName!!] = dName
                newNode
            }
        } else
            JacksonUtils.jsonNode(restResponse).at(path)
        responseNode = checkNotNull(responseNode) {
            "Failed to find path ($path) in response ($responseNode)"
        }

        val valueToPrint = ResourceAssignmentUtils.getValueToLog(metadata, responseNode)
        logger.info("populating value for output mapping ($outputKeyMapping), from json ($valueToPrint)")

        val parsedResponseNode = ResourceAssignmentUtils.parseResponseNode(
            responseNode, resourceAssignment,
            raRuntimeService, outputKeyMapping
        )

        // Set the List of Complex Values
        ResourceAssignmentUtils.setResourceDataValue(resourceAssignment, raRuntimeService, parsedResponseNode)
    }

    @Throws(BluePrintProcessorException::class)
    open fun validate(resourceAssignment: ResourceAssignment) {
        checkNotEmpty(resourceAssignment.name) { "resource assignment template key is not defined" }
        checkNotEmpty(resourceAssignment.dictionaryName) {
            "resource assignment dictionary name is not defined for template key (${resourceAssignment.name})"
        }
        checkNotEmpty(resourceAssignment.dictionarySource) {
            "resource assignment dictionary source is not defined for template key (${resourceAssignment.name})"
        }
    }

    override suspend fun recoverNB(runtimeException: RuntimeException, resourceAssignment: ResourceAssignment) {
        addError(runtimeException.message!!)
    }
}
