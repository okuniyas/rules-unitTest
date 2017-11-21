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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.supercsv.cellprocessor.FmtDate;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ParseDate;
import org.supercsv.cellprocessor.ParseInt;
import org.supercsv.cellprocessor.Token;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.io.dozer.CsvDozerBeanReader;
import org.supercsv.io.dozer.CsvDozerBeanWriter;
import org.supercsv.io.dozer.ICsvDozerBeanReader;
import org.supercsv.io.dozer.ICsvDozerBeanWriter;
import org.supercsv.prefs.CsvPreference;

public class CsvTestHelper {
	private static final Logger logger = LoggerFactory.getLogger(CsvTestHelper.class);

	/**
	 * extension name of column definition files
	 */
	private static final String DEFINITION_FILE_EXT = ".def";
	
	/**
	 * default encoding of CSV files
	 */
	public static String FILE_ENCODING = "Shift_JIS";

	/**
	 * attribute type option for Date type
	 */
	public static String ATTR_TYPE_OPTION_DATE = "date";

	/**
	 * attribute type option for NotNull type
	 */
	public static String ATTR_TYPE_OPTION_NOTNULL = "notnull";
	
	/**
	 * the option name to represent the type of key in Map
	 */
	public static String OPTION_KEY_TYPE = "keyType";
	
	/**
	 * the option name to represent check by index, not by keys.
	 */
	public static String OPTION_CHECK_BY_INDEX = "checkByIndex";

	/**
	 * load CSV file for the fact class (clazz)<BR>
	 * based on the definition file (.def) of columns.
	 * @param <T>
	 *
	 * @param fileName
	 *            file name to load
	 * @param clazz
	 *            POJO class to load to
	 * @param ignoreNull
	 *            true -> ignore null value to set into beans
	 * @return a list of the POJO instances
	 */
	public static <T> List<T> loadCsv(String fileName, Class<T> clazz, boolean ignoreNull) {
		List<CsvColumnDef> columnDefList = null;
		try {
			columnDefList = readColumnDef(fileName, clazz.getSimpleName());
		} catch (Exception e) {
			e.printStackTrace();
			fail("fail at readColumnDef() for class: " + clazz.getSimpleName());
		}

		String[] fileMapping = createFileMappingForBean(columnDefList);
		CellProcessor[] proccessors = createCellProcessorArray(columnDefList, true);
		return loadCsv(fileName, clazz, fileMapping, ignoreNull, proccessors);
	}

	private static CellProcessor[] createCellProcessorArray(List<CsvColumnDef> columnDefs, boolean isRead) {
		CellProcessor[] processors = new CellProcessor[columnDefs.size()];		

		for (int i=0; i < columnDefs.size(); i++) {
			CsvColumnDef def = columnDefs.get(i);
			if (def.getOption() == null) {
				CellProcessor p = new Optional();
				p = createNullAndEmptyProcessor(p);
				p = new Optional(p);
				processors[i] = p;
			} else if (ATTR_TYPE_OPTION_DATE.equals(def.getOption().toLowerCase())) {
				String format = def.getFormat();
				if (isRead) {
					CellProcessor p = new ParseDate(format);
					p = createNullAndEmptyProcessor(p);
					p = new Optional(p);
					processors[i] = p;
				} else {
					processors[i] = new Optional(new FmtDate(format));
				}
			} else if (ATTR_TYPE_OPTION_NOTNULL.equals(def.getOption().toLowerCase())) {
				CellProcessor p = new NotNull();
				p = createNullAndEmptyProcessor(p);
				processors[i] = p;
			}
		}
		return processors;
	}

	private static CellProcessor createNullAndEmptyProcessor(CellProcessor p) {
		CellProcessor pro = new Token(RuleFactWatcher.Constants.nullCheckStr, null, p);
		pro = new Token(RuleFactWatcher.Constants.emptyCheckStr, "", pro);
		return pro;
	}

	private static String[] createFileMappingForBean(List<CsvColumnDef> columnDefList) {
		String[] fileMapping = new String[columnDefList.size()];
		for (int i=0; i < columnDefList.size(); i++) {
			CsvColumnDef def = columnDefList.get(i);
			if (def.getColumnName().indexOf("#") == -1) {
				// an attribute of the bean
				fileMapping[i] = def.getColumnName();
			} else {
				// meta column
				fileMapping[i] = null;
			}
		}
		return fileMapping;
	}

	/**
	 * load definition file (.def) of columns.
	 *
	 * @param fileName file name of the target. (only use its folder information)
	 * @param className class name of the POJO.
	 * @return list of column definitions.
	 */
	private static List<CsvColumnDef> readColumnDef(String fileName, String className) {
		String[] fileMappng = { "columnName", "option", "format", "testPK", "testSkip" };
		CellProcessor[] processors = new CellProcessor[] { new NotNull(), new Optional(), new Optional(), new Optional(), new Optional() };
		List<CsvColumnDef> ret = null;
		try {
			String def_file = getDefFile(fileName, className).getCanonicalPath();
			ret = loadCsv(def_file, CsvColumnDef.class, fileMappng, false, processors);
		} catch (IOException e) {
			e.printStackTrace();
			fail("fail at access: " + className + DEFINITION_FILE_EXT);
		}
		return ret;
	}

	private static File getDefFile(String fileName, String className) throws IOException {
		File path = new File(fileName);
		return new File(path.getParentFile(), className + DEFINITION_FILE_EXT);
	}
	
