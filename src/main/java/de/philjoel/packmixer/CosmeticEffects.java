package de.philjoel.packmixer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/**
 * Particle effects for cosmetics (only you see them, they are spawned locally):
 * - auras from the "Effekte" tab (ring of particles circling around you)
 * - small accents for some items (halo sparkles, santa hat snow, devil flames ...)
 */
public final class CosmeticEffects {
	private static int tick;

	static void tick(Minecraft mc) {
		tick++;
		PackMixerConfig.Settings s = PackMixerConfig.settings();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || !s.cosmeticsEnabled || p.isInvisible() || mc.isPaused()) return;
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		if (firstPerson && !s.effectsFirstPerson) return;
		RandomSource r = p.getRandom();
		double x = p.getX(), y = p.getY(), z = p.getZ();
		double h = p.getBbHeight();

		// ---------------------------------------------------------------- aura
		String fx = Cosmetics.Slot.EFFECT.get();
		if (!fx.isEmpty()) {
			ParticleOptions type = switch (fx) {
				case "fx_sparkle" -> ParticleTypes.END_ROD;
				case "fx_souls" -> ParticleTypes.SOUL_FIRE_FLAME;
				case "fx_flames" -> ParticleTypes.FLAME;
				case "fx_snow" -> ParticleTypes.SNOWFLAKE;
				case "fx_cherry" -> ParticleTypes.CHERRY_LEAVES;
				case "fx_enchant" -> ParticleTypes.ENCHANT;
				case "fx_hearts" -> ParticleTypes.HEART;
				case "fx_notes" -> ParticleTypes.NOTE;
				default -> null;
			};
			if (type != null) {
				boolean slow = fx.equals("fx_hearts") || fx.equals("fx_notes");
				if (!slow || tick % 6 == 0) {
					// two particles circling around the player like a spiral
					for (int i = 0; i < 2; i++) {
						double a = tick * 0.25 + i * Math.PI;
						double ry = 0.2 + (Math.sin(tick * 0.05 + i) + 1) / 2 * (h - 0.2);
						double px = x + Math.cos(a) * 0.75, pz = z + Math.sin(a) * 0.75;
						double vy = fx.equals("fx_enchant") ? 0.4 : fx.equals("fx_snow") || fx.equals("fx_cherry") ? -0.02 : 0.01;
						double vx = fx.equals("fx_notes") ? r.nextDouble() : 0; // note particles use vx as colour
						mc.level.addParticle(type, px, y + ry, pz, vx, vy, 0);
					}
				}
			}
		}

		// ---------------------------------------------------------------- item accents
		String hat = Cosmetics.Slot.HEAD.get();
		if (hat.equals("halo") && tick % 5 == 0) {
			double a = r.nextDouble() * Math.PI * 2;
			mc.level.addParticle(ParticleTypes.END_ROD, x + Math.cos(a) * 0.3, y + h + 0.45, z + Math.sin(a) * 0.3, 0, 0.005, 0);
		} else if (hat.equals("crown") && tick % 8 == 0) {
			mc.level.addParticle(ParticleTypes.WAX_ON, x + (r.nextDouble() - 0.5) * 0.5, y + h + 0.25, z + (r.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
		} else if (hat.equals("santa_hat") && tick % 6 == 0) {
			mc.level.addParticle(ParticleTypes.SNOWFLAKE, x + (r.nextDouble() - 0.5) * 0.8, y + h + 0.6, z + (r.nextDouble() - 0.5) * 0.8, 0, -0.03, 0);
		} else if (hat.equals("horns") && tick % 10 == 0) {
			mc.level.addParticle(ParticleTypes.SMALL_FLAME, x + (r.nextDouble() - 0.5) * 0.4, y + h + 0.3, z + (r.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
		}
		String back = Cosmetics.Slot.BACK.get();
		if (back.equals("angel_wings") && tick % 7 == 0) {
			mc.level.addParticle(ParticleTypes.END_ROD, x + (r.nextDouble() - 0.5) * 1.2, y + h * 0.7, z + (r.nextDouble() - 0.5) * 1.2, 0, -0.02, 0);
		} else if (back.equals("devil_wings") && tick % 5 == 0) {
			mc.level.addParticle(r.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMOKE,
					x + (r.nextDouble() - 0.5) * 1.0, y + h * 0.6, z + (r.nextDouble() - 0.5) * 1.0, 0, 0.02, 0);
		}
	}

	private CosmeticEffects() {
	}
}
