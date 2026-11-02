package de.philjoel.packmixer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Own explosion sound for end crystals only (TNT etc. keep theirs): the sound of the pack chosen for
 * "End crystals" (if that pack has explode sounds), or silent.
 */
public final class CrystalSound {
	public static final Identifier SOUND_ID = Identifier.fromNamespaceAndPath(PackMixerClient.MOD_ID, "crystal_explode");
	private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(SOUND_ID);

	private record Seen(Vec3 pos, long tick) {
	}

	private static final Deque<Seen> recent = new ArrayDeque<>();
	private static long tick;
	/** Set by the pack builder: true if the chosen crystal pack brought its own explosion sound. */
	public static volatile boolean packHasSound;
	private static boolean checked;

	static void markChecked() {
		checked = true;
	}

	private static boolean packSound() {
		if (!checked) { // after a restart: look into the output pack once
			checked = true;
			packHasSound = java.nio.file.Files.exists(Minecraft.getInstance().getResourcePackDirectory()
					.resolve(PackMixerConfig.OUTPUT_PACK_NAME).resolve("assets").resolve(PackMixerClient.MOD_ID).resolve("sounds.json"));
		}
		return packHasSound;
	}

	/** Remembers where crystals were in the last ticks (the crystal may already be gone when the boom arrives). */
	static void tick(Minecraft mc) {
		tick++;
		while (!recent.isEmpty() && tick - recent.peekFirst().tick() > 10) recent.pollFirst();
		if (mc.level == null) return;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof EndCrystal) recent.addLast(new Seen(e.position(), tick));
		}
		while (recent.size() > 400) recent.pollFirst();
	}

	private static boolean isCrystal(double x, double y, double z) {
		for (Seen s : recent) {
			if (s.pos().distanceToSqr(x, y, z) < 2.25) return true;
		}
		return false;
	}

	/** @return true if the explosion sound was handled here (vanilla sound must not play) */
	public static boolean handle(ClientLevel level, double x, double y, double z, float volume, float pitch) {
		PackMixerConfig.Settings s = PackMixerConfig.settings();
		boolean crystal = isCrystal(x, y, z);
		if (crystal) {
			try {
				CrystalPacks.burst(level, x, y, z);
			} catch (Throwable e) {
				PackMixerClient.LOGGER.warn("Crystal burst failed: {}", e.toString());
			}
		}
		if (!s.crystalSilent && !packSound()) return false;
		if (!crystal) return false;
		if (s.crystalSilent) return true;
		level.playLocalSound(x, y, z, SOUND, SoundSource.BLOCKS, volume, pitch, false);
		return true;
	}

	private CrystalSound() {
	}
}
