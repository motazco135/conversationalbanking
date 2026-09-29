package io.github.motazco135.conversationalbanking.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ChatClientConfig {

    @Bean
    @Qualifier("openAiChatClientBuilder")
    ChatClient.Builder openAiChatClientBuilder(
            @Qualifier("openAiChatModel") OpenAiChatModel openAiChatModel,
            ObjectProvider<ChatClientBuilderCustomizer> customizers) {
        return applyCustomizers(
                ChatClient.builder(openAiChatModel),
                customizers
        );
    }

    private ChatClient.Builder applyCustomizers(
            ChatClient.Builder builder,
            ObjectProvider<ChatClientBuilderCustomizer> customizers) {

        customizers.orderedStream()
                .forEach(customizer -> customizer.customize(builder));
        return builder;
    }

    @Bean
    @Primary
    @Qualifier("openAiChatClient")
    ChatClient openAiChatClient(
            @Qualifier("openAiChatClientBuilder") ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.defaultAdvisors(ToolCallingAdvisor.builder().build(),SimpleLoggerAdvisor.builder()
                .build()).build();
    }
}
