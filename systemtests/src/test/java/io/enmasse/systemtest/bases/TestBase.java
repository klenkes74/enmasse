/*
 * Copyright 2016 Red Hat Inc.
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

package io.enmasse.systemtest.bases;

import com.google.common.collect.Ordering;
import io.enmasse.systemtest.*;
import io.enmasse.systemtest.amqp.AmqpClient;
import io.enmasse.systemtest.amqp.AmqpClientFactory;
import io.enmasse.systemtest.executor.client.AbstractClient;
import io.enmasse.systemtest.executor.client.Argument;
import io.enmasse.systemtest.executor.client.ArgumentMap;
import io.enmasse.systemtest.executor.client.rhea.RheaClientConnector;
import io.enmasse.systemtest.executor.client.rhea.RheaClientReceiver;
import io.enmasse.systemtest.executor.client.rhea.RheaClientSender;
import io.enmasse.systemtest.mqtt.MqttClient;
import io.enmasse.systemtest.mqtt.MqttClientFactory;
import io.enmasse.systemtest.selenium.ConsoleWebPage;
import io.enmasse.systemtest.selenium.ISeleniumProvider;
import io.enmasse.systemtest.selenium.SeleniumProvider;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.apache.qpid.proton.message.Message;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.After;
import org.junit.Before;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

/**
 * Base class for all tests
 */
public abstract class TestBase extends SystemTestRunListener {
    private static Logger log = CustomLogger.getLogger();

    protected static final Environment environment = new Environment();

    protected static final Kubernetes kubernetes = Kubernetes.create(environment);
    private static final GlobalLogCollector logCollector = new GlobalLogCollector(kubernetes,
            new File(environment.testLogDir()));
    protected static final AddressApiClient addressApiClient = new AddressApiClient(kubernetes);

    protected String username;
    protected String password;
    protected AmqpClientFactory amqpClientFactory;
    protected MqttClientFactory mqttClientFactory;
    protected List<AddressSpace> addressSpaceList = new ArrayList<>();
    protected KeycloakCredentials managementCredentials = new KeycloakCredentials(null, null);
    protected BrokerManagement brokerManagement = new ArtemisManagement();
    private KeycloakClient keycloakApiClient;

    protected static void deleteAddressSpace(AddressSpace addressSpace) throws Exception {
        if (TestUtils.existAddressSpace(addressApiClient, addressSpace.getName())) {
            logCollector.collectEvents(addressSpace.getNamespace());
            logCollector.collectLogsTerminatedPods(addressSpace.getNamespace());
            addressApiClient.deleteAddressSpace(addressSpace);
            TestUtils.waitForAddressSpaceDeleted(kubernetes, addressSpace);
            logCollector.stopCollecting(addressSpace.getNamespace());
        } else {
            log.info("Address space '" + addressSpace + "' doesn't exists!");
        }
    }

    protected AddressSpace getSharedAddressSpace() {
        return null;
    }

    protected abstract String getDefaultPlan(AddressType addressType);


    @Before
    public void setup() {
        addressSpaceList = new ArrayList<>();
        amqpClientFactory = new AmqpClientFactory(kubernetes, environment, null, username, password);
        mqttClientFactory = new MqttClientFactory(kubernetes, environment, null, username, password);
    }

    @After
    public void teardown() throws Exception {
        try {
            mqttClientFactory.close();
            amqpClientFactory.close();

            for (AddressSpace addressSpace : addressSpaceList) {
                deleteAddressSpace(addressSpace);
            }

            addressSpaceList.clear();
        } catch (Exception e) {
            log.error("Error tearing down test: {}", e.getMessage());
            throw e;
        }
    }

    protected void createAddressSpace(AddressSpace addressSpace, String authService) throws Exception {
        if (!TestUtils.existAddressSpace(addressApiClient, addressSpace.getName())) {
            log.info("Address space '" + addressSpace + "' doesn't exist and will be created.");
            addressApiClient.createAddressSpace(addressSpace, authService);
            logCollector.startCollecting(addressSpace.getNamespace());
            TestUtils.waitForAddressSpaceReady(addressApiClient, addressSpace.getName());

            if (!addressSpace.equals(getSharedAddressSpace())) {
                addressSpaceList.add(addressSpace);
            }

            if (addressSpace.getType().equals(AddressSpaceType.STANDARD)) {
                log.info("Waiting for 2 minutes before starting tests");
                Thread.sleep(120_000);
            }
        } else {
            log.info("Address space '" + addressSpace + "' already exists.");
        }
    }

