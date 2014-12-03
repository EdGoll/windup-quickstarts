package org.jboss.windup.qs.victims;

import org.jboss.windup.qs.victims.model.VulnerabilityModel;
import com.redhat.victims.VictimsException;
import com.redhat.victims.database.VictimsDB;
import com.redhat.victims.database.VictimsDBInterface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;
import org.jboss.windup.config.GraphRewrite;
import org.jboss.windup.config.RulePhase;

import org.jboss.windup.config.WindupRuleProvider;
import org.jboss.windup.config.metadata.RuleMetadata;
import org.jboss.windup.config.operation.Iteration;
import org.jboss.windup.config.operation.ruleelement.AbstractIterationOperation;
import org.jboss.windup.config.query.Query;
import org.jboss.windup.graph.GraphContext;
import org.jboss.windup.graph.model.ArchiveModel;
import org.jboss.windup.graph.service.GraphService;
import org.jboss.windup.qs.victims.model.AffectedJarModel;
import org.jboss.windup.util.Logging;
import org.jboss.windup.util.exception.WindupException;
import org.ocpsoft.rewrite.config.Configuration;
import org.ocpsoft.rewrite.config.ConfigurationBuilder;
import org.ocpsoft.rewrite.context.Context;
import org.ocpsoft.rewrite.context.EvaluationContext;

/**
 * Victi.ms related rules: Archive hashes comparison.
 *
 * @author <a href="mailto:ozizka@redhat.com">Ondrej Zizka</a>
 */
public class CheckArchivesWithVictimsRules extends WindupRuleProvider
{
    private static final Logger log = Logging.get(CheckArchivesWithVictimsRules.class);


    @Override
    public RulePhase getPhase()
    {
        return RulePhase.POST_DISCOVERY;
    }

    @Override
    public void enhanceMetadata(Context context)
    {
        super.enhanceMetadata(context);
        context.put(RuleMetadata.CATEGORY, "Java/Security");
    }

    @Override
    public List<Class<? extends WindupRuleProvider>> getExecuteAfter()
    {
        return asClassList(UpdateVictimsDbRules.class, ComputeArchivesSHA512.class);
    }


    private VictimsDBInterface db;
    private Date victiomsLastUpdated;
    private int victimsRecordCount;


    // @formatter:off
    @Override
    public Configuration getConfiguration(final GraphContext grCtx)
    {

        try
        {
            db = VictimsDB.db();
            victimsRecordCount = db.getRecordCount();
            victiomsLastUpdated = db.lastUpdated();
        }
        catch(VictimsException ex)
        {
            throw new WindupException("Failed initializing Victi.ms database: " + ex.getMessage(), ex);
        }


        return ConfigurationBuilder.begin()

            // Check the jars
            .addRule()
            .when(Query.find(ArchiveModel.class))
            .perform(
                LogOperation.info("Victims database last updated on: " + new SimpleDateFormat("y-M-d H:m:s").format(victiomsLastUpdated)),
                LogOperation.info("Victims database records:   " + victimsRecordCount),

                Iteration.over(ArchiveModel.class) // TODO: Use IteratingRuleProvider?
                .perform(
                    new AbstractIterationOperation<ArchiveModel>()
                    {
                        @Override
                        public void perform(GraphRewrite event, EvaluationContext evCtx, ArchiveModel arch)
                        {
                            log.info("\tVicti.ms checking archive: " + arch.getFilePath());
                            GraphService<VulnerabilityModel> vulGS = new GraphService(event.getGraphContext(), VulnerabilityModel.class);
                            String hash = arch.asVertex().getProperty(ComputeArchivesSHA512.KEY_SHA512);
                            try
                            {
                                HashSet<String> vuls = db.getVulnerabilities(hash);
                                AffectedJarModel jar = GraphService.addTypeToModel(grCtx, arch, AffectedJarModel.class);
                                for( String vul : vuls )
                                {
                                    log.info("\t\tVulnerability found in " + arch.getFilePath() + ": " + vul);
                                    VulnerabilityModel vulM = vulGS.create().setCve(vul);//.setArchive(jar);
                                    vulM.setArchive(jar);
                                    log.info("" + vulM);
                                    jar.addVulnerability(vulM);
                                }
                            }
                            catch(VictimsException ex)
                            {
                                log.severe("Error in Victi.ms when getting vulnerabilities for " + arch.getArchiveName());
                            }
                            vulGS.commit();
                        }


                        @Override
                        public String toString()
                        {
                            return "Checking archives with Victi.ms";
                        }
                    }
                ).endIteration()
            ).withId("CheckArchivesWithVictims");
    }
    // @formatter:on
}