package de.philjoel.packmixer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Draws custom sky boxes in the OptiFine / MCPatcher format ({@code optifine/sky/world0/skyN.properties} + png),
 * so imported sky packs work and the built-in packs can bring their own skies.
 */
public final class SkyboxRenderer {
	record Layer(Identifier texture, int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut,
			boolean additive, boolean rotate, float speed, Vector3f axis) {
	}

	private static List<Layer> overworld = List.of();
	private static List<Layer> end = List.of();
	private static final List<Identifier> loadedTextures = new ArrayList<>();
	private static volatile boolean dirty = true;
	private static List<String> lastPacks;
	private static GpuBuffer box;

	/** Called after the resource packs were (re)applied. */
	public static void markDirty() {
		dirty = true;
	}

	// ======================================================================= loading

	private static void ensureLoaded(Minecraft mc) {
		List<String> packs = new ArrayList<>(mc.getResourcePackRepository().getSelectedIds());
		if (!dirty && packs.equals(lastPacks)) return;
		if (mc.gui.overlay() != null) return; // resources are still reloading
		dirty = false;
		lastPacks = packs;
		for (Identifier id : loadedTextures) mc.getTextureManager().release(id);
		loadedTextures.clear();
		List<Layer> w0 = new ArrayList<>(), w1 = new ArrayList<>();
		try {
			for (String root : new String[]{"optifine/sky", "mcpatcher/sky"}) {
				Map<Identifier, Resource> found = mc.getResourceManager().listResources(root,
						id -> id.getNamespace().equals("minecraft") && id.getPath().endsWith(".properties"));
				List<Identifier> ids = new ArrayList<>(found.keySet());
				ids.sort(Comparator.comparingInt(SkyboxRenderer::number));
				for (Identifier id : ids) {
					String path = id.getPath();
					boolean world0 = path.contains("/world0/"), world1 = path.contains("/world1/");
					if (!world0 && !world1) continue;
					String file = path.substring(path.lastIndexOf('/') + 1);
					if (!file.startsWith("sky")) continue; // sun.properties / moon_phases.properties are not boxes
					Layer layer = parse(mc, id, found.get(id));
					if (layer == null) continue;
					(world0 ? w0 : w1).add(layer);
				}
			}
		} catch (Exception e) {
			PackMixerClient.LOGGER.warn("Could not load custom skies: {}", e.toString());
		}
		overworld = w0;
		end = w1;
		if (!w0.isEmpty() || !w1.isEmpty()) PackMixerClient.LOGGER.info("Custom sky: {} overworld / {} end layers", w0.size(), w1.size());
	}