    //!TODO: protected void appendAddressSpace(...)

    protected JsonObject getAddressSpace(String name) throws Exception {
        return addressApiClient.getAddressSpace(name);
    }

    protected KeycloakClient getKeycloakClient() throws Exception {
        if (keycloakApiClient == null) {
            KeycloakCredentials creds = environment.keycloakCredentials();
            if (creds == null) {
                creds = kubernetes.getKeycloakCredentials();
            }
            keycloakApiClient = new KeycloakClient(kubernetes.getKeycloakEndpoint(), creds, kubernetes.getKeycloakCA());
        }
        return keycloakApiClient;
    }

    protected void deleteAddresses(AddressSpace addressSpace, Destination... destinations) throws Exception {
        TestUtils.delete(addressApiClient, addressSpace, destinations);
    }

    protected void appendAddresses(AddressSpace addressSpace, Destination... destinations) throws Exception {
        TimeoutBudget budget = new TimeoutBudget(5, TimeUnit.MINUTES);
        TestUtils.deploy(addressApiClient, kubernetes, budget, addressSpace, HttpMethod.POST, destinations);
    }


    protected void setAddresses(AddressSpace addressSpace, Destination... destinations) throws Exception {
        TimeoutBudget budget = new TimeoutBudget(5, TimeUnit.MINUTES);
        TestUtils.deploy(addressApiClient, kubernetes, budget, addressSpace, HttpMethod.PUT, destinations);
    }

    /**
     * give you a list of all deployed addresses (or single deployed address)
     *
     * @param addressName name of single address
     * @return list of addresses
     * @throws Exception
     */

    protected Future<List<String>> getAddresses(AddressSpace addressSpace, Optional<String> addressName) throws Exception {
        return TestUtils.getAddresses(addressApiClient, addressSpace, addressName);
    }


    /**
     * scale up/down destination (StatefulSet) to count of replicas, includes waiting for expected replicas
     */
    protected void scale(AddressSpace addressSpace, Destination destination, int numReplicas) throws Exception {
        TimeoutBudget budget = new TimeoutBudget(5, TimeUnit.MINUTES);
        TestUtils.setReplicas(kubernetes, addressSpace, destination, numReplicas, budget);
    }

    protected void scaleKeycloak(int numReplicas) throws Exception {
        scaleInGlobal("keycloak", numReplicas);
    }

    /**
     * scale up/down deployment to count of replicas, includes waiting for expected replicas
     *
     * @param deployment  name of deployment
     * @param numReplicas count of replicas
     * @throws InterruptedException
     */
    private void scaleInGlobal(String deployment, int numReplicas) throws InterruptedException {
        if (numReplicas >= 0) {
            TimeoutBudget budget = new TimeoutBudget(5, TimeUnit.MINUTES);
            TestUtils.setReplicas(kubernetes, environment.namespace(), deployment, numReplicas, budget);
        } else {
            throw new IllegalArgumentException("'numReplicas' must be greater than 0");
        }

    }

    protected void createGroup(AddressSpace addressSpace, String groupName) throws Exception {
        getKeycloakClient().createGroup(addressSpace.getName(), groupName);
    }

    protected void joinGroup(AddressSpace addressSpace, String groupName, String username) throws Exception {
        getKeycloakClient().joinGroup(addressSpace.getName(), groupName, username);
    }

    protected void leaveGroup(AddressSpace addressSpace, String groupName, String username) throws Exception {
        getKeycloakClient().leaveGroup(addressSpace.getName(), groupName, username);
    }

    protected void createUser(AddressSpace addressSpace, String username, String password) throws Exception {
        getKeycloakClient().createUser(addressSpace.getName(), username, password);
    }

    protected void removeUser(AddressSpace addressSpace, String username) throws Exception {
        getKeycloakClient().deleteUser(addressSpace.getName(), username);
    }

