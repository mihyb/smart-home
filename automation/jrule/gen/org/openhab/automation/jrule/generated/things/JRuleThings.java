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

import org.openhab.automation.jrule.things.JRuleThingRegistry;
import org.openhab.automation.jrule.things.JRuleBridgeThing;
import org.openhab.automation.jrule.things.JRuleStandaloneThing;
import org.openhab.automation.jrule.things.JRuleSubThing;

import org.openhab.automation.jrule.generated.things._mqtt_broker_fb0a76c816;
import org.openhab.automation.jrule.generated.things._mqtt_topic_fb0a76c816_e2ff3794eb;

/**
* Automatically Generated Class for Things - DO NOT EDIT!
*
* @author Arne Seime - Initial contribution
*/
public class JRuleThings {



 public static JRuleBridgeThing mqtt_broker_fb0a76c816;

 public static JRuleSubThing mqtt_topic_fb0a76c816_e2ff3794eb;


 public JRuleThings() {

    mqtt_broker_fb0a76c816 = JRuleThingRegistry.get("mqtt:broker:fb0a76c816", _mqtt_broker_fb0a76c816.class);

    mqtt_topic_fb0a76c816_e2ff3794eb = JRuleThingRegistry.get("mqtt:topic:fb0a76c816:e2ff3794eb", _mqtt_topic_fb0a76c816_e2ff3794eb.class);


 }

}

