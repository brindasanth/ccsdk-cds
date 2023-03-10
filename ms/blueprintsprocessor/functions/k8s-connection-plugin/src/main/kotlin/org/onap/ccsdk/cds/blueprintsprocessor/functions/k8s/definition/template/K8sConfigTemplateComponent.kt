/*
 * Copyright © 2017-2018 AT&T Intellectual Property.
 * Modifications Copyright © 2019 IBM.
 * Modifications Copyright © 2020 Orange.
 * Modifications Copyright © 2020 Deutsche Telekom AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onap.ccsdk.cds.blueprintsprocessor.functions.k8s.definition.template

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.commons.io.FileUtils
import org.onap.ccsdk.cds.blueprintsprocessor.core.BluePrintPropertiesService
import org.onap.ccsdk.cds.blueprintsprocessor.core.api.data.ExecutionServiceInput
import org.onap.ccsdk.cds.blueprintsprocessor.functions.k8s.K8sConnectionPluginConfiguration
import org.onap.ccsdk.cds.blueprintsprocessor.functions.k8s.definition.K8sPluginDefinitionApi
import org.onap.ccsdk.cds.blueprintsprocessor.functions.resource.resolution.ResourceResolutionConstants
import org.onap.ccsdk.cds.blueprintsprocessor.functions.resource.resolution.ResourceResolutionService
import org.onap.ccsdk.cds.blueprintsprocessor.services.execution.AbstractComponentFunction
import org.onap.ccsdk.cds.controllerblueprints.core.BluePrintConstants
import org.onap.ccsdk.cds.controllerblueprints.core.BluePrintProcessorException
import org.onap.ccsdk.cds.controllerblueprints.core.asJsonNode
import org.onap.ccsdk.cds.controllerblueprints.core.data.ArtifactDefinition
import org.onap.ccsdk.cds.controllerblueprints.core.returnNullIfMissing
import org.onap.ccsdk.cds.controllerblueprints.core.service.BluePrintVelocityTemplateService
import org.onap.ccsdk.cds.controllerblueprints.core.utils.ArchiveType
import org.onap.ccsdk.cds.controllerblueprints.core.utils.BluePrintArchiveUtils
import org.onap.ccsdk.cds.controllerblueprints.core.utils.JacksonUtils
import org.onap.ccsdk.cds.controllerblueprints.resource.dict.ResourceAssignment
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Component("component-k8s-config-template")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
open class K8sConfigTemplateComponent(
    private var bluePrintPropertiesService: BluePrintPropertiesService,
    private val resourceResolutionService: ResourceResolutionService
) :

    AbstractComponentFunction() {

    companion object {
        const val INPUT_K8S_DEFINITION_NAME = "k8s-rb-definition-name"
        const val INPUT_K8S_DEFINITION_VERSION = "k8s-rb-definition-version"
        const val INPUT_K8S_TEMPLATE_NAME = "k8s-rb-config-template-name"
        const val INPUT_K8S_TEMPLATE_SOURCE = "k8s-rb-config-template-source"
        const val INPUT_RESOURCE_ASSIGNMENT_MAP = "resource-assignment-map"
        const val INPUT_ARTIFACT_PREFIX_NAMES = "artifact-prefix-names"

        const val OUTPUT_STATUSES = "statuses"
        const val OUTPUT_SKIPPED = "skipped"
        const val OUTPUT_UPLOADED = "uploaded"
        const val OUTPUT_ERROR = "error"
    }

    private val log = LoggerFactory.getLogger(K8sConfigTemplateComponent::class.java)!!

    override suspend fun processNB(executionRequest: ExecutionServiceInput) {
        log.info("Triggering K8s Config Upload component logic.")

        val inputParameterNames = arrayOf(
            INPUT_K8S_TEMPLATE_NAME,
            INPUT_K8S_DEFINITION_NAME,
            INPUT_K8S_DEFINITION_VERSION,
            INPUT_K8S_TEMPLATE_SOURCE,
            INPUT_ARTIFACT_PREFIX_NAMES
        )
        val outputPrefixStatuses = mutableMapOf<String, String>()
        val inputParamsMap = mutableMapOf<String, JsonNode?>()

        inputParameterNames.forEach {
            inputParamsMap[it] = getOptionalOperationInput(it)?.returnNullIfMissing()
        }

        log.info("Getting the template prefixes")
        val prefixList: ArrayList<String> = getTemplatePrefixList(inputParamsMap[INPUT_ARTIFACT_PREFIX_NAMES])

        log.info("Iterating over prefixes in resource assignment map.")
        for (prefix in prefixList) {
            // Prefilling prefix sucess status
            outputPrefixStatuses[prefix] = OUTPUT_SKIPPED
            // Resource assignment map is organized by prefixes, in each iteraton we work only
            // on one section of resource assignment map
            val prefixNode: JsonNode = operationInputs[INPUT_RESOURCE_ASSIGNMENT_MAP]?.get(prefix) ?: continue
            val assignmentMapPrefix = JacksonUtils.jsonNode(prefixNode.toPrettyString()) as ObjectNode

            // We are copying the map because for each prefix it might be completed with a different data
            val prefixInputParamsMap = inputParamsMap.toMutableMap()
            prefixInputParamsMap.forEach { (inputParamName, value) ->
                if (value == null) {
                    val mapValue = assignmentMapPrefix.get(inputParamName)
                    log.debug("$inputParamName value was $value so we fetch $mapValue")
                    prefixInputParamsMap[inputParamName] = mapValue
                }
            }

            // For clarity we pull out the required fields
            val templateName: String? = prefixInputParamsMap[INPUT_K8S_TEMPLATE_NAME]?.returnNullIfMissing()?.asText()
            val definitionName: String? = prefixInputParamsMap[INPUT_K8S_DEFINITION_NAME]?.returnNullIfMissing()?.asText()
            val definitionVersion: String? = prefixInputParamsMap[INPUT_K8S_DEFINITION_VERSION]?.returnNullIfMissing()?.asText()

            // rename after commit
            val k8sConnectionPluginConfiguration = K8sConnectionPluginConfiguration(bluePrintPropertiesService)

            // Creating API connector
            val api = K8sPluginDefinitionApi(k8sConnectionPluginConfiguration)
            if ((templateName == null) || (definitionName == null) || (definitionVersion == null)) {
                log.warn("Prefix $prefix does not have required data for us to continue.")
            } else if (!api.hasDefinition(definitionName, definitionVersion)) {
                log.warn("K8s RB Definition ($definitionName/$definitionVersion) not found ")
            } else if (templateName == "") {
                log.warn("K8s rb template name is empty! Either define template name to use or choose default")
            } else if (api.hasTemplate(definitionName, definitionVersion, templateName)) {
                log.info("Template already existing - skipping upload")
            } else {
                log.info("Uploading K8s template..")
                outputPrefixStatuses[prefix] = OUTPUT_ERROR
                var templateSource: String? = prefixInputParamsMap[INPUT_K8S_TEMPLATE_SOURCE]?.returnNullIfMissing()?.asText()

                var templateFilePath: Path? = null
                if (templateSource != null && templateSource != "") {
                    val bluePrintContext = bluePrintRuntimeService.bluePrintContext()
                    val artifact: ArtifactDefinition = bluePrintContext.nodeTemplateArtifact(nodeTemplateName, templateSource)
                    if (artifact.type != BluePrintConstants.MODEL_TYPE_ARTIFACT_K8S_CONFIG)
                        throw BluePrintProcessorException(
                            "Unexpected template artifact type for template source $templateSource. Expecting: $artifact.type"
                        )
                    templateFilePath = prepareTemplateFile(templateName, templateSource, artifact.file)
                } else {
                    templateSource = ""
                    log.info("Configuration template without content. RB definition content will be used instead")
                }
                val template = K8sTemplate()
                template.templateName = templateName
                template.description = templateSource

                api.createTemplate(definitionName, definitionVersion, template)
                if (templateFilePath != null)
                    api.uploadConfigTemplateContent(definitionName, definitionVersion, template, templateFilePath)

                log.info("K8s Config Upload Completed")
                outputPrefixStatuses[prefix] = OUTPUT_UPLOADED
            }
        }
        bluePrintRuntimeService.setNodeTemplateAttributeValue(
            nodeTemplateName,
            OUTPUT_STATUSES,
            outputPrefixStatuses.asJsonNode()
        )
    }

    override suspend fun recoverNB(runtimeException: RuntimeException, executionRequest: ExecutionServiceInput) {
        addError(runtimeException.message!!)
    }

    private fun getTemplatePrefixList(node: JsonNode?): ArrayList<String> {
        val result = ArrayList<String>()
        when (node) {
            is ArrayNode -> {
                val arrayNode = node.toList()
                for (prefixNode in arrayNode)
                    result.add(prefixNode.asText())
            }
            is ObjectNode -> {
                result.add(node.asText())
            }
        }
        return result
    }

    private suspend fun prepareTemplateFile(k8sRbTemplateName: String, ks8ConfigSource: String, k8sConfigLocation: String): Path {
        val bluePrintContext = bluePrintRuntimeService.bluePrintContext()
        val bluePrintBasePath: String = bluePrintContext.rootPath
        val configeSourceFileFolderPath: Path = Paths.get(
            bluePrintBasePath.plus(File.separator).plus(k8sConfigLocation)
        )

        if (configeSourceFileFolderPath.toFile().exists() && !configeSourceFileFolderPath.toFile().isDirectory)
            return configeSourceFileFolderPath
        else if (configeSourceFileFolderPath.toFile().exists()) {
            log.info("Config building started from source $ks8ConfigSource")
            val properties: MutableMap<String, Any> = mutableMapOf()
            properties[ResourceResolutionConstants.RESOURCE_RESOLUTION_INPUT_STORE_RESULT] = false
            properties[ResourceResolutionConstants.RESOURCE_RESOLUTION_INPUT_RESOLUTION_KEY] = ""
            properties[ResourceResolutionConstants.RESOURCE_RESOLUTION_INPUT_RESOURCE_ID] = ""
            properties[ResourceResolutionConstants.RESOURCE_RESOLUTION_INPUT_RESOURCE_TYPE] = ""
            properties[ResourceResolutionConstants.RESOURCE_RESOLUTION_INPUT_OCCURRENCE] = 1
            properties[ResourceResolutionConstants.RESOURCE_RESOLUTION_INPUT_RESOLUTION_SUMMARY] = false
            val resolutionResult: Pair<String, MutableList<ResourceAssignment>> = resourceResolutionService.resolveResources(
                bluePrintRuntimeService,
                nodeTemplateName,
                ks8ConfigSource,
                properties
            )
            val tempMainPath: File = createTempDir("k8s-profile-", "")
            val tempPath: File = createTempDir("content-", "", tempMainPath)

            val resolvedJsonContent = resolutionResult.second
                .associateBy({ it.name }, { it.property?.value })
                .asJsonNode()

            try {
                templateLocation(configeSourceFileFolderPath.toFile(), resolvedJsonContent, tempPath)
                // Preparation of the final config content
                val finalTemplateFilePath = Paths.get(
                    tempMainPath.toString().plus(File.separator).plus(
                        "$k8sRbTemplateName.tar.gz"
                    )
                )
                if (!BluePrintArchiveUtils.compress(tempPath, finalTemplateFilePath.toFile(), ArchiveType.TarGz)) {
                    throw BluePrintProcessorException("Config template compression has failed")
                }
                FileUtils.deleteDirectory(tempPath)

                return finalTemplateFilePath
            } catch (t: Throwable) {
                FileUtils.deleteDirectory(tempMainPath)
                throw t
            }
        } else
            throw BluePrintProcessorException("Config source $k8sConfigLocation is missing in CBA folder")
    }

    private fun templateLocation(location: File, params: JsonNode, destinationFolder: File) {
        val directoryListing: Array<File>? = location.listFiles()
        if (directoryListing != null) {
            for (child in directoryListing) {
                var newDestinationFolder = destinationFolder.toPath()
                if (child.isDirectory)
                    newDestinationFolder = Paths.get(destinationFolder.toString().plus(File.separator).plus(child.name))

                templateLocation(child, params, newDestinationFolder.toFile())
            }
        } else if (!location.isDirectory) {
            if (location.extension.toLowerCase() == "vtl") {
                templateFile(location, params, destinationFolder)
            }
        }
    }

    private fun templateFile(templateFile: File, params: JsonNode, destinationFolder: File) {
        val finalFile = File(destinationFolder.path.plus(File.separator).plus(templateFile.nameWithoutExtension))
        val fileContent = Files.readString(templateFile.toPath())
        val finalFileContent = BluePrintVelocityTemplateService.generateContent(
            fileContent,
            params.toString(), true
        )
        if (!destinationFolder.exists())
            Files.createDirectories(destinationFolder.toPath())
        finalFile.bufferedWriter().use { out -> out.write(finalFileContent) }
    }
}
