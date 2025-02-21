TEMPLATE_MS = """
{
  "cache_dir": "./cache-d2m/mediastore-<<SEED>>",
  "gold_standard_configuration": {
    "path": "./datasets/doc2model/mediastore/goldstandards/goldstandard-mediastore.csv",
    "hasHeader": "true",
    "swap_columns": "true"
  },

  "source_artifact_provider" : {
    "name" : "text",
    "args" : {
      "artifact_type" : "software architecture documentation",
      "path" : "./datasets/doc2model/mediastore/text_2016/mediastore.txt"
    }
  },
  "target_artifact_provider" : {
    "name" : "text",
    "args" : {
      "artifact_type" : "software architecture model",
      "path" : "./datasets/doc2model/mediastore/model_2016/uml/ms.uml"
    }
  },
  "source_preprocessor" : {
    "name" : "sentence",
    "args" : { }
  },
  "target_preprocessor" : {
    "name" : "model_uml",
    "args" : {
      "includeUsages" : false,
      "includeOperations" : false,
      "includeInterfaceRealizations" : false
    }
  },
  "embedding_creator" : {
    "name" : "openai",
    "args" : {
      "model": "text-embedding-3-large"
    }
  },
  "source_store" : {
    "name" : "custom",
    "args" : { }
  },
  "target_store" : {
    "name" : "custom",
    "args" : {
      "max_results" : "10"
    }
  },
  "classifier" : {
    "name" : "reasoning_openai",
    "args" : {
      "model" : "<<MODEL>>",
      "seed": "<<SEED>>"
    }
  },
  "result_aggregator" : {
    "name" : "any_connection",
    "args" : {
      "source_granularity" : "1",
      "target_granularity" : "1"
    }
  },
  "tracelinkid_postprocessor" : {
    "name" : "sad2sam",
    "args" : { }
  }
}
"""


# Configurations
seeds = ["133742243"]
models = ["gpt-4o-mini-2024-07-18", "gpt-4o-2024-05-13"]


import os

for seed in seeds:
    os.makedirs("./configs/doc2model/sad2sam", exist_ok=True)
    # Generate
    for model in models:
        with open(f"./configs/doc2model/sad2sam/mediastore_{seed}_{model}.json", "w") as f:
            f.write(TEMPLATE_MS.replace("<<SEED>>", seed).replace("<<MODEL>>", model))