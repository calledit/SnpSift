package org.snpsift.testCases;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Invoke all test cases
 *
 * @author pcingola
 */

@Suite
@SuiteDisplayName("Unit test cases")
@SelectPackages({"org.snpsift.testCases"})
public class TestSuiteAll {
}
