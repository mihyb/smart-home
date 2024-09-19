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

package org.openhab.automation.jrule.generated.items;

import org.openhab.automation.jrule.items.JRuleItemRegistry;
import org.openhab.automation.jrule.exception.JRuleItemNotFoundException;

import org.openhab.automation.jrule.internal.items.JRuleInternalSwitchItem;
import org.openhab.automation.jrule.items.JRuleSwitchItem;

/**
* Automatically Generated Class for Items - DO NOT EDIT!
*
* @author Arne Seime - Refactoring
* @author Robert Delbrück - Refactoring
*/
public class JRuleItems {



    /**
     * Name: Kotelna_zasuvka
     * <br/>
     * Type: Switch
     * <br/>
     * Label: Kotelna zasuvka
     * <br/>
     * Tags: Point
     * <br/>
     * Metadata: semantics: Point, configuration={}
     */
 public static JRuleSwitchItem Kotelna_zasuvka;


 static {
   loadItems();
 }

  /**
  * Need this method for testing
  */
 private static void loadItems() throws JRuleItemNotFoundException {
     Kotelna_zasuvka = JRuleItemRegistry.get("Kotelna_zasuvka", JRuleInternalSwitchItem.class);

 }

}

