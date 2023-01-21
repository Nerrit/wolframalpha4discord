package it.nerr.wolframalpha4discord.commands;

import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import it.nerr.wolframalpha4j.RestClient;
import it.nerr.wolframalpha4j.types.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@Component
public class ComputeCommand implements SlashCommand {

    private final List<Format> formats = new ArrayList<>(9);
    private final Timeouts timeouts = new Timeouts(5.0, 5.0, 10.0, 10.0, 45.0, false);

    @Autowired
    private RestClient wolframalphaRestClient;

    public ComputeCommand() {
        formats.add(Format.PLAIN_TEXT);
        formats.add(Format.IMAGE);
        formats.add(Format.IMAGE_MAP);
        formats.add(Format.WOLFRAM_LANGUAGE_INPUT);
        formats.add(Format.WOLFRAM_LANGUAGE_OUTPUT);
        formats.add(Format.WOLFRAM_LANGUAGE_CELL_EXPRESSION);
        formats.add(Format.MATH_ML);
        formats.add(Format.SOUND);
        formats.add(Format.WAV);
    }

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

        return event.deferReply().then(wolframalphaRestClient.getFullResultsService().query(expression, timeouts,
                        new Miscellaneous.Builder().reinterpret(true).translation(true).units(Units.METRIC).build(), formats))
                .flatMap(result -> respond(result, event, expression)).then();
    }

    private EmbedCreateSpec createEmbed(QueryResult.Pod.Subpod subpod, String title) {
        return EmbedCreateSpec.builder().title(title).image(subpod.img().src()).description(subpod.plaintext()).build();
    }

    private Mono<Void> respond(QueryResult result, ChatInputInteractionEvent event, String expression) {
        if (result.success()) {
            final Context context = new Context(result, expression);
            return event.editReply().withEmbeds(
                            createEmbed(result.pods().get(context.getPod()).subpods().get(context.getSubpod()),
                                    result.pods().get(context.getPod()).title()))
                    .withComponents(ActionRow.of(context.getButtons())).then(event.getClient()
                            .on(ButtonInteractionEvent.class,
                                    buttonEvent -> parseButtonInteraction(buttonEvent, context, event))
                            .timeout(Duration.ofMinutes(30))
                            .onErrorResume(TimeoutException.class, ignore -> Mono.empty()).then());
        } else if (result.tips() != null && !result.tips().isEmpty()) {
            List<EmbedCreateFields.Field> fields = new ArrayList<>(result.tips().size());
            result.tips().forEach(tip -> fields.add(EmbedCreateFields.Field.of("", tip.text(), false)));
            return event.editReply().withEmbeds(
                    EmbedCreateSpec.builder().title("Error").addFields(fields.toArray(new EmbedCreateFields.Field[0]))
                            .build()).then();
        }
        return event.editReply().withEmbeds(EmbedCreateSpec.builder().title("Error")
                .description("An error occurred while computing the expression.").build()).then();
    }

    private Mono<Void> parseButtonInteraction(ButtonInteractionEvent buttonEvent, Context context,
                                              ChatInputInteractionEvent event) {
        if (buttonEvent.getMessage().get().getId().equals(event.getReply().block().getId())) {
            if (buttonEvent.getCustomId().equals("rewind")) {
                if (context.getPod() > 0) {
                    context.decrementPod();
                }
                context.setSubpod(0);
            } else if (buttonEvent.getCustomId().equals("arrow_backward")) {
                if (context.getSubpod() > 0) {
                    context.decrementSubpod();
                } else if (context.getPod() > 0) {
                    context.decrementPod();
                    context.setSubpod(context.getResult().pods().get(context.getPod()).subpods().size() - 1);
                }
            } else if (buttonEvent.getCustomId().equals("arrow_forward")) {
                if (context.getSubpod() < context.getResult().pods().get(context.getPod()).subpods().size() - 1) {
                    context.incrementSubpod();
                } else if (context.getPod() < context.getResult().pods().size() - 1) {
                    context.incrementPod();
                    context.setSubpod(0);
                }
            } else if (buttonEvent.getCustomId().equals("fast_forward")) {
                if (context.getPod() < context.getResult().pods().size() - 1) {
                    context.incrementPod();
                    context.setSubpod(0);
                } else if (context.getSubpod() <
                        context.getResult().pods().get(context.getPod()).subpods().size() - 1) {
                    context.incrementSubpod();
                }
            } else if (buttonEvent.getCustomId().equals("extend")) {
                context.setResult(wolframalphaRestClient.getFullResultsService().query(context.getExpression(), timeouts,
                        new Miscellaneous.Builder().reinterpret(true).translation(true).units(Units.METRIC).podstate(context.getResult().pods().get(
                                context.getPod()).subpods().get(context.getSubpod()).states().get(0).input()).build(),
                        formats).block());
            }
            context.updateButtons();
            return buttonEvent.edit().withEmbeds(
                            createEmbed(context.getResult().pods().get(context.getPod()).subpods().get(context.getSubpod()),
                                    context.getResult().pods().get(context.getPod()).title()))
                    .withComponents(ActionRow.of(context.getButtons())).then();
        }
        return Mono.empty();
    }

    private class Context {

        private final List<Button> buttons = new ArrayList<>(4);
        private int pod = 0;
        private int subpod = 0;
        private QueryResult result;
        private final String expression;

        public Context(QueryResult result, String expression) {
            this.result = result;
            this.expression = expression;
            buttons.add(Button.primary("rewind", ReactionEmoji.unicode("\u23EA")));
            buttons.add(Button.primary("arrow_backward", ReactionEmoji.unicode("\u25C0")));
            buttons.add(Button.primary("arrow_forward", ReactionEmoji.unicode("\u25B6")));
            buttons.add(Button.primary("fast_forward", ReactionEmoji.unicode("\u23E9")));
            buttons.add(Button.success("extend", ReactionEmoji.unicode("\uD83D\uDD0D")));
            updateButtons();
        }

        public void updateButtons() {
            buttons.set(0, buttons.get(0).disabled(pod == 0));
            buttons.set(1, buttons.get(1).disabled(pod == 0 && subpod == 0));
            buttons.set(2, buttons.get(2)
                    .disabled(pod == result.pods().size() - 1 && subpod == result.pods().get(pod).subpods().size() - 1));
            buttons.set(3, buttons.get(3).disabled(pod == result.pods().size() - 1));
            buttons.set(4,
                    buttons.get(4).disabled(result.pods().get(pod).subpods().get(subpod).states() == null ||
                            result.pods().get(pod).subpods().get(subpod).states().isEmpty()));
        }

        public void incrementPod() {
            pod++;
        }

        public void incrementSubpod() {
            subpod++;
        }

        public void decrementPod() {
            pod--;
        }

        public void decrementSubpod() {
            subpod--;
        }

        public int getPod() {
            return pod;
        }

        public void setPod(int pod) {
            this.pod = pod;
        }

        public int getSubpod() {
            return subpod;
        }

        public void setSubpod(int subpod) {
            this.subpod = subpod;
        }

        public List<Button> getButtons() {
            return buttons;
        }

        public QueryResult getResult() {
            return result;
        }

        public void setResult(QueryResult result) {
            this.result = result;
        }

        public String getExpression() {
            return expression;
        }
    }
}
