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

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.apache.commons.lang3.StringUtils;

import org.kie.api.KieBase;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.process.Node;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.core.node.RuleSetNode;
import org.kie.api.definition.rule.Rule;
import org.drools.core.definitions.rule.impl.RuleImpl;
import org.kie.api.event.KieRuntimeEventManager;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rule Coverage Logger
 * 
 */
public class RuleCoverageLogger extends DefaultAgendaEventListener {
	private static Logger logger = LoggerFactory.getLogger(RuleCoverageLogger.class);
	public static void SetLogger(Logger log) {
		logger = log;
	}

	/**
	 * Set of Initialized (registered) Package
	 */
	private static final Set<String> initializedPackageSet =
			new ConcurrentSkipListSet<String>();

	/**
	 * Map of Rule -> isExecuted (True of False)
	 */
	private static final Map<Rule, Boolean> ruleCoverageMap =
			new ConcurrentHashMap<Rule, Boolean>();

	/**
	 * Map of RuleGroup -> List<Rule>
	 */
	private static final Map<String, Set<Rule>> ruleGroupToRulesMap =
			new ConcurrentHashMap<String, Set<Rule>>();

	/**
	 * Map of RuleFlow -> List<ruleGroupName(String)>
	 */
	private static final Map<String, Set<String>> ruleFlowToRuleGroupMap =
			new ConcurrentHashMap<String, Set<String>>();

	private KieRuntimeEventManager session = null;
	
	/**
	 * Set Session
	 * @param session KieSession or StatelessKieSession
	 */
	public RuleCoverageLogger setSession(KieRuntimeEventManager session) {
		return setSession(session, false);
	}
	
	private RuleCoverageLogger setSession(KieRuntimeEventManager session, boolean inListener) {
		this.session = session;
		KieBase kieBase = null;
		if (session instanceof KieSession) {
			kieBase = ((KieSession)session).getKieBase();
		} else if (session instanceof StatelessKieSession) {
			kieBase = ((StatelessKieSession)session).getKieBase();
		}
		if (kieBase != null) {
			for (org.kie.api.definition.process.Process process : kieBase.getProcesses()) {
				Set<String> ruleGroups = new LinkedHashSet<String>();
				for (Node node : ((RuleFlowProcess)process).getNodes()) {
					if (node instanceof RuleSetNode) {
						ruleGroups.add(((RuleSetNode)node).getRuleFlowGroup());
					}
				}
				Set<String> previous = ruleFlowToRuleGroupMap.put(process.getId(), ruleGroups);
				if (previous != null) {
					// already registered (concurrent access)
					ruleFlowToRuleGroupMap.put(process.getId(), previous);
				}
			}
			for (KiePackage kiePackage : kieBase.getKiePackages()) {
				initPackage(kiePackage);
			}
		}
		if (!inListener) {
			session.addEventListener(this);
		}
		return this;
	}
	
	/**
	 * AgentEventListener method called for each execution of 1 rule activation
	 */
	@Override
	public void beforeMatchFired(BeforeMatchFiredEvent event) {
		if (session == null) {
			setSession(event.getKieRuntime(), true);
		}
		Boolean ret = ruleCoverageMap.put(event.getMatch().getRule(), Boolean.TRUE);
		if (ret == null || !ret) {
			logger.debug("Rule : \"{}\" is covered !", event.getMatch().getRule().getName());
		}
	}

	/**
	 * initialize (register) package
	 * @param kieBase
	 */
	private void initPackage(KiePackage kiePackage) {
		if (!initializedPackageSet.add(kiePackage.getName())) {
			// already initialized
			return;
		}
		final RuleComparator ruleComparator = new RuleComparator();
		for (Rule rule : kiePackage.getRules()) {
			Boolean previous = ruleCoverageMap.put(rule, Boolean.FALSE);
			if (previous != null) {
				// already registered (concurrent access)
				ruleCoverageMap.put(rule, previous);
			}
			String ruleGroupName = ((RuleImpl)rule).getAgendaGroup();
			if (ruleGroupName == null) {
				ruleGroupName = ((RuleImpl)rule).getRuleFlowGroup();
			}
			ruleGroupName = StringUtils.isBlank(ruleGroupName) ? "default" : ruleGroupName;
			Set<Rule> rules = ruleGroupToRulesMap.get(ruleGroupName);
			if (rules == null) {
				rules = new ConcurrentSkipListSet<Rule>(ruleComparator);
				ruleGroupToRulesMap.put(ruleGroupName, rules);
			}
			rules.add(rule);
		}
	}
	
