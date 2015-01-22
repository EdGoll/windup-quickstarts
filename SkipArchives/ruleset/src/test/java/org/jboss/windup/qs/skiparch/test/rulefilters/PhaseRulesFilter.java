package org.jboss.windup.qs.skiparch.test.rulefilters;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.jboss.forge.furnace.util.Predicate;
import org.jboss.windup.config.RulePhase;
import org.jboss.windup.config.WindupRuleProvider;

/**
 * Filters the rules with given phases.
 *
 * @author Ondrej Zizka, ozizka at redhat.com
 */
public class PhaseRulesFilter implements Predicate<WindupRuleProvider>
{
    private final Set<RulePhase> phases;

    public PhaseRulesFilter( RulePhase ... phases )
    {
        this.phases = new HashSet(Arrays.asList(phases));
    }


    @Override
    public boolean accept(WindupRuleProvider provider)
    {
        return this.phases.contains( provider.getPhase() );
    }


    /**
     * Filters the rules with phase = REPORTING_*.
     *
     * @author Ondrej Zizka, ozizka at redhat.com
     */
    public static class ReportingRulesFilter extends PhaseRulesFilter
    {
        public ReportingRulesFilter()
        {
            super(RulePhase.REPORT_GENERATION, RulePhase.REPORT_RENDERING);
        }
    }

}// class