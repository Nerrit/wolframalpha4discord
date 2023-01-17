package it.nerr.wolframalpha4discord;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.rest.RestClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class WolframAlphaBot {

    public static void main(String[] args) {
        SpringApplication.run(WolframAlphaBot.class, args);

        while (true);
    }

    @Bean
    public it.nerr.wolframalpha4j.RestClient wolframalphaRestClient() {
        return it.nerr.wolframalpha4j.RestClient.create(System.getenv("WOLFRAM_APP_ID"));
    }

    @Bean
    public GatewayDiscordClient gatewayDiscordClient() {
        return DiscordClientBuilder.create(System.getenv("BOT_TOKEN")).build().gateway()
                .setInitialPresence(ignore -> ClientPresence.online(
                        ClientActivity.listening("your questions"))).login().block();
    }

	@Bean
	public RestClient discordRestClient(GatewayDiscordClient client) {
		return client.getRestClient();
	}

}
