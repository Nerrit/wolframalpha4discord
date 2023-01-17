package it.nerr.wolframalpha4discord.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import it.nerr.wolframalpha4j.RestClient;
import it.nerr.wolframalpha4j.types.Units;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class SpeakCommand implements SlashCommand {

    @Autowired
    private RestClient wolframalphaRestClient;

    @Override
    public String getName() {
        return "speak";
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        String expression =
                event.getOption("expression").flatMap(ApplicationCommandInteractionOption::getValue)
                        .map(ApplicationCommandInteractionOptionValue::asString).get();
        return wolframalphaRestClient.getShortAnswerService().shortAnswer(expression, Units.METRIC)
                .flatMap(answer -> event.reply().withContent(answer)).then();
    }
}
