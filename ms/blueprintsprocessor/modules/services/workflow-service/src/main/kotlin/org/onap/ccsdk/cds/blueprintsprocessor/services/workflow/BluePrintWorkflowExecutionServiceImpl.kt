/*
 *  Copyright © 2019 IBM.
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

package org.onap.ccsdk.cds.blueprintsprocessor.services.workflow

import com.fasterxml.jackson.databind.JsonNode
import org.onap.ccsdk.cds.blueprintsprocessor.core.api.data.ExecutionServiceInput
import org.onap.ccsdk.cds.blueprintsprocessor.core.api.data.ExecutionServiceOutput
import org.onap.ccsdk.cds.controllerblueprints.core.BluePrintConstants
import org.onap.ccsdk.cds.controllerblueprints.core.BluePrintProcessorException
import org.onap.ccsdk.cds.controllerblueprints.core.asObjectNode
import org.onap.ccsdk.cds.controllerblueprints.core.interfaces.BluePrintWorkflowExecutionService
import org.onap.ccsdk.cds.controllerblueprints.core.service.BluePrintRuntimeService
import org.onap.ccsdk.cds.controllerblueprints.core.utils.JacksonUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service("bluePrintWorkflowExecutionService")
open class BluePrintWorkflowExecutionServiceImpl(
    private val componentWorkflowExecutionService: ComponentWorkflowExecutionService,
    private val dgWorkflowExecutionService: DGWorkflowExecutionService,
    private val imperativeWorkflowExecutionService: ImperativeWorkflowExecutionService
) : BluePrintWorkflowExecutionService<ExecutionServiceInput, ExecutionServiceOutput> {

    private val log = LoggerFactory.getLogger(BluePrintWorkflowExecutionServiceImpl::class.java)!!

    override suspend fun executeBluePrintWorkflow(
        bluePrintRuntimeService: BluePrintRuntimeService<*>,
        executionServiceInput: ExecutionServiceInput,
        properties: MutableMap<String, Any>
    ): ExecutionServiceOutput {

        val bluePrintContext = bluePrintRuntimeService.bluePrintContext()

        val workflowName = executionServiceInput.actionIdentifiers.actionName

        // Assign Workflow inputs
        // check if request structure exists
        if (!executionServiceInput.payload.has("$workflowName-request")) {
            throw BluePrintProcessorException("Input request missing the expected '$workflowName-request' block!")
        }
        val input = executionServiceInput.payload.get("$workflowName-request")
        bluePrintRuntimeService.assignWorkflowInputs(workflowName, input)

        val workflow = bluePrintContext.workflowByName(workflowName)

        val steps = workflow.steps ?: throw BluePrintProcessorException("could't get steps for workflow($workflowName)")

        /** If workflow has multiple steps, then it is imperative workflow */
        val executionServiceOutput: ExecutionServiceOutput = if (steps.size > 1) {
            imperativeWorkflowExecutionService
                .executeBluePrintWorkflow(bluePrintRuntimeService, executionServiceInput, properties)
        } else {
            // Get the DG Node Template
            val nodeTemplateName = bluePrintContext.workflowFirstStepNodeTemplate(workflowName)

            val derivedFrom = bluePrintContext.nodeTemplateNodeType(nodeTemplateName).derivedFrom

            log.info("Executing workflow($workflowName) NodeTemplate($nodeTemplateName), derived from($derivedFrom)")
            /** Return ExecutionServiceOutput based on DG node or Component Node */
            when {
                derivedFrom.startsWith(BluePrintConstants.MODEL_TYPE_NODE_COMPONENT, true) -> {
                    componentWorkflowExecutionService
                        .executeBluePrintWorkflow(bluePrintRuntimeService, executionServiceInput, properties)
                }
                derivedFrom.startsWith(BluePrintConstants.MODEL_TYPE_NODE_WORKFLOW, true) -> {
                    dgWorkflowExecutionService
                        .executeBluePrintWorkflow(bluePrintRuntimeService, executionServiceInput, properties)
                }
                else -> {
                    throw BluePrintProcessorException(
                        "couldn't execute workflow($workflowName) step mapped " +
                            "to node template($nodeTemplateName) derived from($derivedFrom)"
                    )
                }
            }
        }
        executionServiceOutput.commonHeader = executionServiceInput.commonHeader
        executionServiceOutput.actionIdentifiers = executionServiceInput.actionIdentifiers

        // Resolve Workflow Outputs
        var workflowOutputs: MutableMap<String, JsonNode>? = null
        try {
            workflowOutputs = bluePrintRuntimeService.resolveWorkflowOutputs(workflowName)
        } catch (e: RuntimeException) {
            log.error("Failed to resolve outputs for workflow: $workflowName", e)
            e.message?.let { bluePrintRuntimeService.getBluePrintError().addError(it, "workflow") }
        }

        // Set the Response Payload
        executionServiceOutput.payload = JacksonUtils.objectMapper.createObjectNode()
        executionServiceOutput.payload.set<JsonNode>(
            "$workflowName-response",
            workflowOutputs?.asObjectNode() ?: JacksonUtils.objectMapper.createObjectNode()
        )
        return executionServiceOutput
    }
}
