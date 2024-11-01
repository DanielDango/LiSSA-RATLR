TEMPLATE = """
{
  "cache_dir": "./cache-r2c/<<DATASET>>-<<SEED>>",

  "gold_standard_configuration": {
    "path": "./datasets/req2code/<<DATASET>>/answer.csv",
    "hasHeader": "false"
  },

  "source_artifact_provider" : {
    "name" : "text",
    "args" : {
      "artifact_type" : "requirement",
      "path" : "./datasets/req2code/<<DATASET>>/UC"
    }
  },
  "target_artifact_provider" : {
    "name" : "text",
    "args" : {
      "artifact_type" : "source code",
      "path" : "./datasets/req2code/<<DATASET>>/CC"
    }
  },
  "source_preprocessor" : {
    "name" : "artifact",
    "args" : {}
  },
  "target_preprocessor" : {
    "name" : "artifact",
    "args" : {}
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
      "max_results" : "20"
    }
  },
  "classifier" : {
    "name" : "<<CLASSIFIER_MODE>>",
    "args" : {
      <<ARGS>>
    }
  },
  "result_aggregator" : {
    "name" : "any_connection",
    "args" : {}
  },
  "tracelinkid_postprocessor" : {
    "name" : "<<POSTPROCESSOR>>",
    "args" : {}
  }
}
"""

# Configurations
datasets = ["SMOS", "eTour_en"]
postprocessors = ["req2code", "req2code"]

classifier_modes = ["simple", "reasoning"]
gpt_models = ["gpt-4o-mini-2024-07-18"]

seeds = ["133742243", "35418170"]

import os

for seed in seeds:
    os.makedirs(f"./configs/req2code-{seed}", exist_ok=True)
    # Generate
    gpt_args = [("\"model\": \"<<CLASSIFIER_MODEL>>\", \"seed\": \""+seed+"\"").replace("<<CLASSIFIER_MODEL>>", model) for model in gpt_models]
    for dataset, postprocessor in zip(datasets, postprocessors):
        with open(f"./configs/req2code-{seed}/{dataset}_no_llm.json", "w") as f:
            f.write(TEMPLATE.replace("<<SEED>>", seed).replace("<<DATASET>>", dataset).replace("<<CLASSIFIER_MODE>>", "mock").replace("<<ARGS>>", "").replace("<<POSTPROCESSOR>>", postprocessor))
        for classifier_mode in classifier_modes:
            for gpt_model, gpt_arg in zip(gpt_models, gpt_args):
                with open(f"./configs/req2code-{seed}/{dataset}_{classifier_mode}_gpt_{gpt_model}.json", "w") as f:
                    f.write(TEMPLATE.replace("<<SEED>>", seed).replace("<<DATASET>>", dataset).replace("<<CLASSIFIER_MODE>>", classifier_mode+"_openai").replace("<<ARGS>>", gpt_arg).replace("<<POSTPROCESSOR>>", postprocessor))

