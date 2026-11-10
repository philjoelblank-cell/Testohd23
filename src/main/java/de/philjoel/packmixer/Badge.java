package de.philjoel.packmixer;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** The little Testohd "T" next to the names of players who use the mod (name tag and tab list). */
public final class Badge {
	private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath(PackMixerClient.MOD_ID, "badge"));

	public static boolean has(UUID uuid) {
		if (uuid == null || !PackMixerConfig.settings().showBadges) return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.getUUID().equals(uuid)) return true;
		return CosmeticSync.isUser(uuid);
	}

	public static Component prefix(Component name) {
		if (name == null) return null;
		try {
			if (name.getString().indexOf('') >= 0) return name;
			MutableComponent b = Component.literal("").withStyle(s -> s.withFont(FONT).withColor(0xFFFFFF));
			if (PackMixerConfig.settings().badgeLeft) return Component.empty().append(b).append(Component.literal(" ")).append(name);
			// Default: behind the name, so client icons (NoRisk etc.) and tier tags in front stay untouched.
			return Component.empty().append(name).append(Component.literal(" ")).append(b);
		} catch (Throwable e) {
			return name;
		}
	}

	private Badge() {
	}
}
