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

import static org.junit.Assert.fail;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.drools.core.event.DefaultRuleRuntimeEventListener;
import org.kie.api.definition.rule.Rule;
import org.kie.api.event.KieRuntimeEventManager;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RuleFactWatcher watches specified Fact's specified attribute (field) is changed or not while rule execution,<BR>
 *  and it compares the value is equals to specified expected value.
 * 
 * @param <T> the class of fact to watch
 */
public class RuleFactWatcher {
	private static final Logger logger = LoggerFactory.getLogger(RuleFactWatcher.class);
	
	/**
	 * Timing : represent the timing of logging
	 * BEFORE : before of update()
	 * AFTER : after of update()
	 * INSERT : after of insert()
	 */
	private static enum Timing { BEFORE, AFTER, INSERT };
	
	public static final ConstantValues Constants = new ConstantValues();

	List<ExpectedRecord> expectedRecords;
	boolean checkByIndex;
	BiPredicate<ExpectedRecord, Object> predicate;
	Map<String, Boolean> testSkipMap;
	SubListener1 listener1;
	SubListener2 listener2;
	KieRuntimeEventManager runtime;
	
	Map<Object, Map<String, Object>> previousValues = new LinkedHashMap<Object, Map<String, Object>>();

	/**
	 * Constructor to create RuleFactWatcher.<BR>
	 * if you use CSV files to specify expected values, please use {@link CsvTestHelper#createRuleFactWatcher(String, Class, String) CsvTestHelper.createRuleFact()} method.<BR>
	 * <BR>
	 * @param expectedRecords expected record information
	 * @param checkByIndex the flag to check actuals and expected values by same index
	 * @param predicate predicate to check equality of between target fact and expected record
	 * @param testSkipMap map of flags for each attributes to skip the watching.
	 * @param nullCheckStr special string which means the expected value "should be null".
	 */
	public RuleFactWatcher(
			List<ExpectedRecord> expectedRecords,
			boolean checkByIndex,
			BiPredicate<ExpectedRecord, Object> predicate,
			Map<String, Boolean> testSkipMap) {
		this.expectedRecords = expectedRecords;
		this.checkByIndex = checkByIndex;
		this.predicate = predicate;
		this.testSkipMap = testSkipMap;
		listener1 = new SubListener1(this);
		listener2 = new SubListener2(this);
	}
	
	/**
	 * set KieRuntime (KieSession) to watch by this RuleFactWatcher.<BR>
	 * <BR>
	 * As RuleFactWatcher records previous values of specified facts' specified attributes,
	 * a RuleFactWatcher can only be used for single runtime.
	 * @param runtime
	 */
	public RuleFactWatcher setRuntime(KieRuntimeEventManager runtime) {
		if (this.runtime != null) {
			this.runtime.removeEventListener(listener1);
			this.runtime.removeEventListener(listener2);
		}
		previousValues.clear();
		if (this.runtime != runtime) {
			this.runtime = runtime;
			if (runtime != null) {
				runtime.addEventListener(listener1);
				runtime.addEventListener(listener2);
			}
		}
		return this;
	}
	
	/**
	 * reset KieRuntime (KieSession) to watch by this RuleFactWatcher.<BR>
	 */
	public RuleFactWatcher resetRuntime() {
		return setRuntime(null);
	}
	
	private static class SubListener1 extends DefaultAgendaEventListener {
		
		private RuleFactWatcher parent;
		
		private SubListener1(RuleFactWatcher parent) {
			this.parent = parent;
		}
		
		@Override
		public void beforeMatchFired(BeforeMatchFiredEvent event) {
			String ruleName = event.getMatch().getRule().getName();
			List<Object> objects = event.getMatch().getObjects();
			parent.printWatchedAttributes(ruleName, objects, Timing.BEFORE);
		}
		
		@Override
		public void afterMatchFired(AfterMatchFiredEvent event) {
			String ruleName = event.getMatch().getRule().getName();
			List<Object> objects = event.getMatch().getObjects();
			parent.printWatchedAttributes(ruleName, objects, Timing.AFTER);
		}
	}
	
