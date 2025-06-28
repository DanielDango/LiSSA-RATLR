# Command Line Interface

The packaged jar offers a CLI by [Picocli](https://picocli.info/) with the following features.

## Evaluation (Default)

Runs the pipeline and evaluates it against the ground truth.

### Examples

```bash
# Run with default configuration
java -jar ./ratlr.jar eval

# Run with specific configuration file
java -jar ./ratlr.jar eval -c ./config.json

# Run with multiple configurations
java -jar ./ratlr.jar eval -c ./configs/simple.json ./configs/reasoning

# Run with directory of configurations
java -jar ./ratlr.jar eval -c ./configs
```

## Evaluation (Transitive)

Runs the pipeline in transitive mode and evaluates it. This is useful for multi-step traceability link recovery.

### Examples

```bash
# Run transitive evaluation with multiple configurations
java -jar ./ratlr.jar transitive -c ./configs/d2m.json ./configs/m2c.json -e ./configs/eval.json
```

