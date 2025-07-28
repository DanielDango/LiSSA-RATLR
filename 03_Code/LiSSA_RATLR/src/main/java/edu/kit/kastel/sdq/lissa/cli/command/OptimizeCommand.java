/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.cli.command;

import static edu.kit.kastel.sdq.lissa.cli.command.EvaluateCommand.loadConfigs;

import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.sdq.lissa.ratlr.Evaluation;
import edu.kit.kastel.sdq.lissa.ratlr.Optimization;

import picocli.CommandLine;

@CommandLine.Command(
        name = "optimize",
        mixinStandardHelpOptions = true,
        description = "Optimizes a prompt for usage in the pipeline")
public class OptimizeCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(OptimizeCommand.class);

    @CommandLine.Option(
            names = {"-c", "--configs"},
            arity = "1..*",
            description =
                    "Specifies one or more config paths to be invoked by the pipeline iteratively. If the path points to a directory, all files inside are chosen to get invoked.")
    private Path[] optimizationConfigs;

    @CommandLine.Option(
            names = {"-e", "--eval"},
            arity = "0..*",
            description = "Specifies optional evaluation config paths to be invoked by the pipeline iteratively. "
                    + "Each evaluation configuration will be used with each optimization config."
                    + "If the path points to a directory, all files inside are chosen to get invoked.")
    private Path[] evaluationConfigs;

    @Override
    public void run() {
        List<Path> configsToOptimize = loadConfigs(optimizationConfigs);
        List<Path> configsToEvaluate = loadConfigs(evaluationConfigs);
        logger.info(
                "Found {} optimization config files and {} evaluation config files to invoke",
                configsToOptimize.size(),
                configsToEvaluate.size());

        for (Path optimizationConfig : configsToOptimize) {
            logger.info("Invoking the optimization pipeline with '{}'", optimizationConfig);
            String optimizedPrompt = "";
            try {
                var optimization = new Optimization(optimizationConfig);
                optimizedPrompt = optimization.run();
            } catch (Exception e) {
                logger.warn("Exception details", e);
                logger.warn(
                        "Optimization configuration '{}' threw an exception: {}", optimizationConfig, e.getMessage());
            }
            for (Path evaluationConfig : configsToEvaluate) {
                logger.info("Invoking the evaluation pipeline with '{}'", evaluationConfig);
                try {
                    var evaluation = new Evaluation(evaluationConfig, optimizedPrompt);
                    evaluation.run();
                } catch (Exception e) {
                    logger.warn(
                            "Evaluation configuration '{}' threw an exception: {}", evaluationConfig, e.getMessage());
                }
            }
        }
    }
}
