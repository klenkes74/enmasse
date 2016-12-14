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

package enmasse.mqtt.endpoints;

import enmasse.mqtt.messages.AmqpPublishMessage;
import enmasse.mqtt.messages.AmqpPubrelMessage;
import enmasse.mqtt.messages.AmqpSubscriptionsMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Receiver endpoint
 */
public class AmqpReceiverEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpReceiverEndpoint.class);

    public static final String CLIENT_CONTROL_ENDPOINT_TEMPLATE = "$mqtt.to.%s.control";
    public static final String CLIENT_PUBLISH_ENDPOINT_TEMPLATE = "$mqtt.to.%s.publish";

    private AmqpReceiver receiver;

    // handler called when AMQP_SUBSCRIPTIONS is received
    private Handler<AmqpSubscriptionsMessage> subscriptionsHandler;
    // handler called when AMQP_PUBLISH is received
    private Handler<AmqpPublishMessage> publishHandler;
    // handler called when AMQP_PUBREL is received
    private Handler<AmqpPubrelMessage> pubrelHandler;
    // all delivery for received messages if they need settlement (messageId -> delivery)
    private Map<Object, ProtonDelivery> deliveries;

    /**
     * Constructor
     *
     * @param receiver  receiver instance related to unique client addresses
     */
    public AmqpReceiverEndpoint(AmqpReceiver receiver) {
        this.receiver = receiver;
    }

    /**
     * Set the session handler called when AMQP_SUBSCRIPTIONS is received
     *
     * @param handler   the handler
     * @return  the current AmqpReceiverEndpoint instance
     */
    public AmqpReceiverEndpoint subscriptionsHandler(Handler<AmqpSubscriptionsMessage> handler) {

        this.subscriptionsHandler = handler;
        return this;
    }

    /**
     * Set the session handler called when AMQP_PUBLISH is received
     *
     * @param handler   the handler
     * @return  the current AmqpReceiverEndpoint instance
     */
    public AmqpReceiverEndpoint publishHandler(Handler<AmqpPublishMessage> handler) {

        this.publishHandler = handler;
        return this;
    }

    /**
     * Set the session handler called when AMQP_PUBREL is received
     *
     * @param handler   the handler
     * @return  the current AmqpReceiverEndpoint instance
     */
    public AmqpReceiverEndpoint pubrelHandler(Handler<AmqpPubrelMessage> handler) {

        this.pubrelHandler = handler;
        return this;
    }

    /**
     * Handler for the receiver for handling incoming raw AMQP message
     * from the Subscription Service
     *
     * @param delivery  AMQP delivery information
     * @param message   raw AMQP message
     */
    private void messageHandler(ProtonDelivery delivery, Message message) {

        LOG.info("Received {}", message);

        if (message.getSubject() != null) {

            switch (message.getSubject()) {

                case AmqpSubscriptionsMessage.AMQP_SUBJECT:

                    this.handleSession(AmqpSubscriptionsMessage.from(message));
                    delivery.disposition(Accepted.getInstance(), true);

                    break;

                case AmqpPublishMessage.AMQP_SUBJECT:

                    AmqpPublishMessage amqpPublishMessage = AmqpPublishMessage.from(message);

                    // QoS 0 : immediate disposition (settle), then passing to the bridge handler
                    if (amqpPublishMessage.qos() == MqttQoS.AT_MOST_ONCE) {

                        if (!delivery.remotelySettled()) {
                            delivery.disposition(Accepted.getInstance(), true);
                        }
                        this.handlePublish(amqpPublishMessage);

                    // QoS 1 : passing to the bridge handle first, added to deliveries (be settled after bridge handling)
                    } else if (amqpPublishMessage.qos() == MqttQoS.AT_LEAST_ONCE) {

                        this.handlePublish(amqpPublishMessage);
                        if (!delivery.remotelySettled()) {
                            this.deliveries.put(message.getMessageId(), delivery);
                        }

                    // QoS 2 :
                    } else {

                        this.handlePublish(amqpPublishMessage);
                        if (!delivery.remotelySettled()) {
                            this.deliveries.put(message.getMessageId(), delivery);
                        }
                    }

                    break;

                case AmqpPubrelMessage.AMQP_SUBJECT:

                    this.handlePubrel(AmqpPubrelMessage.from(message));
                    if (!delivery.remotelySettled()) {
                        this.deliveries.put(message.getMessageId(), delivery);
                    }

                    break;
            }

        } else {

            // TODO: published message (i.e. from native AMQP clients) could not have subject "publish" and all needed annotations !!!
            message.setSubject(AmqpPublishMessage.AMQP_SUBJECT);
            this.handlePublish(AmqpPublishMessage.from(message));
            if (!delivery.remotelySettled()) {
                this.deliveries.put(message.getMessageId(), delivery);
            }
        }
    }

    /**
     * Open the control endpoint, attaching the link
     */
    public void openControl() {

        this.deliveries = new HashMap<>();

        // attach receiver link on the $mqtt.to.<client-id>.control address for receiving messages (from SS)
        // define handler for received messages
        // - AMQP_SUBSCRIPTIONS after sent AMQP_LIST -> for writing CONNACK (session-present)
        this.receiver.receiverControl()
                .setQoS(ProtonQoS.AT_LEAST_ONCE)
                .handler(this::messageHandler)
                .open();
    }

    /**
     * Open the publish endpoint, attaching the link
     */
    public void openPublish() {

        // attach receiver link on the $mqtt.to.<client-id>.publish address for receiving published messages
        // define handler for received messages
        // - AMQP_PUBLISH for every AMQP published message
        // - AMQP_PUBREL for handling QoS 2
        this.receiver.receiverPublish()
                .setQoS(ProtonQoS.AT_LEAST_ONCE)
                .handler(this::messageHandler)
                .open();
    }

    /**
     * Close the endpoint, detaching the link
     */
    public void close() {

        // detach links
        this.receiver.close();
        this.deliveries.clear();
    }

    /**
     * Settle the delivery for a received message
     *
     * @param messageId message identifier to settle
     */
    public void settle(Object messageId) {

        if (this.deliveries.containsKey(messageId)) {
            ProtonDelivery delivery = this.deliveries.remove(messageId);
            delivery.disposition(Accepted.getInstance(), true);
        }
    }

    /**
     * Used for calling the session handler when AMQP_SUBSCRIPTIONS is received
     *
     * @param amqpSubscriptionsMessage AMQP_SUBSCRIPTIONS message
     */
    private void handleSession(AmqpSubscriptionsMessage amqpSubscriptionsMessage) {

        if (this.subscriptionsHandler != null) {
            this.subscriptionsHandler.handle(amqpSubscriptionsMessage);
        }
    }

    /**
     * Used for calling the session handler when AMQP_PUBLISH is received
     *
     * @param amqpPublishMessage AMQP_PUBLISH message
     */
    private void handlePublish(AmqpPublishMessage amqpPublishMessage) {

        if (this.publishHandler != null) {
            this.publishHandler.handle(amqpPublishMessage);
        }
    }

    /**
     * Used for calling the pubrel handler when AMQP_PUBREL is received
     *
     * @param amqpPubrelMessage AMQP_PUBREL message
     */
    private void handlePubrel(AmqpPubrelMessage amqpPubrelMessage) {

        if (this.pubrelHandler != null) {
            this.pubrelHandler.handle(amqpPubrelMessage);
        }
    }
}
