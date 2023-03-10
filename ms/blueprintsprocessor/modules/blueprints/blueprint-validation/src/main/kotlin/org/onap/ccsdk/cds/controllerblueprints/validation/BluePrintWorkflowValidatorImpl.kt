/*
 * Copyright © 2017-2018 AT&T Intellectual Property.
 * Modifications Copyright © 2019 IBM.
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

package org.onap.ccsdk.cds.controllerblueprints.validation

import org.onap.ccsdk.cds.controllerblueprints.core.BluePrintConstants
import org.onap.ccsdk.cds.controllerblueprints.core.data.Workflow
import org.onap.ccsdk.cds.controllerblueprints.core.interfaces.BluePrintTypeValidatorService
import org.onap.ccsdk.cds.controllerblueprints.core.interfaces.BluePrintWorkflowValidator
import org.onap.ccsdk.cds.controllerblueprints.core.service.BluePrintRuntimeService
import org.onap.ccsdk.cds.controllerblueprints.validation.utils.PropertyAssignmentValidationUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Service

@Service("default-workflow-validator")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
open class BluePrintWorkflowValidatorImpl(private val bluePrintTypeValidatorService: BluePrintTypeValidatorService) : BluePrintWorkflowValidator {

    private val log = LoggerFactory.getLogger(BluePrintServiceTemplateValidatorImpl::class.toString())
    lateinit var bluePrintRuntimeService: BluePrintRuntimeService<*>

    var paths: MutableList<String> = arrayListOf()

    override fun validate(bluePrintRuntimeService: BluePrintRuntimeService<*>, workflowName: String, workflow: Workflow) {
        log.info("Validating Workflow($workflowName)")

        this.bluePrintRuntimeService = bluePrintRuntimeService

        paths.add(workflowName)
        paths.joinToString(BluePrintConstants.PATH_DIVIDER)

        // Validate Workflow Inputs
        validateInputs(workflow)

        // Validate Workflow outputs
        validateOutputs(workflow)

        // Step Validation Start
        paths.add("steps")
        workflow.steps?.forEach { stepName, step ->
            paths.add(stepName)
            paths.joinToString(BluePrintConstants.PATH_DIVIDER)

            // Validate target
            step.target?.let {
                try {
                    val nodeTemplate = bluePrintRuntimeService.bluePrintContext().nodeTemplateByName(it)

                    val nodeTypeDerivedFrom = bluePrintRuntimeService.bluePrintContext().nodeTemplateNodeType(it).derivedFrom

                    check(
                        nodeTypeDerivedFrom == BluePrintConstants.MODEL_TYPE_NODE_WORKFLOW ||
                            nodeTypeDerivedFrom == BluePrintConstants.MODEL_TYPE_NODE_COMPONENT
                    ) {
                        "NodeType(${nodeTemplate.type}) derived from is '$nodeTypeDerivedFrom', Expected " +
                            "'${BluePrintConstants.MODEL_TYPE_NODE_WORKFLOW}' or '${BluePrintConstants.MODEL_TYPE_NODE_COMPONENT}'"
                    }
                } catch (e: Exception) {
                    bluePrintRuntimeService.getBluePrintError()
                        .addError(
                            "Failed to validate Workflow($workflowName)'s step($stepName)'s " +
                                "definition",
                            paths.joinToString(BluePrintConstants.PATH_DIVIDER), e.message!!,
                            "BlueprintWorkflowValidatorImpl"
                        )
                }
            }
        }
        paths.removeAt(paths.lastIndex)
        // Step Validation Ends
        paths.removeAt(paths.lastIndex)

        paths.removeAt(paths.lastIndex)
    }

    private fun validateInputs(workflow: Workflow) {
        workflow.inputs?.let {
            bluePrintTypeValidatorService.validatePropertyDefinitions(bluePrintRuntimeService, workflow.inputs!!)
        }
    }

    private fun validateOutputs(workflow: Workflow) {
        workflow.outputs?.let {

            bluePrintTypeValidatorService.validatePropertyDefinitions(bluePrintRuntimeService, workflow.outputs!!)

            PropertyAssignmentValidationUtils(bluePrintRuntimeService.bluePrintContext())
                .validatePropertyDefinitionNAssignments(workflow.outputs!!)
        }
        // Validate Value or Expression
        workflow.outputs?.forEach { propertyName, propertyDefinition ->
        }
    }
}
