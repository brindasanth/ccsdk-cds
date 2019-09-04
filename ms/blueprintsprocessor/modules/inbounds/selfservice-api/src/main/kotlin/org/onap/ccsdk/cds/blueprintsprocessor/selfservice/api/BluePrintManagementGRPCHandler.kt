/*
 * Copyright © 2017-2018 AT&T Intellectual Property.
 * Modifications Copyright © 2019 Bell Canada.
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

package org.onap.ccsdk.cds.blueprintsprocessor.selfservice.api

import io.grpc.StatusException
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.runBlocking
import org.onap.ccsdk.cds.blueprintsprocessor.selfservice.api.utils.currentTimestamp
import org.onap.ccsdk.cds.controllerblueprints.common.api.CommonHeader
import org.onap.ccsdk.cds.controllerblueprints.common.api.Status
import org.onap.ccsdk.cds.controllerblueprints.core.*
import org.onap.ccsdk.cds.controllerblueprints.core.config.BluePrintLoadConfiguration
import org.onap.ccsdk.cds.controllerblueprints.core.interfaces.BluePrintCatalogService
import org.onap.ccsdk.cds.controllerblueprints.core.scripts.BluePrintCompileCache
import org.onap.ccsdk.cds.controllerblueprints.core.utils.BluePrintFileUtils
import org.onap.ccsdk.cds.controllerblueprints.management.api.*
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import java.io.File
import java.util.*

// TODO("move to management-api or designer-api module")
@Service
open class BluePrintManagementGRPCHandler(private val bluePrintLoadConfiguration: BluePrintLoadConfiguration,
                                          private val blueprintsProcessorCatalogService: BluePrintCatalogService)
    : BluePrintManagementServiceGrpc.BluePrintManagementServiceImplBase() {

    private val log = LoggerFactory.getLogger(BluePrintManagementGRPCHandler::class.java)

    @PreAuthorize("hasRole('USER')")
    override fun uploadBlueprint(request: BluePrintUploadInput, responseObserver:
    StreamObserver<BluePrintManagementOutput>) {
        runBlocking {

            log.info("request(${request.commonHeader.requestId})")
            val uploadId = UUID.randomUUID().toString()
            val blueprintArchive = normalizedPathName(bluePrintLoadConfiguration.blueprintArchivePath, uploadId)
            val blueprintWorking = normalizedPathName(bluePrintLoadConfiguration.blueprintWorkingPath, uploadId)
            try {
                val cbaFile = normalizedFile(blueprintArchive, "cba.zip")

                saveToDisk(request, cbaFile)

                val uploadAction = request.actionIdentifiers?.actionName.emptyTONull()
                        ?: UploadAction.DRAFT.toString()

                when (uploadAction) {
                    UploadAction.DRAFT.toString() -> {
                        val blueprintId = blueprintsProcessorCatalogService.saveToDatabase(uploadId, cbaFile, false)
                        responseObserver.onNext(successStatus("Successfully uploaded CBA($blueprintId)...",
                                request.commonHeader))
                    }
                    UploadAction.PUBLISH.toString() -> {
                        val blueprintId = blueprintsProcessorCatalogService.saveToDatabase(uploadId, cbaFile, true)
                        responseObserver.onNext(successStatus("Successfully uploaded CBA($blueprintId)...",
                                request.commonHeader))
                    }
                    UploadAction.VALIDATE.toString() -> {
                        //TODO("Not Implemented")
                        responseObserver.onError(failStatus("Not Implemented",
                                BluePrintProcessorException("Not Implemented")))
                    }
                    UploadAction.ENRICH.toString() -> {
                        //TODO("Not Implemented")
                        responseObserver.onError(failStatus("Not Implemented",
                                BluePrintProcessorException("Not Implemented")))
                    }
                }
                responseObserver.onCompleted()
            } catch (e: Exception) {
                responseObserver.onError(failStatus("request(${request.commonHeader.requestId}): Failed to upload CBA", e))
            } finally {
                // Clean blueprint script cache
                val cacheKey = BluePrintFileUtils
                        .compileCacheKey(normalizedPathName(bluePrintLoadConfiguration.blueprintWorkingPath, uploadId))
                BluePrintCompileCache.cleanClassLoader(cacheKey)
                deleteNBDir(blueprintArchive)
                deleteNBDir(blueprintWorking)
            }
        }
    }

    @PreAuthorize("hasRole('USER')")
    override fun removeBlueprint(request: BluePrintRemoveInput, responseObserver:
    StreamObserver<BluePrintManagementOutput>) {

        runBlocking {
            val blueprintName = request.blueprintName
            val blueprintVersion = request.blueprintVersion
            val blueprint = "blueprint $blueprintName:$blueprintVersion"

            log.info("request(${request.commonHeader.requestId}): Received delete $blueprint")


            try {
                blueprintsProcessorCatalogService.deleteFromDatabase(blueprintName, blueprintVersion)
                responseObserver.onNext(successStatus("Successfully deleted $blueprint", request.commonHeader))
                responseObserver.onCompleted()
            } catch (e: Exception) {
                responseObserver.onError(failStatus("request(${request.commonHeader.requestId}): Failed to delete $blueprint", e))
            }
        }
    }

    private fun saveToDisk(request: BluePrintUploadInput, cbaFile: File) {
        log.info("request(${request.commonHeader.requestId}): Writing CBA File under :${cbaFile.absolutePath}")

        // Recreate Folder
        cbaFile.parentFile.reCreateDirs()

        // Write the File
        cbaFile.writeBytes(request.fileChunk.chunk.toByteArray()).apply {
            log.info("request(${request.commonHeader.requestId}): CBA file(${cbaFile.absolutePath} written successfully")
        }

    }

    private fun successStatus(message: String, header: CommonHeader): BluePrintManagementOutput =
            BluePrintManagementOutput.newBuilder()
                    .setCommonHeader(header)
                    .setStatus(Status.newBuilder()
                            .setTimestamp(currentTimestamp())
                            .setMessage(message)
                            .setCode(200)
                            .build())
                    .build()

    private fun failStatus(message: String, e: Exception): StatusException {
        log.error(message, e)
        return io.grpc.Status.INTERNAL
                .withDescription(message)
                .withCause(e)
                .asException()
    }
}
