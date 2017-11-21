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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;
import org.kie.api.event.KieRuntimeEventManager;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;

/**
 * Baseテストケース
 */
public class TestCaseBase {
	protected static RuleCoverageLogger ruleCoverageLogger;
	protected static String ruleFlowName = null;

	public static String kieBaseNameProperty = "rules.unittest.kiebasename";
	protected static String kieBaseName = null;
	protected static KieBase kieBase = null;
			
	protected static KieServices ks = KieServices.Factory.get();
	protected static KieContainer kieContainer = ks.newKieClasspathContainer();
	protected static KieCommands kieCommands = ks.getCommands();

	@BeforeClass
	public static void setUpBeforeClass() {
		ruleCoverageLogger = new RuleCoverageLogger();
	}
	
	@Before
	public void prepareKieBase() {
		String targetKieBaseName = System.getProperty(kieBaseNameProperty);
		if (targetKieBaseName != null &&
				kieContainer.getKieBaseNames().contains(targetKieBaseName)) {
			kieBaseName = targetKieBaseName;
		}
		System.out.println("kieBaseName is \"" + kieBaseName + "\"");
		System.out.println("(*) Can change kieBaseName by \"-D" + kieBaseNameProperty + "=...\"");
		if (kieBaseName == null || kieBaseName.length() == 0) {
			kieBase = kieContainer.getKieBase();
		} else {
			kieBase = kieContainer.getKieBase(kieBaseName);
		}
	}
	
	protected void initSession(KieRuntimeEventManager session) {
		ruleCoverageLogger.setSession(session);
		session.addEventListener(new RuleExecutionLogger());
	}


	@AfterClass
	public static void tearDownAfterClass() {
		if (StringUtils.isBlank(ruleFlowName)) {
			// print the coverage all RuleGroupS.
			ruleCoverageLogger.printCoverage();
		} else {
			// print the coverage for the ruleflow only.
			ruleCoverageLogger.printCoverageOfRuleFlow(ruleFlowName);
		}
		RuleCoverageLogger.clear();
	}

	public void executeStateless(List<Command<?>> cmds, KieSessionWrapper sessionWrapper) {
		StatelessKieSession kieSession = kieBase.newStatelessKieSession();
		if (sessionWrapper != null) {
			sessionWrapper.beforeExecute(kieSession);
		}
		kieSession.execute(kieCommands.newBatchExecution(cmds));
	}

	public KieSession executeStateful(KieSessionWrapper sessionWrapper) {
		KieSession kieSession = kieBase.newKieSession();
		if (sessionWrapper != null) {
			sessionWrapper.beforeExecute(kieSession);
		}
		kieSession.fireAllRules();
		if (sessionWrapper != null) {
			sessionWrapper.afterExecute(kieSession);
		}
		return kieSession;
	}

	public interface KieSessionWrapper {
		public void beforeExecute(KieRuntimeEventManager session);
		public void afterExecute(KieRuntimeEventManager session);
	}
	public static class DefaultKieSessionWrapper implements KieSessionWrapper {
		RuleExecutionLogger ruleExecLogger = new RuleExecutionLogger();

		@Override
		public void beforeExecute(KieRuntimeEventManager session) {
			session.addEventListener(ruleExecLogger);
			ruleCoverageLogger.setSession(session);
		}

		@Override
		public void afterExecute(KieRuntimeEventManager session) {
			session.removeEventListener(ruleExecLogger);
			session.removeEventListener(ruleCoverageLogger);
		}
	}
}
