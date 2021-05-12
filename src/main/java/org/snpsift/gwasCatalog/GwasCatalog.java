package org.snpsift.gwasCatalog;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.snpeff.collections.MultivalueHashMap;
import org.snpeff.interval.Chromosome;
import org.snpeff.snpEffect.ErrorWarningType;
import org.snpeff.util.Log;

/**
 * GWAS catalog table.
 * It is small enough to fit in memory
 *
 * Refernces:
 * 		http://www.genome.gov/gwastudies/
 *
 * @author pablocingolani
 *
 */
public class GwasCatalog implements Iterable<GwasCatalogEntry> {

	MultivalueHashMap<String, GwasCatalogEntry> gwasEntryByChrPos; // Entry by "chr:pos" key
	MultivalueHashMap<String, GwasCatalogEntry> gwasEntryByRs; // Entry by RS number

	public GwasCatalog(String fileName) {
		gwasEntryByChrPos = new MultivalueHashMap<>();
		gwasEntryByRs = new MultivalueHashMap<>();
		load(fileName);
	}

	public List<GwasCatalogEntry> get(String key) {
		return gwasEntryByChrPos.get(key);
	}

	/**
	 * Get all entries at a given chromosome & position
	 * Note: Position is zero based.
	 */
	public List<GwasCatalogEntry> get(String chrName, int pos) {
		return get(key(chrName, pos));
	}

	/**
	 * Get an entry based on 'RS' number
	 */
	public List<GwasCatalogEntry> getByRs(String rs) {
		return gwasEntryByRs.get(rs);
	}

	@Override
	public Iterator<GwasCatalogEntry> iterator() {
		LinkedList<GwasCatalogEntry> list = new LinkedList<>();

		for (String key : gwasEntryByChrPos.keySet())
			list.addAll(gwasEntryByChrPos.get(key));

		return list.iterator();
	}

	/**
	 * Create a 'key' string
	 */
	public String key(GwasCatalogEntry ge) {
		return Chromosome.simpleName(ge.chrId) + ":" + (ge.chrPos - 1);
	}

	/**
	 * Create a 'key' string
	 */
	public String key(String chrName, int pos) {
		return Chromosome.simpleName(chrName) + ":" + pos;
	}

	/**
	 * Load data from file
	 */
	protected void load(String fileName) {
		GwasCatalogFileIterator gfile = new GwasCatalogFileIterator(fileName);
		for (GwasCatalogEntry ge : gfile) {
			// Add entries by chr:pos
			String key = key(ge); // Note: Positions in GWAS file are one-based (we use zero-based coordinates everywhere).
			if (gwasEntryByChrPos.containsKey(key)) Log.warning(ErrorWarningType.WARNING_DUPLICATE_PRIMARY_KEY, "GwasCatalog entry chr:pos '" + key + "' already exists");
			gwasEntryByChrPos.add(key, ge);

			// Add entries by RS
			for (String rs : ge.snps.split(","))
				if (!rs.isEmpty() && !rs.equals("NR")) {
					if (gwasEntryByRs.containsKey(rs)) Log.warning(ErrorWarningType.WARNING_DUPLICATE_PRIMARY_KEY, "GwasCatalog entry RS number '" + rs + "' already exists");
					gwasEntryByRs.add(rs, ge);
				}
		}
	}
}
