import json
import os

CONFIG_DIR = "./configs/optimization"
OPTIMIZER_MODE_PLACEHOLDER = "<<OPTIMIZER_MODE>>"
PROMPT_PLACEHOLDER = "<<PROMPT>>"
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
    "name" : "cosine_similarity",
    "args" : {
      "max_results" : "<<RETRIEVAL_COUNT>>"
    }
  },
  "prompt_optimizer": {
    "name" : \"""" + OPTIMIZER_MODE_PLACEHOLDER + """\",
    "args" : {
      "prompt": """ + PROMPT_PLACEHOLDER + """,
      <<ARGS>>
    }
  },
  "classifier" : {
    "name" : "mock",
    "args" : {
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
datasets = ["GANNT", "ModisDataset", "CCHIT", "WARC", "dronology", "CM1-NASA"]
postprocessors = ["req2req", "identity", "identity", "req2req", "identity", "identity"]
retrieval_counts = [str(x) for x in [4, 4, 4, 4, 4, 4]]

optimizer_modes = ["simple", "iterative"]
gpt_models = ["gpt-4o-mini-2024-07-18", "gpt-4o-2024-08-06"]
gpt_models = ["gpt-4o-mini-2024-07-18"]
ollama_models = ["llama3.1:8b-instruct-fp16", "codellama:13b"]
ollama_models = []
prompts = ["Question: Here are two parts of software development artifacts.\n\n            {source_type}: '''{source_content}'''\n\n            {target_type}: '''{target_content}'''\n            Are they related?\n\n            Answer with 'yes' or 'no'.",
           "Below are two artifacts from the same software system. Is there a traceability link between (1) and (2)? Give your reasoning and then answer with 'yes' or 'no' enclosed in <trace> </trace>.\n (1) {source_type}: '''{source_content}''' \n (2) {target_type}: '''{target_content}''' ",
           "Below are two artifacts from the same software system. Is there a conceivable traceability link between (1) and (2)? Give your reasoning and then answer with 'yes' or 'no' enclosed in <trace> </trace>.\n (1) {source_type}: '''{source_content}''' \n (2) {target_type}: '''{target_content}''' ",
           "Below are two artifacts from the same software system.\n Is there a traceability link between (1) and (2)? Give your reasoning and then answer with 'yes' or 'no' enclosed in <trace> </trace>. Only answer yes if you are absolutely certain.\n (1) {source_type}: '''{source_content}''' \n (2) {target_type}: '''{target_content}''' "]

# Generate
if not os.path.exists(CONFIG_DIR):
    os.makedirs(CONFIG_DIR)
for model in gpt_models + ollama_models:
    model_dir = os.path.join(CONFIG_DIR, model.replace(":", "_"))
    for dataset in datasets:
        dataset_dir = os.path.join(model_dir, dataset)
        if not os.path.exists(dataset_dir):
            os.makedirs(dataset_dir)

gpt_args = ["\"model\": \"<<CLASSIFIER_MODEL>>\"".replace("<<CLASSIFIER_MODEL>>", model) for model in gpt_models]
ollama_args = ["\"model\": \"<<CLASSIFIER_MODEL>>\"".replace("<<CLASSIFIER_MODEL>>", model) for model in ollama_models]
for dataset, postprocessor, retrieval_count in zip(datasets, postprocessors, retrieval_counts):
    for classifier_mode in optimizer_modes:
        for gpt_model, gpt_arg in zip(gpt_models, gpt_args):
            for prompt in prompts:
                with open(f"{CONFIG_DIR}/{gpt_model}/{dataset}/{dataset}_{classifier_mode}_gpt_{gpt_model}.json", "w+") as f:
                    f.write(TEMPLATE.replace("<<DATASET>>", dataset)
                            .replace(OPTIMIZER_MODE_PLACEHOLDER, classifier_mode + "_openai")
                            .replace("<<ARGS>>", gpt_arg)
                            .replace(PROMPT_PLACEHOLDER, json.dumps(prompt))
                            .replace("<<POSTPROCESSOR>>", postprocessor)
                            .replace("<<RETRIEVAL_COUNT>>", retrieval_count))

        for ollama_model, ollama_arg in zip(ollama_models, ollama_args):
            model = ollama_model.replace(":", "_")
            for prompt in prompts:
                with open(f"{CONFIG_DIR}/{model}/{dataset}/{dataset}_{classifier_mode}_ollama_{model}.json", "w+") as f:
                    f.write(TEMPLATE.replace("<<DATASET>>", dataset)
                            .replace(OPTIMIZER_MODE_PLACEHOLDER, classifier_mode + "_ollama")
                            .replace("<<ARGS>>", ollama_arg)
                            .replace(PROMPT_PLACEHOLDER, json.dumps(prompt))
                            .replace("<<POSTPROCESSOR>>", postprocessor)
                            .replace("<<RETRIEVAL_COUNT>>", retrieval_count))
