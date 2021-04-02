package org.snpsift.caseControl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.snpeff.collections.AutoHashMap;
import org.snpeff.fileIterator.BedFileIterator;
import org.snpeff.fileIterator.VcfFileIterator;
import org.snpeff.interval.Marker;
import org.snpeff.interval.Markers;
import org.snpeff.interval.Variant;
import org.snpeff.interval.tree.IntervalForest;
import org.snpeff.ped.PedPedigree;
import org.snpeff.ped.TfamEntry;
import org.snpeff.snpEffect.VariantEffect;
import org.snpeff.stats.CountByType;
import org.snpeff.util.Log;
import org.snpeff.vcf.EffFormatVersion;
import org.snpeff.vcf.VcfEffect;
import org.snpeff.vcf.VcfEntry;
import org.snpeff.vcf.VcfGenotype;
import org.snpeff.vcf.VcfHeader;
import org.snpeff.vcf.VcfHeaderInfo;
import org.snpsift.SnpSift;
import org.snpsift.SnpSiftCmdDbNsfp;

/**
 * Summarize a VCF annotated file
 *
 * @author pcingola
 */
public class SnpSiftCmdCaseControlSummary extends SnpSift {

	public static EffFormatVersion formatVersion = null;

	public static Boolean CaseControl[] = { true, false };

	PedPedigree pedPedigree;
	boolean headerSummary = true;
	String tfamFile, bedFile, vcfFile; // File names
	HashMap<String, Boolean> caseControls; // Cases and controls
	HashMap<String, String> groups; // ID -> Group map
	List<Variant> intervals; // Intervals to summarize
	ArrayList<String> groupNamesSorted; // Group names sorted alphabetically
	IntervalForest intForest; // Intervals
	List<String> sampleIds; // Sample IDs
	AutoHashMap<Marker, Summary> summaryByInterval; // Summary by interval
	ArrayList<String> infoFields; // Other info fields to include in summaries (per snp)

	public SnpSiftCmdCaseControlSummary() {
		super();
	}

	public SnpSiftCmdCaseControlSummary(String[] args) {
		super(args);
	}

	/**
	 * Load all data
	 */
	void load() {
		/**
		 * Read cases & controls file
		 * Format:	"id\tphenotype\n"
		 */
		Log.info("Reading cases & controls from " + tfamFile);
		pedPedigree = new PedPedigree(tfamFile);

		int countCase = 0, countCtrl = 0, countMissing = 0;
		caseControls = new HashMap<String, Boolean>();
		for (TfamEntry te : pedPedigree) {
			if (te.isCase() | te.isControl()) caseControls.put(te.getId(), te.isCase()); // Add to hash (do not add if 'missing'

			if (te.isCase()) countCase++;
			else if (te.isControl()) countCtrl++;
			else countMissing++;
		}
		Log.info("Total : " + caseControls.size() + " entries. Cases: " + countCase + ", controls: " + countCtrl + ", missing: " + countMissing);

		//---
		// Parse groups (families)
		//---
		groups = new HashMap<String, String>();
		HashSet<String> groupNames = new HashSet<String>();
		CountByType countByType = new CountByType();
		for (TfamEntry te : pedPedigree) {
			String id = te.getId();
			String group = te.getFamilyId();

			if ((group != null) && (!group.isEmpty())) {
				groups.put(id, group);
				groupNames.add(group);
				countByType.inc(group);
			}
		}
		Log.info("Total : " + groups.size() + " entries.\n" + countByType);

		// Sort group names
		groupNamesSorted = new ArrayList<String>();
		groupNamesSorted.addAll(groupNames);
		Collections.sort(groupNamesSorted);

		//---
		// Load intervals
		//---
		Log.info("Loading intervals from " + bedFile);
		BedFileIterator bed = new BedFileIterator(bedFile);
		intervals = bed.load();
		Log.info("Done. Number of intervals: " + intervals.size());
		if (intervals.size() <= 0) {
			System.err.println("Fatal error: No intervals!");
			System.exit(1);
		}

		// Create interval forest
		Log.info("Building interval forest.");
		intForest = new IntervalForest();
		for (Variant sc : intervals)
			intForest.add(sc);
		intForest.build();
	}

