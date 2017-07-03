/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.jdbc.framework;

import org.elasticsearch.common.Strings;
import org.elasticsearch.test.ESTestCase;
import org.junit.Assert;
import org.junit.ClassRule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.lang.String.format;

public abstract class SpecBaseIntegrationTestCase extends ESTestCase {

    protected static final String PARAM_FORMATTNG = "%0$s.test%2$s";

    protected final String groupName;
    protected final String testName;
    protected final Integer lineNumber;
    protected final Path source;

    @ClassRule
    public static EsJdbcServer ES = new EsJdbcServer();

    //
    //    This typically is uncommented when starting up a new instance of ES
    //
    //    @BeforeClass
    //    public static void start() throws Exception {
    //        TestUtils.loadDatasetInEs(TestUtils.restClient("localhost", 9200));
    //    }

    public SpecBaseIntegrationTestCase(String groupName, String testName, Integer lineNumber, Path source) {
        this.groupName = groupName;
        this.testName = testName;
        this.lineNumber = lineNumber;
        this.source = source;
    }

    public Connection esCon() throws Exception {
        return ES.get();
    }

    protected Throwable reworkException(Throwable th) {
        StackTraceElement[] stackTrace = th.getStackTrace();
        StackTraceElement[] redone = new StackTraceElement[stackTrace.length + 1];
        System.arraycopy(stackTrace, 0, redone, 1, stackTrace.length);
        redone[0] = new StackTraceElement(getClass().getName(), groupName + "." + testName, source.getFileName().toString(), lineNumber);

        th.setStackTrace(redone);
        return th;
    }
    
    //
    // spec reader
    //
    
    // returns groupName, testName, its line location, its source and the custom object (based on each test parser)
    protected static List<Object[]> readScriptSpec(String url, Parser parser) throws Exception {
        Path source = Paths.get(TestUtils.class.getResource(url).toURI());
        String fileName = source.getFileName().toString();
        int dot = fileName.indexOf(".");
        String groupName = dot > 0 ? fileName.substring(0, dot) : fileName;

        List<String> lines = Files.readAllLines(source);

        Map<String, Integer> testNames = new LinkedHashMap<>();
        List<Object[]> pairs = new ArrayList<>();

        String testName = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            // ignore comments
            if (!line.isEmpty() && !line.startsWith("//")) {
                // parse test name
                if (testName == null) {
                    if (testNames.keySet().contains(line)) {
                        throw new IllegalStateException(format(Locale.ROOT, "Duplicate test name '%s' at line %d (previously seen at line %d)", line, i, testNames.get(line)));
                    }
                    else {
                        testName = Strings.capitalize(line);
                        testNames.put(testName, Integer.valueOf(i));
                    }
                }
                else {
                    Object result = parser.parse(line);
                    // only if the parser is ready, add the object - otherwise keep on serving it lines
                    if (result != null) {
                        pairs.add(new Object[] { groupName, testName, Integer.valueOf(i), source, result });
                        testName = null;
                    }
                }
            }
        }
        Assert.assertNull("Cannot find spec for test " + testName, testName);

        return pairs;
    }
    
    public interface Parser {
        Object parse(String line);
    }
}