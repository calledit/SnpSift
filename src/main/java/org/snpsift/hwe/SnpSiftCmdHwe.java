package org.snpsift.hwe;

import java.util.List;

import org.snpeff.fileIterator.VcfFileIterator;
import org.snpeff.util.Gpr;
import org.snpeff.util.Log;
import org.snpeff.vcf.VcfEntry;
import org.snpeff.vcf.VcfHeaderEntry;
import org.snpeff.vcf.VcfHeaderInfo;
import org.snpeff.vcf.VcfInfoType;
import org.snpsift.SnpSift;

/**
 * Calculate Hardy-Weinberg equilibrium and goodness of fit for each entry in a VCF file
 *
 * @author pablocingolani
 */
public class SnpSiftCmdHwe extends SnpSift {

	public static final int SHOW_EVERY = 1000;

	/**
	 * Main
	 */
	public static void main(String[] args) {
		SnpSiftCmdHwe vcfhwe = new SnpSiftCmdHwe(args);
		vcfhwe.run();
	}

	public SnpSiftCmdHwe() {
		super();
	}

	public SnpSiftCmdHwe(String[] args) {
		super(args);
	}

	@Override
	protected List<VcfHeaderEntry> headers() {
		List<VcfHeaderEntry> addh = super.headers();
		addh.add(new VcfHeaderInfo("HWE", VcfInfoType.Float, "1", "HardyWeinberg 'p'"));
		addh.add(new VcfHeaderInfo("HWEP", VcfInfoType.Float, "1", "HardyWeinberg p-value using Fisher exact test"));
		addh.add(new VcfHeaderInfo("HHWEPCHIWE", VcfInfoType.Float, "1", "HardyWeinberg p-value using Chi sqaure approximation"));
		return addh;
	}

	/**
	 * Parse command line arguments
	 */
	@Override
	public void parseArgs(String[] args) {
		if (args.length == 0) usage(null);

		for (int argc = 0; argc < args.length; argc++) {
			String arg = args[argc];

			if (isOpt(arg)) { // Is it a command line option?

				if (arg.equals("-h") || args[argc].equalsIgnoreCase("-help")) usage(null);
				else if (arg.equals("-v")) verbose = true;
				else if (arg.equals("-q")) verbose = false;
				else if (arg.equals("-d")) debug = false;
				else usage("Unknown option '" + args[argc] + "'");
			} else vcfInputFile = args[argc++];
		}

		// Sanity check
		if (vcfInputFile == null) vcfInputFile = "-";
	}

	/**
	 * Analyze the file (run multi-thread mode)
	 */
	@Override
	public boolean run() {
		Log.info("Reading '" + vcfInputFile + "'. Running single threaded mode.");

		VcfFileIterator vcfFile = new VcfFileIterator(vcfInputFile);
		vcfFile.setDebug(debug);

		VcfHwe vcfHwe = new VcfHwe();
		VcfHwe.debug = debug;

		// Read all vcfEntries
		int entryNum = 1;
		for (VcfEntry vcfEntry : vcfFile) {

			if (entryNum == 1) {
				headers();
				String headerStr = vcfFile.getVcfHeader().toString();
				if (!headerStr.isEmpty()) System.out.println(headerStr);
			}

			vcfHwe.hwe(vcfEntry, true);
			System.out.println(vcfEntry);

			// Show progress
			Gpr.showMark(entryNum++, SHOW_EVERY);
		}

		Log.info("Done: " + entryNum + " entries processed.");
		return true;
	}

	/**
	 * Show usage and exit
	 */
	@Override
	public void usage(String errMsg) {
		if (errMsg != null) System.err.println("Error: " + errMsg);
		System.err.println("Usage: java -jar " + SnpSift.class.getSimpleName() + "" + ".jar hwe [-v] [-q] [file.vcf]");
		System.err.println("\t-q       : Be quite");
		System.err.println("\t-v       : Be verbose");
		System.exit(1);
	}
}
