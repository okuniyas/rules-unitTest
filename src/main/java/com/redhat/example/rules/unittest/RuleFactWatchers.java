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

import java.util.LinkedHashSet;
import java.util.Set;

import org.kie.api.event.KieRuntimeEventManager;

public class RuleFactWatchers {
	private Set<RuleFactWatcher> watchers =
			new LinkedHashSet<RuleFactWatcher>();
	private KieRuntimeEventManager runtime = null;
	
	public boolean add(RuleFactWatcher watcher) {
		return watchers.add(watcher);
	}
	public boolean remove(RuleFactWatcher watcher) {
		return watchers.remove(watcher);
	}

	/**
	 * set KieRuntime (KieSession) to watch by this RuleFactWatchers.<BR>
	 * <BR>
	 * As RuleFactWatcher records previous values of specified facts' specified attributes,
	 * a RuleFactWatcher can only be used for single runtime.
	 * @param runtime
	 */
	public RuleFactWatchers setRuntime(KieRuntimeEventManager runtime) {
		if (this.runtime != null && this.runtime != runtime) {
			for (RuleFactWatcher watcher : watchers) {
				watcher.resetRuntime();
			}
		}
		if (this.runtime != runtime) {
			this.runtime = runtime;
			if (runtime != null) {
				for (RuleFactWatcher watcher : watchers) {
					watcher.setRuntime(runtime);
				}
			}
		}
		return this;
	}
	
	/**
	 * reset KieRuntime (KieSession) to watch by this RuleFactWatchers.<BR>
	 */
	public RuleFactWatchers resetRuntime() {
		return setRuntime(null);
	}
	
}