    protected void createUsers(AddressSpace addressSpace, String prefixName, String prefixPswd, int from, int to)
            throws Exception {
        for (int i = from; i < to; i++) {
            createUser(addressSpace, prefixName + i, prefixPswd + i);
        }
    }

    protected void removeUsers(AddressSpace addressSpace, String prefixName, int from, int to) throws Exception {
        for (int i = from; i < to; i++) {
            removeUser(addressSpace, prefixName + i);
        }
    }

    protected boolean isBrokered(AddressSpace addressSpace) {
        return addressSpace.getType().equals(AddressSpaceType.BROKERED);
    }

    protected void assertCanConnect(AddressSpace addressSpace, String username, String password, List<Destination> destinations) throws Exception {
        assertTrue("Client failed, cannot connect under user " + username,
                canConnectWithAmqp(addressSpace, username, password, destinations));
        // TODO: Enable this when mqtt is stable enough
        // assertTrue(canConnectWithMqtt(addressSpace, username, password));
    }

    protected void assertCannotConnect(AddressSpace addressSpace, String username, String password, List<Destination> destinations) throws Exception {
        try {
            assertFalse("Client failed, can connect under user " + username,
                    canConnectWithAmqp(addressSpace, username, password, destinations));
            fail("Expected connection to timeout");
        } catch (ConnectTimeoutException e) {
        }

        // TODO: Enable this when mqtt is stable enough
        // assertFalse(canConnectWithMqtt(addressSpace, username, password));
    }


    private boolean canConnectWithAmqp(AddressSpace addressSpace, String username, String password, List<Destination> destinations) throws Exception {
        for (Destination destination : destinations) {
            String message = String.format("Client failed, cannot connect to %s under user %s", destination.getType(), username);
            switch (destination.getType()) {
                case "queue":
                    assertTrue(message, canConnectWithAmqpToQueue(addressSpace, username, password, destination.getAddress()));
                    break;
                case "topic":
                    assertTrue(message, canConnectWithAmqpToTopic(addressSpace, username, password, destination.getAddress()));
                    break;
                case "multicast":
                    if (!isBrokered(addressSpace))
                        assertTrue(message, canConnectWithAmqpToMulticast(addressSpace, username, password, destination.getAddress()));
                    break;
                case "anycast":
                    if (!isBrokered(addressSpace))
                        assertTrue(message, canConnectWithAmqpToAnycast(addressSpace, username, password, destination.getAddress()));
                    break;
            }
        }
        return true;
    }

    private boolean canConnectWithMqtt(String name, String username, String password) throws Exception {
        AddressSpace addressSpace = new AddressSpace(name);
        MqttClient client = mqttClientFactory.createClient(addressSpace);
        MqttConnectOptions options = client.getMqttConnectOptions();
        options.setUserName(username);
        options.setPassword(password.toCharArray());

        Future<List<String>> received = client.recvMessages("t1", 1);
        Future<Integer> sent = client.sendMessages("t1", Arrays.asList("msgt1"));

        return (sent.get(1, TimeUnit.MINUTES) == received.get(1, TimeUnit.MINUTES).size());
    }

    protected boolean canConnectWithAmqpToQueue(AddressSpace addressSpace, String username, String password, String queueAddress) throws InterruptedException, IOException, TimeoutException, ExecutionException {
        AmqpClient client = amqpClientFactory.createQueueClient(addressSpace);
        client.getConnectOptions().setUsername(username).setPassword(password);

        Future<Integer> sent = client.sendMessages(queueAddress, Arrays.asList("msg1"), 10, TimeUnit.SECONDS);
        Future<List<Message>> received = client.recvMessages(queueAddress, 1, 10, TimeUnit.SECONDS);

        return (sent.get(10, TimeUnit.SECONDS) == received.get(10, TimeUnit.SECONDS).size());
    }

    protected boolean canConnectWithAmqpToAnycast(AddressSpace addressSpace, String username, String password, String anycastAddress) throws InterruptedException, IOException, TimeoutException, ExecutionException {
        AmqpClient client = amqpClientFactory.createQueueClient(addressSpace);
        client.getConnectOptions().setUsername(username).setPassword(password);

        Future<List<Message>> received = client.recvMessages(anycastAddress, 1, 10, TimeUnit.SECONDS);
        Future<Integer> sent = client.sendMessages(anycastAddress, Arrays.asList("msg1"), 10, TimeUnit.SECONDS);

        return (sent.get(10, TimeUnit.SECONDS) == received.get(10, TimeUnit.SECONDS).size());
    }