	/**
	 * Calculate Minimum allele frequency
	 * @param ve
	 * @return
	 */
	double maf(VcfEntry ve) {
		double maf = -1;
		if (ve.hasField("AF")) maf = ve.getInfoFloat("AF"); // Do we have it annotated as AF or MAF?
		else if (ve.hasField("MAF")) maf = ve.getInfoFloat("MAF");
		else {
			int ac = 0, count = 0;
			for (VcfGenotype gen : ve) {
				count += 2;
				int genCode = gen.getGenotypeCode();
				if (genCode > 0) ac += genCode;
			}
			maf = ((double) ac) / count;
		}

		// Always use the Minor Allele Frequency
		if (maf > 0.5) maf = 1.0 - maf;

		return maf;
	}

	@Override
	public void parseArgs(String[] args) {
		if (args.length <= 0) usage(null);

		for (int argc = 0; argc < args.length; argc++) {
			if (isOpt(args[argc])) usage("Unknown option '" + args[argc] + "'"); // Argument starts with '-' (most of them parse in SnpSift class)
			else if (tfamFile == null) tfamFile = args[argc];
			else if (bedFile == null) bedFile = args[argc];
			else if (vcfFile == null) vcfFile = args[argc];
		}

		// Sanity check
		if (tfamFile == null) usage("Missing paramter 'file.tped'");
		if (bedFile == null) usage("Missing paramter 'file.bed'");
		if (vcfFile == null) usage("Missing paramter 'file.vcf'");
	}

	/**
	 * Parse DBNSFP fields to add to summary
	 * @param vcf
	 */
	void parseDbNsfpFields(VcfFileIterator vcf) {
		VcfHeader vcfHeader = vcf.getVcfHeader();
		for (VcfHeaderInfo vcfInfo : vcfHeader.getVcfHeaderInfo())
			if (vcfInfo.getId().startsWith(SnpSiftCmdDbNsfp.DBNSFP_VCF_INFO_PREFIX)) infoFields.add(vcfInfo.getId());
		Collections.sort(infoFields);

	}

	/**
	 * Parse VCF header to get sample IDs
	 * @param vcf
	 */
	List<String> parseSampleIds(VcfFileIterator vcf) {
		int missingCc = 0, missingGroups = 0;

		// Read IDs
		List<String> sampleIds = vcf.getSampleNames();
		for (String id : sampleIds) {
			if (!caseControls.containsKey(id)) missingCc++;
			if (!groups.containsKey(id)) missingGroups++;
		}

		Log.info("Samples missing case/control info : " + missingCc);
		Log.info("Samples missing groups info       : " + missingGroups);

		// Too many missing IDs? Error
		if (((1.0 * missingCc) / sampleIds.size() > 0.5) || ((1.0 * missingCc) / sampleIds.size() > 0.5)) {
			Log.info("Fatal error: Too much missing data!");
			System.exit(1);
		}

		// Add an sort info fields
		VcfHeader vcfHeader = vcf.getVcfHeader();
		for (VcfHeaderInfo vcfInfo : vcfHeader.getVcfHeaderInfo())
			if (vcfInfo.getId().startsWith(SnpSiftCmdDbNsfp.DBNSFP_VCF_INFO_PREFIX)) infoFields.add(vcfInfo.getId());
		Collections.sort(infoFields);

		return sampleIds;
	}

