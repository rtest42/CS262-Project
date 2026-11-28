package edu.sjsu.cs262.gossip;

import edu.sjsu.cs262.gossip.config.ExperimentConfig;
import edu.sjsu.cs262.gossip.sim.Experiment;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Usage: java ... Main n=200 L=500 R=50 tickMs=50 algorithm=PUSH_SUM field=SMOOTH churn=0.5 loss=0.05 durationSec=30 seed=1
 */
public final class Main {

    private Main() {
    }

    static void main(String[] args) throws Exception {
        var cfg = ExperimentConfig.fromArgs(args);
        var csv = Path.of("results", String.format(
                Locale.ROOT,
                "%s_n%d_L%s_R%.3f_tick%dms_%s_churn%s_loss%s_seed%d.csv",
                cfg.algorithm().name().toLowerCase(Locale.ROOT),
                cfg.n(),
                cfg.L(),
                cfg.R(),
                cfg.meanTick().toMillis(),
                cfg.field().name().toLowerCase(Locale.ROOT),
                cfg.churnRate(),
                cfg.lossProbability(),
                cfg.seed())
        );

        System.out.println(cfg);
        var result = Experiment.run(cfg, csv);

        System.out.println("final sample: " + result.last());
        System.out.println("traffic:      " + result.traffic());
        System.out.println("conservation: " + result.conservation());
        System.out.println("csv:          " + csv.toAbsolutePath());
    }
}