	private void printWatchedAttributes(String ruleName, List<Object> objects, Timing timing) {
		List<Object> targets = objects;
		int actualIndex = 0;
		int expectIndex = 0;
		
		// if it is child watcher, require to replace targets by child attributes.
		if (insertedHeaderClass != null) {
			LinkedList<Object> subTargets = new LinkedList<Object>();
			for (Object obj : objects) {
				if (insertedHeaderClass.isInstance(obj)) {
					Object child = accessFunctionFromHeader.apply(obj, accessArgs);
					if (child instanceof Collection<?>) {
						subTargets.addAll((Collection<?>)child);
					} else if (child instanceof Map) {
						for (Object key : ((Map<?, ?>)child).keySet()) {
							MapEntry entry = new MapEntry();
							entry.key = key;
							entry.value = ((Map<?, ?>)child).get(key);
							subTargets.add(entry);
						}
					} else {
						subTargets.add(child);
					}
				}
			}
			targets = subTargets;
		}
		
		List<ExpectedRecord> workExpectedRecords = new ArrayList<ExpectedRecord>();
		for (ExpectedRecord e : expectedRecords) {
			workExpectedRecords.add(e);
		}
		
		// checkByIndex mode, remove other parents' records
		if (checkByIndex && parentRow != null) {
			for (int i=0; i < workExpectedRecords.size();) {
				if (! parentRow.equals(workExpectedRecords.get(i).map.get(Constants.parentRowKey))) {
					workExpectedRecords.remove(i);
				} else {
					i++;
				}
			}
		}

		for (Object obj : targets) {
			actualIndex++;
			expectIndex= 0;
			if (obj != null) {
				for (ExpectedRecord expect : workExpectedRecords) {
					expectIndex++;
					if (expect.fact == null) {
						if (checkByIndex &&
								actualIndex == expectIndex) {
							logger.debug("** expeced record (null) of the actual record [{}] is not null at rule ({})",
									actualIndex - 1,
									ruleName);
						}
						// skip null record in expected records
					} else if (isChild && ! expect.map.get(Constants.parentRowKey).equals(parentRow)) {
						// skip if this watcher is child, but parentRow index is not same.
					} else if ((checkByIndex &&
							actualIndex == expectIndex)
							|| (!checkByIndex && predicate.test(expect, obj))) {
						// checkByIndex AND index is same OR !checkByIndex AND class type and primary keys match
						checkAttributes(ruleName, timing, obj, expect);
						// call child listeners
						for (RuleFactWatcher childWatcher : childWatcherMap.values()) {
							childWatcher.parentRow = Integer.toString(expectedRecords.indexOf(expect)+1);
							childWatcher.printWatchedAttributes(ruleName, Arrays.asList(obj), timing);
						}
					}
				}
			} else {
				if (checkByIndex) {
					if (workExpectedRecords.size() < actualIndex) {
						logger.debug("** expeced record of the actual record [{}] (null) does not exist at rule ({})",
								actualIndex - 1,
								ruleName);
					} else if (workExpectedRecords.get(actualIndex - 1).fact != null) {
						Object expect = workExpectedRecords.get(actualIndex - 1).fact;
						logger.debug("** expeced record ({}) {}[{}] of the actual record [{}] is null at rule ({})",
								expect, expect.getClass().getSimpleName(),
								actualIndex - 1,
								actualIndex - 1,
								ruleName);
					}
				} else {
					logger.warn("** Skipping a null record in the actual records");
				}
			}
		}
	}

