package de.philjoel.packmixer;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Durability protection: a tool, weapon or other item with durability can't be used any more once it is
 * almost broken, so it never breaks by accident. Same options as the "Durability" mod:
 * limit type PERCENTAGE / DURABILITY / BOTH, minimum percentage, minimum durability, ignore unenchanted items,
 * ignored items (item ids, * as placeholder) and "use ignored items as whitelist".
 */
public final class DurabilityGuard {
	private static long lastNotice;

	private DurabilityGuard() {
	}

	public static void register() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> check(player, level, hand));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> check(player, level, hand));
		UseItemCallback.EVENT.register((player, level, hand) -> check(player, level, hand));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> check(player, level, hand));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			// opening a chest, door, crafting table ... with a worn tool in the hand is still fine
			if (!player.isShiftKeyDown() && level.getBlockState(hit.getBlockPos()).getMenuProvider(level, hit.getBlockPos()) != null)
				return InteractionResult.PASS;
			return check(player, level, hand);
		});
	}

	private static InteractionResult check(Player player, Level level, InteractionHand hand) {
		// only for yourself, only on the client (the integrated server must not block anything)
		if (!level.isClientSide() || player != Minecraft.getInstance().player) return InteractionResult.PASS;
		if (player.isCreative() || player.isSpectator()) return InteractionResult.PASS;
		ItemStack stack = player.getItemInHand(hand);
		if (!blocked(stack)) return InteractionResult.PASS;
		notice(stack);
		return InteractionResult.FAIL;
	}

	/** True if this item may not be used right now. */
	public static boolean blocked(ItemStack stack) {
		PackMixerConfig.Settings s = PackMixerConfig.settings();
		if (!s.durabilityActive || stack.isEmpty() || !stack.isDamageableItem()) return false;
		if (s.durabilityIgnoreUnenchanted && !stack.isEnchanted()) return false;
		boolean listed = listed(stack, s.durabilityIgnoredItems);
		if (s.durabilityWhitelist ? !listed : listed) return false;
		int max = stack.getMaxDamage();
		int left = max - stack.getDamageValue();
		boolean lowPercent = left * 100.0 / max < s.durabilityMinPercent;
		boolean lowValue = left < s.durabilityMinDurability;
		return switch (s.durabilityLimitType) {
			case "DURABILITY" -> lowValue;
			case "BOTH" -> lowPercent || lowValue;
			default -> lowPercent;
		};
	}

	/** Item id in the list? "minecraft:stone_*" style placeholders allowed, "minecraft:" may be left out. */
	static boolean listed(ItemStack stack, List<String> list) {
		if (list == null || list.isEmpty()) return false;
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		for (String raw : list) {
			String p = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
			if (p.isEmpty()) continue;
			if (!p.contains(":")) p = "minecraft:" + p;
			if (matches(id, p)) return true;
		}
		return false;
	}

	static boolean matches(String id, String pattern) {
		StringBuilder re = new StringBuilder();
		String[] parts = pattern.split("\\*", -1);
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) re.append(".*");
			if (!parts[i].isEmpty()) re.append(java.util.regex.Pattern.quote(parts[i]));
		}
		return id.matches(re.toString());
	}

	private static void notice(ItemStack stack) {
		long now = System.currentTimeMillis();
		if (now - lastNotice < 1500) return;
		lastNotice = now;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		int max = stack.getMaxDamage();
		int left = max - stack.getDamageValue();
		mc.player.sendOverlayMessage(Component.translatable("packmixer.durability.blocked", stack.getHoverName(), left, max,
				Math.round(left * 100.0 / max)).withStyle(ChatFormatting.RED));
	}
}
