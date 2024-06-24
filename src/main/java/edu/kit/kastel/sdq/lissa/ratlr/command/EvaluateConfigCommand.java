package edu.kit.kastel.sdq.lissa.ratlr.command;

import edu.kit.kastel.sdq.lissa.ratlr.Evaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "c", mixinStandardHelpOptions = true, description = "Evaluates a specified config, can be called repeatedly")
public class EvaluateConfigCommand implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(EvaluateConfigCommand.class);

    @CommandLine.Parameters(description = "The path to the config file being used. If the path is a directory all files inside " + "are considered config files to be evaluated.")
    private Path configPath;

    @CommandLine.ParentCommand
    private EvaluateCommand evaluateCommand;

    @Override
    public void run() {
        List<Evaluation> evaluations = new ArrayList<>();
        if (!Files.isDirectory(configPath)) {
            evaluations.add(new Evaluation(evaluateCommand.getGroundTruth(), evaluateCommand.hasHeader(), configPath));
        } else {
            try (DirectoryStream<Path> configDir = Files.newDirectoryStream(configPath)) {
                configDir.forEach(dirEntry -> {
                    if (Files.isRegularFile(dirEntry)) {
                        evaluations.add(new Evaluation(evaluateCommand.getGroundTruth(), evaluateCommand.hasHeader(), dirEntry));
                    }
                });
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }

        evaluations.forEach(eval -> {
            try {
                eval.run();
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        });
    }
}
