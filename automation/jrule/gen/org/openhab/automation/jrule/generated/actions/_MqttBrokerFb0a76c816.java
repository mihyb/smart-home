/**
* Copyright (c) 2010-2023 Contributors to the openHAB project
*
* See the NOTICE file(s) distributed with this work for additional
* information.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0
*
* SPDX-License-Identifier: EPL-2.0
*/

package org.openhab.automation.jrule.generated.actions;

import org.openhab.automation.jrule.actions.JRuleAbstractAction;
import java.util.Objects;

/**
 * Automatically Generated Class for Action MqttBrokerFb0a76c816 - DO NOT EDIT!
 * <br/>
 * All functions of <b>mqtt:broker:fb0a76c816</b> from Java Class {@link org.openhab.binding.mqtt.internal.action.MQTTActions}
 *
 * @author Robert Delbrück - Initial contribution
*/
public class _MqttBrokerFb0a76c816 extends JRuleAbstractAction {
    public _MqttBrokerFb0a76c816(String scope, String thingUid) {
        super(scope, thingUid);
    }

    /**
     * mqtt:broker:fb0a76c816: publishMQTT
     * @topic: topic of java.lang.String
     * @value: value of byte[]
     * @retain: retain of java.lang.Boolean
     
     */
    public void publishMQTT(java.lang.String topic, byte[] value, java.lang.Boolean retain) {
         super.invokeMethod("publishMQTT", new Class<?>[]{String.class, byte[].class, Boolean.class}, topic, value, retain);
    }

    /**
     * mqtt:broker:fb0a76c816: publishMQTT
     * @topic: topic of java.lang.String
     * @value: value of java.lang.String
     
     */
    public void publishMQTT(java.lang.String topic, java.lang.String value) {
         super.invokeMethod("publishMQTT", new Class<?>[]{String.class, String.class}, topic, value);
    }

    /**
     * mqtt:broker:fb0a76c816: publishMQTT
     * @topic: topic of java.lang.String
     * @value: value of java.lang.String
     * @retain: retain of java.lang.Boolean
     
     */
    public void publishMQTT(java.lang.String topic, java.lang.String value, java.lang.Boolean retain) {
         super.invokeMethod("publishMQTT", new Class<?>[]{String.class, String.class, Boolean.class}, topic, value, retain);
    }

    /**
     * mqtt:broker:fb0a76c816: publishMQTT
     * @topic: topic of java.lang.String
     * @value: value of byte[]
     
     */
    public void publishMQTT(java.lang.String topic, byte[] value) {
         super.invokeMethod("publishMQTT", new Class<?>[]{String.class, byte[].class}, topic, value);
    }

}