    protected boolean canConnectWithAmqpToMulticast(AddressSpace addressSpace, String username, String password, String multicastAddress) throws InterruptedException, IOException, TimeoutException, ExecutionException {
        AmqpClient client = amqpClientFactory.createBroadcastClient(addressSpace);
        client.getConnectOptions().setUsername(username).setPassword(password);

        Future<List<Message>> received = client.recvMessages(multicastAddress, 1, 10, TimeUnit.SECONDS);
        Future<Integer> sent = client.sendMessages(multicastAddress, Arrays.asList("msg1"), 10, TimeUnit.SECONDS);

        return (sent.get(10, TimeUnit.SECONDS) == received.get(10, TimeUnit.SECONDS).size());
    }

    protected boolean canConnectWithAmqpToTopic(AddressSpace addressSpace, String username, String password, String topicAddress) throws InterruptedException, IOException, TimeoutException, ExecutionException {
        AmqpClient client = amqpClientFactory.createTopicClient(addressSpace);
        client.getConnectOptions().setUsername(username).setPassword(password);

        Future<List<Message>> received = client.recvMessages(topicAddress, 1, 10, TimeUnit.SECONDS);
        Future<Integer> sent = client.sendMessages(topicAddress, Arrays.asList("msg1"), 10, TimeUnit.SECONDS);

        return (sent.get(10, TimeUnit.SECONDS) == received.get(10, TimeUnit.SECONDS).size());
    }

    protected Endpoint getRouteEndpoint(AddressSpace addressSpace) {
        Endpoint messagingEndpoint = kubernetes.getExternalEndpoint(addressSpace.getNamespace(), "messaging");

        if (TestUtils.resolvable(messagingEndpoint)) {
            return messagingEndpoint;
        } else {
            return kubernetes.getEndpoint(addressSpace.getNamespace(), "messaging", "amqps");
        }
    }

    protected String getConsoleRoute(AddressSpace addressSpace, String username, String password) {
        String consoleRoute = String.format("https://%s:%s@%s", username, password,
                kubernetes.getExternalEndpoint(addressSpace.getNamespace(), "console"));
        log.info(consoleRoute);
        return consoleRoute;
    }

    protected String getConsoleRoute(AddressSpace addressSpace) {
        return getConsoleRoute(addressSpace, username, password);
    }

    protected FirefoxDriver getFirefoxDriver() {
        FirefoxOptions opts = new FirefoxOptions();
        opts.setHeadless(true);
        return new FirefoxDriver(opts);
    }

    protected ChromeDriver getChromeDriver() {
        ChromeOptions opts = new ChromeOptions();
        opts.setHeadless(true);
        opts.addArguments("--no-sandbox");
        return new ChromeDriver(opts);
    }

    /**
     * selenium provider with Firefox webdriver
     */
    private SeleniumProvider getFirefoxSeleniumProvider() throws Exception {
        SeleniumProvider seleniumProvider = new SeleniumProvider();
        seleniumProvider.setupDriver(environment, kubernetes, getFirefoxDriver());
        return seleniumProvider;
    }

    protected void waitForSubscribersConsole(AddressSpace addressSpace, Destination destination, int expectedCount) throws Exception {
        int budget = 60; //seconds
        waitForSubscribersConsole(addressSpace, destination, expectedCount, budget);
    }

    /**
     * wait for expected count of subscribers on topic (check via console)
     *
     * @param budget timeout budget in seconds
     */
    protected void waitForSubscribersConsole(AddressSpace addressSpace, Destination destination, int expectedCount, int budget) {
        SeleniumProvider selenium = null;
        try {
            selenium = getFirefoxSeleniumProvider();
            ConsoleWebPage console = new ConsoleWebPage(selenium, getConsoleRoute(addressSpace), addressApiClient, addressSpace);
            console.openWebConsolePage();
            console.openAddressesPageWebConsole();
            selenium.waitUntilPropertyPresent(budget, expectedCount, () -> console.getAddressItem(destination).getReceiversCount());
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (selenium != null) {
                selenium.tearDownDrivers();
            }
        }
    }

