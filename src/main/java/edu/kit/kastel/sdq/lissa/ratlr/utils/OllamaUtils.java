package edu.kit.kastel.sdq.lissa.ratlr.utils;

import com.google.gson.Gson;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Arrays;

public final class OllamaUtils {
    private OllamaUtils() {
        throw new IllegalAccessError("Utility class");
    }

    public static void setAuthForOllama(Object ollamaWrapperWithClientField, String baseUrl, Duration timeout, String user, String password) {
        try {
            var client = getClient(ollamaWrapperWithClientField);
            var gson = getGson(client);
            var ollamaApi = getOllamaApi(client);

            OkHttpClient okHttpClient = new OkHttpClient.Builder().callTimeout(timeout)
                    .connectTimeout(timeout)
                    .readTimeout(timeout)
                    .writeTimeout(timeout)
                    .authenticator((route, response) -> response.request().newBuilder().header("Authorization", Credentials.basic(user, password)).build())
                    .build();
            Retrofit retrofit = new Retrofit.Builder().baseUrl(baseUrl).client(okHttpClient).addConverterFactory(GsonConverterFactory.create(gson)).build();

            var ollamaInterface = Arrays.stream(ollamaApi.getClass().getInterfaces())
                    .filter(it -> it.getSimpleName().equals("OllamaApi"))
                    .findFirst()
                    .orElseThrow();
            var newApi = retrofit.create(ollamaInterface);
            setOllamaApi(client, newApi);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setOllamaApi(Object client, Object newApi) throws Exception {
        var ollamaApiField = client.getClass().getDeclaredField("ollamaApi");
        ollamaApiField.setAccessible(true);
        ollamaApiField.set(client, newApi);
    }

    private static Object getOllamaApi(Object client) {
        try {
            var ollamaApiField = client.getClass().getDeclaredField("ollamaApi");
            ollamaApiField.setAccessible(true);
            return ollamaApiField.get(client);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Gson getGson(Object client) throws Exception {
        var gsonField = client.getClass().getDeclaredField("GSON");
        gsonField.setAccessible(true);
        return (Gson) gsonField.get(null);
    }

    private static Object getClient(Object ollamaWrapperWithClientField) throws Exception {
        Field clientField = ollamaWrapperWithClientField.getClass().getDeclaredField("client");
        clientField.setAccessible(true);
        return clientField.get(ollamaWrapperWithClientField);
    }

}
