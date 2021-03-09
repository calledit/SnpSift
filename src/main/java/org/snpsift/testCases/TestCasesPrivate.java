package org.snpsift.testCases;

import java.util.List;

import org.junit.Assert;
import org.snpeff.util.Log;
import org.snpeff.vcf.VcfEntry;
import org.snpsift.SnpSift;
import org.snpsift.SnpSiftCmdPrivate;

import junit.framework.TestCase;

/**
 * SnpSift 'private' test cases
 *
 * @author pcingola
 */
public class TestCasesPrivate extends TestCase {

	public static boolean debug = false;
	public static boolean verbose = false;

	void checkPrivate(String vcfFile, String tfamFile, boolean isPrivate) {
		String args[] = { "private", tfamFile, vcfFile };
		SnpSift snpSift = new SnpSift(args);
		SnpSiftCmdPrivate cmd = (SnpSiftCmdPrivate) snpSift.cmd();

		int count = 0;
		List<VcfEntry> vcfEntries = cmd.run(true);
		for (VcfEntry ve : vcfEntries) {
			if (verbose) System.out.println(ve);

			if (!isPrivate && (ve.getInfo(VcfEntry.VCF_INFO_PRIVATE) != null)) throw new RuntimeException("This should not be a 'private' variant!");
			if (isPrivate && (ve.getInfo(VcfEntry.VCF_INFO_PRIVATE) == null)) throw new RuntimeException("This should be a 'private' variant!");
			count++;
		}

		Assert.assertTrue("No variants checked!", count > 0);
	}

	/**
	 * Non private
	 */
	public void test_01() {
		Log.debug("Test");
		String vcfFile = "test/test.private.01.vcf";
		String tfamFile = "test/test.private.01.tfam";
		checkPrivate(vcfFile, tfamFile, false);
	}

	/**
	 * Non private
	 */
	public void test_02() {
		Log.debug("Test");
		String vcfFile = "test/test.private.02.vcf";
		String tfamFile = "test/test.private.01.tfam";
		checkPrivate(vcfFile, tfamFile, false);
	}

	/**
	 * Private variant
	 */
	public void test_03() {
		Log.debug("Test");
		String vcfFile = "test/test.private.03.vcf";
		String tfamFile = "test/test.private.01.tfam";
		checkPrivate(vcfFile, tfamFile, true);
	}

	/**
	 * Private variant
	 */
	public void test_04() {
		Log.debug("Test");
		String vcfFile = "test/test.private.04.vcf";
		String tfamFile = "test/test.private.01.tfam";
		checkPrivate(vcfFile, tfamFile, true);
	}

	public void test_05() {
		Log.debug("Test");
		checkPrivate("test/test.private.05.vcf", "test/test.private.05.tfam", false);
	}

	public void test_06() {
		Log.debug("Test");
		checkPrivate("test/test.private.06.vcf", "test/test.private.05.tfam", true);
	}

	public void test_07() {
		Log.debug("Test");
		checkPrivate("test/test.private.07.vcf", "test/test.private.05.tfam", true);
	}

	public void test_08() {
		Log.debug("Test");
		checkPrivate("test/test.private.08.vcf", "test/test.private.05.tfam", false);
	}

}
