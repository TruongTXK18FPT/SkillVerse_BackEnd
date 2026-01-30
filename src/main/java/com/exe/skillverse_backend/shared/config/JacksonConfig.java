package com.exe.skillverse_backend.shared.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Configuration
public class JacksonConfig {

  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    // Ignore unknown properties like "num_cached_tokens" from Mistral AI API
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }

  /**
   * Customize RestClient to use ObjectMapper that ignores unknown properties.
   * This fixes the "num_cached_tokens" error from Mistral AI API.
   */
  @Bean
  public RestClientCustomizer restClientCustomizer(ObjectMapper objectMapper) {
    return restClientBuilder -> restClientBuilder.messageConverters(converters -> {
      converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
      converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
    });
  }
}
