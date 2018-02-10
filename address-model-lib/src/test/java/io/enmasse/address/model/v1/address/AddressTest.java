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
package io.enmasse.address.model.v1.address;

import io.enmasse.address.model.*;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class AddressTest {
    @Test
    public void testCreateFromBuilder() {
        Address.Builder b1 = new Address.Builder()
                .setAddress("addr1")
                .setAddressSpace("space1")
                .setName("myname")
                .setType("queue")
                .setPlan("myplan")
                .setStatus(new Status(true))
                .setUuid("myuuid");

        Address a1 = b1.build();

        Address.Builder b2 = new Address.Builder(a1);

        Address a2 = b2.build();

        assertThat(a1.getAddress(), is(a2.getAddress()));
        assertThat(a1.getAddressSpace(), is(a2.getAddressSpace()));
        assertThat(a1.getName(), is(a2.getName()));
        assertThat(a1.getPlan(), is(a2.getPlan()));
        assertThat(a1.getStatus(), is(a2.getStatus()));
        assertThat(a1.getType(), is(a2.getType()));
        assertThat(a1.getUuid(), is(a2.getUuid()));
    }
}
