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
				burst(level, x, y, z);
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

	/** Extra explosion effect in the colours of the chosen animated crystal. */
	static void burst(ClientLevel level, double x, double y, double z) {
		if (!CrystalPacks.activeChecked()) CrystalPacks.selectionChanged(PackMixerConfig.loadSelection());
		CrystalPacks.Design d = CrystalPacks.active;
		if (d == null) return;
		net.minecraft.util.RandomSource r = level.getRandom();
		for (int i = 0; i < 64; i++) { // shock ring
			double a = i / 64.0 * Math.PI * 2;
			double s = 0.55 + r.nextDouble() * 0.15;
			level.addParticle(new net.minecraft.core.particles.DustParticleOptions(i % 2 == 0 ? d.c1() : d.c2(), 2.2f), x, y + 0.3, z,
					Math.cos(a) * s * 8, 0.05, Math.sin(a) * s * 8);
		}
		for (int i = 0; i < 70; i++) { // sphere of sparks
			double th = r.nextDouble() * Math.PI * 2, ph = Math.acos(r.nextDouble() * 2 - 1);
			double s = 0.25 + r.nextDouble() * 0.35;
			double vx = Math.sin(ph) * Math.cos(th) * s, vy = Math.cos(ph) * s, vz = Math.sin(ph) * Math.sin(th) * s;
			if (i % 3 == 0) level.addParticle(extra(d), x, y + 0.5, z, vx, vy, vz);
			else level.addParticle(new net.minecraft.core.particles.DustParticleOptions(i % 2 == 0 ? d.c2() : d.c1(), 1.4f),
					x, y + 0.5, z, vx * 9, vy * 9, vz * 9);
		}
		for (int i = 0; i < 20; i++) // column of light
			level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, x + (r.nextDouble() - 0.5) * 0.4, y + 0.2,
					z + (r.nextDouble() - 0.5) * 0.4, 0, 0.25 + r.nextDouble() * 0.35, 0);
	}

	private static net.minecraft.core.particles.ParticleOptions extra(CrystalPacks.Design d) {
		return switch (d.key()) {
			case "c_frost" -> net.minecraft.core.particles.ParticleTypes.SNOWFLAKE;
			case "c_plasma" -> net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK;
			case "c_inferno" -> net.minecraft.core.particles.ParticleTypes.FLAME;
			case "c_void" -> net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL;
			case "c_holy" -> net.minecraft.core.particles.ParticleTypes.END_ROD;
			default -> net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME;
		};
	}

	private CrystalSound() {
	}
}
