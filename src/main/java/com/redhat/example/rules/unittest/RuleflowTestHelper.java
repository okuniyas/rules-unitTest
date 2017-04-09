/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.example.rules.unittest;

import org.apache.commons.lang3.StringUtils;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent;
import org.kie.api.runtime.KieRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuleflowTestHelper {
	public static final Logger logger = LoggerFactory.getLogger(RuleflowTestHelper.class);
	
	/**
	 * Set to skip the ruleflow execution after specified ruleflow-group
	 * @param runtime KieRuntime, KieSession
	 * @param group ruleflow-group name
	 */
	public static void setSkipAfterRuleGroup(KieRuntime runtime, String group) {
		SubListener1 listener1 = new SubListener1(group);
		runtime.addEventListener(listener1);
	}
	
	/**
	 * Reset to skip the ruleflow exection after specified ruleflow-group
	 * @param runtime
	 */
	public static void resetSkipAfterRuleGroup(KieRuntime runtime) {
		for (AgendaEventListener listener : runtime.getAgendaEventListeners()) {
			if (listener instanceof SubListener1) {
				runtime.removeEventListener(listener);
			}
		}
	}
	
	private static class SubListener1 extends DefaultAgendaEventListener {
		String group;

		private SubListener1(String group) {
			this.group = group;
		}
		@Override
		public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
			if (!StringUtils.isEmpty(group) &&
					group.equals(event.getRuleFlowGroup().getName())) {
				logger.debug("** SKIP after Rule Group ({}) **", group);
				KieRuntime runtime = event.getKieRuntime();
				runtime.getAgenda().clear();
			}
		}

	}
}