	/**
	 * Parse a single VCF entry
	 * @param ve
	 */
	void parseVcfEntry(VcfEntry ve) {
		// Parse effect fields. Get highest functional class
		VariantEffect.FunctionalClass funcClass = VariantEffect.FunctionalClass.NONE;
		VcfEffect effMax = null;
		StringBuilder effAll = new StringBuilder();
		StringBuilder otherInfo = new StringBuilder();

		// Find highest impact effect
		for (VcfEffect eff : ve.getVcfEffects(formatVersion)) {
			if ((eff.getFunClass() != null) && (funcClass.ordinal() < eff.getFunClass().ordinal())) {
				funcClass = eff.getFunClass();
				effMax = eff;
			}
			effAll.append(eff + "\t");
		}

		// Ignore 'NONE' functional class
		if (funcClass != VariantEffect.FunctionalClass.NONE) {
			maf(ve);

			// Variant type (based on allele frequency)
			String variantAf = ve.alleleFrequencyType().toString();

			// Summary per line
			Summary summary = new Summary();

			// Does this entry intersect any intervals?
			Markers intersect = intForest.query(ve);

			// For all samples, update summaries
			int sampleNum = 0;
			for (String id : sampleIds) {
				String group = groups.get(id);
				Boolean caseControl = caseControls.get(id);

				// Should we ignore this entry?
				if (caseControl != null) {
					int count = ve.getVcfGenotype(sampleNum).getGenotypeCode();
					if (count > 0) {
						summary.count(group, caseControl, funcClass, variantAf, count); // Update summary for this variant

						// Update all intersecting intervals
						for (Marker interval : intersect) {
							Summary summaryInt = summaryByInterval.getOrCreate(interval); // Get (or create) summary
							summaryInt.count(group, caseControl, funcClass, variantAf, count); // Update summary
						}
					}
				}

				sampleNum++;
			}

			// Add other info fields
			for (String oi : infoFields) {
				String val = ve.getInfo(oi);
				if (val != null) otherInfo.append(val + "\t");
				else otherInfo.append("\t");
			}

			//---
			// Show
			//---

			// Show title
			if (headerSummary) {
				System.out.print("chr\tstart\tref\talt\tgene\teffect\taa\t" + summary.toStringTitle(groupNamesSorted));
				for (String oi : infoFields)
					System.out.print(oi + "\t");
				System.out.println("effect (max)\teffects (all)");
				headerSummary = false;
			}

			// Show "per variant" summary
			System.out.println(ve.getChromosomeName() //
					+ "\t" + (ve.getStart() + 1) //
					+ "\t" + ve.getRef() //
					+ "\t" + ve.getAltsStr() //
					+ "\t" + effMax.getGeneName() //
					+ "\t" + effMax.getEffectType() //
					+ "\t" + effMax.getAa() //
					+ "\t" + summary.toString(groupNamesSorted) //
					+ otherInfo //
					+ "\t" + effMax //
					+ "\t" + effAll //
			);
		}
	}

	/**
	 * Run summary
	 */
	@Override
	public boolean run() {
		// Load intervals, phenotypes, etc.
		load();

		// Initialize
		summaryByInterval = new AutoHashMap<Marker, Summary>(new Summary());
		infoFields = new ArrayList<String>();
		headerSummary = true;
		boolean headerVcf = true;

		// Read VCF
		VcfFileIterator vcf = new VcfFileIterator(vcfFile);
		vcf.setDebug(debug);

		for (VcfEntry ve : vcf) {
			if (headerVcf) {
				// Read header info
				headerVcf = false;
				sampleIds = parseSampleIds(vcf);
			}

			parseVcfEntry(ve);
		}

		// Show summaries by interval
		System.err.println("#Summary by interval\n");
		System.err.println("chr\tstart\tend\tname\t" + (new Summary()).toStringTitle(groupNamesSorted));
		for (Variant sc : intervals) {
			Summary summary = summaryByInterval.get(sc);
			if (summary != null) {
				System.err.println(sc.getChromosomeName() //
						+ "\t" + (sc.getStart() + 1) //
						+ "\t" + (sc.getEnd() + 1) //
						+ "\t" + sc.getId() //
						+ "\t" + summary.toString(groupNamesSorted) //
				);
			}
		}

		return true;
	}

	/**
	 * Show usage message
	 * @param msg
	 */
	@Override
	public void usage(String msg) {
		if (msg != null) {
			System.err.println("Error: " + msg);
			showCmd();
		}

		showVersion();

		System.err.println("Usage: java -jar " + SnpSift.class.getSimpleName() + ".jar ccs [-v] [-q] file.tfam file.bed file.vcf");
		System.err.println("Where:");
		System.err.println("\tfile.tfam  : File with genotypes and groups informations (groups are in familyId)");
		System.err.println("\tfile.bed   : File with regions of interest (intervals in BED format)");
		System.err.println("\tfile.vcf   : A VCF file (variants and genotype data)");
		System.err.println("\nOptions:");
		System.err.println("\t-q       : Be quiet");
		System.err.println("\t-v       : Be verbose");
		System.exit(1);
	}
}
