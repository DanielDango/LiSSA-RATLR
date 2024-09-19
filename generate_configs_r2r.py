TEMPLATE = """
{
  "cache_dir": "./cache/<<DATASET>>",

  "gold_standard_configuration": {
    "path": "./datasets/req2req/<<DATASET>>/answer.csv",
    "hasHeader": "true"
  },

  "source_artifact_provider" : {
    "name" : "text",
    "args" : {
      "artifact_type" : "requirement",
      "path" : "./datasets/req2req/<<DATASET>>/high"
    }
  },
  "target_artifact_provider" : {
    "name" : "text",
    "args" : {
      "artifact_type" : "requirement",
      "path" : "./datasets/req2req/<<DATASET>>/low"
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
      "max_results" : "4"
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
datasets = ["CM1Dataset", "GANNT", "ModisDataset"]
postprocessors = ["req2req", "req2req", "identity"]

classifier_modes = ["simple", "reasoning"]
gpt_models = ["gpt-4o-mini-2024-07-18", "gpt-4o-2024-08-06"]
ollama_models = ["llama3.1:8b-instruct-fp16", "codellama:13b", "gemma2:27b"]

# Generate
gpt_args = ["\"model\": \"<<CLASSIFIER_MODEL>>\"".replace("<<CLASSIFIER_MODEL>>", model) for model in gpt_models]
ollama_args = ["\"model\": \"<<CLASSIFIER_MODEL>>\"".replace("<<CLASSIFIER_MODEL>>", model) for model in ollama_models]

for dataset, postprocessor in zip(datasets, postprocessors):
    with open(f"./configs/req2req/{dataset}_no_llm.json", "w") as f:
        f.write(TEMPLATE.replace("<<DATASET>>", dataset).replace("<<CLASSIFIER_MODE>>", "mock").replace("<<ARGS>>", "").replace("<<POSTPROCESSOR>>", postprocessor))
    for classifier_mode in classifier_modes:
        for gpt_model, gpt_arg in zip(gpt_models, gpt_args):
            with open(f"./configs/req2req/{dataset}_{classifier_mode}_gpt_{gpt_model}.json", "w") as f:
                f.write(TEMPLATE.replace("<<DATASET>>", dataset).replace("<<CLASSIFIER_MODE>>", classifier_mode+"_openai").replace("<<ARGS>>", gpt_arg).replace("<<POSTPROCESSOR>>", postprocessor))

        for ollama_model, ollama_arg in zip(ollama_models, ollama_args):
            with open(f"./configs/req2req/{dataset}_{classifier_mode}_ollama_{ollama_model.replace(":", "_")}.json", "w") as f:
                f.write(TEMPLATE.replace("<<DATASET>>", dataset).replace("<<CLASSIFIER_MODE>>", classifier_mode+"_ollama").replace("<<ARGS>>", ollama_arg).replace("<<POSTPROCESSOR>>", postprocessor))