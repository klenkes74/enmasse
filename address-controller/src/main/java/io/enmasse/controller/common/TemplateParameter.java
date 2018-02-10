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

package io.enmasse.controller.common;

/**
 * Template parameters that are dynamically set by the address controller.
 */
public interface TemplateParameter {
    String ADDRESS_SPACE = "ADDRESS_SPACE";
    String ADDRESS_SPACE_SERVICE_HOST = "ADDRESS_SPACE_SERVICE_HOST";

    String CONSOLE_SECRET = "CONSOLE_SECRET";
    String MESSAGING_SECRET = "MESSAGING_SECRET";
    String MQTT_SECRET = "MQTT_SECRET";

    String AUTHENTICATION_SERVICE_HOST = "AUTHENTICATION_SERVICE_HOST";
    String AUTHENTICATION_SERVICE_PORT = "AUTHENTICATION_SERVICE_PORT";
    String AUTHENTICATION_SERVICE_CA_CERT = "AUTHENTICATION_SERVICE_CA_CERT";
    String AUTHENTICATION_SERVICE_CLIENT_SECRET = "AUTHENTICATION_SERVICE_CLIENT_SECRET";
    String AUTHENTICATION_SERVICE_SASL_INIT_HOST = "AUTHENTICATION_SERVICE_SASL_INIT_HOST";
    String ADDRESS_CONTROLLER_CA_CERT = "ADDRESS_CONTROLLER_CA_CERT";
}
