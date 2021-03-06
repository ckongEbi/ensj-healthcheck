/*
 * Copyright [1999-2014] Wellcome Trust Sanger Institute and the EMBL-European Bioinformatics Institute
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

/*
 * Copyright (C) 2003 EBI, GRL
 * 
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.ensembl.healthcheck.testcase.generic;

import java.sql.Connection;

import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.DatabaseType;
import org.ensembl.healthcheck.ReportManager;
import org.ensembl.healthcheck.Team;
import org.ensembl.healthcheck.testcase.SingleDatabaseTestCase;
import org.ensembl.healthcheck.util.DBUtils;
import org.ensembl.healthcheck.util.SqlTemplate;
import java.util.List;

/**
 * Check that all seq_regions comprising genes are marked as toplevel in seq_region_attrib. Also checks that there is at least one
 * seq_region marked as toplevel (needed by compara). Also check that all toplevel seq regions are marked as such, and no seq
 * regions that are marked as toplevel are not toplevel.
 */

public class SeqRegionsTopLevel extends SingleDatabaseTestCase {

	/**
	 * Create a new SeqRegionsTopLevel testcase.
	 */
	public SeqRegionsTopLevel() {

		addToGroup("post_genebuild");
		addToGroup("pre-compara-handover");
		addToGroup("post-compara-handover");
                addToGroup("post-projection");
		
		setDescription("Check that all seq_regions comprising genes are marked as toplevel in seq_region_attrib, and that there is at least one toplevel seq_region. Also check that all toplevel seq regions are marked as such, and no seq regions that are marked as toplevel are not toplevel. Will check as well if the toplevel seqregions have information in the assembly table");
		setTeamResponsible(Team.GENEBUILD);
	}

	public void types() {

		removeAppliesToType(DatabaseType.RNASEQ);

	}

	/**
	 * Run the test.
	 * 
	 * @param dbre
	 *          The database to use.
	 * @return true if the test passed.
	 * 
	 */
	public boolean run(DatabaseRegistryEntry dbre) {

		boolean result = true;

		Connection con = dbre.getConnection();

                String AssemblyAccession = DBUtils.getMetaValue(con, "assembly.accession");

		int topLevelAttribTypeID = getAttribTypeID(con);
		if (topLevelAttribTypeID == -1) {
			return false;
		}

		result &= check_genes(con, topLevelAttribTypeID);

		result &= check_one_seq_region(con, topLevelAttribTypeID);

		result &= checkRankOne(dbre);

		result &= checkAssemblyTable(con, topLevelAttribTypeID);

                if (AssemblyAccession.contains("GCA")) {
                        result &= checkSynonyms(dbre, topLevelAttribTypeID);
                }

		return result;

	} // run

	// --------------------------------------------------------------------------

	private int getAttribTypeID(Connection con) {

		// check that all gene seq_regions have toplevel attributes
		String val = DBUtils.getRowColumnValue(con, "SELECT attrib_type_id FROM attrib_type WHERE code=\'toplevel\'");
		if (val == null || val.equals("")) {
			ReportManager.problem(this, con, "Can't find a seq_region attrib_type with code 'toplevel', exiting");
			return -1;
		}
		int topLevelAttribTypeID = Integer.parseInt(val);

		logger.info("attrib_type_id for toplevel: " + topLevelAttribTypeID);

		return topLevelAttribTypeID;

	}

	// --------------------------------------------------------------------------

	private boolean check_genes(Connection con, int topLevelAttribTypeID) {

		boolean result = true;

		int numTopLevelGenes = DBUtils.getRowCount(con, "SELECT COUNT(*) FROM seq_region_attrib sra, gene g WHERE sra.attrib_type_id = " + topLevelAttribTypeID + " AND sra.seq_region_id=g.seq_region_id");
		int numGenes = DBUtils.getRowCount(con, "SELECT COUNT(*) FROM gene");

		int nonTopLevelGenes = numGenes - numTopLevelGenes;

		if (nonTopLevelGenes > 0) {

			ReportManager.problem(this, con, nonTopLevelGenes + " genes are on seq_regions which are not toplevel; this may cause problems for Compara and slow down the mapper.");
			result = false;

		} else {

			ReportManager.correct(this, con, "All genes are on toplevel seq regions");

		}

		return result;
	}

	// --------------------------------------------------------------------------

	private boolean check_one_seq_region(Connection con, int topLevelAttribTypeID) {

		boolean result = true;

		// check for at least one toplevel seq_region
		int rows = DBUtils.getRowCount(con, "SELECT COUNT(*) FROM seq_region_attrib WHERE attrib_type_id=" + topLevelAttribTypeID);
		if (rows == 0) {

			ReportManager.problem(this, con, "No seq_regions are marked as toplevel. This may cause problems for Compara");
			result = false;

		} else {

			ReportManager.correct(this, con, rows + " seq_regions are marked as toplevel");

		}

		return result;

	}

