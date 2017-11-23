# rules-unitTest
Tool for rules unit test with JBoss BRMS/Drools project.

## Why

With JBoss BRMS/Drools project, this tool supports unit testing of rules such as ...

+ Mapping unit test of rules to JUnit
    - Test class
        - a Test class contains test methods for single ruleflow or single rule package.
        - after all tests, the Test class produces *Test Coverage of Rules* for the ruleflow or the rule package.
    - Test methods
        - a Test method do test the entire execution of the ruleflow, OR,
        - a Test method do test up to specified ruleflow-group in the ruleflow and *skip execution after the ruleflow-group*.
+ Test Coverage of Rules (`RuleCoverageLogger`)
    - Collecting a break down structure of {package} -> {ruleflow} -> {ruleflow-group} -> {rule}
    - Reporting test coverage by ruleflow (and every ruleflow-group) or by entire package.
    - Reporting rules which were not covered by the unit tests.
+ Use of CSV files to specify the input data, the expected data and the output dump of the facts. (`CsvTestHelper`)
    - a definition file (*.def) contains column names of CSV file for specific FACT class.
        - for example; to test about `FactOne.class`, we need to prepare `FactOne.def` then
        - prepare `in_FactOne.csv` (an input data) based on the columns defined in the `FactOne.def` and
        - prepare `ex_FactOne.csv` (an expected data) based on the columns defined in the `FactOne.def`
    - then you can load FACTS from the CSV for the input data and for the expected data by the simple API.
+ Fact Watcher (`RuleFactWatchers`)
    - output log about changes of the specific attributes of the specific facts.
+ An Excel sheet template for test cases and An Excel Macro which generates CSV files from that sheet.


With this tool, we don't need to use debugger for rules<BR>
as focused attributes are tracked and easy to trace what happened in the rules.

## How to use

### Maven

Add a dependency to `rules-unitTest` in the test scope of your target rules project.


```
    <dependency>
        <groupId>com.redhat.example</groupId>
        <artifactId>rules-unitTest</artifactId>
        <version>3.2.0</version>
        <scope>test</scope>
    </dependency>
```

### Test Class and Test Coverage

+ Extend `TestClassBase` which has a `RuleCoverageLogger` to report below.
    - the parcentage of the rules which were executed
    - the rules which were not executed
+ Set the name of KieBase to `kieBaseName` or you can set it by a property like `"-Drules.unittest.kiebasename=..."`.
+ Set the name of Ruleflow to `ruleFlowName`, if your target rules uses a ruleflow.


```
    @BeforeClass
    public static void init() {
        kieBaseName = "your KieBase name";
        ruleFlowName = "your ruleflow name to test in this TestCase class";
    }

    // Your Session either KieSession or StatelessKieSession
    StatelessKieSession kSession;

    @Before
    public void initKieSession() {
        // Your initialize of the session.
        kSession = kieBase.newStatelessKieSession();
        
        // initializes required listeners for you.
        initSession(kSession);
    }
```

+ That's all setting. Add your test methods by using below.
    - KieServices `ks`
    - KieContainer `kieContainer`
    - KieCommands `kieCommands`
    - and your session `kSession` declared in your test class.

`RuleCoverageLogger` reports like below as first group-by-group and then entire ruleflow/package.

