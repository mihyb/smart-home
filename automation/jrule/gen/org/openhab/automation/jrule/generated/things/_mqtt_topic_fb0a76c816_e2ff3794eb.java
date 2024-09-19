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

import org.openhab.automation.jrule.things.JRuleSubThing;

/**
 * Automatically Generated Class for Thing mqtt_topic_fb0a76c816_e2ff3794eb - DO NOT EDIT!
 *
 * @author Arne Seime - Initial contribution
*/
public class _mqtt_topic_fb0a76c816_e2ff3794eb extends JRuleSubThing {

    public static final String ID = "mqtt:topic:fb0a76c816:e2ff3794eb";

    public static final String LABEL = "zasuvka kotelna";


    public _mqtt_topic_fb0a76c816_e2ff3794eb(String id) {
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




    public String getBridgeUID() {
        return "mqtt:broker:fb0a76c816";
    }
}