	private static int number(Identifier id) {
		String p = id.getPath();
		String digits = p.substring(p.lastIndexOf('/') + 1).replaceAll("\\D", "");
		try {
			return digits.isEmpty() ? 0 : Integer.parseInt(digits);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static Layer parse(Minecraft mc, Identifier id, Resource res) {
		Properties p = new Properties();
		try (InputStream in = res.open()) {
			p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (Exception e) {
			return null;
		}
		String path = id.getPath();
		String folder = path.substring(0, path.lastIndexOf('/') + 1);
		String source = p.getProperty("source", "./" + path.substring(folder.length()).replace(".properties", ".png")).trim();
		Identifier tex;
		if (source.startsWith("./")) tex = Identifier.withDefaultNamespace(folder + source.substring(2));
		else if (source.contains(":")) tex = Identifier.tryParse(source);
		else if (source.startsWith("assets/minecraft/")) tex = Identifier.withDefaultNamespace(source.substring("assets/minecraft/".length()));
		else if (source.startsWith("~/")) tex = Identifier.withDefaultNamespace("optifine/" + source.substring(2));
		else tex = Identifier.withDefaultNamespace(source.startsWith("/") ? source.substring(1) : source);
		if (tex == null || mc.getResourceManager().getResource(tex).isEmpty()) return null;
		loadedTextures.add(tex);
		String blend = p.getProperty("blend", "add").trim().toLowerCase(java.util.Locale.ROOT);
		boolean additive = blend.equals("add") || blend.equals("screen") || blend.equals("dodge");
		float speed = parseFloat(p.getProperty("speed"), 1f);
		Vector3f axis = new Vector3f(0, 0, 1);
		String ax = p.getProperty("axis");
		if (ax != null) {
			String[] parts = ax.trim().split("\\s+");
			if (parts.length == 3) {
				axis = new Vector3f(parseFloat(parts[0], 0), parseFloat(parts[1], 0), parseFloat(parts[2], 1));
				if (axis.lengthSquared() < 1e-6f) axis.set(0, 0, 1);
				axis.normalize();
			}
		}
		int sIn = time(p.getProperty("startFadeIn")), eIn = time(p.getProperty("endFadeIn"));
		int sOut = time(p.getProperty("startFadeOut")), eOut = time(p.getProperty("endFadeOut"));
		if (sOut < 0 && sIn >= 0 && eIn >= 0 && eOut >= 0) sOut = norm(eOut - (eIn - sIn));
		return new Layer(tex, sIn, eIn, sOut, eOut, additive, !"false".equalsIgnoreCase(p.getProperty("rotate", "true").trim()), speed, axis);
	}

	private static float parseFloat(String s, float def) {
		try {
			return s == null ? def : Float.parseFloat(s.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}

	/** "hh:mm" -> world ticks (06:00 = 0). */
	private static int time(String s) {
		if (s == null) return -1;
		String[] t = s.trim().split(":");
		if (t.length != 2) return -1;
		try {
			int h = Integer.parseInt(t[0].trim()), m = Integer.parseInt(t[1].trim());
			return norm((h - 6) * 1000 + m * 1000 / 60);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static int norm(int t) {
		return Math.floorMod(t, 24000);
	}

	private static boolean between(int t, int a, int b) {
		return a <= b ? t >= a && t <= b : t >= a || t <= b;
	}

	static float fade(Layer l, int t) {
		if (l.startFadeIn < 0 || l.endFadeIn < 0 || l.endFadeOut < 0) return 1f;
		if (between(t, l.startFadeIn, l.endFadeIn)) return norm(t - l.startFadeIn) / (float) Math.max(1, norm(l.endFadeIn - l.startFadeIn));
		if (between(t, l.endFadeIn, l.startFadeOut)) return 1f;
		if (between(t, l.startFadeOut, l.endFadeOut)) return 1f - norm(t - l.startFadeOut) / (float) Math.max(1, norm(l.endFadeOut - l.startFadeOut));
		return 0f;
	}

	// ======================================================================= drawing

	/** Overworld: after the sky colour, before sun / moon / stars (inside the vanilla sky render pass). */
	public static void renderOverworld(RenderPass pass, float sunAngle, float rainBrightness) {
		render(pass, overworldLayers(), sunAngle, rainBrightness);
	}

	/** The End: on top of the vanilla end sky. */
	public static void renderEnd(RenderPass pass) {
		Minecraft mc = Minecraft.getInstance();
		ensureLoaded(mc);
		render(pass, end, 0f, 1f);
	}

	private static List<Layer> overworldLayers() {
		ensureLoaded(Minecraft.getInstance());
		return overworld;
	}

	private static void render(RenderPass pass, List<Layer> layers, float sunAngle, float rain) {
		if (layers.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		try {
			if (box == null) box = buildBox();
			int time = (int) Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
			RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
			GpuBuffer indices = quads.getBuffer(36);
			for (Layer l : layers) {
				float b = Math.max(0f, Math.min(1f, fade(l, time) * rain));
				if (b < 1e-4f) continue;
				AbstractTexture tex = mc.getTextureManager().getTexture(l.texture);
				Matrix4fStack mv = RenderSystem.getModelViewStack();
				mv.pushMatrix();
				if (l.rotate) mv.rotate(sunAngle * l.speed, l.axis);
				mv.rotateY((float) Math.toRadians(-90));
				GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(mv), new Vector4f(1f, 1f, 1f, b));
				mv.popMatrix();
				pass.pushDebugGroup(() -> "Testohd sky");
				pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.END_SKY));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				pass.setUniform("Sampler0", tex.getTextureView(), tex.getSampler());
				pass.setVertexBuffer(0, box.slice());
				pass.setIndexBuffer(indices, quads.type());
				pass.drawIndexed(36, 1, 0, 0, 0);
				pass.popDebugGroup();
			}
		} catch (Throwable e) {
			PackMixerClient.LOGGER.warn("Custom sky failed, turning it off: {}", e.toString());
			overworld = List.of();
			end = List.of();
		}
	}

	/** The 6 faces exactly like OptiFine lays them out in the 3x2 image. */
	private static GpuBuffer buildBox() {
		VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(24 * format.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);
			for (int side : new int[]{4, 1, 0, 5, 2, 3}) {
				float tx = (side % 3) / 3f, ty = (side / 3) / 2f;
				vertex(b, side, -1, -1, tx, ty);
				vertex(b, side, -1, 1, tx, ty + 0.5f);
				vertex(b, side, 1, 1, tx + 1 / 3f, ty + 0.5f);
				vertex(b, side, 1, -1, tx + 1 / 3f, ty);
			}
			try (MeshData mesh = b.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "Testohd sky box", 40, mesh.vertexBuffer());
			}
		}
	}

	private static void vertex(BufferBuilder b, int side, int x, int z, float u, float v) {
		double[] p = PackExtras.faceToBox(side, x * 100.0, -100.0, z * 100.0);
		b.addVertex((float) p[0], (float) p[1], (float) p[2]).setUv(u, v).setColor(-1);
	}

	private SkyboxRenderer() {
	}
}
