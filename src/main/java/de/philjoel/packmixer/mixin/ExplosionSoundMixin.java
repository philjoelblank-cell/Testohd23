package de.philjoel.packmixer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.philjoel.packmixer.CrystalSound;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** End crystal explosions get the sound of the chosen crystal pack (or none). */
@Mixin(ClientPacketListener.class)
public abstract class ExplosionSoundMixin {
	@WrapOperation(method = "handleExplosion", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
	private void testohd$crystalSound(ClientLevel level, double x, double y, double z, SoundEvent sound, SoundSource source,
			float volume, float pitch, boolean delayed, Operation<Void> original) {
		if (CrystalSound.handle(level, x, y, z, volume, pitch)) return;
		original.call(level, x, y, z, sound, source, volume, pitch, delayed);
	}
}
