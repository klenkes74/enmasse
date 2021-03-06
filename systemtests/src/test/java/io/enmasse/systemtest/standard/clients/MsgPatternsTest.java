package io.enmasse.systemtest.standard.clients;

import io.enmasse.systemtest.AddressSpaceType;
import io.enmasse.systemtest.AddressType;
import io.enmasse.systemtest.bases.clients.MsgPatternsTestBase;

public abstract class MsgPatternsTest extends MsgPatternsTestBase {
    @Override
    protected String getDefaultPlan(AddressType addressType) {
        switch (addressType) {
            case QUEUE:
                return "sharded-queue";
            case TOPIC:
                return "sharded-topic";
            case ANYCAST:
                return "standard-anycast";
            case MULTICAST:
                return "standard-multicast";
        }
        return null;
    }

    @Override
    protected AddressSpaceType getAddressSpaceType() {
        return AddressSpaceType.STANDARD;
    }

}