	// --------------------------------------------------------------------------

	private boolean checkRankOne(DatabaseRegistryEntry dbre) {

		boolean result = true;

		Connection con = dbre.getConnection();

		// check that there is one co-ordinate system with rank = 1
		if (!dbre.isMultiSpecies()) {

			int rows = DBUtils.getRowCount(con, "SELECT COUNT(*) FROM coord_system WHERE rank=1");
			if (rows == 0) {

				ReportManager.problem(this, con, "No co-ordinate systems have rank = 1");
				result = false;

			} else if (rows > 1) {

				if (rows != dbre.getSpeciesIds().size()) {
					ReportManager.problem(this, con, rows + " rows in coord_system have a rank of 1. There should be " + dbre.getSpeciesIds().size());
					result = false;
				} else {
					ReportManager.correct(this, con, dbre.getSpeciesIds().size() + " co-ordinate systems with rank = 1");
				}

			} else {

				ReportManager.correct(this, con, "One co-ordinate system has rank = 1");

			}
		}
		return result;

	}

        // --------------------------------------------------------------------------

	private boolean checkAssemblyTable(Connection con, int topLevelAttribTypeID) {
		boolean result = true;

		int rows = DBUtils.getRowCount(con, "SELECT count(*) FROM seq_region_attrib sra LEFT JOIN assembly a on sra.seq_region_id = a.asm_seq_region_id, seq_region s, coord_system c "
				+ "where a.asm_seq_region_id is null and sra.attrib_type_id =" + topLevelAttribTypeID + " and c.coord_system_id = s.coord_system_id "
				+ " and s.seq_region_id = sra.seq_region_id and c.attrib not like '%sequence_level%'");

		if (rows > 0) {

			ReportManager.problem(this, con, "There are toplevel regions in the database with no assembly information.Try the query to get the regions: "
					+ "SELECT s.name FROM seq_region_attrib sra LEFT JOIN assembly a ON sra.seq_region_id = a.asm_seq_region_id, seq_region s, " + "coord_system c where sra.attrib_type_id = "
					+ topLevelAttribTypeID + " AND sra.seq_region_id = s.seq_region_id and a.asm_seq_region_id is null " + "and c.coord_system_id = s.coord_system_id and c.attrib not like '%sequence_level%'");

			result = false;
		} else {
			ReportManager.correct(this, con, "All toplevel regions have assembly information");
		}

		return result;

	}

	// --------------------------------------------------------------------------


        private boolean checkSynonyms(DatabaseRegistryEntry dbre, int topLevelAttribTypeID) {

                boolean result = true;
                SqlTemplate t = DBUtils.getSqlTemplate(dbre);
                Connection con = dbre.getConnection();

                int numMissingRefseq = 0;
                int numMissingINSDC = 0;
                String topSql = "SELECT DISTINCT s.name FROM seq_region s, seq_region_attrib sa WHERE s.seq_region_id = sa.seq_region_id AND s.name NOT LIKE 'LRG%' AND s.name NOT LIKE 'MT' AND attrib_type_id = " + topLevelAttribTypeID;
                String synsSql = "SELECT COUNT(*) FROM seq_region s, seq_region_synonym ss, external_db e WHERE s.seq_region_id = ss.seq_region_id AND ss.external_db_id = e.external_db_id AND s.name = ? AND e.db_name = ?";
                List<String> regions = t.queryForDefaultObjectList(topSql, String.class);

                for (String region : regions) {
                        int refseq = t.queryForDefaultObject(synsSql, Integer.class, region, "RefSeq_genomic");
                        if (refseq == 0) { 
                               // ReportManager.problem(this, con, region + " does not have a RefSeq_genomic synonym");
                               numMissingRefseq++;
                        }
                        int insdc = t.queryForDefaultObject(synsSql, Integer.class, region, "INSDC");
                        if (insdc == 0) {
                               // ReportManager.problem(this, con, region + " does not have an INSDC synonym");
                               numMissingINSDC++;
                        }
                }

                if (numMissingRefseq > 0) {

                        ReportManager.problem(this, con, numMissingRefseq + " regions do not have a RefSeq_genomic synonym");
                        result = false;

                } else if (numMissingINSDC > 0) {

                        ReportManager.problem(this, con, numMissingINSDC + " regions do not have an INSDC synonym");
                        result = false;

                } else {

                        ReportManager.correct(this, con, "All toplevel regions have the required synonyms");

                }

                return result;
        }


} // SeqRegionsTopLevel
