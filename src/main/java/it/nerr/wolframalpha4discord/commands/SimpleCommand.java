package it.nerr.wolframalpha4discord.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.core.spec.MessageCreateFields;
import it.nerr.wolframalpha4j.RestClient;
import it.nerr.wolframalpha4j.types.Units;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;

@Component
public class SimpleCommand implements SlashCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleCommand.class);
    private final Random random = new Random();

    @Autowired
    private RestClient wolframalphaRestClient;

    @Override
    public String getName() {
        return "simple";
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        Optional<ApplicationCommandInteractionOption> expressionOption = event.getOption("expression");
        if (expressionOption.isEmpty()) {
            return event.reply().withEphemeral(true).withContent("Please provide an expression to compute.").then();
        }
        String expression = expressionOption.flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString).get();

        return event.reply(InteractionApplicationCommandCallbackSpec.builder().content("Generating Image").build())
                .then(wolframalphaRestClient.getSimpleService().simpleQuery(expression, Units.METRIC, 2))
                .map(image -> {
                    String path = "cache/" + random.nextInt() + ".png";
                    try (OutputStream os = new FileOutputStream(path)) {
                        image.transferTo(os);
                    } catch (IOException e) {
                        throw new RuntimeException("Error while saving image", e);
                    }
                    return path;
                }).flatMap(path -> {
                            try {
                                FileInputStream is = new FileInputStream(path);
                                return event.editReply(InteractionReplyEditSpec.builder().content("")
                                        .addFile(MessageCreateFields.File.of(path, is)).build()).doAfterTerminate(() -> {
                                    try {
                                        is.close();
                                    } catch (IOException e) {
                                        LOGGER.error("Error closing file", e);
                                    }
                                    try {
                                        Files.delete(Path.of(path));
                                    } catch (IOException e) {
                                        LOGGER.error("Error deleting file", e);
                                    }
                                });
                            } catch (IOException e) {
                                LOGGER.error("Error while reading file", e);
                                return event.editReply(InteractionReplyEditSpec.builder().content("Error while " +
                                        "computing the input").build());
                            }
                        }
                ).then();
    }
}
