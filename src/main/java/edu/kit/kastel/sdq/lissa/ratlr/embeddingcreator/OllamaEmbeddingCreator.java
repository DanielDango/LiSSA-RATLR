package edu.kit.kastel.sdq.lissa.ratlr.embeddingcreator;

import com.google.gson.Gson;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OllamaEmbeddingCreator extends EmbeddingCreator {
    private final String model;
    private final Cache cache;
    private final OllamaEmbeddingModel embeddingModel;

    public OllamaEmbeddingCreator(RatlrConfiguration.ModuleConfiguration configuration) {

        this.model = configuration.arguments().getOrDefault("model", "nomic-embed-text:v1.5");
        this.cache = CacheManager.getInstance().getCache(this.getClass().getSimpleName() + "_" + model);

        String host = System.getenv("OLLAMA_EMBEDDING_HOST");
        String user = System.getenv("OLLAMA_EMBEDDING_USER");
        String password = System.getenv("OLLAMA_EMBEDDING_PASSWORD");

        OllamaEmbeddingModel ollamaEmbedding = new OllamaEmbeddingModel(host, model, Duration.ofMinutes(5), 3);
        if (user != null && password != null) {
            ollamaEmbedding = new OllamaEmbeddingModelWithAuth(host, model, user, password);
        }

        this.embeddingModel = ollamaEmbedding;
    }

    @Override
    public List<float[]> calculateEmbeddings(List<Element> elements) {
        List<float[]> embeddings = new ArrayList<>();
        for (Element element : elements) {
            String key = element.getIdentifier();
            float[] cachedEmbedding = cache.get(key, float[].class);
            if (cachedEmbedding != null) {
                embeddings.add(cachedEmbedding);
            } else {
                float[] embedding = embeddingModel.embed(element.getContent()).content().vector();
                cache.put(key, embedding);
                embeddings.add(embedding);
            }
        }
        return embeddings;
    }

    private static class OllamaEmbeddingModelWithAuth extends OllamaEmbeddingModel {
        public OllamaEmbeddingModelWithAuth(String baseUrl, String modelName, String user, String password) {
            super(baseUrl, modelName, Duration.ofMinutes(5), 3);
            if (user != null && password != null) {
                this.setAuth(baseUrl, user, password, Duration.ofMinutes(5), 3);
            }
        }

        private void setAuth(String baseUrl, String user, String password, Duration timeout, int maxRetries) {
            Object ollamaApi = getApi();
            if (ollamaApi == null) {
                System.err.println("Could not set auth for Ollama API");
                return;
            }
            OkHttpClient okHttpClient = new OkHttpClient.Builder().callTimeout(timeout)
                    .connectTimeout(timeout)
                    .readTimeout(timeout)
                    .writeTimeout(timeout)
                    .authenticator((route, response) -> response.request().newBuilder().header("Authorization", Credentials.basic(user, password)).build())
                    .build();

            Retrofit retrofit = new Retrofit.Builder().baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(getGson()))
                    .build();
            var ollamaInterface = Arrays.stream(ollamaApi.getClass().getInterfaces())
                    .filter(it -> it.getSimpleName().equals("OllamaApi"))
                    .findFirst()
                    .orElseThrow();
            var api = retrofit.create(ollamaInterface);
            setApi(api);
        }

        private void setApi(Object api) {
            try {
                var clientField = this.getClass().getSuperclass().getDeclaredField("client");
                clientField.setAccessible(true);
                var client = clientField.get(this);
                var ollamaApiField = client.getClass().getDeclaredField("ollamaApi");
                ollamaApiField.setAccessible(true);
                ollamaApiField.set(client, api);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private Gson getGson() {
            try {
                var clientField = this.getClass().getSuperclass().getDeclaredField("client");
                clientField.setAccessible(true);
                var client = clientField.get(this);
                var gsonField = client.getClass().getDeclaredField("GSON");
                gsonField.setAccessible(true);
                return (Gson) gsonField.get(null);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        private Object getApi() {
            try {
                var clientField = this.getClass().getSuperclass().getDeclaredField("client");
                clientField.setAccessible(true);
                var client = clientField.get(this);
                var ollamaApiField = client.getClass().getDeclaredField("ollamaApi");
                ollamaApiField.setAccessible(true);
                return ollamaApiField.get(client);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

    }
}
