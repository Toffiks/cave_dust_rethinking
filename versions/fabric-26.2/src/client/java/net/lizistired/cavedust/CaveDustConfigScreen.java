package net.lizistired.cavedust;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

final class CaveDustConfigScreen extends Screen {
    private final Screen parent;
    private final CaveDustConfig config;

    CaveDustConfigScreen(Screen parent) {
        super(Component.translatable("menu.cavedust.title"));
        this.parent = parent;
        this.config = CaveDustClient.config();
    }

    @Override
    protected void init() {
        int left = (this.width - 200) / 2;
        int row = (this.height - (20 + 5 * 24)) / 2;

        Button enabled = Button.builder(enabledText(), button -> {
                    config.toggleEnabled();
                    button.setMessage(enabledText());
                    button.setTooltip(enabledTooltip());
                })
                .bounds(left, row, 200, 20)
                .tooltip(enabledTooltip())
                .build();
        this.addRenderableWidget(enabled);

        row += 24;
        IntSlider amount = new IntSlider(left, row, 1, 100, config.particleMultiplier(),
                "menu.cavedust.particlemultiplier", config::setParticleMultiplier);
        amount.setTooltip(Tooltip.create(Component.translatable("menu.cavedust.particlemultiplier.tooltip")));
        this.addRenderableWidget(amount);

        row += 24;
        Button particle = Button.builder(particleText(), button -> {
                    config.iterateParticle();
                    button.setMessage(particleText());
                })
                .bounds(left, row, 200, 20)
                .tooltip(Tooltip.create(Component.translatable("menu.cavedust.particle.tooltip")))
                .build();
        this.addRenderableWidget(particle);

        row += 24;
        IntSlider radius = new IntSlider(left, row, 1, 50, config.width(),
                "menu.cavedust.width", config::setWidth);
        radius.setTooltip(Tooltip.create(Component.translatable("menu.cavedust.width.tooltip")));
        this.addRenderableWidget(radius);

        row += 24;
        this.addRenderableWidget(Button.builder(Component.translatable("menu.cavedust.reset"), button -> {
                    config.reset();
                    enabled.setMessage(enabledText());
                    enabled.setTooltip(enabledTooltip());
                    amount.setCurrent(config.particleMultiplier());
                    particle.setMessage(particleText());
                    radius.setCurrent(config.width());
                })
                .bounds(left, row, 200, 20)
                .tooltip(Tooltip.create(Component.translatable("menu.cavedust.reset.tooltip")))
                .build());

        row += 24;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"), button -> this.onClose())
                .bounds(left, row, 200, 20)
                .build());
    }

    private Component enabledText() {
        return Component.translatable("menu.cavedust.global." + config.enabled());
    }

    private Tooltip enabledTooltip() {
        return Tooltip.create(Component.translatable("menu.cavedust.global.tooltip." + config.enabled()));
    }

    private Component particleText() {
        return Component.translatable("menu.cavedust.particle", config.particleName());
    }

    @Override
    public void onClose() {
        config.saveIfDirty();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        config.saveIfDirty();
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 30, 0xFFFFFF);
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final int minimum;
        private final int maximum;
        private final String translationKey;
        private final IntConsumer consumer;

        private IntSlider(int x, int y, int minimum, int maximum, int current,
                          String translationKey, IntConsumer consumer) {
            super(x, y, 200, 20, Component.empty(), normalize(minimum, maximum, current));
            this.minimum = minimum;
            this.maximum = maximum;
            this.translationKey = translationKey;
            this.consumer = consumer;
            this.updateMessage();
        }

        private int current() {
            return minimum + (int) Math.round(this.value * (maximum - minimum));
        }

        private void setCurrent(int current) {
            this.value = normalize(minimum, maximum, current);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable(translationKey, current()));
        }

        @Override
        protected void applyValue() {
            consumer.accept(current());
        }

        private static double normalize(int minimum, int maximum, int value) {
            return (double) (value - minimum) / (double) (maximum - minimum);
        }
    }
}