	private void checkAttributes(String ruleName, Timing timing, Object actual, ExpectedRecord expect) {
		int id = System.identityHashCode(actual);
		for (Map.Entry<String, Object> entry : expect.map.entrySet()) {
			// filter out meta attributes
			if (!Constants.parentRowKey.equals(entry.getKey()) &&
					!Constants.typeAttributeStr.equals(entry.getKey()) &&
					!testSkipMap.get(entry.getKey())) {
				String key = entry.getKey();
				Object expectedValueString = entry.getValue();
				Object actualValue = null;
				Object expectedValue = null;
				boolean toBeChecked = false;
				actualValue = getProperty(actual, key);
				if (!StringUtils.isEmpty((String)expectedValueString)) {
					toBeChecked = true;
					if (Constants.nullCheckStr.equals(expectedValueString)) {
						expectedValue = null;
					} else if (Constants.emptyCheckStr.equals(expectedValueString)) {
						expectedValue = "";
					} else  {
						expectedValue = getProperty(expect.fact, key);
					}
				}
				String attrName = key;
				String className = actual.getClass().getSimpleName();
				String changeStr = null;
				if (toBeChecked) {
					if (actual instanceof MapEntry) {
						if (attrName.startsWith(Constants.keyAttributeStr)) {
							Object o = ((MapEntry)actual).key;
							className = o.getClass().getSimpleName();
							id = System.identityHashCode(o);
							attrName = "";
						} else {
							Object o = ((MapEntry)actual).value;
							className = o.getClass().getSimpleName();
							id = System.identityHashCode(o);
							if (attrName.equals(Constants.valueAttributeStr) &&
									isImmutable(o.getClass())) {
								attrName = "";
							}
						}
					} else {
						if (attrName.equals(Constants.valueAttributeStr) &&
								isImmutable(actual.getClass())) {
							attrName = "";
						}
					}

					if (timing == Timing.BEFORE) {
						registerValue(actual, attrName, actualValue);
					} else if (timing == Timing.AFTER) {
						changeStr = getChangeString(actual, attrName, actualValue);
						printAnAttribute(ruleName, className, id, attrName, changeStr, actualValue, expectedValue);
					} else { // (timing == Timing.INSERT)
						registerValue(actual, attrName, Constants.unknownValueLavel);
						changeStr = getChangeString(actual, attrName, actualValue);
						printAnAttribute(ruleName, className, id, attrName, changeStr, actualValue, expectedValue);
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private String getChangeString(Object obj, String attrName, Object currentValue) {
		Map<String, Object> valuesMap = previousValues.get(obj);
		if (valuesMap == null) {
			Object map = null;
			// as the hash value of the obj was changed, so need to search
			for (Map.Entry<Object, Map<String, Object>> entry : previousValues.entrySet()) {
				if (entry.getKey() == obj) {
					map = (Object)entry.getValue();
				}
			}
			valuesMap = (Map<String, Object>) map;
		}
		if (valuesMap == null) {
			// no previous value
			return null;
		}
		Object pre = valuesMap.remove(attrName);
		if (pre == null) {
			// no previous value
			return null;
		}
		boolean updated;
		if (currentValue == null) {
			updated = ! Constants.registeredNullValue.equals(pre);
		} else {
			updated = ! currentValue.equals(pre);
		}
		if (updated) {
			return "(" + pre + ") => (" + currentValue + ")";			
		} else {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private void registerValue(Object obj, String attrName, Object actualValue) {
		Map<String, Object> valuesMap = previousValues.get(obj);
		if (valuesMap == null) {
			Object map = null;
			// as the hash value of the obj was changed, so need to search
			for (Map.Entry<Object, Map<String, Object>> entry : previousValues.entrySet()) {
				if (entry.getKey() == obj) {
					map = (Object)entry.getValue();
				}
			}
			valuesMap = (Map<String, Object>) map;
			if (valuesMap == null) {
				valuesMap = new LinkedHashMap<String, Object>();
				previousValues.put(obj, valuesMap);
			}
		}
		if (actualValue == null) {
			actualValue = Constants.registeredNullValue;
		}
		valuesMap.put(attrName, actualValue);
	}

	private void printAnAttribute(String ruleName, String className, int id, String attrName,
			String changeStr, Object actualValue, Object expectedValue) {
		boolean asExpected = false;
		if (actualValue == null) {
			if (expectedValue == null)
				asExpected = true;
		} else {
			asExpected = actualValue.equals(expectedValue);
		}
		if (changeStr != null) {
			logger.debug("** {}@{}{} was CHANGED [{}]{} at rule ({})",
					className, id,
					(StringUtils.isEmpty(attrName) ? "" : "#" + attrName),
					changeStr,
					(asExpected ? " as Expected" : ""),
					ruleName);
		} else {
			logger.debug("** {}@{}{} was NOT changed ({}){} at rule ({})",
					className, id,
					(StringUtils.isEmpty(attrName) ? "" : "#" + attrName),
					actualValue,
					(asExpected ? " as Expected" : ""),
					ruleName);
		}
		if (!asExpected) {
			logger.debug(" * {}@{}#{} is Expected ({}) But is ({})",
					className, id,
					(StringUtils.isEmpty(attrName) ? "" : "#" + attrName),
					expectedValue,
					actualValue);
		}
	}
	
	/**
	 * get property - attribute / field - value of target object by a string property name.
	 * @param obj target object
	 * @param key property name
	 * @return property value
	 */
	public static Object getProperty(Object obj, String key) {
		if (obj == null) {
			fail("fail at getProperty(" + null + ", " + key + ")");
			throw new NullPointerException();
		}
		if (obj.getClass().equals(MapEntry.class)) {
			if (key.startsWith(Constants.keyAttributeStr)) {
				return ((MapEntry)obj).key;
			}
			obj = ((MapEntry)obj).value;
		}
		if (isImmutable(obj.getClass()) &&
				Constants.valueAttributeStr.equals(key)) {
			return obj;
		}
			
		try {
			return PropertyUtils.getProperty(obj, key);
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			e.printStackTrace();
			fail("fail at getProperty(" + obj + ", " + key + ")");
		}
		return null;
	}

	/**
	 * class immutable check
	 * @param clazz
	 * @return true if the parameter class is immutable.
	 */
	public static boolean isImmutable(Class<?> clazz) {
		if (clazz != null) {
			if (clazz.equals(String.class) ||
					Number.class.isAssignableFrom(clazz) ||
					Date.class.isAssignableFrom(clazz) ||
					clazz == Object.class) {
				return true;
			}
		}
		return false;
	}


	private static class SubListener2 extends DefaultRuleRuntimeEventListener {

		private RuleFactWatcher parent;
		
		private SubListener2(RuleFactWatcher parent) {
			this.parent = parent;
		}
		
		@Override
		public void objectInserted(ObjectInsertedEvent event) {
			Rule rule = event.getRule();
			parent.printWatchedAttributes(rule != null ? rule.getName() : "KieRuntime#insert() API", Arrays.asList(event.getObject()), Timing.INSERT);
		}
	}
	
	private Class<?> insertedHeaderClass = null;
	private BiFunction<Object, Object[], Object> accessFunctionFromHeader = null;
	private Object[] accessArgs = null;
	
	/**
	 * RuleFactWatcher watches inserted FACTs in default.<BR>
	 * In case to watch objects which are not inserted,<BR>
	 * the access function from the inserted fact to the target object(s) and the access arguments.
	 * 
	 * @param insertedHeaderClass
	 * @param accessFunctionFromHeader
	 * @param accessArgs
	 */
	public RuleFactWatcher setHeaderFact(Class<?> insertedHeaderClass, BiFunction<Object, Object[], Object> accessFunctionFromHeader, Object[] accessArgs) {
		this.insertedHeaderClass = insertedHeaderClass;
		this.accessFunctionFromHeader = accessFunctionFromHeader;
		this.accessArgs = accessArgs;
		return this;
	}

	private boolean isChild = false;
	private String parentRow = null;
	private Map<String, RuleFactWatcher> childWatcherMap =
			new LinkedHashMap<String, RuleFactWatcher>();
	
	/**
	 * @return true if this watcher is child watcher for an internal field.
	 */
	public boolean isChild() {
		return isChild;
	}
	
	/**
	 * register a child watcher for an internal field.
	 * @param fieldName
	 * @param watcher
	 */
	public void registerChildWatcher(String fieldName, RuleFactWatcher watcher) {
		watcher.isChild = true;
		childWatcherMap.put(fieldName, watcher);
	}
	
	public static class ConstantValues {
		private ConstantValues() {
		}
		/**
		 * key of parent number
		 */
		public String parentRowKey = "parent#";
		/**
		 * string to represent null value
		 */
		public String registeredNullValue = "null";
		/**
		 * string to represent unknown value
		 */
		public String unknownValueLavel = "unknown";
		/**
		 * null check string
		 */
		public String nullCheckStr = "[null]";
		/**
		 * empty check string
		 */
		public String emptyCheckStr = "[empty]";
		/**
		 * value attribute label for primitive type
		 */
		public String valueAttributeStr = "value";
		/**
		 * key attribute label for Map type
		 */
		public String keyAttributeStr = "key#";
		/**
		 * type attribute label for meta Object field
		 */
		public String typeAttributeStr = "type#";
	}
}
