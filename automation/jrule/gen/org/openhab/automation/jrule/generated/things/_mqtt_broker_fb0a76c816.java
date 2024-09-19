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

package org.openhab.automation.jrule.generated.things;

import org.openhab.automation.jrule.things.JRuleBridgeThing;
import java.util.List;
/**
 * Automatically Generated Class for Thing mqtt_broker_fb0a76c816 - DO NOT EDIT!
 *
 * @author Arne Seime - Initial contribution
*/
public class _mqtt_broker_fb0a76c816 extends JRuleBridgeThing {

    public static final String ID = "mqtt:broker:fb0a76c816";

    public static final String LABEL = "MQTT Broker";


    public _mqtt_broker_fb0a76c816(String id) {
        super(id);
    }

    @Override
    public String toString() {
        return "'"+LABEL+"'/"+ID;
    }

    @Override
    public String getLabel() {
        return LABEL;
    }




    public List<String> getSubThingUIDs() {
        return List.of(
        );

    }
}