    /**
     * wait for expected count of subscribers on topic
     *
     * @param addressSpace
     * @param topic         name of topic
     * @param expectedCount count of expected subscribers
     * @throws Exception
     */
    protected void waitForSubscribers(AddressSpace addressSpace, String topic, int expectedCount) throws Exception {
        TimeoutBudget budget = new TimeoutBudget(1, TimeUnit.MINUTES);
        waitForSubscribers(addressSpace, topic, expectedCount, budget);
    }

    protected void waitForSubscribers(AddressSpace addressSpace, String topic, int expectedCount, TimeoutBudget budget) throws Exception {
        AmqpClient queueClient = null;
        try {
            queueClient = amqpClientFactory.createQueueClient(addressSpace);
            queueClient.setConnectOptions(queueClient.getConnectOptions()
                    .setUsername(managementCredentials.getUsername())
                    .setPassword(managementCredentials.getPassword()));
            String replyQueueName = "reply-queue";
            Destination replyQueue = Destination.queue(replyQueueName, getDefaultPlan(AddressType.QUEUE));
            appendAddresses(addressSpace, replyQueue);

            boolean done = false;
            int actualSubscribers = 0;
            do {
                actualSubscribers = getSubscriberCount(queueClient, replyQueue, topic);
                log.info("Have " + actualSubscribers + " subscribers. Expecting " + expectedCount);
                if (actualSubscribers != expectedCount) {
                    Thread.sleep(1000);
                } else {
                    done = true;
                }
            } while (budget.timeLeft() >= 0 && !done);
            if (!done) {
                throw new RuntimeException("Only " + actualSubscribers + " out of " + expectedCount + " subscribed before timeout");
            }
        } catch (Exception ex) {
            throw ex;
        } finally {
            queueClient.close();
        }
    }

    /**
     * return list of queue names created for subscribers
     *
     * @param queueClient
     * @param replyQueue  queue for answer is required
     * @param topic       topic name
     * @return
     * @throws Exception
     */
    protected List<String> getBrokerQueueNames(AmqpClient queueClient, Destination replyQueue, String topic) throws Exception {
        return brokerManagement.getQueueNames(queueClient, replyQueue, topic);
    }

