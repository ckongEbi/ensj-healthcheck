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
import org.apache.commons.lang.StringUtils;

import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.ReportManager;
import org.ensembl.healthcheck.Team;
import org.ensembl.healthcheck.testcase.Repair;
import org.ensembl.healthcheck.testcase.SingleDatabaseTestCase;
import org.ensembl.healthcheck.util.DBUtils;

/**                                                                                                                                                                                 
 * An EnsEMBL Healthcheck test case for MethodLinkSpeciesSetTag entries
 */

public class CheckMethodLinkSpeciesSetTag extends SingleDatabaseTestCase {

        private HashMap MLSSTagEntriesToAdd = new HashMap();

        private HashMap MLSSTagEntriesToRemove = new HashMap();

        public CheckMethodLinkSpeciesSetTag() {
                addToGroup("compara_genomic");
                addToGroup("compara_homology");
                setDescription("Tests that proper entries are in method_link_species_set_tag.");
                setTeamResponsible(Team.COMPARA);
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

                if (!DBUtils.checkTableExists(con, "method_link_species_set_tag")) {
                        result = false;
                        ReportManager.problem(this, con, "method_link_species_set_tag table not present");
                        return result;
                }

                // These methods return false if there is any problem with the test
                result &= checkSpeciesTreesArePresent(dbre);

                return result;
        }

/**                                                                                                                                                                             
* Check that the each multi-species analysis that uses a species tree has the species tree stored in the method_link_species_set_tag table.
*/

        private boolean checkSpeciesTreesArePresent(DatabaseRegistryEntry dbre) {

                boolean result = true;

                // get version from mlsstag table 
                Connection con = dbre.getConnection();

                // get all the links between conservation scores and multiple genomic alignments
        String sql = "SELECT method_link_species_set_id, IFNULL(tag, 'NULL'), IFNULL(value, 'NULL'),"
                + " method_link_species_set.name, count(distinct genome_db_id)"
                + " FROM method_link_species_set"
                + " JOIN method_link USING (method_link_id)"
                + " LEFT JOIN method_link_species_set_tag USING (method_link_species_set_id)"
                + " JOIN species_set USING (species_set_id)"
                + " WHERE (class LIKE 'GenomicAlignTree%' OR class LIKE '%multiple_alignment' OR class LIKE '%tree_node')"
	        + " AND tag = 'species_tree'"
                + " GROUP BY method_link_species_set_id";

        try {
                Statement stmt = con.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                        Integer methodLinkSpeciesSetIdInt = rs.getInt(1);
                        String metaKeyStr = rs.getString(2);
                        String treeStr = rs.getString(3);
                        String methodLinkSpeciesSetNameStr = rs.getString(4);
                        Integer numSpecies = rs.getInt(5);
                        if (metaKeyStr.equals("NULL")) {
                                ReportManager.problem(this, con, "MethodLinkSpeciesSet " + methodLinkSpeciesSetIdInt  +
                                                      " (" + methodLinkSpeciesSetNameStr  + ") does not have its tree in the method_link_species_set_tag table!");
                                result = false;
                        } else if (StringUtils.countMatches(treeStr, "(") != StringUtils.countMatches(treeStr, ")")) {
                                ReportManager.problem(this, con, "The tree for MethodLinkSpeciesSet " + methodLinkSpeciesSetIdInt  +
                                                      " (" + methodLinkSpeciesSetNameStr  + ") does not have the same number of opening and closing brackets!");
                                result = false;
                        } else if (StringUtils.countMatches(treeStr, ",") + 1 != numSpecies) {
                                ReportManager.problem(this, con, "The tree for MethodLinkSpeciesSet " + methodLinkSpeciesSetIdInt  +
                                                      " (" + methodLinkSpeciesSetNameStr  + ") does not have the right number of leaves!");
                                result = false;
                        }
                }
                rs.close();
                stmt.close();
        } catch (SQLException se) {
                se.printStackTrace();
                result = false;
        }

        return result;

        }
}