![An example rule coverage report](https://github.com/okuniyas/rules-unitTest/blob/images/BRMS_UnitTest_RuleCoverageLogger.png)

### CSV files for test input and expect

If you like to prepare test data for the input and the expected results,<BR>
One of options is CSV files for easy to visualize in Spread Sheets.

This tool has a helper for it, named `CsvTestHelper`.

#### CSV file lists

For a test scenario, prepare a file to specify the files list in CSV format as below.

<table border="1">
  <tr>
    <td> type </td>
    <td> file </td>
    <td> clazz </td>
    <td> path </td>
    <td> parentAtr </td>
    <td> options </td>
  </tr>
  <tr>
    <td> IN </td>
    <td> in_ParentFact_1.csv </td>
    <td> com.redhat.example.fact.ExampleFactParent </td>
    <td> parent </td>
    <td/>
    <td/>
  </tr>
  <tr>
    <td> IN </td>
    <td> in_ChildFact_1.csv </td>
    <td> com.redhat.example.fact.ExampleFactChild </td>
    <td> parent.childList </td>
    <td> parent </td>
    <td/>
  </tr>
  <tr>
    <td> IN </td>
    <td> in_Map_1.csv </td>
    <td> java.lang.String </td>
    <td> rent.childList.mapAttr </td>
    <td/>
    <td> keyType=java.lang.Integer </td>
  </tr>
  <tr>
    <td> EX </td>
    <td> ex_ParentFact_1.csv </td>
    <td> com.redhat.example.fact.ExampleFactParent </td>
    <td> parent </td>
    <td/>
    <td/>
  </tr>
  <tr>
    <td> EX </td>
    <td> ex_ChildFact_1.csv </td>
    <td> com.redhat.example.fact.ExampleFactChild </td>
    <td> parent.childList </td>
    <td> parent </td>
    <td/>
  </tr>
  <tr>
    <td> EX </td>
    <td> ex_ValidationResult_1.csv </td>
    <td> com.redhat.example.fact.ExampleValidationResult </td>
    <td> validationResult </td>
    <td/>
    <td/>
  </tr>
  <tr>
    <td> EX </td>
    <td> ex_Map_1.csv </td>
    <td> java.lang.String </td>
    <td> parent.childList.mapAttr </td>
    <td/>
    <td> keyType=java.lang.Integer </td>
  </tr>
</table>


+ `type`
    - `IN` : input data
    - `EX` : expected results data
+ `file`
    - csv file which contains the data records
    - you can include relative path name from this file's folder.
+ `clazz`
    - class name to populate the data
+ `path`
    - to support internal List records, you set this attribute to specify place to populate.
+ `parentAtr`
    - if there is a reverse pointer from a child object to its parent object, specify the attribute name.
+ `options` : specify other options by comma `(,)` separated.
    - keyType=... : specify the type of key, if a record is `Map.Entry`.
    - checkByIndex=true : specify if the actual object order is exactly same as CSV files.
        - it does not search record by primary keys with `testPK=tue` attributes. (see below)


#### Type definition

For each fact class, you need to prepare the definition file `*.def`

<table border="1">
  <tr>
    <td> columnName </td>
    <td> option </td>
    <td> format </td>
    <td> testPK </td>
    <td> testSkip </td>
  </tr>
  <tr>
    <td> age </td>
    <td/>
    <td/>
    <td> Y </td>
    <td> Y </td>
  </tr>
  <tr>
    <td> sex </td>
    <td/>
    <td/>
    <td> Y </td>
    <td> Y </td>
  </tr>
  <tr>
    <td> message </td>
    <td/>
    <td/>
    <td/>
    <td/>
  </tr>
</table>

Above example represents there are three attributes `age, sex and message` and there are same name fields in `ExampleFact.class`.

+ You put only fields which you want to load, output and check into the definition file.
+ You don't need to specify each type of files. This helper uses [Super CSV and its Dozer extension](https://super-csv.github.io/super-csv/dozer.html).
    - For date types, specify `"date"` as the `option` and `"yyyy/MM/dd hh:mm:ss"` as the `format` for example.
    - You can use `"id.key1"` and/or `"foo[0].attr1"` style as `columnName`.
+ `testPK` and `testSkip` are used for expected records.
    - `testPK` is the flag `Y, yes or true` as it's primary key attributes to match an actual fact and an expected record.
        - This is default that the order of the actual and the expect records are *NOT* same.
        - You can change this by `checkByIndex=true` option. (see above)
    - `testSkip` is the flag `Y, yes or true` as it's ignored by `RuleFactWatchers` to trace changes.

#### Loading and Checking with CSV files

`CsvTestHelper` contains helper static methods as below.

+ Input Data Load - loadInputMap(String fileListCsvPath)


```
    Map<String, List<?>> inputMap =
        CsvTestHelper.loadInputMap("testdata/checkByIndex/Files_1.csv");
    @SuppressWarnings("unchecked")
    List<ExampleFactParent> parentList =
        (List<ExampleFactParent>) inputMap.get("parent");
```

+ Check with Expected Results  - assertExpectCSVs(List<?> actuals, String fileListCsvPath, String pathPrefix)


```
    CsvTestHelper.assertExpectCSVs(parentList, "testdata/checkByIndex/Files_1.csv", "parent");
    CsvTestHelper.assertExpectCSVs(validationResultList, "testdata/checkByIndex/Files_1.csv", "validationResult");
```

### Excel sheet template for test cases and An Excel Macro to generate CSV files from that sheet

There is an Excel sheet template and an Excel Macro to generate above all CSV files `*.csv and *.def`.

### Fact Watcher

Fact Watcher helps to trace how expected fields are changed inside the rule engine.

#### Sample Output of Fact Watcher

Two patterns of output.


```
    ** ExampleFact@1957338226#message was NOT changed (null) at rule (Initial_exampleList)
    * ExampleFact@1957338226#message is Expected (man under 30) But is (null)
```


+ `"ExampleFact@1957338226"` means fact's Class name and the identity hash code.<BR>
  You can trace specific fact by searching with this part in the log file.
+ `"ExampleFact@1957338226#message"` means this log is about its `message` attribute,<BR>
  as I specified an expected value to the specific attribute in this specific fact.<BR>
  If you didn't specify the expected value, this line does not appear.
+ `"was NOT changed (null) at rule (Initial_exampleList)"` means the rule "Initial_exampleList" matches to the FACT but<BR>
   the message attribute was not changed and its value was null.
+ `"is Expected (man under 30) But is (null)"` means the expected value of the message is `"man under 30"` but the actual value is `null`.



```
    ** ExampleFact@1957338226#message was CHANGED [(null) => (man under 30)] as Expected at rule (Targeting_13)
```


+ `"was CHANGED [(null) => (man under 30)]"` means the value change from `null` to `"man under 30"`
+ `"as Expected"` means the updated value was same as the expected value.


#### Setting Fact Watcher with CSV files

`CsvTestHelper` contains helper static methods for Fact Watcher as well as below.

+ Create Fact Watchers  - createRuleFactWatchers(String fileListCsvPath)


```
    CsvTestHelper.createRuleFactWatchers("testdata/kadai3/Files_1.csv").setRuntime(kieSession);
```

### To test a ruleflow group-by-group

Sometimes we want to check the intermediate value in the execution of a ruleflow
especially If the value are updated as the ruleflow execution progress.

#### Specify the last ruleflow-group to execute


```
    // "ruleflow_group_01" become the last ruleflow-group to execute.
    RuleflowTestHelper.setSkipAfterRuleGroup(kieSession, "ruleflow_group_01");
    ...
    // execute rules
    ...
    
    // check the intermediate results
    CsvTestHelper.assertExpectCSVs(parentList, "testdata/checkByIndex/Files_1.csv", "parent");
```

## License

[Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.html)

## Author

[okuniyas](https://github.com/okuniyas)

