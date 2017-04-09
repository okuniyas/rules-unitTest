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

import org.drools.core.definitions.rule.impl.RuleImpl;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.RuleFlowGroupActivatedEvent;
import org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rule Execution Logger
 * outputs executions of rule group begin&end and rule begin&end.
 */
public class RuleExecutionLogger extends DefaultAgendaEventListener {
	private static Logger logger = LoggerFactory.getLogger(RuleExecutionLogger.class);
	public static void SetLogger(Logger log) {
		logger = log;
	}
	
	@Override
	public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
		logger.debug("Exec_Rule_Group: {}", event.getRuleFlowGroup().getName());
	}
	
	@Override
	public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
		logger.debug("End__Rule_Group: {}", event.getRuleFlowGroup().getName());
	}
	
	@Override
	public void beforeMatchFired(BeforeMatchFiredEvent event) {
		RuleImpl rule = (RuleImpl)event.getMatch().getRule();
		logger.debug("Exec_Rule: {} in Rule_Group {}",
				rule.getName(),
				rule.getAgendaGroup()
				);
	}
	
	@Override
	public void afterMatchFired(AfterMatchFiredEvent event) {
		logger.debug("End__Rule: {}", event.getMatch().getRule().getName());
	}
	
}
