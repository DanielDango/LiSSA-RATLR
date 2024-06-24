package edu.kit.kastel.sdq.lissa.ratlr.command;

import edu.kit.kastel.sdq.lissa.ratlr.Evaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;

@CommandLine.Command(name = "eval", mixinStandardHelpOptions = true, subcommandsRepeatable = true, description = "Evaluates the model on the default config", subcommands = EvaluateConfigCommand.class)
public class EvaluateCommand implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(EvaluateCommand.class);

    @CommandLine.Parameters(description = "The path to the ground truth file.")
    private Path groundTruth;

    @CommandLine.Option(names = { "-h", "--header" }, description = "Skips the first line of the ground truth due to being a header.")
    private boolean hasHeader;

    @Override
    public void run() {
        try {
            new Evaluation(groundTruth, hasHeader, Path.of("config.json")).run();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    public Path getGroundTruth() {
        return groundTruth;
    }

    public boolean hasHeader() {
        return hasHeader;
    }
}
