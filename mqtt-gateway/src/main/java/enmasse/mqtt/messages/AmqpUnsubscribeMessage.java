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

package enmasse.mqtt.messages;

import io.vertx.proton.ProtonHelper;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;

import java.util.List;

/**
 * Represents an AMQP_UNSUBSCRIBE message
 */
public class AmqpUnsubscribeMessage {

    public static final String AMQP_SUBJECT = "unsubscribe";

    private final String clientId;
    private final Object messageId;
    private final List<String> topics;

    /**
     * Constructor
     *
     * @param clientId  client identifier
     * @param messageId message identifier
     * @param topics    topics to subscribe
     */
    public AmqpUnsubscribeMessage(String clientId, Object messageId, List<String> topics) {

        this.clientId = clientId;
        this.messageId = messageId;
        this.topics = topics;
    }

    /**
     * Return an AMQP_UNSUBSCRIBE message from the raw AMQP one
     *
     * @param message   raw AMQP message
     * @return  AMQP_UNSUBSCRIBE message
     */
    @SuppressWarnings("unchecked")
    public static AmqpUnsubscribeMessage from(Message message) {

        if (!message.getSubject().equals(AMQP_SUBJECT)) {
            throw new IllegalArgumentException(String.format("AMQP message subject is no s%", AMQP_SUBJECT));
        }

        Section section = message.getBody();
        if ((section != null) && (section instanceof AmqpValue)) {

            List<String> topics = (List<String>) ((AmqpValue) section).getValue();

            return new AmqpUnsubscribeMessage(AmqpHelper.getClientIdFromPublishAddress((String) message.getCorrelationId()),
                    message.getMessageId(),
                    topics);

        } else {
            throw new IllegalArgumentException("AMQP message wrong body type");
        }
    }

    /**
     * Return a raw AMQP message
     *
     * @return
     */
    public Message toAmqp() {

        Message message = ProtonHelper.message();

        message.setSubject(AMQP_SUBJECT);

        message.setMessageId(this.messageId);

        message.setCorrelationId(String.format(AmqpHelper.AMQP_CLIENT_PUBLISH_ADDRESS_TEMPLATE, this.clientId));

        message.setBody(new AmqpValue(this.topics));

        return message;
    }

    /**
     * Client identifier
     * @return
     */
    public String clientId() {
        return this.clientId;
    }

    /**
     * Message identifier
     * @return
     */
    public Object messageId() {
        return messageId;
    }

    /**
     * Topics to subscribe
     * @return
     */
    public List<String> topics() {
        return this.topics;
    }

    @Override
    public String toString() {

        return "AmqpUnsubscribeMessage{" +
                "clientId=" + this.clientId +
                ", messageId=" + this.messageId +
                ", topics=" + this.topics +
                "}";
    }
}
