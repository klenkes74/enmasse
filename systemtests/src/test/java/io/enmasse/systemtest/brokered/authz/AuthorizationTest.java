package io.enmasse.systemtest.brokered.authz;

import io.enmasse.systemtest.AddressSpaceType;
import io.enmasse.systemtest.AddressType;
import io.enmasse.systemtest.bases.authz.AuthorizationTestBase;

public class AuthorizationTest extends AuthorizationTestBase {
    @Override
    protected AddressSpaceType getAddressSpaceType() {
        return AddressSpaceType.BROKERED;
    }

    @Override
    protected String getDefaultPlan(AddressType addressType) {
        switch (addressType) {
            case QUEUE:
                return "brokered-queue";
            case TOPIC:
                return "brokered-topic";
        }
        return null;
    }

    //@Test
    public void testSendAuthz() throws Exception {
        doTestSendAuthz();
    }

    //@Test
    public void testReceiveAuthz() throws Exception {
        doTestReceiveAuthz();
    }

    //@Test
    public void testUserPermissionAfterRemoveAuthz() throws Exception {
        doTestUserPermissionAfterRemoveAuthz();
    }

    //@Test
    public void testSendAuthzWithWIldcards() throws Exception {
        doTestSendAuthzWithWIldcards();
    }

    //@Test
    public void testReceiveAuthzWithWIldcards() throws Exception {
        doTestReceiveAuthzWithWIldcards();
    }
}
