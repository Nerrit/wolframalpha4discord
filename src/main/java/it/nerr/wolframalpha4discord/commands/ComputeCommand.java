package it.nerr.wolframalpha4discord.commands;

import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.Embed;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.discordjson.json.EmojiData;
import it.nerr.wolframalpha4j.RestClient;
import it.nerr.wolframalpha4j.types.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeoutException;

@Component
public class ComputeCommand implements SlashCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeCommand.class);
    private final Random random = new Random();

    @Autowired
    private RestClient wolframalphaRestClient;

    @Override
    public String getName() {
        return "compute";
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        Optional<ApplicationCommandInteractionOption> expressionOption = event.getOption("expression");
        if (expressionOption.isEmpty()) {
            return event.reply().withEphemeral(true).withContent("Please provide an expression to compute.").then();
        }
        String expression = expressionOption.flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString).get();

        return event.deferReply().then(wolframalphaRestClient.getFullResultsService().query(expression)).map(Result::
                queryresult).flatMap(result -> {
            if (result.success()) {
                List<Button> buttons = new ArrayList<>(4);
                buttons.add(Button.primary("rewind", ReactionEmoji.unicode("\u23EA")));
                buttons.add(Button.primary("arrow_backward",
                        ReactionEmoji.unicode("\u25C0")));
                buttons.add(Button.primary("arrow_forward",
                        ReactionEmoji.unicode("\u25B6")));
                buttons.add(Button.primary("fast_forward",
                        ReactionEmoji.unicode("\u23E9")));
                final int[] pod = {0};
                final int[] subpod = {0};
                return event.editReply().withEmbeds(createEmbed(result.pods().get(pod[0]).subpods().get(subpod[0]),
                        result.pods().get(pod[0]).title())).withComponents(ActionRow.of(buttons)).then(
                        event.getClient().on(ButtonInteractionEvent.class, buttonEvent -> {
                                    if (buttonEvent.getMessage().get().getId().equals(event.getReply().block().getId())) {
                                        if (buttonEvent.getCustomId().equals("rewind")) {
                                            if (pod[0] > 0) {
                                                pod[0]--;
                                            }
                                            subpod[0] = 0;
                                        } else if (buttonEvent.getCustomId().equals("arrow_backward")) {
                                            if (subpod[0] > 0) {
                                                subpod[0]--;
                                            } else if (pod[0] > 0) {
                                                pod[0]--;
                                                subpod[0] = result.pods().get(pod[0]).subpods().size() - 1;
                                            }
                                        } else if (buttonEvent.getCustomId().equals("arrow_forward")) {
                                            if (subpod[0] < result.pods().get(pod[0]).subpods().size() - 1) {
                                                subpod[0]++;
                                            } else if (pod[0] < result.pods().size() - 1) {
                                                pod[0]++;
                                                subpod[0] = 0;
                                            }
                                        } else if (buttonEvent.getCustomId().equals("fast_forward")) {
                                            if (pod[0] < result.pods().size() - 1) {
                                                pod[0]++;
                                                subpod[0] = 0;
                                            } else if (subpod[0] < result.pods().get(pod[0]).subpods().size() - 1) {
                                                subpod[0]++;
                                            }
                                        }
                                        buttons.set(0, buttons.get(0).disabled(pod[0] == 0));
                                        buttons.set(1, buttons.get(1).disabled(pod[0] == 0 && subpod[0] == 0));
                                        buttons.set(2, buttons.get(2).disabled(pod[0] == result.pods().size() - 1 &&
                                                subpod[0] == result.pods().get(pod[0]).subpods().size() - 1));
                                        buttons.set(3, buttons.get(3).disabled(pod[0] == result.pods().size() - 1));

                                        return buttonEvent.edit()
                                                .withEmbeds(createEmbed(result.pods().get(pod[0]).subpods().get(subpod[0]),
                                                        result.pods().get(pod[0]).title())).withComponents(ActionRow.of(buttons)).then();
                                    }
                                    return Mono.empty();
                                }).timeout(Duration.ofMinutes(30)).onErrorResume(TimeoutException.class, ignore -> Mono.empty())
                                .then());
            } else if (result.tips() != null && !result.tips().isEmpty()) {
                List<EmbedCreateFields.Field> fields = new ArrayList<>(result.tips().size());
                result.tips().forEach(tip -> fields.add(EmbedCreateFields.Field.of("", tip.text(), false)));
                return event.editReply().withEmbeds(EmbedCreateSpec.builder().title("Error").addFields((EmbedCreateFields.Field[])fields.toArray()).build()).then();
            }
            return event.editReply().withEmbeds(EmbedCreateSpec.builder().title("Error").description("An error occurred while computing the expression.").build()).then();
        }).then();
    }

    public EmbedCreateSpec createEmbed(Result.QueryResult.Pod.Subpod subpod, String title) {
        return EmbedCreateSpec.builder().title(title).image(subpod.img().src()).description(subpod.plaintext()).build();
    }
}
