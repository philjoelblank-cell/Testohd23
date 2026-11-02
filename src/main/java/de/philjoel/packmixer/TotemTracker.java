package de.philjoel.packmixer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** "TT" – counts how many totems each player around you has used. */
public final class TotemTracker {
	private static final Map<UUID, Integer> POPS = new HashMap<>();

	public static void onEntityEvent(Entity entity, byte event) {
		if (!(entity instanceof Player player)) return;
		if (event == 3) { // death -> reset
			Integer n = POPS.remove(player.getUUID());
			if (n != null && n > 0 && PackMixerConfig.settings().totemTracker) {
				send(Component.literal(player.getName().getString()).withStyle(ChatFormatting.WHITE)
						.append(Component.translatable("packmixer.tt.died", n).withStyle(ChatFormatting.GRAY)));
			}
			return;
		}
		if (event != 35) return;
		int n = POPS.merge(player.getUUID(), 1, Integer::sum);
		if (!PackMixerConfig.settings().totemTracker) return;
		boolean self = player == Minecraft.getInstance().player;
		send(Component.literal(player.getName().getString()).withStyle(self ? ChatFormatting.AQUA : ChatFormatting.WHITE)
				.append(Component.translatable("packmixer.tt.popped", n).withStyle(ChatFormatting.GOLD)));
	}

	private static void send(Component msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui == null) return;
		mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[TT] ").withStyle(ChatFormatting.LIGHT_PURPLE).append(msg));
	}

	public static void reset() {
		POPS.clear();
	}

	private TotemTracker() {
	}
}
