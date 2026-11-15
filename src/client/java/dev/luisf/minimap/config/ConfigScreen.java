package dev.luisf.minimap.config;

import dev.luisf.minimap.MinimapClient;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Config screen built from vanilla widgets. Changes apply when Save is pressed. */
public final class ConfigScreen extends Screen {
	private static final int TOP = 40;
	private static final int ROW = 22;
	private static final int WIDTH = 200;

	private final Screen parent;
	private final MinimapConfig working;

	public ConfigScreen(Screen parent) {
		super(Component.translatable("minimap.config.title"));
		this.parent = parent;
		this.working = MinimapClient.config().copy();
	}

	@Override
	protected void init() {
		int left = width / 2 - WIDTH / 2;
		int y = TOP;

		addRenderableWidget(withTooltip(new MiniRadiusSlider(left, y, WIDTH, 20), "minimap.config.mini_radius.tooltip"));
		y += ROW;
		addRenderableWidget(withTooltip(new MiniSizeSlider(left, y, WIDTH, 20), "minimap.config.mini_size.tooltip"));
		y += ROW;
		addRenderableWidget(withTooltip(CycleButton.onOffBuilder(working.rotate)
				.create(left, y, WIDTH, 20, Component.translatable("minimap.config.rotate"), (button, value) -> working.rotate = value),
				"minimap.config.rotate.tooltip"));
		y += ROW;
		addRenderableWidget(withTooltip(CycleButton.<MinimapConfig.Shape>builder(
						shape -> Component.translatable("minimap.config.shape." + shape.name().toLowerCase(Locale.ROOT)),
						working.shape)
				.withValues(MinimapConfig.Shape.values())
				.create(left, y, WIDTH, 20, Component.translatable("minimap.config.shape"), (button, value) -> working.shape = value),
				"minimap.config.shape.tooltip"));
		y += ROW;
		addRenderableWidget(withTooltip(new MapRadiusSlider(left, y, WIDTH, 20), "minimap.config.map_radius.tooltip"));
		y += ROW;
		addRenderableWidget(withTooltip(CycleButton.onOffBuilder(working.showCoordinates)
				.create(left, y, WIDTH, 20, Component.translatable("minimap.config.show_coordinates"), (button, value) -> working.showCoordinates = value),
				"minimap.config.show_coordinates.tooltip"));
		y += ROW;
		addRenderableWidget(withTooltip(CycleButton.onOffBuilder(working.showCompass)
				.create(left, y, WIDTH, 20, Component.translatable("minimap.config.show_compass"), (button, value) -> working.showCompass = value),
				"minimap.config.show_compass.tooltip"));
		y += ROW;
		addRenderableWidget(withTooltip(CycleButton.onOffBuilder(working.showPlayers)
				.create(left, y, WIDTH, 20, Component.translatable("minimap.config.show_players"), (button, value) -> working.showPlayers = value),
				"minimap.config.show_players.tooltip"));
		y += ROW;
		addRenderableWidget(withTooltip(CycleButton.onOffBuilder(working.showRespawn)
				.create(left, y, WIDTH, 20, Component.translatable("minimap.config.show_respawn"), (button, value) -> working.showRespawn = value),
				"minimap.config.show_respawn.tooltip"));
		y += ROW;
		addRenderableWidget(withTooltip(CycleButton.onOffBuilder(working.allowMultiplayer)
				.create(left, y, WIDTH, 20, Component.translatable("minimap.config.allow_multiplayer"), (button, value) -> working.allowMultiplayer = value),
				"minimap.config.allow_multiplayer.tooltip"));

		addRenderableWidget(Button.builder(Component.translatable("minimap.config.save"), button -> save())
				.bounds(width / 2 - WIDTH / 2 - 2, height - 28, WIDTH / 2, 20)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("minimap.config.cancel"), button -> onClose())
				.bounds(width / 2 + 2, height - 28, WIDTH / 2, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private void save() {
		working.normalize();
		MinimapClient.applyConfig(working);
		onClose();
	}

	private static <T extends net.minecraft.client.gui.components.AbstractWidget> T withTooltip(T widget, String key) {
		widget.setTooltip(Tooltip.create(Component.translatable(key)));
		return widget;
	}

	private final class MiniRadiusSlider extends AbstractSliderButton {
		private static final int RANGE = MinimapConfig.MAX_MINI_RADIUS - MinimapConfig.MIN_MINI_RADIUS;

		MiniRadiusSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty(), (double) (working.miniRadius - MinimapConfig.MIN_MINI_RADIUS) / RANGE);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("minimap.config.mini_radius", radius()));
		}

		@Override
		protected void applyValue() {
			working.miniRadius = radius();
		}

		private int radius() {
			return MinimapConfig.MIN_MINI_RADIUS + (int) Math.round(value * RANGE);
		}
	}

	private final class MiniSizeSlider extends AbstractSliderButton {
		private static final int RANGE = MinimapConfig.MAX_MINI_SIZE - MinimapConfig.MIN_MINI_SIZE;

		MiniSizeSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty(), (double) (working.miniSize - MinimapConfig.MIN_MINI_SIZE) / RANGE);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("minimap.config.mini_size", size()));
		}

		@Override
		protected void applyValue() {
			working.miniSize = size();
		}

		private int size() {
			return MinimapConfig.MIN_MINI_SIZE + (int) Math.round(value * RANGE);
		}
	}

	private final class MapRadiusSlider extends AbstractSliderButton {
		private static final int RANGE = MinimapConfig.MAX_MAP_RADIUS - MinimapConfig.MIN_MAP_RADIUS;

		MapRadiusSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty(), (double) (working.mapRadius - MinimapConfig.MIN_MAP_RADIUS) / RANGE);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("minimap.config.map_radius", radius()));
		}

		@Override
		protected void applyValue() {
			working.mapRadius = radius();
		}

		private int radius() {
			return MinimapConfig.MIN_MAP_RADIUS + (int) Math.round(value * RANGE);
		}
	}
}
