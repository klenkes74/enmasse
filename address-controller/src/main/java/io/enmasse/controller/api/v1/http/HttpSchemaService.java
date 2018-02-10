/*
 * Copyright 2017 Red Hat Inc.
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
package io.enmasse.controller.api.v1.http;

import io.enmasse.address.model.v1.SchemaProvider;
import io.enmasse.k8s.api.SchemaApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * API for serving address model schema.
 */
@Path("/apis/enmasse.io/v1/schema")
public class HttpSchemaService {
    private static final Logger log = LoggerFactory.getLogger(HttpSchemaService.class.getName());

    private final SchemaProvider schemaProvider;

    public HttpSchemaService(SchemaProvider schemaProvider) {
        this.schemaProvider = schemaProvider;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Consumes({MediaType.APPLICATION_JSON})
    public Response getSchema() {
        try {
            return Response.ok(schemaProvider.getSchema()).build();
        } catch (Exception e) {
            log.warn("Exception handling GET schema", e);
            return Response.serverError().build();
        }
    }
}
