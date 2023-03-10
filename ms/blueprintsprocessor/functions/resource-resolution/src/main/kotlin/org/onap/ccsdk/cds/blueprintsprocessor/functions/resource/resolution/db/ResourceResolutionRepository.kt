/*
 * Copyright (C) 2019 Bell Canada.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onap.ccsdk.cds.blueprintsprocessor.functions.resource.resolution.db

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import javax.transaction.Transactional

@Repository
interface ResourceResolutionRepository : JpaRepository<ResourceResolution, String> {

    @Query(
        value = "SELECT * FROM RESOURCE_RESOLUTION WHERE resolution_key = :key AND blueprint_name = :blueprintName AND blueprint_version = :blueprintVersion AND artifact_name = :artifactName AND name = :name ORDER BY occurrence DESC, creation_date DESC LIMIT 1",
        nativeQuery = true
    )
    fun findByResolutionKeyAndBlueprintNameAndBlueprintVersionAndArtifactNameAndName(
        @Param("key")key: String,
        @Param("blueprintName")blueprintName: String,
        @Param("blueprintVersion")blueprintVersion: String,
        @Param("artifactName")artifactName: String,
        @Param("name")name: String
    ): ResourceResolution?

    @Query(
        value = """
        SELECT * FROM RESOURCE_RESOLUTION WHERE resolution_key = :key
           AND blueprint_name = :blueprintName AND blueprint_version = :blueprintVersion
           AND artifact_name = :artifactName AND occurrence <=  :firstN
    """,
        nativeQuery = true
    )
    fun findFirstNOccurrences(
        @Param("key")key: String,
        @Param("blueprintName")blueprintName: String,
        @Param("blueprintVersion")blueprintVersion: String,
        @Param("artifactName")artifactName: String,
        @Param("firstN")begin: Int
    ): List<ResourceResolution>

    @Query(
        value = """
        SELECT * FROM RESOURCE_RESOLUTION WHERE resolution_key = :key
            AND blueprint_name = :blueprintName AND blueprint_version = :blueprintVersion
            AND artifact_name = :artifactName
            AND occurrence > (
                select max(occurrence) - :lastN from RESOURCE_RESOLUTION
                WHERE resolution_key = :key AND blueprint_name = :blueprintName
                      AND blueprint_version = :blueprintVersion AND artifact_name = :artifactName
                )
            ORDER BY occurrence DESC, creation_date DESC
    """,
        nativeQuery = true
    )
    fun findLastNOccurrences(
        @Param("key")key: String,
        @Param("blueprintName")blueprintName: String,
        @Param("blueprintVersion")blueprintVersion: String,
        @Param("artifactName")artifactName: String,
        @Param("lastN")begin: Int
    ): List<ResourceResolution>

    @Query(
        value = """
        SELECT * FROM RESOURCE_RESOLUTION WHERE resource_id = :resourceId
            AND resource_type =:resourceType AND blueprint_name = :blueprintName
            AND blueprint_version = :blueprintVersion AND artifact_name = :artifactName
            AND occurrence > (
                select max(occurrence) - :lastN from RESOURCE_RESOLUTION
                WHERE resource_id = :resourceId
                    AND resource_type =:resourceType AND blueprint_name = :blueprintName
                    AND blueprint_version = :blueprintVersion AND artifact_name = :artifactName)
                    ORDER BY occurrence DESC, creation_date DESC
          """,
        nativeQuery = true
    )
    fun findLastNOccurrences(
        @Param("resourceId")resourceId: String,
        @Param("resourceType")resourceType: String,
        @Param("blueprintName")blueprintName: String,
        @Param("blueprintVersion")blueprintVersion: String,
        @Param("artifactName")artifactName: String,
        @Param("lastN")begin: Int
    ): List<ResourceResolution>

    @Query(
        value = """
        SELECT * FROM RESOURCE_RESOLUTION WHERE resolution_key = :key
            AND blueprint_name = :blueprintName AND blueprint_version = :blueprintVersion
            AND artifact_name = :artifactName AND occurrence BETWEEN :begin AND :end
            ORDER BY occurrence DESC, creation_date DESC
    """,
        nativeQuery = true
    )
    fun findOccurrencesWithinRange(
        @Param("key")key: String,
        @Param("blueprintName")blueprintName: String,
        @Param("blueprintVersion")blueprintVersion: String,
        @Param("artifactName")artifactName: String,
        @Param("begin")begin: Int,
        @Param("end")end: Int
    ): List<ResourceResolution>

    @Query(
        value = """
        SELECT max(occurrence) FROM RESOURCE_RESOLUTION WHERE resolution_key = :key
            AND blueprint_name = :blueprintName AND blueprint_version = :blueprintVersion
            AND artifact_name = :artifactName
    """,
        nativeQuery = true
    )
    fun findMaxOccurrenceByResolutionKeyAndBlueprintNameAndBlueprintVersionAndArtifactName(
        @Param("key")key: String,
        @Param("blueprintName")blueprintName: String,
        @Param("blueprintVersion")blueprintVersion: String,
        @Param("artifactName")artifactName: String
    ): Int?

    @Query(
        value = """
        SELECT max(occurrence) FROM RESOURCE_RESOLUTION WHERE blueprint_name = :blueprintName
            AND blueprint_version = :blueprintVersion AND resource_id = :resourceId
            AND resource_type = :resourceType
    """,
        nativeQuery = true
    )
    fun findMaxOccurrenceByBlueprintNameAndBlueprintVersionAndResourceIdAndResourceType(
        @Param("blueprintName")blueprintName: String,
        @Param("blueprintVersion")blueprintVersion: String,
        @Param("resourceId")resourceId: String,
        @Param("resourceType")resourceType: String
    ): Int?

    fun findByResolutionKeyAndBlueprintNameAndBlueprintVersionAndArtifactName(
        resolutionKey: String,
        blueprintName: String,
        blueprintVersion: String,
        artifactPrefix: String
    ): List<ResourceResolution>

    fun findByBlueprintNameAndBlueprintVersionAndResourceIdAndResourceType(
        blueprintName: String,
        blueprintVersion: String,
        resourceId: String,
        resourceType: String
    ): List<ResourceResolution>

    fun findByBlueprintNameAndBlueprintVersionAndArtifactNameAndResolutionKeyAndOccurrence(
        blueprintName: String?,
        blueprintVersion: String?,
        artifactName: String,
        resolutionKey: String,
        occurrence: Int
    ): List<ResourceResolution>

    fun findByBlueprintNameAndBlueprintVersionAndArtifactNameAndResourceIdAndResourceTypeAndOccurrence(
        blueprintName: String?,
        blueprintVersion: String?,
        artifactName: String,
        resourceId: String,
        resourceType: String,
        occurrence: Int
    ): List<ResourceResolution>

    @Transactional
    fun deleteByBlueprintNameAndBlueprintVersionAndArtifactNameAndResolutionKey(
        blueprintName: String,
        blueprintVersion: String,
        artifactName: String,
        resolutionKey: String
    ): Int

    @Transactional
    fun deleteByBlueprintNameAndBlueprintVersionAndArtifactNameAndResourceTypeAndResourceId(
        blueprintName: String,
        blueprintVersion: String,
        artifactName: String,
        resourceType: String,
        resourceId: String
    ): Int

    @Transactional
    @Modifying
    @Query(
        value = """
        DELETE FROM RESOURCE_RESOLUTION
        WHERE resolution_key = :resolutionKey AND blueprint_name = :blueprintName
            AND blueprint_version = :blueprintVersion AND artifact_name = :artifactName
            AND occurrence > (
                SELECT MAX(occurrence) - :lastN FROM (
                    SELECT occurrence from RESOURCE_RESOLUTION
                    WHERE resolution_key = :resolutionKey
                        AND blueprint_name = :blueprintName
                        AND blueprint_version = :blueprintVersion
                        AND artifact_name = :artifactName) AS o
                )
    """,
        nativeQuery = true
    )
    fun deleteLastNOccurrences(
        @Param("blueprintName") blueprintName: String,
        @Param("blueprintVersion") blueprintVersion: String,
        @Param("artifactName") artifactName: String,
        @Param("resolutionKey") resolutionKey: String,
        @Param("lastN") lastN: Int
    ): Int

    @Transactional
    @Modifying
    @Query(
        value = """
        DELETE FROM RESOURCE_RESOLUTION
        WHERE resource_type = :resourceType AND resource_id = :resourceId
            AND blueprint_name = :blueprintName
            AND blueprint_version = :blueprintVersion AND artifact_name = :artifactName
            AND occurrence > (
                SELECT MAX(occurrence) - :lastN FROM (
                    SELECT occurrence FROM RESOURCE_RESOLUTION
                    WHERE resource_type = :resourceType
                        AND resource_id = :resourceId
                        AND blueprint_name = :blueprintName
                        AND blueprint_version = :blueprintVersion
                        AND artifact_name = :artifactName) AS o
                )
    """,
        nativeQuery = true
    )
    fun deleteLastNOccurrences(
        @Param("blueprintName") blueprintName: String,
        @Param("blueprintVersion") blueprintVersion: String,
        @Param("artifactName") artifactName: String,
        @Param("resourceType") resourceType: String,
        @Param("resourceId") resourceId: String,
        @Param("lastN") lastN: Int
    ): Int
}