	/**
	 * print the coverage result of All RuleGroup
	 */
	public void printCoverage() {
		logger.debug("##### RULE COVERAGE - START #####");
		int sumExecuted = 0;
		int sumDefined = 0;
		TreeSet<String> sortedRuleGroupNames = new TreeSet<String>(ruleGroupToRulesMap.keySet());
		if (sortedRuleGroupNames.size() == 0) {
			logger.debug("##### Rule Group is EMPTY!! : The coverage info. has just cleared or No rules has been executed since cleared. #####");
		}
		for (String ruleGroupName : sortedRuleGroupNames) {
			int nums[] = printCoverageOfRuleGroup(ruleGroupName);
			sumExecuted += nums[0];
			sumDefined += nums[1];
		}
		if (sumDefined > 0) {
			logger.debug("# All Rule Groups - Coverage {}% ( {} / {} )", 100*sumExecuted/sumDefined, sumExecuted, sumDefined);
		}
		logger.debug("##### RULE COVERAGE -  END  #####");
	}
	
	/**
	 * print the coverage result of a RuleFlow
	 * @param ruleFlowName
	 */
	public void printCoverageOfRuleFlow(String ruleFlowName) {
		if (StringUtils.isBlank(ruleFlowName)) {
			logger.debug("printCoverageOfRuleFlow(\"{}\"): ruleflow name is blank.", ruleFlowName);
			return;
		}
		Set<String> ruleGroupNames = ruleFlowToRuleGroupMap.get(ruleFlowName);
		if (ruleGroupNames == null) {
			logger.debug("##### No such ruleflow \"{}\" or No rules has been executed since cleared. #####", ruleFlowName);
			return;
		}
		logger.debug("##### RULE COVERAGE of RuleFlow : \"{}\" - START #####", ruleFlowName);
		int sumExecuted = 0;
		int sumDefined = 0;
		for (String ruleGroupName : ruleGroupNames) {
			int nums[] = printCoverageOfRuleGroup(ruleGroupName);
			sumExecuted += nums[0];
			sumDefined += nums[1];
		}
		if (sumDefined > 0) {
			logger.debug("# RuleFlow : \"{}\" - Coverage {}% ( {} / {} )", ruleFlowName, 100*sumExecuted/sumDefined, sumExecuted, sumDefined);
		}
		logger.debug("##### RULE COVERAGE of RuleFlow : \"{}\" -  END  #####", ruleFlowName);
	}

	/**
	 * print the coverage result of a RuleGroup
	 * @param ruleGroupName
	 */
	public int[] printCoverageOfRuleGroup(String ruleGroupName) {
		Set<Rule> rules = ruleGroupToRulesMap.get(ruleGroupName);
		int rulesNum = rules.size();
		int coveredNum = 0;
		TreeSet<String> notCoveredRules = new TreeSet<String>();
		Iterator<Rule> it = rules.iterator();
		while (it.hasNext()) {
			Rule rule = it.next();
			boolean isCovered = ruleCoverageMap.get(rule);
			if (isCovered) {
				coveredNum ++;
			} else {
				notCoveredRules.add(rule.getName());
			}
		}
		if (rulesNum > 0) {
			logger.debug("# Rule group : \"{}\" - Coverage {}% ( {} / {} )", ruleGroupName, 100*coveredNum/rulesNum, coveredNum, rulesNum);
			for (String name : notCoveredRules) {
				logger.debug("  * Rule not covered : \"{}\"", name);
			}
		}
		return new int[] { coveredNum, rulesNum };
	}
	
	public class RuleComparator implements Comparator<Rule> {
		@Override
		public int compare(Rule o1, Rule o2) {
			return o1.getName().compareTo(o2.getName());
		}
	}
	
	/**
	 * clear Coverage information
	 */
	@SuppressWarnings("rawtypes")
	public static void clear() {
		final Object[] collections
		= { initializedPackageSet, ruleFlowToRuleGroupMap, ruleGroupToRulesMap, ruleCoverageMap };
		// clear map and collection (twice just in case)
		for (int i=0; i<2; i++) {
			for (Object c : collections) {
				if (c instanceof Map)
					((Map)c).clear();
				else if (c instanceof Collection)
					((Collection)c).clear();
			}
		}
	}
}

