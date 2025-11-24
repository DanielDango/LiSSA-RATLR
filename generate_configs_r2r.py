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
      "max_results" : "<<RETRIEVAL_COUNT>>"
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
datasets = ["GANNT", "ModisDataset", "WARC", "dronology", "CM1-NASA"]
postprocessors = ["req2req", "identity", "req2req", "identity", "identity"]
retrieval_counts = [str(x) for x in [4, 4, 4, 4, 4]]

classifier_modes = ["simple", "reasoning"]
gpt_models = [] # ["gpt-4o-mini-2024-07-18", "gpt-4o-2024-08-06"]
ollama_models = [] # ["llama3.1:8b-instruct-fp16", "codellama:13b"]
openwebui_models = ["gpt-oss:120b", "qwen3-vl:235b-a22b-instruct", "mixtral:8x22b", "azure.gpt-5", "azure.gpt-4.1"]

# Generate
gpt_args = ["\"model\": \"<<CLASSIFIER_MODEL>>\"".replace("<<CLASSIFIER_MODEL>>", model) for model in gpt_models]
ollama_args = ["\"model\": \"<<CLASSIFIER_MODEL>>\"".replace("<<CLASSIFIER_MODEL>>", model) for model in ollama_models]
openwebui_args = ["\"model\": \"<<CLASSIFIER_MODEL>>\"".replace("<<CLASSIFIER_MODEL>>", model) for model in openwebui_models]

# gpt-5 needs temperature 1
openwebui_args = [arg if "gpt-5" not in arg else f"{arg}, \"temperature\": 1" for arg in openwebui_args]

dir = "req2req"

for dataset, postprocessor, retrieval_count in zip(datasets, postprocessors, retrieval_counts):
    with open(f"./configs/{dir}/{dataset}_no_llm.json", "w") as f:
        f.write(TEMPLATE.replace("<<DATASET>>", dataset).replace("<<CLASSIFIER_MODE>>", "mock").replace("<<ARGS>>", "").replace("<<POSTPROCESSOR>>", postprocessor).replace("<<RETRIEVAL_COUNT>>", retrieval_count))
    for classifier_mode in classifier_modes:
        for gpt_model, gpt_arg in zip(gpt_models, gpt_args):
            with open(f"./configs/{dir}/{dataset}_{classifier_mode}_gpt_{gpt_model}.json", "w") as f:
                f.write(TEMPLATE.replace("<<DATASET>>", dataset).replace("<<CLASSIFIER_MODE>>", classifier_mode+"_openai").replace("<<ARGS>>", gpt_arg).replace("<<POSTPROCESSOR>>", postprocessor).replace("<<RETRIEVAL_COUNT>>", retrieval_count))

        for ollama_model, ollama_arg in zip(ollama_models, ollama_args):
            with open(f"./configs/{dir}/{dataset}_{classifier_mode}_ollama_{ollama_model.replace(':', '_')}.json", "w") as f:
                f.write(TEMPLATE.replace("<<DATASET>>", dataset).replace("<<CLASSIFIER_MODE>>", classifier_mode+"_ollama").replace("<<ARGS>>", ollama_arg).replace("<<POSTPROCESSOR>>", postprocessor).replace("<<RETRIEVAL_COUNT>>", retrieval_count))

        for openwebui_model, openwebui_arg in zip(openwebui_models, openwebui_args):
            with open(f"./configs/{dir}/{dataset}_{classifier_mode}_owu_{openwebui_model.replace(':', '_')}.json", "w") as f:
                f.write(TEMPLATE.replace("<<DATASET>>", dataset).replace("<<CLASSIFIER_MODE>>", classifier_mode+"_openwebui").replace("<<ARGS>>", openwebui_arg).replace("<<POSTPROCESSOR>>", postprocessor).replace("<<RETRIEVAL_COUNT>>", retrieval_count))