	@SuppressWarnings("unchecked")
	private static <T> List<T> loadCsv(String fileName, Class<T> clazz,
			String[] fieldMapping, boolean ignoreNull, CellProcessor... processors) {
		List<T> resultList = new ArrayList<T>();
		
		// support of Immutable classes. java.lang.*, Date, BigDecimal, BigInteger
		if (Date.class.isAssignableFrom(clazz)) {
			CellProcessor p = null;
			for (int i=0; i<fieldMapping.length; i++) {
				if (RuleFactWatcher.Constants.valueAttributeStr.equals(fieldMapping[i])) {
					p = processors[i];
				}
			}
			CellProcessor dateProcessor = p;
			for (Map<String, Object> map : readCsvIntoMaps(fileName, true)) {
				T v = null;
				String strV = (String)map.get(RuleFactWatcher.Constants.valueAttributeStr);
				v = dateProcessor.execute(strV, null);
				resultList.add(v);
			}
			return resultList;
		} else if (Number.class.isAssignableFrom(clazz)) {
			for (Map<String, Object> map : readCsvIntoMaps(fileName, true)) {
				String strV = (String)map.get(RuleFactWatcher.Constants.valueAttributeStr);
				if (strV == null) {
					resultList.add(null);
				} else if (RuleFactWatcher.isImmutable(clazz)) {
					resultList.add((T)getImmutableObject(clazz, strV));
				}
			}
			return resultList;
		} else if (clazz.equals(String.class)) {
			for (Map<String, Object> map : readCsvIntoMaps(fileName, true)) {
				String strV = (String)map.get(RuleFactWatcher.Constants.valueAttributeStr);
				resultList.add((T) strV);
			}
			return resultList;
		} else if (clazz == Object.class) {
			for (Map<String, Object> map : readCsvIntoMaps(fileName, true)) {
				String strV = (String)map.get(RuleFactWatcher.Constants.valueAttributeStr);
				String typeV = (String)map.get(RuleFactWatcher.Constants.typeAttributeStr);
				Class<?> clazzActual = null;
				try {
					clazzActual = (typeV != null) ? Class.forName(typeV) : null;
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
				Object obj = getImmutableObject(clazzActual, strV);
				resultList.add((T) obj);
			}
			return resultList;
		}
		
		ICsvDozerBeanReader beanReader = null;
		try {
			Reader reader = new InputStreamReader(new FileInputStream(fileName), FILE_ENCODING);
			beanReader = new CsvDozerBeanReader(reader, CsvPreference.STANDARD_PREFERENCE);
			beanReader.getHeader(true);
			beanReader.configureBeanMapping(clazz, fieldMapping);

			T oneRecord;
			while ((oneRecord = beanReader.read(clazz, processors)) != null) {
				resultList.add(oneRecord);
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail("fail to load: " + fileName);
		} finally {
			IOUtils.closeQuietly(beanReader);
		}
		
		if (ignoreNull) {
			int i = 0;
			for (Map<String, Object> map : readCsvIntoMaps(fileName, false)) {
				try {
					T bean = clazz.newInstance();
					T loadedBean = resultList.get(i);
					for (String key : map.keySet()) {
						Object value = map.get(key);
						if (value != null && key.indexOf("#") == -1) {
							setProperty(bean, key, RuleFactWatcher.getProperty(loadedBean, key));
						}
					}
					resultList.set(i, bean);
					i++;
				} catch (Exception e) {
					e.printStackTrace();
					fail("fail to newInstance() of class:" + clazz.getSimpleName());
				}
			}
		}
		
		return resultList;
	}

	private static Object getImmutableObject(Class<?> clazz, String strV) {
		if (clazz.equals(BigDecimal.class)) {
			return new BigDecimal(strV);
		} else if (clazz.equals(BigInteger.class)) {
			return new BigInteger(strV);
		} else if (clazz.equals(Double.class)) {
			return new Double(strV);
		} else if (clazz.equals(Float.class)) {
			return new Float(strV);
		} else if (clazz.equals(Integer.class)) {
			return new Integer(strV);
		} else if (clazz.equals(Long.class)) {
			return new Long(strV);
		} else if (clazz.equals(String.class)) {
			return strV;
		}
		return null;
	}

	/**
	 * write POJO instances to a CSV file.
	 *
	 * @param fileName file name to write to
	 * @param contentsList list of POJO instances
	 * @param clazz the POJO class
	 */
	public static void writeCsv(List<?> contentsList, String fileName) {
		if (contentsList == null || contentsList.size() == 0) {
			logger.debug("no contents");
			return;
		}
		Class<?> clazz = contentsList.get(0).getClass();
		List<CsvColumnDef> columnDefList = readColumnDef(fileName, clazz.getSimpleName());

		String[] fileMapping = createFileMappingForBean(columnDefList);
		CellProcessor[] proccessors = createCellProcessorArray(columnDefList, false);
		writeCsv(fileName, clazz, fileMapping, contentsList, proccessors);
	}

	private static void writeCsv(String fileName, Class<?> clazz, String[] fieldMapping, List<?> contentsList,
			CellProcessor... processors) {
		ICsvDozerBeanWriter beanWriter = null;
		try {
			Writer writer = new OutputStreamWriter(new FileOutputStream(fileName), FILE_ENCODING);
			beanWriter = new CsvDozerBeanWriter(writer, CsvPreference.STANDARD_PREFERENCE);
			beanWriter.configureBeanMapping(clazz, fieldMapping);
			beanWriter.writeHeader(fieldMapping);

			for (Object oneBean : contentsList) {
				beanWriter.write(oneBean, processors);
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail("fail to write: " + fileName);
		} finally {
			IOUtils.closeQuietly(beanWriter);
		}
	}

	/**
	 * check two CSV files as<BR>
	 *  - if there are same number of lines<BR>
	 *  - if inside of each line contains same string.<BR>
	 *  - differences of order of each lines are allowed.<BR>
	 *
	 * @param actualFileName output of actual result facts
	 * @param expectedFileName CSV with expected results
	 */
	public static void assertCsv(String actualFileName, String expectedFileName) {
		String[] actualList = null;
		try {
			actualList = readCsv(actualFileName);
		} catch (Exception e) {
			e.printStackTrace();
			fail("fail at readCsv(" + actualFileName + ")");
		}
		String[] expectedList = null;
		try {
			expectedList = readCsv(expectedFileName);
		} catch (Exception e) {
			e.printStackTrace();
			fail("fail at readCsv(" + expectedFileName + ")");
		}

		Assert.assertThat(actualList, arrayWithSize(expectedList.length));
		Assert.assertThat(actualList, arrayContainingInAnyOrder(expectedList));
	}

	private static String[] readCsv(String fileName) {
		Path file = Paths.get(fileName);

		List<String> rtn = new ArrayList<String>();
		try {
			Files.lines(file).skip(1).forEach(line -> rtn.add(line));
		} catch (IOException e) {
			e.printStackTrace();
			fail("fail at readCsv(" + fileName + ")");
		}
		return rtn.toArray(new String[0]);
	}
	
	private static List<ExpectedRecord>
	readExpectedCsv(String filename, Class<?> clazz, Class<?> keyClass) {
				
		ArrayList<ExpectedRecord> ret = new ArrayList<ExpectedRecord>();
		for (Map<String, Object> map : readCsvIntoMaps(filename, false)) {
			ExpectedRecord record = new ExpectedRecord();
			record.map = map;
			ret.add(record);
		}
		List<CsvColumnDef> columnDefs = readColumnDef(filename, clazz.getSimpleName());
		String[] fileMapping = createFileMappingForBean(columnDefs);
		CellProcessor[] processors = createCellProcessorArray(columnDefs, true);
		int i=0;
		for (Object fact : loadCsv(filename, clazz, fileMapping, false, processors)) {
			if (keyClass == null) {
				ret.get(i).fact = fact;
			} else {
				MapEntry mapEntry = new MapEntry();
				mapEntry.key = getKeyValue(keyClass, ret.get(i).map);
				mapEntry.value = fact;
				ret.get(i).fact = mapEntry;
			}
			i++;
		}
		return ret;
	}
	
	private static Object getKeyValue(Class<?> keyClass, Map<String, Object> map) {
		if (RuleFactWatcher.isImmutable(keyClass)) {
			return getImmutableObject(keyClass, (String)map.get(RuleFactWatcher.Constants.keyAttributeStr));
		} else {
			Object keyObj = null;
			try {
				keyObj = keyClass.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				e.printStackTrace();
				fail("fail to create instance:" + keyClass.getName());
			}
			for (String key : map.keySet()) {
				if (key.startsWith(RuleFactWatcher.Constants.keyAttributeStr)) {
					String attributeName = key.substring(4);
					setProperty(keyObj, attributeName, map.get(key));
				}
			}
			return keyObj;
		}
	}

	private static List<Map<String, Object>> readCsvIntoMaps(String filename, boolean replaceToken) {
		List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>();
		ICsvMapReader mapReader = null;
		try {
			Reader reader = new InputStreamReader(new FileInputStream(filename), FILE_ENCODING);
			mapReader = new CsvMapReader(reader, CsvPreference.STANDARD_PREFERENCE);

			// the header columns are used as the keys to the Map
			String[] header = mapReader.getHeader(true);
			CellProcessor[] processors = new CellProcessor[header.length];
			for (int i=0; i<processors.length; i++) {
				if (replaceToken) {
					processors[i] = new Optional(createNullAndEmptyProcessor(new Optional()));
				} else {
					processors[i] = new Optional();
				}
			}

			Map<String, Object> factMap;
			while( (factMap = mapReader.read(header, processors)) != null ) {
				ret.add(factMap);
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail("fail to access:" + filename);
		} finally {
			IOUtils.closeQuietly(mapReader);
		}
		return ret;
	}
		
	/**
	 * Check as actual result of facts has expected values<BR>
	 * only checks specified expected values of specified attributes of specified facts.<BR>
	 * you can specify expected values into important attributes of important facts.<BR>
	 * <BR>
	 * @param actuals a list of actual result facts
	 * @param filename the file name which contains expected values
	 * @see CsvTestHelper#assertExpectCSV(List, String, boolean)
	 */
	@Deprecated
	public static Integer[] assertExpectCSV(List<?> actuals, String filename) {
		return assertExpectCSVwithParentRow(actuals, filename, false, null);
	}

	/**
	 * Check as actual result of facts has expected values<BR>
	 * only checks specified expected values of specified attributes of specified facts.<BR>
	 * you can specify expected values into important attributes of important facts.<BR>
	 * <BR>
	 * @param actuals a list of actual result facts
	 * @param filename the file name which contains expected values
	 * @param checkByIndex the flag to check actuals and expected values by same index
	 */
	public static Integer[] assertExpectCSV(List<?> actuals, String filename, boolean checkByIndex) {
		return assertExpectCSVwithParentRow(actuals, filename, checkByIndex, null);
	}

	private static Map<String, Boolean> createTestSkipMap(List<CsvColumnDef> columnDefs) {
		LinkedHashMap<String, Boolean> ret = new LinkedHashMap<String, Boolean>();
		for (CsvColumnDef columnDef : columnDefs) {
			Boolean isTestSkip = columnDef.getTestSkip() != null && columnDef.getTestSkip() == Boolean.TRUE;
			ret.put(columnDef.getColumnName(), isTestSkip);
		}
		return ret;
	}

	/**
	 * create BiPredicate to check equality of an actual fact and an expected record fact.
	 * @param columnDefs column definition list
	 * @param clazz FACT class
	 * @return BiPredicate to check equality of an actual fact and an expected record fact.
	 */
	private static BiPredicate<ExpectedRecord, Object>
	createTestPredicate(List<CsvColumnDef> columnDefs, Class<?> clazz) {
		BiPredicate<ExpectedRecord, Object> predicate =
				new BiPredicate<ExpectedRecord, Object>() {

			@Override
			public boolean test(ExpectedRecord expected, Object fact) {
				if (fact.getClass().equals(MapEntry.class)) {
					// do nothing
				} else if (!fact.getClass().isAssignableFrom(clazz)) {
					return false;
				}
				boolean ret = true;
				for (CsvColumnDef columnDef : columnDefs) {
					if (columnDef.getTestPK() != null && columnDef.getTestPK() == Boolean.TRUE) {
						Object value1 = RuleFactWatcher.getProperty(expected.fact, columnDef.getColumnName());
						Object value2 = RuleFactWatcher.getProperty(fact, columnDef.getColumnName());
						if (value1 == null) {
							if (value2 != null) {
								ret = false;
							}
						} else {
							if (!value1.equals(value2)) {
								ret = false;
							}
						}
					}
				}
				return ret;
			}
		};
		return predicate;
	}
	
	/**
	 * create a RuleFactWatcher to output log about the changes of specified attributes of specified facts.<BR>
	 * and check the value is equals to the expected value.<BR>
	 * <BR>
	 * @param filename filename of expect information
	 * @param clazz FACT class
	 * @return a RuleFactWatcher
	 */
	public static RuleFactWatcher createRuleFactWatcher(String filename, Class<?> clazz) {
		return createRuleFactWatcher(filename, clazz, false, null);
	}

	/**
	 * create a RuleFactWatcher to output log about the changes of specified attributes of specified facts.<BR>
	 * and check the value is equals to the expected value.<BR>
	 * <BR>
	 * @param filename filename of expect information
	 * @param clazz FACT class
	 * @param checkByIndex the flag to check actuals and expected values by same index
	 * @return a RuleFactWatcher
	 */
	public static RuleFactWatcher createRuleFactWatcher(String filename, Class<?> clazz, boolean checkByIndex) {
		return createRuleFactWatcher(filename, clazz, checkByIndex, null);
	}

	/**
	 * create a RuleFactWatcher to output log about the changes of specified attributes of specified facts.<BR>
	 * and check the value is equals to the expected value.<BR>
	 * <BR>
	 * @param filename filename of expect information
	 * @param clazz FACT class
	 * @param checkByIndex the flag to check actuals and expected values by same index
	 * @param keyClass class of key if the target is Map type.
	 * @return a RuleFactWatcher
	 */
	public static RuleFactWatcher createRuleFactWatcher(String filename, Class<?> clazz, boolean checkByIndex, Class<?> keyClass) {
		List<CsvColumnDef> columnDefs = CsvTestHelper.readColumnDef(filename, clazz.getSimpleName());
		List<ExpectedRecord> expectedRecords = CsvTestHelper.readExpectedCsv(filename, clazz, keyClass);
		BiPredicate<ExpectedRecord, Object> predicate = CsvTestHelper.createTestPredicate(columnDefs, clazz);
		Map<String, Boolean> testSkipMap = CsvTestHelper.createTestSkipMap(columnDefs);
		return new RuleFactWatcher(expectedRecords, checkByIndex, predicate, testSkipMap);
	}

	/**
	 * POJO for the CSV column definition (.def) file
	 */
	public static class CsvColumnDef {
		/**
		 * columnName of CSV<BR>
		 * attribute name of Fact<BR>
		 */
		private String columnName;
		/**
		 * option of CSV<BR>
		 * not specify (default) : the column become optional.<BR>
		 * notnull : if require Not Null value.<BR>
		 * date : if require format parse.<BR>
		 */
		private String option;
		/**
		 * format of value<BR>
		 * mostly used for date format such as "yyyy/MM/dd HH:mm:ss"
		 */
		private String format;
		/**
		 * primary key flag used by the test tool to match with FACT. (if not null)
		 */
		private Boolean testPK;
		/**
		 * skip flag of the attribute to ignore by the test tool. (if not null)
		 */
		private Boolean testSkip;

		public String getColumnName() {
			return columnName;
		}
		public void setColumnName(String columnName) {
			this.columnName = columnName;
		}

		public String getOption() {
			return option;
		}
		public void setOption(String option) {
			this.option = option;
		}

		public String getFormat() {
			return format;
		}
		public void setFormat(String format) {
			this.format = format;
		}

		public Boolean getTestPK() {
			return testPK;
		}
		public void setTestPK(Boolean testPK) {
			this.testPK = testPK;
		}

		public Boolean getTestSkip() {
			return testSkip;
		}
		public void setTestSkip(Boolean testSkip) {
			this.testSkip = testSkip;
		}

	}
	
	/**
	 * POJO for the CSV list file (Files_*.csv)
	 */
	public static class CsvFiles {
		private TestFileType type;
		private String file;
		private String clazz;
		private String path;
		private String parentAttr;
		private String options;
		
		public TestFileType getType() {
			return type;
		}
		public void setType(TestFileType type) {
			this.type = type;
		}
		public String getFile() {
			return file;
		}
		public void setFile(String file) {
			this.file = file;
		}
		public String getClazz() {
			return clazz;
		}
		public void setClazz(String clazz) {
			this.clazz = clazz;
		}
		public String getPath() {
			return path;
		}
		public void setPath(String path) {
			this.path = path;
		}
		public String getParentAttr() {
			return parentAttr;
		}
		public void setParentAttr(String parentAttr) {
			this.parentAttr = parentAttr;
		}
		public String getOptions() {
			return options;
		}
		public void setOptions(String options) {
			this.options = options;
		}
	}
	
	/**
	 * type of CSV test file. input or expected.
	 */
	public static enum TestFileType {
		IN,
		EX;
	}
	
	/**
	 * load CSV list file (Files_*.csv)
	 *
	 * @param fileName file name of the target.
	 */
	private static List<CsvFiles> readCsvListFiles(String fileName) {
		String[] fileMappng = { "type", "file", "clazz", "path", "parentAttr", "options" };
		CellProcessor[] processors = new CellProcessor[] { new NotNull(), new NotNull(), new NotNull(), new NotNull(), new Optional(), new Optional() };
		List<CsvFiles> ret = loadCsv(fileName, CsvFiles.class, fileMappng, false, processors);
		return ret;
	}


	/**
	 * create RuleFactWatchers from a CSV file list
	 * @param fileListCsvPath
	 * @return
	 */
	public static RuleFactWatchers createRuleFactWatchers(String fileListCsvPath) {
		boolean hasError = false;
		Map<String, Class<?>> factClassMap = new LinkedHashMap<String, Class<?>>();
		Map<String, RuleFactWatcher> watcherMap = new LinkedHashMap<String, RuleFactWatcher>();
		
		File folder = new File(fileListCsvPath).getParentFile();
		// load CSV files list
		List<CsvFiles> csvFiles = readCsvListFiles(fileListCsvPath);
		// process only expected files
		for (int i=0; i < csvFiles.size(); ) {
			CsvFiles csvFile = csvFiles.get(i);
			if (csvFile.type != TestFileType.EX) {
				csvFiles.remove(i);
			} else {
				i++;
			}
		}
		// sort by path
		Collections.sort(csvFiles, new Comparator<CsvFiles>() {
			@Override
			public int compare(CsvFiles o1, CsvFiles o2) {
				return o1.path.compareTo(o2.path);
			}
		});
		// process each expected file
		for (CsvFiles csvFile: csvFiles) {
			// create new RuleFactWatcher
			String path[] = separateParentPath(csvFile.path);
			RuleFactWatcher parentWatcher = watcherMap.get(path[0]);
			Class<?> clazz;
			try {
				clazz = Class.forName(csvFile.clazz);
				Map<String, String> optionMap = getOptionMap(csvFile.options);
				boolean checkByIndex = "true".equalsIgnoreCase(optionMap.get(OPTION_CHECK_BY_INDEX));
				String keyType = optionMap.get(OPTION_KEY_TYPE);
				Class<?> keyClass = (keyType != null) ? Class.forName(keyType) : null;
				RuleFactWatcher watcher = createRuleFactWatcher(new File(folder, csvFile.file).getCanonicalPath(), clazz, checkByIndex, keyClass);
				Class<?> parentClass = factClassMap.get(path[0]);
				watcher.setHeaderFact(parentClass, new BiFunction<Object, Object[], Object>() {
					@Override
					public Object apply(Object t, Object[] u) {
						return RuleFactWatcher.getProperty(t, path[1]);
					}
				}, null);
				if (parentWatcher != null) {
					parentWatcher.registerChildWatcher(path[1], watcher);
				}
				watcherMap.put(csvFile.path, watcher);
				factClassMap.put(csvFile.path, clazz);
			} catch (Exception e) {
				e.printStackTrace();
				hasError = true;
			}
		}
		if (! hasError) {
			// returns RuleFactWatchers
			RuleFactWatchers watchers = new RuleFactWatchers();
			for (RuleFactWatcher watcher : watcherMap.values()) {
				// filter out the watchers for child
				if (! watcher.isChild()) {
					watchers.add(watcher);
				}
			}
			return watchers;
		} else {
			fail("fail at createRuleFactWatchers(" + fileListCsvPath + ")");
			return null;
		}
	}

	private static String[] separateParentPath(String path) {
		String parentPath = "";
		String currentPath = path;
		int dotIndex = path.lastIndexOf(".");
		if (dotIndex >= 0) {
			parentPath = path.substring(0, dotIndex);
			currentPath = path.substring(dotIndex + 1);
		}
		return new String[] {parentPath, currentPath};
	}
	
	/**
	 * Check the actual list with the expected list which are filtered by the parentRow info
	 * @param actuals a list of actual result facts
	 * @param filename the file name which contains expected values
	 * @param checkByIndex the flag to check actuals and expected values by same index
	 * @param parentRow parent row to filter this child list
	 * @return mapping array with actualIndex and expectIndex
	 * @see CsvTestHelper#assertExpectCSV(List, String, boolean)
	 */
	public static <T> Integer[] assertExpectCSVwithParentRow(List<T> actuals,
			String filename, boolean checkByIndex, String parentRow) {
		Integer[] ret = new Integer[actuals != null ? actuals.size() : 0];
		// get Class from actuals
		boolean isMap = false;
		Class<?> clazz = null;
		Class<?> keyClass = null;
		if (actuals != null) {
			for (int i=0; i<actuals.size(); i++) {
				T o = actuals.get(i);
				if (o != null) {
					clazz = o.getClass();
					// if clazz is MapEntry, get the class of the value
					if (clazz.equals(MapEntry.class)) {
						isMap = true;
						Object v = ((MapEntry)o).value;
						Object k = ((MapEntry)o).key;
						if (v != null) {
							clazz = v.getClass();
						} else {
							clazz = null;
						}
						if (k != null) {
							keyClass = k.getClass();
						}
						if (clazz != null && keyClass != null)
							break;
					}
				}
			}
		}
		
		// in case of no actuals, or all actuals are null
		if (clazz == null) {
			// check if there are expected records
			int countExpectedRecords = 0;
			for (Map<String, Object> map : readCsvIntoMaps(filename, false)) {
				if (parentRow != null) {
					// count expected records where the parent# value equals parentRow
					if (parentRow.equals(map.get(RuleFactWatcher.Constants.parentRowKey))) {
						countExpectedRecords++;
						if (map.containsKey(RuleFactWatcher.Constants.valueAttributeStr) &&
								!RuleFactWatcher.Constants.nullCheckStr.equals(map.get(RuleFactWatcher.Constants.valueAttributeStr))) {
							fail("actual is null but expect is not null");
						}
					}
				} else {
					countExpectedRecords++;
					if (map.containsKey(RuleFactWatcher.Constants.valueAttributeStr) &&
							!RuleFactWatcher.Constants.nullCheckStr.equals(map.get(RuleFactWatcher.Constants.valueAttributeStr))) {
						fail("actual is null but expect is not null");
					}
				}
			}
			if (actuals == null) {
				if (countExpectedRecords > 0) {
					fail("No actuals records but there are expected records in " + filename
							+ (parentRow != null ? (" " + RuleFactWatcher.Constants.parentRowKey + ":" + parentRow) : ""));
				}
			} else if (actuals.size() != countExpectedRecords) {
				fail("Record number mismatch of actuals and expected records in " + filename
						+ (parentRow != null ? (" " + RuleFactWatcher.Constants.parentRowKey + ":" + parentRow) : ""));
			}
			return ret;
		}
						
		List<CsvColumnDef> columnDefs = null;
		try {
			File defFile = getDefFile(filename, clazz.getSimpleName());
			if (! defFile.exists()) {
				clazz = Object.class;
			}
			columnDefs = readColumnDef(filename, clazz.getSimpleName());
		} catch (Exception e1) {
			e1.printStackTrace();
			fail("fail at readColumnDef(" + filename + ", " + clazz.getSimpleName() + ")");
		}
		BiPredicate<ExpectedRecord, Object> predicate = createTestPredicate(columnDefs, clazz);
		Map<String, Boolean> testSkipMap = createTestSkipMap(columnDefs);
		String factClassName = clazz.getSimpleName();
			
		// the index of actual record
		int actualIndex = 0;
		// the flag the record is checked or not
		boolean foundExpect = false;
		// the index of expect record
		int expectIndex = 0;
		List<ExpectedRecord> expectedRecords = readExpectedCsv(filename, clazz, keyClass);
		
		// checkByIndex mode, remove other parents' records
		if (checkByIndex && parentRow != null) {
			for (int i=0; i < expectedRecords.size();) {
				if (! parentRow.equals(expectedRecords.get(i).map.get(RuleFactWatcher.Constants.parentRowKey))) {
					expectedRecords.remove(i);
				} else {
					i++;
				}
			}
		}
		
		for (Object actual : actuals) {
			actualIndex++;
			foundExpect = false;
			expectIndex = 0;
			if (actual != null) {
				for(ExpectedRecord expect : expectedRecords) {
					expectIndex++;
					if (expect.fact == null) {
						if (checkByIndex &&
								actualIndex == expectIndex) {
							fail(
									"**assertExpectCSV** Expected record for the actual record (" + actual + ") " + factClassName
									+ "[" + (actualIndex - 1) + "] " +
									"is null."
									);
						}
						// skip if it's null record in expected records
					} else if (parentRow != null && ! parentRow.equals(expect.map.get(RuleFactWatcher.Constants.parentRowKey))) {
						// skip if the parent row is not same
					} else if (
							(checkByIndex &&
									actualIndex == expectIndex)
							|| (!checkByIndex && predicate.test(expect, actual))) {
						// index match OR the actual fact matches the expected record
						// if there is at least one expected record for the actual fact, set the flag true.
						foundExpect = true;
						expect.used = true;
						// update the mapping array of actualIndex and expectIndex;
						ret[actualIndex - 1] = expectIndex - 1;
						checkAttributes(actual, actualIndex, expect, isMap, testSkipMap);
					}
				}
				if (foundExpect == false) {
					// an actual record is not in the expected records
					fail(
							"**assertExpectCSV** Expected record for the actual record " + factClassName
							+ "[" + (actualIndex - 1) + "] " +
							"does NOT exist."
							);
				}
			} else {
				if (checkByIndex) {
					if (expectedRecords.size() < actualIndex) {
						fail(
								"**assertExpectCSV** Expected record for the actual record " + factClassName
								+ "[" + (actualIndex - 1) + "] " +
								"does NOT exist."
						);
					} else if (expectedRecords.get(actualIndex - 1).fact != null) {
						fail(
								"**assertExpectCSV** Expected record for the actual record (null) " + factClassName
								+ "[" + (actualIndex - 1) + "] " +
								"is not null."
						);
					} else {
						foundExpect = true;
					}
				} else {
					logger.debug("**assertExpectCSV** Skipping actual record {}[{}] as it's null",
							factClassName, actualIndex - 1);
				}
			}
		}
		int expectedRecordNo = 0;
		boolean unused = false;
		for (ExpectedRecord record : expectedRecords) {
			if ((parentRow == null || parentRow.equals(record.map.get(RuleFactWatcher.Constants.parentRowKey)))
					&& record.fact != null && ! record.used) {
				logger.warn("**assertExpectCSV** Expected record {}[{}] is not used.",
						factClassName, expectedRecordNo);
				unused = true;
			}
			expectedRecordNo++;
		}
		if (unused == true) {
			fail("fail as there are unused expected record(s) of the " + factClassName + " class");
		}

		// checkByIndex mode, no fails -> matching each index
		if (checkByIndex) {
			for (int i=0 ; i < ret.length; i++) {
				ret[i] = i;
			}
		}
		
		return ret;
	}

	private static void checkAttributes(Object actual, int actualIndex, ExpectedRecord expect,
			boolean isMap, Map<String, Boolean> testSkipMap) {
		Object expectedFact = expect.fact;
		for (Map.Entry<String, Object> entry : expect.map.entrySet()) {
			// filter out attributes both of PARENT_ROW and Test Skip keys.
			if (entry.getKey().indexOf("#") == -1 && !testSkipMap.get(entry.getKey())) {
				String key = entry.getKey();
				Object value = entry.getValue();
				if (RuleFactWatcher.Constants.nullCheckStr.equals(value)) {
					logger.debug("**assertExpectCSV** Checking actual record {}[{}]@{}",
							actual.getClass().getSimpleName(), actualIndex - 1,
							System.identityHashCode(actual));
					// check if it is null as the expected value is "should be null" value
					assertThat(actual, hasProperty(key, is(nullValue())));
				} else if (!StringUtils.isEmpty((String)value)) {
					// check if the equality between the expected value and the fact's attribute value
					Object actualObj = (!isMap) ? actual : ((MapEntry)actual).value;
					Object expectObj = (!isMap) ? expectedFact : ((MapEntry)expectedFact).value;
					logger.debug("**assertExpectCSV** Checking actual record {}[{}]@{}",
							actualObj.getClass().getSimpleName(), actualIndex - 1,
							System.identityHashCode(actualObj));
					if (key.contains(".") || key.contains("[")) {
						// get the target attribute value
						Object actualField = RuleFactWatcher.getProperty(actualObj, key);
						Object expectField = RuleFactWatcher.getProperty(expectObj, key);
						assertThat(actualField, is(expectField));
					} else if (key.equals(RuleFactWatcher.Constants.valueAttributeStr) &&
							RuleFactWatcher.isImmutable(actualObj.getClass())) {
						assertThat(actualObj, is(expectObj));
					} else {
						// get the target attribute value
						Object expectField = RuleFactWatcher.getProperty(expectObj, key);
						assertThat(actualObj, hasProperty(key, is(expectField)));
					}
				}
			}
		}
	}

	/**
	 * create an input map building object hierarchy by using the path information.
	 * @param fileListCsvPath
	 * @return input map
	 */
	public static Map<String, List<?>> loadInputMap(String fileListCsvPath) {
		Map<String, List<?>> retMap = new LinkedHashMap<String, List<?>>();
		File folder = new File(fileListCsvPath).getParentFile();
		List<CsvFiles> csvFiles = readCsvListFiles(fileListCsvPath);
		for (int i = 0; i < csvFiles.size(); ) {
			CsvFiles csvFile = csvFiles.get(i);
			// process only input files
			if (csvFile.type != TestFileType.IN) {
				csvFiles.remove(i);
			} else {
				i++;
			}
		}
		// sort by "path"
		Collections.sort(csvFiles, new Comparator<CsvFiles>() {
			@Override
			public int compare(CsvFiles o1, CsvFiles o2) {
				return o1.path.compareTo(o2.path);
			}
		});
		// process each expected file
		for (CsvFiles csvFile : csvFiles) {
			Class<?> clazz = null;
			try {
				clazz = Class.forName(csvFile.clazz);
			} catch (Exception e) {
				e.printStackTrace();
				fail("fail at load class:" + csvFile.clazz);
			}
			List<?> objs = null;
			try {
				objs = loadCsv(new File(folder, csvFile.file).getCanonicalPath(), clazz, true);
			} catch (Exception e) {
				e.printStackTrace();
				fail("fail at access file: " + csvFile.file);
			}
			
			// Map case
			if (csvFile.options != null && csvFile.options.indexOf(OPTION_KEY_TYPE + "=") != -1) {
				Map<String, String> optionMap = getOptionMap(csvFile.options);
				String keyType = optionMap.get(OPTION_KEY_TYPE);
				Class<?> keyClass = null;
				try {
					keyClass = Class.forName(keyType);
				} catch (Exception e) {
					e.printStackTrace();
					fail("fail at load class:" + keyType);
				}
				List<Map<String, Object>> keyMapList = null;
				try {
					keyMapList = loadKeyInfo(new File(folder, csvFile.file).getCanonicalPath());
				} catch (Exception e) {
					e.printStackTrace();
					fail("fail at load keys:" + csvFile.file);
				}
				List<MapEntry> mapEntries = new ArrayList<MapEntry>();
				int i = 0;
				for (Map<String, Object> map : keyMapList) {
					MapEntry entry = new MapEntry();
					entry.key = getKeyValue(keyClass, map);
					entry.value = objs.get(i);
					mapEntries.add(entry);
					i++;
				}
				objs = mapEntries;
			}
			retMap.put(csvFile.path, objs);
			
			// create parent -> child relation
			String path[] = separateParentPath(csvFile.path);
			List<?> parents = retMap.get(path[0]);
			if (parents != null) {
				if (parents.size() == 1) {
					for (Object obj : objs) {
						putChild(parents.get(0), path[1], obj);
						// create child -> parent relation
						if (csvFile.parentAttr != null) {
							setProperty(obj, csvFile.parentAttr, parents.get(0));
						}
					}
				} else if (parents.size() > 1) {
					List<Integer> indexs = null;
					try {
						indexs = loadParentRowInfo(new File(folder, csvFile.file).getCanonicalPath());
					} catch (Exception e) {
						e.printStackTrace();
						fail("fail at access file: " + csvFile.file);
					}
					for (int i=0; i < indexs.size(); i++) {
						Object parent = parents.get(indexs.get(i)-1);
						Object child = objs.get(i);
						putChild(parent, path[1], child);
						// create child -> parent relation
						if (csvFile.parentAttr != null) {
							setProperty(child, csvFile.parentAttr, parent);
						}
					}
				}
			}
		}
		return retMap;
	}
	
	private static Map<String, String> getOptionMap(String optionStr) {
		LinkedHashMap<String, String> ret = new LinkedHashMap<String, String>();
		if (optionStr == null) {
			return ret;
		}
		String[] options = optionStr.split(",");
		for (String option : options) {
			if (option != null && option.indexOf("=") != -1) {
				String[] keyValue = option.split("=");
				ret.put(keyValue[0], keyValue[1]);
			}
		}
		return ret;
	}

	private static List<Integer> loadParentRowInfo(String filename) {
		ArrayList<Integer> ret = new ArrayList<Integer>();
		ICsvMapReader mapReader = null;
		try {
//			Reader reader = new FileReader(filename);
			Reader reader = new InputStreamReader(new FileInputStream(filename), FILE_ENCODING);
			mapReader = new CsvMapReader(reader, CsvPreference.STANDARD_PREFERENCE);

			// the header columns are used as the keys to the Map
			String[] headers = mapReader.getHeader(true);
			for (int i=0; i < headers.length; i++) {
				// load the parentRow column only
				if (headers[i] == null || !RuleFactWatcher.Constants.parentRowKey.equals(headers[i])) {
					// ignore column
					headers[i] = null;
				}
			}
			CellProcessor[] processors = new CellProcessor[headers.length];
			for (int i=0; i<processors.length; i++) {
				if (headers[i] == null)
					processors[i] = new Optional();
				else
					processors[i] = new ParseInt();
			}

			Map<String, Object> factMap;
			while( (factMap = mapReader.read(headers, processors)) != null ) {
				ret.add((Integer)factMap.get(RuleFactWatcher.Constants.parentRowKey));
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if( mapReader != null ) {
				try {
					mapReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return ret;
	}

	private static List<Map<String, Object>> loadKeyInfo(String filename) {
		ArrayList<Map<String, Object>> ret = new ArrayList<Map<String, Object>>();
		ICsvMapReader mapReader = null;
		try {
			Reader reader = new InputStreamReader(new FileInputStream(filename), FILE_ENCODING);
			mapReader = new CsvMapReader(reader, CsvPreference.STANDARD_PREFERENCE);

			// the header columns are used as the keys to the Map
			String[] headers = mapReader.getHeader(true);
			for (int i=0; i < headers.length; i++) {
				// load the key column only
				if (headers[i] == null || !headers[i].startsWith(RuleFactWatcher.Constants.keyAttributeStr)) {
					// ignore column
					headers[i] = null;
				}
			}
			CellProcessor[] processors = new CellProcessor[headers.length];
			for (int i=0; i<processors.length; i++) {
				processors[i] = new Optional();
			}

			Map<String, Object> factMap;
			while( (factMap = mapReader.read(headers, processors)) != null ) {
				ret.add(factMap);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if( mapReader != null ) {
				try {
					mapReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return ret;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void putChild(Object parent, String attribute, Object child) {
		Object attr = RuleFactWatcher.getProperty(parent, attribute);
		if (attr instanceof Collection) {
			((Collection)attr).add(child);
		} else if (attr instanceof Map) {
			Object key = ((MapEntry)child).key;
			Object value = ((MapEntry)child).value;
			((Map)attr).put(key, value);
		} else {
			setProperty(parent, attribute, child);
		}
	}
	
	/**
	 * set property
	 * @param obj target object
	 * @param key property name
	 * @param value value to set
	 */
	public static void setProperty(Object obj, String key, Object value) {
		try {
			PropertyUtils.setProperty(obj, key, value);
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			e.printStackTrace();
			fail("fail to access setProperty(" + obj + ", " + key + ", " + value + ")");
		}
	}

	/**
	 * check the actual records with the expected records in CSV including sub attributes
	 * @param actuals actual records
	 * @param fileListCsvPath CSV file list
	 * @param pathPrefix path of the actual records
	 */
	@SuppressWarnings("unchecked")
	public static void assertExpectCSVs(List<?> actuals,
			String fileListCsvPath, String pathPrefix) {
		Map<String, List<Object>> actualsMap =
				new LinkedHashMap<String, List<Object>>();
		Map<String, Integer[]> indexMap =
				new LinkedHashMap<String, Integer[]>();
		File folder = new File(fileListCsvPath).getParentFile();
		List<CsvFiles> csvFiles;
		try {
			csvFiles = readCsvListFiles(fileListCsvPath);
		} catch (Exception e1) {
			e1.printStackTrace();
			fail("fail to access:" + fileListCsvPath);
			return;
		}
		// process only expected files
		// process only specified "path" files
		for (int i=0; i < csvFiles.size(); ) {
			CsvFiles csvFile = csvFiles.get(i);
			if (csvFile.type != TestFileType.EX ||
					!csvFile.path.startsWith(pathPrefix)) {
				csvFiles.remove(i);
			} else {
				i++;
			}
		}
		// sort by "path"
		Collections.sort(csvFiles, new Comparator<CsvFiles>() {
			@Override
			public int compare(CsvFiles o1, CsvFiles o2) {
				return o1.path.compareTo(o2.path);
			}
		});
		// process each expected file
		for (CsvFiles csvFile : csvFiles) {
			String path[] = separateParentPath(csvFile.path);
			List<Object> parentActuals = actualsMap.get(path[0]);
			Integer[] parentIndexArray = indexMap.get(path[0]);
			if (parentActuals == null) {
				// check actual records at the top level
				String expectedFile = null;
				try {
					expectedFile = new File(folder, csvFile.file).getCanonicalPath();
				} catch (Exception e) {
					e.printStackTrace();
					fail("fail to access: " + csvFile.file);
				}
				
				boolean checkByIndex = "true".equalsIgnoreCase(
						getOptionMap(csvFile.options).get(OPTION_CHECK_BY_INDEX));

				logger.debug("**assertExpectCSV** Checking path:" + csvFile.path);
				Integer[] indexArray = assertExpectCSV(actuals, expectedFile, checkByIndex);
				
				// register for the next level
				actualsMap.put(pathPrefix, (List<Object>)actuals);
				indexMap.put(pathPrefix, indexArray);
			} else {
				// check internal
				boolean isNeedParentRow = true;
				if (parentActuals.size() == 1) {
					// parent is only one record
					isNeedParentRow = false;
				}
				for (int i=0; i < parentActuals.size(); i++) {
					// get internal actual object
					List<Object> internalActuals;
					Object child = RuleFactWatcher.getProperty(parentActuals.get(i), path[1]);
					if (child instanceof List) {
						internalActuals = (List<Object>)child;
					} else if (child instanceof Map) {
						internalActuals = new ArrayList<Object>();
						for (Map.Entry<Object, Object> entry : ((Map<Object, Object>)child).entrySet()) {
							MapEntry mapEntry = new MapEntry();
							mapEntry.key = entry.getKey();
							mapEntry.value = entry.getValue();
							internalActuals.add((Object)mapEntry);
						}
					} else if (child != null) {
						internalActuals = Arrays.asList(child);
					} else { // child == null
						internalActuals = null;
					}

					String childExpectedFile = null;
					try {
						childExpectedFile = new File(folder, csvFile.file).getCanonicalPath();
					} catch (Exception e) {
						e.printStackTrace();
						fail("fail to access: " + csvFile.file);
					}
					
					boolean checkByIndex = "true".equalsIgnoreCase(
							getOptionMap(csvFile.options).get(OPTION_CHECK_BY_INDEX));

					// check child
					String parentRow = isNeedParentRow ? ("" + (parentIndexArray[i]+1)) : null;
					logger.debug("**assertExpectCSV** Checking path:" + csvFile.path + ", "
							+ RuleFactWatcher.Constants.parentRowKey + ":" + parentRow
							);
					Integer indexArray[] =
							assertExpectCSVwithParentRow(internalActuals,
									childExpectedFile, checkByIndex, parentRow);
					
					// register internal actuals for the next level
					List<Object> registeredList = (List<Object>)actualsMap.get(csvFile.path);
					if (registeredList == null) {
						registeredList = new ArrayList<Object>();
						actualsMap.put(csvFile.path, registeredList);
					}
					for (Object t : internalActuals) {
						registeredList.add(t);
					}
					Integer[] registeredIndex = indexMap.get(csvFile.path);
					if (registeredIndex == null) {
						indexMap.put(csvFile.path, indexArray);
					} else {
						Integer[] newIndexArray = new Integer[registeredIndex.length + indexArray.length];
						for (int k=0; k < registeredIndex.length; k++) {
							newIndexArray[k] = registeredIndex[k];
						}
						for (int k=0; k < indexArray.length; k++) {
							newIndexArray[registeredIndex.length + k] = indexArray[k];
						}
						indexMap.put(csvFile.path, newIndexArray);
					}
				}
			}
		}
	}
	
}
