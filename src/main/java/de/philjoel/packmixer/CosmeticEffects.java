package de.philjoel.packmixer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * Particle effects of the cosmetics, for you and for other players using the mod.
 * Every effect has its own shape (ring, helix, wings, orbit ...) made of short-lived particles,
 * so it stays clean while you run instead of leaving a messy cloud behind.
 */
public final class CosmeticEffects {
	private static int tick;

	static void tick(Minecraft mc) {
		tick++;
		if (mc.level == null || mc.player == null || mc.isPaused()) return;
		for (Player p : mc.level.players()) {
			if (p.isInvisible() || p.isSpectator() || p.distanceToSqr(mc.player) > 48 * 48) continue;
			boolean self = p == mc.player;
			if (self && mc.options.getCameraType().isFirstPerson() && !PackMixerConfig.settings().effectsFirstPerson) continue;
			Cosmetics.Loadout l = Cosmetics.of(p.getUUID());
			if (l.isEmpty()) continue;
			try {
				aura(mc.level, p, l.effect());
				accents(mc.level, p, l);
			} catch (Exception ignored) {
				// a particle that doesn't exist in this version must never crash the game
			}
		}
	}

	private static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(rgb, size);
	}

	private static int hsv(double h) {
		return BuiltinPacks.hsv((float) h, 0.85f, 1f);
	}

	private static void aura(ClientLevel level, Player p, String fx) {
		if (fx.isEmpty()) return;
		RandomSource r = p.getRandom();
		double x = p.getX(), y = p.getY(), z = p.getZ(), h = p.getBbHeight();
		// particles get the player's speed so the shape moves with you
		double mx = p.getX() - p.xo, my = p.getY() - p.yo, mz = p.getZ() - p.zo;
		double yaw = Math.toRadians(p.yBodyRot);
		int t = tick + p.getId() * 7;
		switch (fx) {
			case "fx_sparkle" -> { // rotating crown of twinkling stars + rising star dust
				for (int i = 0; i < 3; i++) {
					double a = t * 0.18 + i * Math.PI * 2 / 3;
					level.addParticle(dust(i == 0 ? 0xFFF2A8 : 0xFFFFFF, 0.8f), x + Math.cos(a) * 0.6, y + h * 0.55, z + Math.sin(a) * 0.6, mx, my, mz);
				}
				if (t % 3 == 0) level.addParticle(ParticleTypes.WAX_OFF, x + (r.nextDouble() - 0.5) * 1.1, y + r.nextDouble() * h,
						z + (r.nextDouble() - 0.5) * 1.1, 0, 0.02, 0);
				if (t % 9 == 0) level.addParticle(ParticleTypes.END_ROD, x + (r.nextDouble() - 0.5) * 0.8, y + 0.2,
						z + (r.nextDouble() - 0.5) * 0.8, 0, 0.06, 0);
			}
			case "fx_souls" -> { // blue double helix rising around you
				for (int i = 0; i < 2; i++) {
					double a = t * 0.3 + i * Math.PI;
					double ry = (t * 0.06 + i * 0.5) % 1.0 * h;
					level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + Math.cos(a) * 0.55, y + ry, z + Math.sin(a) * 0.55, mx, 0.005, mz);
				}
				if (t % 12 == 0) level.addParticle(ParticleTypes.SOUL, x + (r.nextDouble() - 0.5) * 0.6, y + 0.3, z + (r.nextDouble() - 0.5) * 0.6, 0, 0.03, 0);
			}
			case "fx_flames" -> { // turning ring of fire at your feet
				for (int i = 0; i < 4; i++) {
					double a = t * 0.15 + i * Math.PI / 2;
					level.addParticle(ParticleTypes.FLAME, x + Math.cos(a) * 0.7, y + 0.05, z + Math.sin(a) * 0.7, mx, 0.003, mz);
				}
				if (t % 4 == 0) level.addParticle(ParticleTypes.SMALL_FLAME, x + (r.nextDouble() - 0.5) * 0.6, y + 0.1, z + (r.nextDouble() - 0.5) * 0.6, 0, 0.04, 0);
			}
			case "fx_snow" -> { // a little snow cloud above your head
				for (int i = 0; i < 2; i++) {
					double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * 0.45;
					level.addParticle(dust(0xF4F8FF, 1.4f), x + Math.cos(a) * d, y + h + 0.55 + r.nextDouble() * 0.1, z + Math.sin(a) * d, mx, my, mz);
				}
				if (t % 2 == 0) level.addParticle(ParticleTypes.SNOWFLAKE, x + (r.nextDouble() - 0.5) * 0.7, y + h + 0.45,
						z + (r.nextDouble() - 0.5) * 0.7, mx, -0.05, mz);
			}
			case "fx_cherry" -> {
				if (t % 3 == 0) level.addParticle(ParticleTypes.CHERRY_LEAVES, x + (r.nextDouble() - 0.5) * 1.4, y + h + 0.4,
						z + (r.nextDouble() - 0.5) * 1.4, 0, -0.02, 0);
				double a = t * 0.12;
				level.addParticle(dust(0xFFB7DD, 0.7f), x + Math.cos(a) * 0.65, y + h * 0.4, z + Math.sin(a) * 0.65, mx, my, mz);
			}
			case "fx_enchant" -> { // glyphs fly into you, purple orbit
				if (t % 2 == 0) {
					double a = r.nextDouble() * Math.PI * 2;
					// the enchant particle starts at pos + velocity and flies to pos
					level.addParticle(ParticleTypes.ENCHANT, x, y + h * 0.6, z, Math.cos(a) * 1.4, r.nextDouble() * 0.8, Math.sin(a) * 1.4);
				}
				double a = t * 0.2;
				level.addParticle(dust(0xB38CFF, 0.8f), x + Math.cos(a) * 0.55, y + h * 0.75 + Math.sin(t * 0.1) * 0.2, z + Math.sin(a) * 0.55, mx, my, mz);
			}
			case "fx_hearts" -> {
				if (t % 10 == 0) level.addParticle(ParticleTypes.HEART, x + (r.nextDouble() - 0.5) * 0.9, y + h + 0.2, z + (r.nextDouble() - 0.5) * 0.9, 0, 0.05, 0);
			}
			case "fx_notes" -> {
				if (t % 6 == 0) {
					double a = t * 0.4;
					level.addParticle(ParticleTypes.NOTE, x + Math.cos(a) * 0.7, y + h + 0.1, z + Math.sin(a) * 0.7, (t / 6 % 24) / 24.0, 0, 0);
				}
			}
			case "fx_rainbow" -> { // rainbow ring around your waist
				for (int i = 0; i < 3; i++) {
					double a = t * 0.22 + i * Math.PI * 2 / 3;
					level.addParticle(dust(hsv(t * 0.02 + i / 3.0), 0.9f), x + Math.cos(a) * 0.7, y + h * 0.5 + Math.sin(a * 2) * 0.08,
							z + Math.sin(a) * 0.7, mx, my, mz);
				}
			}
			case "fx_galaxy" -> { // two tilted orbits with planets
				for (int ring = 0; ring < 2; ring++) {
					double a = t * (ring == 0 ? 0.16 : -0.12) + ring;
					double tilt = ring == 0 ? 0.45 : -0.45;
					double cx = Math.cos(a) * 0.85, cz = Math.sin(a) * 0.85;
					double cy = cx * tilt;
					level.addParticle(dust(ring == 0 ? 0x8A6CFF : 0x5CF0FF, 1.1f), x + cx, y + h * 0.55 + cy, z + cz, mx, my, mz);
				}
				if (t % 2 == 0) level.addParticle(ParticleTypes.REVERSE_PORTAL, x + (r.nextDouble() - 0.5) * 1.2, y + r.nextDouble() * h,
						z + (r.nextDouble() - 0.5) * 1.2, 0, 0.01, 0);
				if (t % 5 == 0) level.addParticle(ParticleTypes.WAX_OFF, x + (r.nextDouble() - 0.5) * 1.6, y + r.nextDouble() * (h + 0.4),
						z + (r.nextDouble() - 0.5) * 1.6, 0, 0, 0);
			}
			case "fx_totem" -> { // green-gold tornado
				for (int i = 0; i < 2; i++) {
					double ry = (t * 0.05 + i * 0.5) % 1.0;
					double a = t * 0.35 + i * Math.PI;
					double rad = 0.3 + ry * 0.55;
					level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, x + Math.cos(a) * rad, y + ry * (h + 0.3), z + Math.sin(a) * rad, mx, 0.01, mz);
				}
			}
			case "fx_lightning" -> { // electric sparks + a cyan ring that flickers
				if (t % 2 == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (r.nextDouble() - 0.5) * 1.0, y + r.nextDouble() * h,
						z + (r.nextDouble() - 0.5) * 1.0, (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.3);
				if (t % 20 < 6) {
					double a = r.nextDouble() * Math.PI * 2;
					level.addParticle(dust(0x7DF9FF, 0.9f), x + Math.cos(a) * 0.65, y + 0.1, z + Math.sin(a) * 0.65, mx, my, mz);
				}
			}
			case "fx_void" -> { // dark particles sucked down into a void ring
				if (t % 2 == 0) {
					double a = r.nextDouble() * Math.PI * 2;
					level.addParticle(ParticleTypes.PORTAL, x + Math.cos(a) * 0.8, y + 0.1, z + Math.sin(a) * 0.8, 0, 0.6, 0);
				}
				double a = -t * 0.2;
				level.addParticle(dust(0x2A0A40, 1.3f), x + Math.cos(a) * 0.6, y + 0.05, z + Math.sin(a) * 0.6, mx, my, mz);
				level.addParticle(dust(0x9B30FF, 0.6f), x + Math.cos(a + Math.PI) * 0.6, y + 0.05, z + Math.sin(a + Math.PI) * 0.6, mx, my, mz);
			}
			case "fx_lightwings" -> lightWings(level, p, x, y, z, h, yaw, mx, my, mz, t);
			case "fx_inferno" -> { // red fire tornado around you
				for (int i = 0; i < 3; i++) {
					double a = t * 0.35 + i * Math.PI * 2 / 3;
					double ry = (t * 0.05 + i / 3.0) % 1.0;
					double rad = 0.75 - ry * 0.35;
					level.addParticle(i == 0 ? ParticleTypes.FLAME : dust(i == 1 ? 0xFF2A10 : 0xFF7A20, 0.9f),
							x + Math.cos(a) * rad, y + ry * h, z + Math.sin(a) * rad, mx, 0.01, mz);
				}
				if (t % 5 == 0) level.addParticle(ParticleTypes.LAVA, x + (r.nextDouble() - 0.5) * 0.6, y + 0.1, z + (r.nextDouble() - 0.5) * 0.6, 0, 0, 0);
			}
			case "fx_plasma" -> { // pink plasma orbs circling, with sparks jumping between them
				for (int i = 0; i < 3; i++) {
					double a = t * 0.22 + i * Math.PI * 2 / 3;
					double py = y + h * 0.5 + Math.sin(t * 0.15 + i * 2) * 0.4;
					level.addParticle(dust(i == 1 ? 0xB02CFF : 0xFF4FD8, 1.2f), x + Math.cos(a) * 0.8, py, z + Math.sin(a) * 0.8, mx, my, mz);
				}
				if (t % 3 == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (r.nextDouble() - 0.5) * 1.4, y + r.nextDouble() * h,
						z + (r.nextDouble() - 0.5) * 1.4, 0, 0, 0);
			}
			case "fx_frost" -> { // icy ring at your feet + snowflakes falling around you
				for (int i = 0; i < 2; i++) {
					double a = -t * 0.12 + i * Math.PI;
					level.addParticle(dust(i == 0 ? 0x8FF5EA : 0xFFFFFF, 0.8f), x + Math.cos(a) * 0.75, y + 0.05, z + Math.sin(a) * 0.75, mx, my, mz);
				}
				if (t % 3 == 0) level.addParticle(ParticleTypes.SNOWFLAKE, x + (r.nextDouble() - 0.5) * 1.3, y + h + 0.2,
						z + (r.nextDouble() - 0.5) * 1.3, 0, -0.04, 0);
			}
			case "fx_energy" -> { // two turquoise rings moving up and down around you
				for (int i = 0; i < 2; i++) {
					double ry = (Math.sin(t * 0.08 + i * Math.PI) * 0.5 + 0.5) * h;
					for (int k = 0; k < 3; k++) {
						double a = t * 0.3 + k * Math.PI * 2 / 3 + i;
						level.addParticle(dust(k == 0 ? 0xE6FFFC : 0x35F2DD, 0.7f), x + Math.cos(a) * 0.65, y + ry, z + Math.sin(a) * 0.65, mx, my, mz);
					}
				}
			}
			default -> {
			}
		}
	}

	/** Glowing wings made of particles, behind your back, slowly beating. */
	private static void lightWings(ClientLevel level, Player p, double x, double y, double z, double h, double yaw,
			double mx, double my, double mz, int t) {
		if (t % 2 != 0) return;
		double flap = 0.35 + 0.25 * Math.sin(t * 0.12);
		// Minecraft: forward = (-sin yaw, cos yaw) -> back = (sin yaw, -cos yaw), sideways = (cos yaw, sin yaw)
		double rx = Math.cos(yaw), rz = Math.sin(yaw);
		double bx = Math.sin(yaw), bz = -Math.cos(yaw);
		double baseY = y + h * 0.72;
		for (int side = -1; side <= 1; side += 2) {
			// outline of a feather wing: points along a curve from the shoulder outwards
			for (int i = 0; i <= 10; i++) {
				double s = i / 10.0;
				double out = 0.15 + s * 1.05;
				double up = Math.sin(s * Math.PI) * 0.55 - s * 0.25;
				double back = 0.25 + out * flap * 0.6;
				double px = x + rx * out * side + bx * back, pz = z + rz * out * side + bz * back;
				int col = PackExtras.mix(0xFFF6D0, 0x7FDBFF, s);
				level.addParticle(new DustParticleOptions(col, 0.7f), px, baseY + up, pz, mx, my, mz);
				if (i % 2 == 0 && i > 1) { // lower feather edge
					double up2 = up - 0.25 - s * 0.35;
					level.addParticle(new DustParticleOptions(col, 0.55f), px, baseY + up2, pz, mx, my, mz);
				}
			}
		}
	}

	/** Small extras for some wearables. */
	private static void accents(ClientLevel level, Player p, Cosmetics.Loadout l) {
		RandomSource r = p.getRandom();
		double x = p.getX(), y = p.getY(), z = p.getZ(), h = p.getBbHeight();
		int t = tick + p.getId() * 3;
		switch (l.head()) {
			case "halo" -> {
				if (t % 5 == 0) {
					double a = r.nextDouble() * Math.PI * 2;
					level.addParticle(ParticleTypes.WAX_OFF, x + Math.cos(a) * 0.3, y + h + 0.45, z + Math.sin(a) * 0.3, 0, 0, 0);
				}
			}
			case "crown" -> {
				if (t % 8 == 0) level.addParticle(ParticleTypes.WAX_ON, x + (r.nextDouble() - 0.5) * 0.5, y + h + 0.25, z + (r.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
			}
			case "santa_hat" -> {
				if (t % 6 == 0) level.addParticle(ParticleTypes.SNOWFLAKE, x + (r.nextDouble() - 0.5) * 0.8, y + h + 0.6, z + (r.nextDouble() - 0.5) * 0.8, 0, -0.03, 0);
			}
			case "horns" -> {
				if (t % 10 == 0) level.addParticle(ParticleTypes.SMALL_FLAME, x + (r.nextDouble() - 0.5) * 0.4, y + h + 0.3, z + (r.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
			}
			case "wizard_hat" -> {
				if (t % 4 == 0) level.addParticle(ParticleTypes.ENCHANT, x, y + h + 0.9, z, (r.nextDouble() - 0.5) * 1.2, 0.3, (r.nextDouble() - 0.5) * 1.2);
			}
			case "inferno_crown" -> {
				if (t % 3 == 0) level.addParticle(r.nextBoolean() ? ParticleTypes.SMALL_FLAME : dust(0xFF2A10, 0.6f),
						x + (r.nextDouble() - 0.5) * 0.55, y + h + 0.35, z + (r.nextDouble() - 0.5) * 0.55, 0, 0.03, 0);
			}
			case "plasma_crown" -> {
				if (t % 4 == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (r.nextDouble() - 0.5) * 0.6, y + h + 0.35,
						z + (r.nextDouble() - 0.5) * 0.6, 0, 0, 0);
			}
			case "frost_crown" -> {
				if (t % 5 == 0) level.addParticle(ParticleTypes.SNOWFLAKE, x + (r.nextDouble() - 0.5) * 0.7, y + h + 0.45,
						z + (r.nextDouble() - 0.5) * 0.7, 0, -0.02, 0);
			}
			case "void_crown", "void_halo" -> {
				if (t % 4 == 0) level.addParticle(ParticleTypes.REVERSE_PORTAL, x + (r.nextDouble() - 0.5) * 0.6, y + h + 0.4,
						z + (r.nextDouble() - 0.5) * 0.6, 0, 0, 0);
			}
			case "energy_halo" -> {
				double a = t * 0.25;
				level.addParticle(dust(0x35F2DD, 0.6f), x + Math.cos(a) * 0.38, y + h + 0.5, z + Math.sin(a) * 0.38, 0, 0, 0);
			}
			case "divine_halo" -> {
				if (t % 4 == 0) {
					double a = r.nextDouble() * Math.PI * 2;
					level.addParticle(ParticleTypes.END_ROD, x + Math.cos(a) * 0.35, y + h + 0.5, z + Math.sin(a) * 0.35, 0, -0.01, 0);
				}
			}
			case "headphones" -> {
				if (t % 14 == 0) level.addParticle(ParticleTypes.NOTE, x + (r.nextDouble() - 0.5) * 0.6, y + h + 0.3, z + (r.nextDouble() - 0.5) * 0.6, r.nextDouble(), 0, 0);
			}
			default -> {
			}
		}
		switch (l.back()) {
			case "angel_wings" -> {
				if (t % 7 == 0) level.addParticle(ParticleTypes.END_ROD, x + (r.nextDouble() - 0.5) * 1.2, y + h * 0.7, z + (r.nextDouble() - 0.5) * 1.2, 0, -0.02, 0);
			}
			case "devil_wings" -> {
				if (t % 5 == 0) level.addParticle(r.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMOKE,
						x + (r.nextDouble() - 0.5) * 1.0, y + h * 0.6, z + (r.nextDouble() - 0.5) * 1.0, 0, 0.02, 0);
			}
			case "plasma_wings", "inferno_wings", "void_wings", "energy_wings", "frost_wings" -> {
				if (t % 3 == 0) {
					double yaw = Math.toRadians(p.yBodyRot);
					double side = r.nextBoolean() ? 1 : -1, out = 0.3 + r.nextDouble() * 1.0;
					double px = x + Math.cos(yaw) * out * side + Math.sin(yaw) * 0.45;
					double pz = z + Math.sin(yaw) * out * side - Math.cos(yaw) * 0.45;
					double py = y + h * (0.45 + r.nextDouble() * 0.6);
					switch (l.back()) {
						case "plasma_wings" -> level.addParticle(r.nextBoolean() ? ParticleTypes.ELECTRIC_SPARK : dust(0xFF4FD8, 0.8f), px, py, pz, 0, 0, 0);
						case "inferno_wings" -> level.addParticle(r.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : dust(0xFF2A10, 0.9f), px, py, pz, 0, 0.02, 0);
						case "void_wings" -> level.addParticle(ParticleTypes.REVERSE_PORTAL, px, py, pz, 0, 0, 0);
						case "energy_wings" -> level.addParticle(dust(r.nextBoolean() ? 0x35F2DD : 0xE6FFFC, 0.8f), px, py, pz, 0, 0.01, 0);
						default -> level.addParticle(ParticleTypes.SNOWFLAKE, px, py, pz, 0, -0.02, 0);
					}
				}
			}
			case "phoenix_wings" -> {
				if (t % 2 == 0) {
					double yaw = Math.toRadians(p.yBodyRot);
					double side = r.nextBoolean() ? 1 : -1, out = 0.3 + r.nextDouble() * 0.8;
					double px = x + Math.cos(yaw) * out * side + Math.sin(yaw) * 0.35;
					double pz = z + Math.sin(yaw) * out * side - Math.cos(yaw) * 0.35;
					level.addParticle(r.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME, px, y + h * (0.55 + r.nextDouble() * 0.35), pz, 0, 0.015, 0);
				}
			}
			default -> {
			}
		}
	}

	private CosmeticEffects() {
	}
}
