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


package org.ensembl.healthcheck.testcase.compara;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.ensembl.healthcheck.DatabaseRegistry;
import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.DatabaseType;
import org.ensembl.healthcheck.ReportManager;
import org.ensembl.healthcheck.Species;
import org.ensembl.healthcheck.Team;
import org.ensembl.healthcheck.testcase.MultiDatabaseTestCase;
import org.ensembl.healthcheck.util.DBUtils;

/**
 * An EnsEMBL Healthcheck test case that looks for broken foreign-key
 * relationships.
 */

public class CheckSpeciesSetTag extends MultiDatabaseTestCase {

	/**
	 * Create an ForeignKeyMethodLinkSpeciesSetId that applies to a specific set
	 * of databases.
	 */
	public CheckSpeciesSetTag() {

		addToGroup("compara_homology");
		setDescription("Check the content of the species_set_tag table");
		setTeamResponsible(Team.COMPARA);

	}

	/**
	 * Run the test.
	 * 
	 * @param dbre
	 *            The database to use.
	 * @return true if the test passed.
	 * 
	 */
	public boolean run(DatabaseRegistry dbr) {

		boolean result = true;

		// Get compara DB connection
		DatabaseRegistryEntry[] allPrimaryComparaDBs = DBUtils
				.getMainDatabaseRegistry().getAll(DatabaseType.COMPARA);
		if (allPrimaryComparaDBs.length == 0) {
			result = false;
			ReportManager.problem(this, "", "Cannot find compara database");
			usage();
			return false;
		}

		DatabaseRegistryEntry[] allSecondaryComparaDBs = DBUtils
				.getSecondaryDatabaseRegistry().getAll(DatabaseType.COMPARA);

		Map speciesDbrs = getSpeciesDatabaseMap(dbr, true);

		// For each compara connection...
		for (int i = 0; i < allPrimaryComparaDBs.length; i++) {
			// ... check that the genome_db names are correct
			result &= checkProductionNames(allPrimaryComparaDBs[i], speciesDbrs);
			// ... check that we have one name tag for every MSA
			result &= checkNameTagForMultipleAlignments(allPrimaryComparaDBs[i]);

			if (allSecondaryComparaDBs.length == 0) {
				result = false;
				ReportManager
						.problem(
								this,
								allPrimaryComparaDBs[i].getConnection(),
								"Cannot find the compara database in the secondary server. This check expects to find a previous version of the compara database for checking that all the *named* species_sets are still present in the current database.");
				usage();
			}
			for (int j = 0; j < allSecondaryComparaDBs.length; j++) {
				// Check vs previous compara DB.
				result &= checkSetOfSpeciesSets(allPrimaryComparaDBs[i],
						allSecondaryComparaDBs[j]);
			}
		}

		return result;
	}