    /**
     * get count of subscribers subscribed to 'topic'
     *
     * @param queueClient queue client with admin permissions
     * @param replyQueue  queue for answer is required
     * @param topic       topic name
     * @return
     * @throws Exception
     */
    protected int getSubscriberCount(AmqpClient queueClient, Destination replyQueue, String topic) throws Exception {
        List<String> queueNames = getBrokerQueueNames(queueClient, replyQueue, topic);

        AtomicInteger subscriberCount = new AtomicInteger(0);
        queueNames.forEach((String queue) -> {
            try {
                subscriberCount.addAndGet(brokerManagement.getSubscriberCount(queueClient, replyQueue, queue));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return subscriberCount.get();
    }

    protected ArrayList<Destination> generateTopicsList(String prefix, IntStream range) {
        ArrayList<Destination> addresses = new ArrayList<>();
        range.forEach(i -> addresses.add(Destination.topic(prefix + i, getDefaultPlan(AddressType.QUEUE))));
        return addresses;
    }

    protected ArrayList<Destination> generateQueueList(String prefix, IntStream range) {
        ArrayList<Destination> addresses = new ArrayList<>();
        range.forEach(i -> addresses.add(Destination.queue(prefix + i, getDefaultPlan(AddressType.QUEUE))));
        return addresses;
    }

    protected ArrayList<Destination> generateQueueTopicList(String infix, IntStream range) {
        ArrayList<Destination> addresses = new ArrayList<>();
        range.forEach(i -> {
            if (i % 2 == 0) {
                addresses.add(Destination.topic(String.format("topic-%s-%d", infix, i), getDefaultPlan(AddressType.TOPIC)));
            } else {
                addresses.add(Destination.queue(String.format("queue-%s-%d", infix, i), getDefaultPlan(AddressType.QUEUE)));
            }
        });
        return addresses;
    }

    /**
     * attach N receivers into one address with default username/password
     */
    protected List<AbstractClient> attachReceivers(AddressSpace addressSpace, Destination destination, int receiverCount) throws Exception {
        return attachReceivers(addressSpace, destination, receiverCount, username, password);
    }

    /**
     * attach N receivers into one address with own username/password
     */
    protected List<AbstractClient> attachReceivers(AddressSpace addressSpace, Destination destination, int receiverCount, String username, String password) throws Exception {
        ArgumentMap arguments = new ArgumentMap();
        arguments.put(Argument.BROKER, getRouteEndpoint(addressSpace).toString());
        arguments.put(Argument.TIMEOUT, "120");
        arguments.put(Argument.CONN_SSL, "true");
        arguments.put(Argument.USERNAME, username);
        arguments.put(Argument.PASSWORD, password);
        arguments.put(Argument.LOG_MESSAGES, "json");
        arguments.put(Argument.ADDRESS, destination.getAddress());
        arguments.put(Argument.CONN_PROPERTY, "connection_property1~50");
        arguments.put(Argument.CONN_PROPERTY, "connection_property2~testValue");

        List<AbstractClient> receivers = new ArrayList<>();
        for (int i = 0; i < receiverCount; i++) {
            RheaClientReceiver rec = new RheaClientReceiver();
            rec.setArguments(arguments);
            rec.runAsync();
            receivers.add(rec);
        }

        Thread.sleep(15000); //wait for attached
        return receivers;
    }

    /**
     * attach senders to destinations (for N-th destination is attached N+1 senders)
     */
    protected List<AbstractClient> attachSenders(AddressSpace addressSpace, List<Destination> destinations) throws Exception {
        List<AbstractClient> senders = new ArrayList<>();

        ArgumentMap arguments = new ArgumentMap();
        arguments.put(Argument.BROKER, getRouteEndpoint(addressSpace).toString());
        arguments.put(Argument.TIMEOUT, "60");
        arguments.put(Argument.CONN_SSL, "true");
        arguments.put(Argument.USERNAME, username);
        arguments.put(Argument.PASSWORD, password);
        arguments.put(Argument.LOG_MESSAGES, "json");
        arguments.put(Argument.MSG_CONTENT, "msg no.%d");
        arguments.put(Argument.COUNT, "30");
        arguments.put(Argument.DURATION, "30");
        arguments.put(Argument.CONN_PROPERTY, "connection_property1~50");
        arguments.put(Argument.CONN_PROPERTY, "connection_property2~testValue");

        for (int i = 0; i < destinations.size(); i++) {
            arguments.put(Argument.ADDRESS, destinations.get(i).getAddress());
            for (int j = 0; j < i + 1; j++) {
                AbstractClient send = new RheaClientSender();
                send.setArguments(arguments);
                send.runAsync();
                senders.add(send);
            }
        }

        return senders;
    }

    /**
     * attach receivers to destinations (for N-th destination is attached N+1 senders)
     */
    protected List<AbstractClient> attachReceivers(AddressSpace addressSpace, List<Destination> destinations) throws Exception {
        List<AbstractClient> receivers = new ArrayList<>();

        ArgumentMap arguments = new ArgumentMap();
        arguments.put(Argument.BROKER, getRouteEndpoint(addressSpace).toString());
        arguments.put(Argument.TIMEOUT, "60");
        arguments.put(Argument.CONN_SSL, "true");
        arguments.put(Argument.USERNAME, username);
        arguments.put(Argument.PASSWORD, password);
        arguments.put(Argument.LOG_MESSAGES, "json");
        arguments.put(Argument.CONN_PROPERTY, "connection_property1~50");
        arguments.put(Argument.CONN_PROPERTY, "connection_property2~testValue");

        for (int i = 0; i < destinations.size(); i++) {
            arguments.put(Argument.ADDRESS, destinations.get(i).getAddress());
            for (int j = 0; j < i + 1; j++) {
                AbstractClient rec = new RheaClientReceiver();
                rec.setArguments(arguments);
                rec.runAsync();
                receivers.add(rec);
            }
        }

        return receivers;
    }

    /**
     * create M connections with N receivers and K senders
     */
    protected AbstractClient attachConnector(AddressSpace addressSpace, Destination destination, int connectionCount,
                                             int senderCount, int receiverCount, String username, String password) throws Exception {
        ArgumentMap arguments = new ArgumentMap();
        arguments.put(Argument.BROKER, getRouteEndpoint(addressSpace).toString());
        arguments.put(Argument.TIMEOUT, "120");
        arguments.put(Argument.CONN_SSL, "true");
        arguments.put(Argument.USERNAME, username);
        arguments.put(Argument.PASSWORD, password);
        arguments.put(Argument.OBJECT_CONTROL, "CESR");
        arguments.put(Argument.ADDRESS, destination.getAddress());
        arguments.put(Argument.COUNT, Integer.toString(connectionCount));
        arguments.put(Argument.SENDER_COUNT, Integer.toString(senderCount));
        arguments.put(Argument.RECEIVER_COUNT, Integer.toString(receiverCount));
        arguments.put(Argument.CONN_PROPERTY, "connection_property1~50");
        arguments.put(Argument.CONN_PROPERTY, "connection_property2~testValue");

        AbstractClient cli = new RheaClientConnector();
        cli.setArguments(arguments);
        cli.runAsync();

        return cli;
    }

    /**
     * stop all clients from list of Abstract clients
     */
    protected void stopClients(List<AbstractClient> clients) {
        log.info("Stopping clients...");
        clients.forEach(AbstractClient::stop);
    }

    /**
     * create users and groups for wildcard authz tests
     */
    protected List<KeycloakCredentials> createUsersWildcard(AddressSpace addressSpace, String groupPrefix) throws Exception {
        List<KeycloakCredentials> users = new ArrayList<>();
        users.add(new KeycloakCredentials("user_" + groupPrefix + "_*", "password"));
        users.add(new KeycloakCredentials("user_" + groupPrefix + "_queue*", "password"));
        users.add(new KeycloakCredentials("user_" + groupPrefix + "_topic*", "password"));
        users.add(new KeycloakCredentials("user_" + groupPrefix + "_queue_*", "password"));
        users.add(new KeycloakCredentials("user_" + groupPrefix + "_topic_*", "password"));
        users.add(new KeycloakCredentials("user_" + groupPrefix + "_queueA*", "password"));
        users.add(new KeycloakCredentials("user_" + groupPrefix + "_topicA*", "password"));

        for (KeycloakCredentials cred : users) {
            getKeycloakClient().createUser(addressSpace.getName(), cred.getUsername(), cred.getPassword(),
                    cred.getUsername().replace("user_", ""));
        }

        return users;
    }

    protected List<Destination> getAddressesWildcard() throws Exception {
        Destination queue = Destination.queue("queue_1234", getDefaultPlan(AddressType.QUEUE));
        Destination queue2 = Destination.queue("queueABCD", getDefaultPlan(AddressType.QUEUE));
        Destination topic = Destination.topic("topic_2345", getDefaultPlan(AddressType.TOPIC));
        Destination topic2 = Destination.topic("topicABCD", getDefaultPlan(AddressType.TOPIC));

        return Arrays.asList(queue, queue2, topic, topic2);
    }

    //================================================================================================
    //==================================== Asserts methods ===========================================
    //================================================================================================
    public void assertSorted(String message, Iterable list) throws Exception {
        assertSorted(message, list, false);
    }

    public void assertSorted(String message, Iterable list, Comparator comparator) throws Exception {
        assertSorted(message, list, false, comparator);
    }

    public void assertSorted(String message, Iterable list, boolean reverse) throws Exception {
        log.info("Assert sort reverse: " + reverse);
        if (!reverse)
            assertTrue(message, Ordering.natural().isOrdered(list));
        else
            assertTrue(message, Ordering.natural().reverse().isOrdered(list));
    }

    public void assertSorted(String message, Iterable list, boolean reverse, Comparator comparator) throws Exception {
        log.info("Assert sort reverse: " + reverse);
        if (!reverse)
            assertTrue(message, Ordering.from(comparator).isOrdered(list));
        else
            assertTrue(message, Ordering.from(comparator).reverse().isOrdered(list));
    }
}
