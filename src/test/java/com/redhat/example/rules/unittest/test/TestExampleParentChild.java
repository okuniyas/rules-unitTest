package com.redhat.example.rules.unittest.test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import org.drools.core.util.StringUtils;
import org.hamcrest.core.IsNull;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.command.Command;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.command.CommandFactory;

import com.redhat.example.fact.ExampleFactChild;
import com.redhat.example.fact.ExampleFactParent;
import com.redhat.example.fact.ExampleValidationResult;
import com.redhat.example.json.JsonUtils;
import com.redhat.example.rules.unittest.CsvTestHelper;
import com.redhat.example.rules.unittest.RuleFactWatcher;
import com.redhat.example.rules.unittest.RuleFactWatchers;
import com.redhat.example.rules.unittest.TestCaseBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestExampleParentChild extends TestCaseBase {
	private static Logger logger = LoggerFactory.getLogger(TestExampleParentChild.class);
	
	@BeforeClass
	public static void init() { 
		ruleFlowName = null;
	}
	
	/**
	 * current version (2.X) style test code.
	 */
	@Test
	public void test_with_csv_v2() {
		Map<String, Object> parameterMap = new LinkedHashMap<String, Object>();
		// 入力データの準備
		Map<String, List<?>> inputMap =
				CsvTestHelper.loadInputMap("testdata/parentChild2/Files_1.csv");
		@SuppressWarnings("unchecked")
		List<ExampleFactParent> parentList =
		(List<ExampleFactParent>) inputMap.get("parent");
						
		parameterMap.put("ExampleFactParent", parentList);

		// 結果(個数不明)を入れるための空のリスト
		LinkedList<ExampleValidationResult> results =
				new LinkedList<ExampleValidationResult>();
		parameterMap.put("ExampleValidationResult", results);
						
		// RuleFactWatchers の作成
		RuleFactWatchers ruleFactWatchers =
				CsvTestHelper.createRuleFactWatchers("testdata/parentChild2/Files_1.csv");
			
		// KieSession (pooling から取得)
		KieServices ks = KieServices.Factory.get();
		boolean stateful = false;
		// ルール実行
		if (stateful) {
			KieSession kieSession = ks.getKieClasspathContainer().newKieSession();
			initSession(kieSession);
			// RuleFactWatcher の設定
			ruleFactWatchers.setRuntime(kieSession);
			if (!StringUtils.isEmpty(ruleFlowName))
				kieSession.startProcess(ruleFlowName);
			kieSession.insert(parameterMap);
			kieSession.fireAllRules();
			// RuleFactWatcher 後処理
			ruleFactWatchers.resetRuntime();
			kieSession.dispose();
		} else {
			StatelessKieSession kieSession = ks.getKieClasspathContainer().newStatelessKieSession();
			initSession(kieSession);
			// RuleFactWatcher の設定
			ruleFactWatchers.setRuntime(kieSession);
			List<Command<?>> cmds = new ArrayList<Command<?>>();
			if (!StringUtils.isEmpty(ruleFlowName))
				cmds.add( CommandFactory.newStartProcess(ruleFlowName));
			cmds.add( CommandFactory.newInsert(parameterMap));
			kieSession.execute( CommandFactory.newBatchExecution( cmds ));
		}

		// 期待値との一致チェック（一括、配下の内部オブジェクトも含む）
		CsvTestHelper.assertExpectCSVs(results,
				"testdata/parentChild2/Files_1.csv",
				"validationResult");
		CsvTestHelper.assertExpectCSVs(parentList,
				"testdata/parentChild2/Files_1.csv",
				"parent");
	}
	
	@Test
	public void test_null_value_ignore() {
		// 指定がない属性を上書きしない
		List<ExampleFactChild> childs = CsvTestHelper.loadCsv(
				"testdata/parentChild2/in_ChildFact_1.csv", ExampleFactChild.class, true);
		// 1がロードされていること
		assertThat(childs.get(0).getAttrBigDecimal(), is(BigDecimal.ONE));
		assertThat(childs.get(1).getAttrBigDecimal(), is(BigDecimal.ONE));
		// 指定がないため、初期値(0)のままであること
		assertThat(childs.get(2).getAttrBigDecimal(), is(BigDecimal.ZERO));
	}

	@Test
	public void test_Immutable_List() {
		Map<String, List<?>> inputMap =
				CsvTestHelper.loadInputMap("testdata/immutableList/Files_1.csv");
		@SuppressWarnings("unchecked")
		List<ExampleFactParent> parentList =
		(List<ExampleFactParent>) inputMap.get("parent");

		// List<String>
		assertThat(parentList.get(0).getChildList().get(0).getStrList().get(0), new IsNull<String>());
		assertThat(parentList.get(1).getChildList().get(0).getStrList().get(0), is(""));
		assertThat(parentList.get(1).getChildList().get(1).getStrList().get(0), is("p2c2s1"));
		assertThat(parentList.get(1).getChildList().get(1).getStrList().get(1), is("p2c2s2"));

		// List<BigDecimal>
		assertThat(parentList.get(0).getChildList().get(0).getBdList().get(0), is(new BigDecimal("111")));
		assertThat(parentList.get(1).getChildList().get(0).getBdList().get(0), is(new BigDecimal("211")));
		assertThat(parentList.get(1).getChildList().get(1).getBdList().get(0), new IsNull<BigDecimal>());
		assertThat(parentList.get(1).getChildList().get(1).getBdList().get(1), is(new BigDecimal("222")));

		// List<Date>
		Calendar cal = Calendar.getInstance();
		cal.clear();
		cal.set(2016, 6 - 1, 30);
		assertThat(parentList.get(0).getChildList().get(0).getDateList().get(0), is(cal.getTime()));
		cal.add(Calendar.DAY_OF_MONTH, 1);
		assertThat(parentList.get(1).getChildList().get(0).getDateList().get(0), is(cal.getTime()));
		assertThat(parentList.get(1).getChildList().get(1).getDateList().get(0), new IsNull<Date>());
		cal.add(Calendar.DAY_OF_MONTH, 1);
		assertThat(parentList.get(1).getChildList().get(1).getDateList().get(1), is(cal.getTime()));

		// List<Integer>
		assertThat(parentList.get(0).getChildList().get(0).getIntList().get(0), is(new Integer("111")));
		assertThat(parentList.get(1).getChildList().get(0).getIntList().get(0), is(new Integer("211")));
		assertThat(parentList.get(1).getChildList().get(1).getIntList().get(0), new IsNull<Integer>());
		assertThat(parentList.get(1).getChildList().get(1).getIntList().get(1), is(new Integer("222")));

		// List<Double>
		assertThat(parentList.get(0).getChildList().get(0).getDoubleList().get(0), is(new Double("111")));
		assertThat(parentList.get(1).getChildList().get(0).getDoubleList().get(0), is(new Double("211")));
		assertThat(parentList.get(1).getChildList().get(1).getDoubleList().get(0), new IsNull<Double>());
		assertThat(parentList.get(1).getChildList().get(1).getDoubleList().get(1), is(new Double("222")));

		// 
		assertThat(parentList.get(0).getChildList().get(0).getAttrBigDecimal(), notNullValue());
		assertThat(parentList.get(1).getChildList().get(0).getAttrBigDecimal(), notNullValue());
		assertThat(parentList.get(1).getChildList().get(1).getAttrBigDecimal(), notNullValue());

		Map<String, Object> parameterMap = new LinkedHashMap<String, Object>();						
		parameterMap.put("ExampleFactParent", parentList);
		
		// KieSession (pooling から取得)
		KieServices ks = KieServices.Factory.get();
		StatelessKieSession kieSession = ks.getKieClasspathContainer().newStatelessKieSession();
		initSession(kieSession);
		// RuleFactWatcher の設定
		CsvTestHelper.createRuleFactWatchers("testdata/immutableList/Files_1.csv").setRuntime(kieSession);
		List<Command<?>> cmds = new ArrayList<Command<?>>();
		if (!StringUtils.isEmpty(ruleFlowName))
			cmds.add( CommandFactory.newStartProcess(ruleFlowName));
		cmds.add( CommandFactory.newInsert(parameterMap));
		kieSession.execute( CommandFactory.newBatchExecution( cmds ));
		
		CsvTestHelper.assertExpectCSVs(parentList, "testdata/immutableList/Files_1.csv",
				"parent");

		@SuppressWarnings("unchecked")
		List<ExampleValidationResult> validationResultList =
				(List<ExampleValidationResult>)parameterMap.get("ExampleValidationResult");
		CsvTestHelper.assertExpectCSVs(validationResultList, "testdata/immutableList/Files_1.csv",
				"validationResult");
	}
	
	@Test
	public void test_Map() {
		Map<String, List<?>> inputMap =
				CsvTestHelper.loadInputMap("testdata/map/Files_1.csv");
		@SuppressWarnings("unchecked")
		List<ExampleFactParent> parentList =
		(List<ExampleFactParent>) inputMap.get("parent");

		// List<String>
		assertThat(parentList.get(0).getChildList().get(0).getMapAttr().get(111), is("str111"));
		assertThat(parentList.get(1).getChildList().get(0).getMapAttr().get(222), is("str222"));
		assertThat(parentList.get(1).getChildList().get(0).getMapAttr().get(333), is("str333"));
		assertThat(parentList.get(1).getChildList().get(1).getMapAttr().size(), is(0));

		Map<String, Object> parameterMap = new LinkedHashMap<String, Object>();						
		parameterMap.put("ExampleFactParent", parentList);
		
		// KieSession (pooling から取得)
		KieServices ks = KieServices.Factory.get();
		StatelessKieSession kieSession = ks.getKieClasspathContainer().newStatelessKieSession();
		initSession(kieSession);
		// RuleFactWatcher の設定
		CsvTestHelper.createRuleFactWatchers("testdata/map/Files_1.csv").setRuntime(kieSession);
		List<Command<?>> cmds = new ArrayList<Command<?>>();
		if (!StringUtils.isEmpty(ruleFlowName))
			cmds.add( CommandFactory.newStartProcess(ruleFlowName));
		cmds.add( CommandFactory.newInsert(parameterMap));
		kieSession.execute( CommandFactory.newBatchExecution( cmds ));
		
		CsvTestHelper.assertExpectCSVs(parentList, "testdata/map/Files_1.csv",
				"parent");

		@SuppressWarnings("unchecked")
		List<ExampleValidationResult> validationResultList =
				(List<ExampleValidationResult>)parameterMap.get("ExampleValidationResult");
		CsvTestHelper.assertExpectCSVs(validationResultList, "testdata/map/Files_1.csv",
				"validationResult");
	}

	@Test
	public void test_checkByIndex() {
		Map<String, List<?>> inputMap =
				CsvTestHelper.loadInputMap("testdata/checkByIndex/Files_1.csv");
		@SuppressWarnings("unchecked")
		List<ExampleFactParent> parentList =
		(List<ExampleFactParent>) inputMap.get("parent");

		// List<String>
		assertThat(parentList.get(0).getChildList().get(0).getStrList().get(0), new IsNull<String>());
		assertThat(parentList.get(1).getChildList().get(0).getStrList().get(0), is(""));
		assertThat(parentList.get(1).getChildList().get(1).getStrList().get(0), is("p2c2s1"));
		assertThat(parentList.get(1).getChildList().get(1).getStrList().get(1), is("p2c2s2"));

		// List<BigDecimal>
		assertThat(parentList.get(0).getChildList().get(0).getBdList().get(0), is(new BigDecimal("111")));
		assertThat(parentList.get(1).getChildList().get(0).getBdList().get(0), is(new BigDecimal("211")));
		assertThat(parentList.get(1).getChildList().get(1).getBdList().get(0), new IsNull<BigDecimal>());
		assertThat(parentList.get(1).getChildList().get(1).getBdList().get(1), is(new BigDecimal("222")));

		// List<Date>
		Calendar cal = Calendar.getInstance();
		cal.clear();
		cal.set(2016, 6 - 1, 30);
		assertThat(parentList.get(0).getChildList().get(0).getDateList().get(0), is(cal.getTime()));
		cal.add(Calendar.DAY_OF_MONTH, 1);
		assertThat(parentList.get(1).getChildList().get(0).getDateList().get(0), is(cal.getTime()));
		assertThat(parentList.get(1).getChildList().get(1).getDateList().get(0), new IsNull<Date>());
		cal.add(Calendar.DAY_OF_MONTH, 1);
		assertThat(parentList.get(1).getChildList().get(1).getDateList().get(1), is(cal.getTime()));

		// List<Integer>
		assertThat(parentList.get(0).getChildList().get(0).getIntList().get(0), is(new Integer("111")));
		assertThat(parentList.get(1).getChildList().get(0).getIntList().get(0), is(new Integer("211")));
		assertThat(parentList.get(1).getChildList().get(1).getIntList().get(0), new IsNull<Integer>());
		assertThat(parentList.get(1).getChildList().get(1).getIntList().get(1), is(new Integer("222")));

		// List<Double>
		assertThat(parentList.get(0).getChildList().get(0).getDoubleList().get(0), is(new Double("111")));
		assertThat(parentList.get(1).getChildList().get(0).getDoubleList().get(0), is(new Double("211")));
		assertThat(parentList.get(1).getChildList().get(1).getDoubleList().get(0), new IsNull<Double>());
		assertThat(parentList.get(1).getChildList().get(1).getDoubleList().get(1), is(new Double("222")));

		// 
		assertThat(parentList.get(0).getChildList().get(0).getAttrBigDecimal(), notNullValue());
		assertThat(parentList.get(1).getChildList().get(0).getAttrBigDecimal(), notNullValue());
		assertThat(parentList.get(1).getChildList().get(1).getAttrBigDecimal(), notNullValue());

		Map<String, Object> parameterMap = new LinkedHashMap<String, Object>();						
		parameterMap.put("ExampleFactParent", parentList);
		
		// KieSession (pooling から取得)
		KieServices ks = KieServices.Factory.get();
		StatelessKieSession kieSession = ks.getKieClasspathContainer().newStatelessKieSession();
		initSession(kieSession);
		// RuleFactWatcher の設定
		CsvTestHelper.createRuleFactWatchers("testdata/checkByIndex/Files_1.csv").setRuntime(kieSession);
		List<Command<?>> cmds = new ArrayList<Command<?>>();
		if (!StringUtils.isEmpty(ruleFlowName))
			cmds.add( CommandFactory.newStartProcess(ruleFlowName));
		cmds.add( CommandFactory.newInsert(parameterMap));
		kieSession.execute( CommandFactory.newBatchExecution( cmds ));
		
		CsvTestHelper.assertExpectCSVs(parentList, "testdata/checkByIndex/Files_1.csv",
				"parent");

		@SuppressWarnings("unchecked")
		List<ExampleValidationResult> validationResultList =
				(List<ExampleValidationResult>)parameterMap.get("ExampleValidationResult");
		CsvTestHelper.assertExpectCSVs(validationResultList, "testdata/checkByIndex/Files_1.csv",
				"validationResult");
	}
	
    @Test
    public void test_with_kadai3() {
        Map<String, Object> parameterMap = new LinkedHashMap<String, Object>();
        // 入力データの準備
        Map<String, List<?>> inputMap =
                CsvTestHelper.loadInputMap("testdata/kadai3/Files_1.csv");
        @SuppressWarnings("unchecked")
        List<ExampleFactParent> parentList =
        (List<ExampleFactParent>) inputMap.get("parent");
                        
        parameterMap.put("ExampleFactParent", parentList);

        // 結果(個数不明)を入れるための空のリスト
        LinkedList<ExampleValidationResult> results =
                new LinkedList<ExampleValidationResult>();
        parameterMap.put("ExampleValidationResult", results);
                        
        // RuleFactWatchers の作成
        RuleFactWatchers ruleFactWatchers =
                CsvTestHelper.createRuleFactWatchers("testdata/kadai3/Files_1.csv");
            
        // KieSession (pooling から取得)
        KieServices ks = KieServices.Factory.get();
        boolean stateful = false;
        // ルール実行
        if (stateful) {
            KieSession kieSession = ks.getKieClasspathContainer().newKieSession();
            initSession(kieSession);
            // RuleFactWatcher の設定
            ruleFactWatchers.setRuntime(kieSession);
            if (!StringUtils.isEmpty(ruleFlowName))
                kieSession.startProcess(ruleFlowName);
            kieSession.insert(parameterMap);
            kieSession.fireAllRules();
            // RuleFactWatcher 後処理
            ruleFactWatchers.resetRuntime();
            kieSession.dispose();
        } else {
            StatelessKieSession kieSession = ks.getKieClasspathContainer().newStatelessKieSession();
            initSession(kieSession);
            // RuleFactWatcher の設定
            ruleFactWatchers.setRuntime(kieSession);
            List<Command<?>> cmds = new ArrayList<Command<?>>();
            if (!StringUtils.isEmpty(ruleFlowName))
                cmds.add( CommandFactory.newStartProcess(ruleFlowName));
            cmds.add( CommandFactory.newInsert(parameterMap));
            kieSession.execute( CommandFactory.newBatchExecution( cmds ));
        }

        // 期待値との一致チェック（一括、配下の内部オブジェクトも含む）
        CsvTestHelper.assertExpectCSVs(parentList,
                "testdata/kadai3/Files_1.csv",
                "parent");
    }

	@SuppressWarnings("unchecked")
	@Test
	public void json_serialize_test() {
		// 入力データの準備
		Map<String, Object> factMap = new LinkedHashMap<String, Object>();
		ExampleFactParent p1 = new ExampleFactParent();
		p1.setId("p1");
		p1.setName("Parent 1");
		ExampleFactParent p2 = new ExampleFactParent();
		p2.setId("p2");
		p2.setName("Parent 2");
		
		List<ExampleFactParent> parents = new LinkedList<ExampleFactParent>();
		parents.add(p1);
		parents.add(p2);
		
		ExampleFactChild child1_1 = new ExampleFactChild();
		child1_1.setParent(p1);
		child1_1.setId("p1c1");
		child1_1.setName("Child 1");
		child1_1.setAttrBigDecimal(BigDecimal.ONE);
		p1.addChild(child1_1);

		ExampleFactChild child2_1 = new ExampleFactChild();
		child2_1.setParent(p2);
		child2_1.setId("p2c1");
		child2_1.setName("Child 1");
		p2.addChild(child2_1);

		ExampleFactChild child2_2 = new ExampleFactChild();
		child2_2.setParent(p2);
		child2_2.setId("p2c2");
		child2_2.setName("Child 2");
		p2.addChild(child2_2);

		List<ExampleFactChild> children = new LinkedList<ExampleFactChild>();
		children.add(child1_1);
		children.add(child2_1);
		children.add(child2_2);
		
		factMap.put("ExampleFactChild", children);
		factMap.put("ExampleFactParent", parents);

		KieSession kieSession = null;

		try {

			// 受信するJson文字列
			String factMapJson = JsonUtils.fact2Json(factMap);
			// 受信したJson文字列からfactMapにデシリアライズ
			LinkedHashMap<String, Object> factMapRestored =
					(LinkedHashMap<String, Object>) JsonUtils.json2Fact(factMapJson, LinkedHashMap.class);
			
			// デシリアライズのチェック
			List<ExampleFactParent> parentsRestored = (List<ExampleFactParent>) factMapRestored.get("ExampleFactParent");
			// 親の数
			assertThat(parentsRestored.toArray(), is(arrayWithSize(parents.size())));
			// 親の同一性
			assertThat(parentsRestored.get(0), is(p1));
			assertThat(parentsRestored.get(1), is(p2));
			// 子の同一性
			assertThat(parentsRestored.get(0).getChildList().toArray(), is(arrayContaining(p1.getChildList().toArray())));
			assertThat(parentsRestored.get(1).getChildList().toArray(), is(arrayContaining(p2.getChildList().toArray())));
			// 「親」と「子の親属性」の一致性確認（同じオブジェクトが複数生成されていないか）
			assertThat(parentsRestored.get(1), is(sameInstance(parentsRestored.get(1).getChildList().get(0).getParent())));
			assertThat(parentsRestored.get(1), is(sameInstance(parentsRestored.get(1).getChildList().get(1).getParent())));

			// デシリアライズしたfactMapでルールエンジン実行
			KieServices ks = KieServices.Factory.get();
			kieSession = ks.getKieClasspathContainer().newKieSession();
			initSession(kieSession);
			if (! StringUtils.isEmpty(ruleFlowName))
				kieSession.startProcess(ruleFlowName);
			kieSession.insert(factMapRestored);
			kieSession.fireAllRules();

			// 返信するデータの取得
			LinkedList<ExampleValidationResult> results
			= (LinkedList<ExampleValidationResult>) factMapRestored.get("ExampleValidationResult");
			logger.info("results = {}", results);
			// ルールでは、ExampleFactChild のうち、attrBigDecimal がゼロのものについて、ExampleValidationResult を生成。２個存在する
			assertThat(results.size(), is(2));
			
			// 返信するJson文字列の作成
			Map<String, Object> resultMap = new LinkedHashMap<String, Object>();
			resultMap.put("ExampleValidationResult", results);
			String resultMapJson = JsonUtils.fact2Json(resultMap);
			logger.info("resultMapJson = {}", resultMapJson);
			
			// 受信側で、返信したJsonのデシリアライズ
			LinkedHashMap<String, Object> resultMapRestored
			= JsonUtils.json2Fact(resultMapJson, LinkedHashMap.class);
			LinkedList<ExampleValidationResult> resultsRestored
			= (LinkedList<ExampleValidationResult>) resultMapRestored.get("ExampleValidationResult"); 
			logger.info("resultsRestored = {}", resultsRestored);
			
			// デシリアライズのチェック
			assertThat(resultsRestored.toArray(), is(arrayContaining(results.toArray())));
			
		} catch (Exception e) {
			e.printStackTrace();
			if (kieSession != null)
				kieSession.dispose();
			fail();
		}
	}
	
	/**
	 * old version (1.X) style test code.
	 */
	@Test
	public void test_with_csv_v1() {
		Map<String, Object> parameterMap = new LinkedHashMap<String, Object>();
		// 入力データの準備
		List<ExampleFactParent> parentList =
				CsvTestHelper.loadCsv("testdata/parentChild/in_ExampleFactParent_1.csv", ExampleFactParent.class, true);
		List<ExampleFactChild> childList_1 =
				CsvTestHelper.loadCsv("testdata/parentChild/in_ExampleFactChild_1-1.csv", ExampleFactChild.class, true);
		List<ExampleFactChild> childList_2 =
				CsvTestHelper.loadCsv("testdata/parentChild/in_ExampleFactChild_1-2.csv", ExampleFactChild.class, true);
			
		// 親子関係の設定
		final ExampleFactParent parent1 = parentList.get(0);
		for (ExampleFactChild child : childList_1) {
			child.setParent(parent1);
			parent1.addChild(child);
		}
		final ExampleFactParent parent2  = parentList.get(1);
		for (ExampleFactChild child : childList_2) {
			child.setParent(parent2);
			parent2.addChild(child);
		}
						
		//parameterMap.put("ExampleFactChild", childList);
		parameterMap.put("ExampleFactParent", parentList);

		// 結果(個数不明)を入れるための空のリスト
		LinkedList<ExampleValidationResult> results =
				new LinkedList<ExampleValidationResult>();
		parameterMap.put("ExampleValidationResult", results);
			
		RuleFactWatcher ruleFactWatcher_ExampleValidationResult =
				CsvTestHelper.createRuleFactWatcher("testdata/parentChild/ex_ExampleValidationResult_1.csv",
						ExampleValidationResult.class);
		RuleFactWatcher ruleFactWatcher_ExampleFactChild_1 =
				CsvTestHelper.createRuleFactWatcher("testdata/parentChild/ex_ExampleFactChild_1-1.csv",
						ExampleFactChild.class);
		RuleFactWatcher ruleFactWatcher_ExampleFactChild_2 =
				CsvTestHelper.createRuleFactWatcher("testdata/parentChild/ex_ExampleFactChild_1-2.csv",
						ExampleFactChild.class);
			
		// Watch する ExampleFactChild を insert しない場合の設定。
		// insertするFACTクラスと、そのFACTクラスからのアクセス関数を設定する
		BiFunction<Object, Object[], Object> accessFunctionFromHeader = new BiFunction<Object, Object[], Object>() {
			@Override
			public Object apply(Object t, Object[] args) {
				if (t instanceof ExampleFactParent) {
					ExampleFactParent parent = (ExampleFactParent)t;
					return parent.getChildList();
				}
				return null;
			}
		};
		ruleFactWatcher_ExampleFactChild_1.setHeaderFact(ExampleFactParent.class, accessFunctionFromHeader, null);
		ruleFactWatcher_ExampleFactChild_2.setHeaderFact(ExampleFactParent.class, accessFunctionFromHeader, null);
			
		// KieSession (pooling から取得)
		KieServices ks = KieServices.Factory.get();
		boolean stateful = false;
		// ルール実行
		if (stateful) {
			KieSession kieSession = ks.getKieClasspathContainer().newKieSession();
			initSession(kieSession);
			// RuleFactWatcher の設定
			ruleFactWatcher_ExampleValidationResult.setRuntime(kieSession);
			ruleFactWatcher_ExampleFactChild_1.setRuntime(kieSession);
			ruleFactWatcher_ExampleFactChild_2.setRuntime(kieSession);
			if (!StringUtils.isEmpty(ruleFlowName))
				kieSession.startProcess(ruleFlowName);
			kieSession.insert(parameterMap);
			kieSession.fireAllRules();
			// RuleFactWatcher 後処理
			ruleFactWatcher_ExampleValidationResult.resetRuntime();
			ruleFactWatcher_ExampleFactChild_1.resetRuntime();
			ruleFactWatcher_ExampleFactChild_2.resetRuntime();
			kieSession.dispose();
		} else {
			StatelessKieSession kieSession = ks.getKieClasspathContainer().newStatelessKieSession();
			initSession(kieSession);
			// RuleFactWatcher の設定
			ruleFactWatcher_ExampleValidationResult.setRuntime(kieSession);
			ruleFactWatcher_ExampleFactChild_1.setRuntime(kieSession);
			ruleFactWatcher_ExampleFactChild_2.setRuntime(kieSession);
			List<Command<?>> cmds = new ArrayList<Command<?>>();
			if (!StringUtils.isEmpty(ruleFlowName))
				cmds.add( CommandFactory.newStartProcess(ruleFlowName));
			cmds.add( CommandFactory.newInsert(parameterMap));
			kieSession.execute( CommandFactory.newBatchExecution( cmds ));
		}
		
		// 結果リストのダンプ
		CsvTestHelper.writeCsv(results,
				"testdata/parentChild/out_ExampleValidationResult_1.csv");
		CsvTestHelper.writeCsv(childList_1,
				"testdata/parentChild/out_ExampleFactChild_1-1.csv");
		CsvTestHelper.writeCsv(childList_2,
				"testdata/parentChild/out_ExampleFactChild_1-2.csv");
		// 期待値との一致チェック
		CsvTestHelper.assertExpectCSV(results,
				"testdata/parentChild/ex_ExampleValidationResult_1.csv", false);
		CsvTestHelper.assertExpectCSV(childList_1,
				"testdata/parentChild/ex_ExampleFactChild_1-1.csv", false);
		CsvTestHelper.assertExpectCSV(childList_2,
				"testdata/parentChild/ex_ExampleFactChild_1-2.csv", false);
	}
	
}
