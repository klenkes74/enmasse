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
package io.enmasse.address.model;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * An EnMasse Address addressspace.
 */
public class Address {
    private final String name;
    private final String uuid;
    private final String address;
    private final String addressSpace;
    private final String type;
    private final String plan;
    private final Status status;
    private final String version;

    private Address(String name, String uuid, String address, String addressSpace, String type, String plan, Status status, String version) {
        this.name = name;
        this.uuid = uuid;
        this.address = address;
        this.addressSpace = addressSpace;
        this.type = type;
        this.plan = plan;
        this.status = status;
        this.version = version;
    }

    public String getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    public String getUuid() {
        return uuid;
    }

    public String getAddressSpace() {
        return addressSpace;
    }

    public String getType() {
        return type;
    }

    public String getPlan() {
        return plan;
    }

    public Status getStatus() {
        return status;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{address=").append(address).append(",");
        sb.append("name=").append(name).append(",");
        sb.append("uuid=").append(uuid).append(",");
        sb.append("type=").append(type).append(",");
        sb.append("plan=").append(plan).append(",");
        sb.append("status=").append(status).append(",");
        sb.append("version=").append(version).append("}");
        return sb.toString();
    }

    public void validate() {
        Objects.requireNonNull(name, "name not set");
        Objects.requireNonNull(address, "address not set");
        Objects.requireNonNull(addressSpace, "addressSpace not set");
        Objects.requireNonNull(plan, "plan not set");
        Objects.requireNonNull(type, "type not set");
        Objects.requireNonNull(status, "status not set");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Address address1 = (Address) o;

        if (!name.equals(address1.name)) return false;
        if (!uuid.equals(address1.uuid)) return false;
        return address.equals(address1.address);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + uuid.hashCode();
        result = 31 * result + address.hashCode();
        return result;
    }

    public static class Builder {
        private String name;
        private String uuid;
        private String address;
        private String addressSpace;
        private String type;
        private String plan;
        private Status status = new Status(false);
        private String version;

        public Builder() {
        }

        public Builder(io.enmasse.address.model.Address address) {
            this.name = address.getName();
            this.uuid = address.getUuid();
            this.address = address.getAddress();
            this.addressSpace = address.getAddressSpace();
            this.type = address.getType();
            this.plan = address.getPlan();
            this.status = new Status(address.getStatus());
            this.version = address.getVersion();
        }

        public Builder setUuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            if (this.address == null ) {
                this.address = name;
            }
            return this;
        }

        public Builder setAddress(String address) {
            this.address = address;
            return this;
        }

        public Builder setAddressSpace(String addressSpace) {
            this.addressSpace = addressSpace;
            return this;
        }

        public Builder setType(String addressType) {
            this.type = addressType;
            return this;
        }

        public Builder setPlan(String plan) {
            this.plan = plan;
            return this;
        }

        public Builder setStatus(Status status) {
            this.status = status;
            return this;
        }

        public Builder setVersion(String version) {
            this.version = version;
            return this;
        }

        public Address build() {
            Objects.requireNonNull(name, "name not set");
            Objects.requireNonNull(address, "address not set");
            Objects.requireNonNull(type, "type not set");
            Objects.requireNonNull(status, "status not set");
            if (uuid == null) {
                uuid = UUID.nameUUIDFromBytes(address.getBytes(StandardCharsets.UTF_8)).toString();
            }
            return new Address(name, uuid, address, addressSpace, type, plan, status, version);
        }
    }
}
