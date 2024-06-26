package ai.chat2db.server.web.api.controller.ai.rest.client;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.controller.ai.rest.model.RestAiCompletion;
import cn.hutool.http.ContentType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unfbx.chatgpt.sse.ConsoleEventSourceListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.apache.commons.lang3.StringUtils;

/**
 * Custom AI interface client
 * @author moji
 */
@Slf4j
public class RestAiStreamClient {
    /**
     * rest api url
     */
    @Getter
    private String apiUrl;

    /**
     * Whether to stream interface
     */
    @Getter
    private Boolean stream;
    /**
     * okHttpClient
     */
    @Getter
    private OkHttpClient okHttpClient;

    /**
     * Construct instance object
     *
     * @param url
     */
    public RestAiStreamClient(String url, Boolean stream) {
        this.apiUrl = url;
        this.stream = stream;
        this.okHttpClient = new OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(50, TimeUnit.SECONDS)
            .readTimeout(50, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Request RESTAI interface
     *
     * @param prompt
     * @param eventSourceListener
     */
    public void restCompletions(String prompt,
        EventSourceListener eventSourceListener) {
        log.info("Start calling the custom AI, prompt:{}", prompt);
        RestAiCompletion completion = new RestAiCompletion();
        completion.setPrompt(prompt);
        if (Objects.isNull(stream) || stream) {
            streamCompletions(completion, eventSourceListener);
            log.info("End calling streaming output custom AI");
            return;
        }
        nonStreamCompletions(completion, eventSourceListener);
        log.info("End calling non-streaming output custom AI");
    }

    /**
     * Q&A interface stream form
     *
     * @param completion open ai parameter
     * @param eventSourceListener sse listener
     * @see ConsoleEventSourceListener
     */
    public void streamCompletions(RestAiCompletion completion, EventSourceListener eventSourceListener) {
        if (Objects.isNull(eventSourceListener)) {
            log.error("Parameter exception: EventSourceListener cannot be empty");
            throw new ParamBusinessException();
        }
        if (StringUtils.isBlank(completion.getPrompt())) {
            log.error("Parameter exception: Prompt cannot be empty");
            throw new ParamBusinessException();
        }
        try {
            EventSource.Factory factory = EventSources.createFactory(this.okHttpClient);
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            String requestBody = mapper.writeValueAsString(completion);
            Request request = new Request.Builder()
                .url(this.apiUrl)
                .post(RequestBody.create(MediaType.parse(ContentType.JSON.getValue()), requestBody))
                .build();
            //Create event
            EventSource eventSource = factory.newEventSource(request, eventSourceListener);
        } catch (Exception e) {
            log.error("Request parameter parsing exception", e);
            throw new ParamBusinessException();
        }
    }

    /**
     * Request non-streaming output interface
     *
     * @param completion
     * @param eventSourceListener
     */
    public void nonStreamCompletions(RestAiCompletion completion, EventSourceListener eventSourceListener) {
        if (StringUtils.isBlank(completion.getPrompt())) {
            log.error("Parameter exception: Prompt cannot be empty");
            throw new ParamBusinessException();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            String requestBody = mapper.writeValueAsString(completion);
            Request request = new Request.Builder()
                .url(this.apiUrl)
                .post(RequestBody.create(MediaType.parse(ContentType.JSON.getValue()), requestBody))
                .build();

            this.okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    eventSourceListener.onFailure(null, e, null);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            String content = responseBody.string();
                            eventSourceListener.onEvent(null, "[DATA]", null, content);
                            eventSourceListener.onEvent(null, "[DONE]", null, "[DONE]");
                        }
                    } catch (IOException e) {
                        eventSourceListener.onFailure(null, e, response);
                    }
                }
            });

        } catch (Exception e) {
            log.error("Request parameter parsing exception", e);
            throw new ParamBusinessException();
        }
    }

}
