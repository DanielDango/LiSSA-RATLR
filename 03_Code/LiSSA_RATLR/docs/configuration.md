# Configuration Guide

## Overview

LiSSA uses JSON configuration files to define the behavior of the traceability link recovery process. This guide provides detailed information about available configuration options.

## Finding Configuration Options

Configuration options in LiSSA are defined in the code through several mechanisms:

1. **Component Classes**: Each component (e.g., `ArtifactProvider`, `Preprocessor`, `Classifier`) has a corresponding class that defines its configuration options. For example:
   - [`TextArtifactProvider`](../src/main/java/edu/kit/kastel/sdq/lissa/ratlr/artifactprovider/TextArtifactProvider.java) defines options for text-based artifact loading
   - [`CodeTreePreprocessor`](../src/main/java/edu/kit/kastel/sdq/lissa/ratlr/preprocessor/CodeTreePreprocessor.java) defines options for code tree processing
   - [`OpenAiEmbeddingCreator`](../src/main/java/edu/kit/kastel/sdq/lissa/ratlr/embeddingcreator/OpenAiEmbeddingCreator.java) defines options for OpenAI embedding generation
2. **Configuration Classes**: The [`Configuration`](../src/main/java/edu/kit/kastel/sdq/lissa/ratlr/configuration/Configuration.java) class serves as the central configuration container, defining the structure of the configuration file.
3. **Example Configurations**: You can find example configurations in the `example-configs` directory, which demonstrate different configuration setups for various use cases.
4. **Configuration Template**: The `config-template.json` file provides a template with all available configuration options and their default values.

## Basic Configuration

```json
{
  "cache_dir": "./cache/path",  // Directory for caching results
  "gold_standard_configuration": {
    "path": "path/to/answer.csv",  // Path to ground truth file
    "hasHeader": false  // Whether the CSV has a header
  }
}
```

## Artifact Providers

```json
{
  "source_artifact_provider": {
    "name": "text",  // or "recursive_text"
    "args": {
      "artifact_type": "requirement",  // Type of artifact
      "path": "path/to/artifacts",  // Path to artifacts
      "extensions": "java"  // For recursive_text provider
    }
  }
}
```

## Preprocessors

```json
{
  "source_preprocessor": {
    "name": "artifact",  // or "code_tree", "code_chunking", etc.
    "args": {
      "language": "JAVA",  // For code processors
      "chunk_size": 60,    // For chunking
      "compare_classes": false,  // For code tree
      "includeUsages": true,  // For UML processor
      "includeOperations": true,  // For UML processor
      "includeInterfaceRealizations": true  // For UML processor
    }
  }
}
```

## Embedding and Classification

This section describes how to configure the embedding creation and classification steps. You must configure either a single `classifier` or a list of `classifiers` for multi-stage pipelines.

### Single Classifier

Use the `classifier` field to configure a single classifier.

```json
{
  "embedding_creator": {
    "name": "openai",
    "args": {
      "model": "text-embedding-3-large"
    }
  },
  "classifier": {
    "name": "reasoning_openai",  // or "simple_openai", "mock"
    "args": {
      "model": "gpt-4o-mini-2024-07-18",
      ...  // Other classifier-specific arguments
    }
  }
}
```

### Multi-Stage Classifiers

Use the `classifiers` field to define a pipeline of classification stages. This field takes a list of lists of classifier configurations.

Each inner list represents a stage in the pipeline. Classifiers within the same stage are executed in parallel, and their results are aggregated using majority voting. The results of one stage are passed as input to the next stage.

```json
{
  "embedding_creator": {
    "name": "openai",
    "args": {
      "model": "text-embedding-3-large"
    }
  },
  "classifiers": [
    // Stage 1
    [
      {
        "name": "simple_openai",
        "args": {
          "model": "gpt-4o-mini-2024-07-18"
        }
      },
      {
        "name": "reasoning_openai",
        "args": {
          "model": "gpt-4o-mini-2024-07-18"
        }
      }
    ],
    // Stage 2
    [
      {
        "name": "reasoning_openai",
        "args": {
          "model": "gpt-4o-2024-05-13",
          // Additional arguments for the second stage
        }
      }
    ]
  ]
}
```

## Stores and Aggregation

```json
{
  "source_store": {
    "name": "custom",
    "args": {}
  },
  "target_store": {
    "name": "custom",
    "args": {
      "max_results": "20"  // or "infinity"
    }
  },
  "result_aggregator": {
    "name": "any_connection",
    "args": {
      "source_granularity": 0,
      "target_granularity": 0
    }
  }
}
```

For more information about using the CLI to run configurations, see the [CLI documentation](cli.md).
