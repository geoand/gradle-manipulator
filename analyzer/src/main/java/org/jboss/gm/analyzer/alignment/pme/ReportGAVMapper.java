/*
 * Copyright (C) 2012 Red Hat, Inc. (jcasey@redhat.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.gm.analyzer.alignment.pme;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.io.rest.exception.RestException;
import org.commonjava.maven.ext.io.rest.mapper.ErrorMessage;
import org.commonjava.maven.ext.io.rest.mapper.GAVSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mashape.unirest.http.ObjectMapper;

/**
 * @author vdedik@redhat.com
 */
public class ReportGAVMapper
        implements ObjectMapper {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private final String repositoryGroup;

    private final String versionSuffix;

    private String errorString;

    private Translator.RestProtocol protocol;

    public ReportGAVMapper(Translator.RestProtocol protocol, String repositoryGroup, String incrementalSerialSuffix) {
        this.protocol = protocol;
        this.repositoryGroup = repositoryGroup;
        this.versionSuffix = incrementalSerialSuffix;
    }

    @Override
    public Map<ProjectVersionRef, Translator.Value> readValue(String s) {
        Map<ProjectVersionRef, Translator.Value> result = new HashMap<>();

        // Workaround for https://github.com/Mashape/unirest-java/issues/122
        // Rather than throwing an exception we return an empty body which allows
        // DefaultTranslator to examine the status codes.

        if (s.length() == 0) {
            errorString = "No content to read.";
            return result;
        } else if (s.startsWith("<")) {
            // Read an HTML string.
            String stripped = s.replaceAll("<.*?>", "").replaceAll("\n", " ").trim();
            logger.debug("Read HTML string '{}' rather than a JSON stream; stripping message to '{}'", s, stripped);
            errorString = stripped;
            return result;
        }

        try {
            if (s.startsWith("{\"")) {
                errorString = objectMapper.readValue(s, ErrorMessage.class).toString();

                logger.debug("Read message string {}, processed to {} ", s, errorString);

                return result;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> responseBody = objectMapper.readValue(s, List.class);

            for (Map<String, Object> gav : responseBody) {
                String groupId = (String) gav.get("groupId");
                String artifactId = (String) gav.get("artifactId");
                String version = (String) gav.get("version");
                String bestMatchVersion = (String) gav.get("bestMatchVersion");
                List<String> availableVersions = (List<String>) gav.get("availableVersions");

                ProjectVersionRef project = new SimpleProjectVersionRef(groupId, artifactId, version);
                result.put(project, new Translator.Value(bestMatchVersion, availableVersions));
            }
        } catch (IOException e) {
            logger.error("Failed to decode map when reading string {}", s);
            throw new RestException("Failed to read list-of-maps response from version server: " + e.getMessage(), e);
        }

        return result;
    }

    @Override
    public String writeValue(Object value) {
        @SuppressWarnings("unchecked")
        List<ProjectVersionRef> projects = (List<ProjectVersionRef>) value;
        Object request;

        List<Map<String, Object>> requestBody = new ArrayList<>();

        for (ProjectVersionRef project : projects) {
            Map<String, Object> gav = new HashMap<>();
            gav.put("groupId", project.getGroupId());
            gav.put("artifactId", project.getArtifactId());
            gav.put("version", project.getVersionString());

            requestBody.add(gav);
        }

        if (protocol == Translator.RestProtocol.CURRENT) {
            request = new GAVSchema(new String[] {}, new String[] {}, repositoryGroup, versionSuffix, requestBody);
        } else {
            throw new RestException("Unknown protocol value " + protocol);
        }

        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RestException("Failed to serialize version request: " + e.getMessage(), e);
        }
    }

    public String getErrorString() {
        return errorString;
    }
}
