package io.enmasse.systemtest.brokered.web;

import io.enmasse.systemtest.AddressType;
import io.enmasse.systemtest.Destination;
import org.openqa.selenium.WebDriver;

public class ChromeWebConsoleTest extends BrokeredWebConsoleTest {

    //@Test
    public void testCreateDeleteQueue() throws Exception {
        doTestCreateDeleteAddress(Destination.queue("test-queue" ,getDefaultPlan(AddressType.QUEUE)));
    }

    //@Test
    public void testCreateDeleteTopic() throws Exception {
        doTestCreateDeleteAddress(Destination.topic("test-topic", getDefaultPlan(AddressType.TOPIC)));
    }

    //@Test
    public void testFilterAddressesByType() throws Exception {
        doTestFilterAddressesByType();
    }

    //@Test
    public void testFilterAddressesByName() throws Exception {
        doTestFilterAddressesByName();
    }

    //@Test
    public void testSortAddressesByName() throws Exception {
        doTestSortAddressesByName();
    }

    //@Test
    public void testSortConnectionsBySenders() throws Exception {
        doTestSortConnectionsBySenders();
    }

    //@Test
    public void testSortConnectionsByReceivers() throws Exception {
        doTestSortConnectionsByReceivers();
    }

    //@Test
    public void testFilterConnectionsByEncrypted() throws Exception {
        doTestFilterConnectionsByEncrypted();
    }

    //@Test
    public void testFilterConnectionsByUser() throws Exception {
        doTestFilterConnectionsByUser();
    }

    //@Test
    public void testFilterConnectionsByHostname() throws Exception {
        doTestFilterConnectionsByHostname();
    }

    //@Test
    public void testSortConnectionsByHostname() throws Exception {
        doTestSortConnectionsByHostname();
    }

    //@Test
    public void testFilterConnectionsByContainerId() throws Exception {
        doTestFilterConnectionsByContainerId();
    }

    //@Test
    public void testSortConnectionsByContainerId() throws Exception {
        doTestSortConnectionsByContainerId();
    }

    //@Test
    public void testMessagesMetrics() throws Exception {
        doTestMessagesMetrics();
    }

    //@Test
    public void testClientsMetrics() throws Exception {
        doTestClientsMetrics();
    }

    //@Test
    public void testCannotCreateAddresses() throws Exception {
        doTestCannotCreateAddresses();
    }

    //@Test
    public void testCannotDeleteAddresses() throws Exception {
        doTestCannotDeleteAddresses();
    }

    //@Test
    public void testViewAddresses() throws Exception {
        doTestViewAddresses();
    }

    //@Test
    public void testViewConnections() throws Exception {
        doTestViewConnections();
    }

    //@Test
    public void testViewAddressesWildcards() throws Exception {
        doTestViewAddressesWildcards();
    }

    //@Test
    public void testCannotOpenConsolePage() throws Exception {
        doTestCanOpenConsolePage("pepa", "pepaPa555", false);
    }

    //@Test
    public void testCanOpenConsolePage() throws Exception {
        doTestCanOpenConsolePage(username, password, true);
    }

    @Override
    public WebDriver buildDriver() {
        return getChromeDriver();
    }
}