	public boolean checkSetOfSpeciesSets(
			DatabaseRegistryEntry primaryComparaDbre,
			DatabaseRegistryEntry secondaryComparaDbre) {

		boolean result = true;
		Connection con1 = primaryComparaDbre.getConnection();
		Connection con2 = secondaryComparaDbre.getConnection();
		HashMap primarySets = new HashMap();
		HashMap secondarySets = new HashMap();

		// Get list of species_set sets in the secondary server
		String sql = "SELECT value, count(*) FROM species_set_tag WHERE tag = 'name' GROUP BY value";
		try {
			Statement stmt1 = con1.createStatement();
			ResultSet rs1 = stmt1.executeQuery(sql);
			while (rs1.next()) {
				primarySets.put(rs1.getString(1), rs1.getInt(2));
			}
			rs1.close();
			stmt1.close();

			Statement stmt2 = con2.createStatement();
			ResultSet rs2 = stmt2.executeQuery(sql);
			while (rs2.next()) {
				secondarySets.put(rs2.getString(1), rs2.getInt(2));
			}
			rs2.close();
			stmt2.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		Iterator it = secondarySets.keySet().iterator();
		while (it.hasNext()) {
			Object next = it.next();
			Object primaryValue = primarySets.get(next);
			Integer secondaryValue = new Integer(secondarySets.get(next)
					.toString());
			if (primaryValue == null) {
				ReportManager.problem(
						this,
						con1,
						"Species set \"" + next.toString()
								+ "\" is missing (it appears " + secondaryValue
								+ " time(s) in "
								+ DBUtils.getShortDatabaseName(con2) + ")");
				result = false;
			} else if (new Integer(primaryValue.toString()) < secondaryValue) {
				ReportManager.problem(
						this,
						con1,
						"Species set \"" + next.toString()
								+ "\" is present only " + primaryValue
								+ " times instead of " + secondaryValue
								+ " as in "
								+ DBUtils.getShortDatabaseName(con2));
				result = false;
			}
		}

		return result;
	}


	public boolean checkProductionNames(DatabaseRegistryEntry comparaDbre,
			Map speciesDbrs) {

		boolean result = true;

		Connection con = comparaDbre.getConnection();

		// Get list of species (assembly_default) in compara
		Vector comparaSpeciesStr = new Vector();
		Vector comparaSpecies = new Vector();
		String sql2 = "SELECT genome_db.name FROM genome_db WHERE assembly_default = 1"
				+ " AND name <> 'Ancestral sequences' AND name <> 'ancestral_sequences' ORDER BY genome_db.genome_db_id";
		try {
			Statement stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery(sql2);
			while (rs.next()) {
				comparaSpeciesStr.add(rs.getString(1));
				comparaSpecies.add(Species.resolveAlias(rs.getString(1)
						.toLowerCase().replace(' ', '_')));
			}
			rs.close();
			stmt.close();
		} catch (Exception e) {
			e.printStackTrace();
		}



		// get all the production names
		boolean allSpeciesFound = true;

		for (int i = 0; i < comparaSpecies.size(); i++) {
			Species species = (Species) comparaSpecies.get(i);
			DatabaseRegistryEntry[] speciesDbr = (DatabaseRegistryEntry[]) speciesDbrs
					.get(species);
			if (speciesDbr != null) {
				Connection speciesCon = speciesDbr[0].getConnection();
				String productionName = DBUtils
						.getRowColumnValue(speciesCon,
								"SELECT meta_value FROM meta WHERE meta_key = \"species.production_name\"");

				if (!productionName.equals(comparaSpeciesStr.get(i))) {
					ReportManager.problem(this, con,
						"The genome_db '" + comparaSpeciesStr.get(i).toString() + "' as a different 'species.production_name' key: '" + productionName + "'"
						);
					result = false;
				}
			} else {
				ReportManager.problem(this, con, "No connection for "
						+ comparaSpeciesStr.get(i).toString());
				allSpeciesFound = false;
			}
		}
		if (!allSpeciesFound) {
			ReportManager.problem(this, con, "Cannot find all the species");
		}

        if (result) {
            ReportManager.correct(this, con, "PASSED genome_db and ncbi_taxa_name table share the same species names");
        }

        return result;

	}

	public boolean checkNameTagForMultipleAlignments(DatabaseRegistryEntry dbre) {

		boolean result = true;

		Connection con = dbre.getConnection();
		HashMap allSetsWithAName = new HashMap();
		HashMap allSetsForMultipleAlignments = new HashMap();

		if (tableHasRows(con, "species_set_tag")) {

			// Get list of species_set sets in the secondary server
			String sql1 = "SELECT species_set_id, value FROM species_set_tag WHERE tag = 'name'";

			// Find all the species_set_ids for multiple alignments
			String sql2 = "SELECT species_set_id, name FROM method_link_species_set JOIN method_link USING (method_link_id) WHERE"
					+ " class LIKE '%multiple_alignment%' OR class LIKE '%tree_alignment%' OR class LIKE '%ancestral_alignment%'";

			try {
				Statement stmt1 = con.createStatement();
				ResultSet rs1 = stmt1.executeQuery(sql1);
				while (rs1.next()) {
					allSetsWithAName.put(rs1.getInt(1), rs1.getString(2));
				}
				rs1.close();
				stmt1.close();

				Statement stmt2 = con.createStatement();
				ResultSet rs2 = stmt2.executeQuery(sql2);
				while (rs2.next()) {
					allSetsForMultipleAlignments.put(rs2.getInt(1),
							rs2.getString(2));
				}
				rs2.close();
				stmt2.close();
			} catch (Exception e) {
				e.printStackTrace();
			}

			Iterator it = allSetsForMultipleAlignments.keySet().iterator();
			while (it.hasNext()) {
				Object next = it.next();
				Object setName = allSetsWithAName.get(next);
				String multipleAlignmentName = allSetsForMultipleAlignments
						.get(next).toString();
				if (setName == null) {
					ReportManager.problem(this, con,
							"There is no name entry in species_set_tag for MSA \""
									+ multipleAlignmentName + "\".");
					result = false;
				}
			}

		} else {
			ReportManager
					.problem(
							this,
							con,
							"species_set_tag table is empty. There will be no aliases for multiple alignments");
			result = false;
		}

		return result;

	}


	private void usage() {

		ReportManager
				.problem(
						this,
						"USAGE",
						"run-healthcheck.sh -d ensembl_compara_.+ "
								+ " -d2 .+_core_.+ -d2 .+_compara_.+ CheckSpeciesSetTag");
	}

} // CheckSpeciesSetTag

