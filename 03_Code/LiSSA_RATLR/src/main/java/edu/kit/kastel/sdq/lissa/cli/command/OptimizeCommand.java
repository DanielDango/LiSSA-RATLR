/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.cli.command;

import static edu.kit.kastel.sdq.lissa.cli.command.EvaluateCommand.loadConfigs;

import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private Path[] configs;

    @Override
    public void run() {
        List<Path> configsToEvaluate = loadConfigs(configs);
        logger.info("Found {} config files to invoke", configsToEvaluate.size());

        for (Path config : configsToEvaluate) {
            logger.info("Invoking the pipeline with '{}'", config);
            try {
                var evaluation = new Optimization(config);
                logger.info("Running evaluation for configuration");
                evaluation.run();
            } catch (Exception e) {
                logger.warn("Exception details", e);
                logger.warn("Configuration '{}' threw an exception: {}", config, e.getMessage());
            }
        }
    }
}
