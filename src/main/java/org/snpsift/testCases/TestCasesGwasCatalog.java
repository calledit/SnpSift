package org.snpsift.testCases;

import java.util.List;

import org.junit.Assert;
import org.snpeff.util.Gpr;
import org.snpeff.vcf.VcfEntry;
import org.snpsift.SnpSift;
import org.snpsift.SnpSiftCmdGwasCatalog;
import org.snpsift.gwasCatalog.GwasCatalog;
import org.snpsift.gwasCatalog.GwasCatalogEntry;

import junit.framework.TestCase;

/**
 * Test GWAS catalog classes
 *
 * @author pcingola
 */
public class TestCasesGwasCatalog extends TestCase {

	public static boolean verbose = false;

	List<VcfEntry> snpSiftCmdGwasCatalog(String args[]) {
		SnpSift snpSift = new SnpSift(args);
		SnpSiftCmdGwasCatalog snpSiftGwasCat = (SnpSiftCmdGwasCatalog) snpSift.cmd();
		snpSiftGwasCat.setVerbose(verbose);
		snpSiftGwasCat.setSuppressOutput(!verbose);
		return snpSiftGwasCat.run(true);
	}

	public void test_01() {
		Gpr.debug("Test");

		// Load catalog
		GwasCatalog gwasCatalog = new GwasCatalog("test/gwasCatalog/gwascatalog.txt.gz");

		// Search by chr:pos
		List<GwasCatalogEntry> list = gwasCatalog.get("20", 1759590 - 1);
		Assert.assertEquals(list.get(0).snps, "rs6080550");

		// Search by RS number
		list = gwasCatalog.getByRs("rs6080550");
		Assert.assertEquals(list.get(0).snps, "rs6080550");
	}

	public void test_02() {
		Gpr.debug("Test");

		String args[] = { "gwasCat" //
				, "-db" //
				, "test/gwasCatalog/gwascatalog.20130907.tsv" //
				, "test/test_gwascat_02.vcf" //
		};

		List<VcfEntry> vcfEntries = snpSiftCmdGwasCatalog(args);

		int countOk = 0;
		for (VcfEntry ve : vcfEntries) {
			if (verbose) Gpr.debug(ve);

			if (ve.getInfo("GWC").equals("Y") // We added this INFO fields for VCF entries in the test case that matching GWACAT database
					&& ve.getInfo("GWASCAT_TRAIT") != null) {
				countOk++;
			}
		}

		Assert.assertEquals("Two VCF entries should have been annotated", 2, countOk);
	}

}
