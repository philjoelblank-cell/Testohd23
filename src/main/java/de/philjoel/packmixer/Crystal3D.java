package de.philjoel.packmixer;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.ArrayList;
import java.util.List;

/**
 * Small software renderer for the end crystal preview: the same three nested cubes as the placed crystal
 * (outer glass, inner glass, core), each one turned 60 degrees around the diagonal axis and spinning, the
 * whole crystal bobbing up and down. Output: vertical strip of square frames.
 */
final class Crystal3D {
	private record Face(double[] p0, double[] pu, double[] pv, int u, int v, int w, int h, double depth, double shade,
			int texFrame) {
	}

	/** Unit cube faces: origin corner, corner along u, corner along v (texture rect index). */
	private static final double[][][] FACES = {
			{{-.5, .5, .5}, {.5, .5, .5}, {-.5, -.5, .5}},     // 0 front
			{{.5, .5, .5}, {.5, .5, -.5}, {.5, -.5, .5}},      // 1 right
			{{.5, .5, -.5}, {-.5, .5, -.5}, {.5, -.5, -.5}},   // 2 back
			{{-.5, .5, -.5}, {-.5, .5, .5}, {-.5, -.5, -.5}},  // 3 left
			{{-.5, .5, -.5}, {.5, .5, -.5}, {-.5, .5, .5}},    // 4 top
			{{-.5, -.5, .5}, {.5, -.5, .5}, {-.5, -.5, -.5}}}; // 5 bottom
	private static final double[][] NORMALS = {{0, 0, 1}, {1, 0, 0}, {0, 0, -1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

	/** Box-UV rectangles (in 8px units) of the faces, relative to the cube's texture origin. */
	private static int[] rect(int face) {
		return switch (face) {
			case 0 -> new int[]{8, 8, 8, 8};
			case 1 -> new int[]{16, 8, 8, 8};
			case 2 -> new int[]{24, 8, 8, 8};
			case 3 -> new int[]{0, 8, 8, 8};
			case 4 -> new int[]{8, 0, 8, 8};
			default -> new int[]{16, 0, 8, 8};
		};
	}

	static double[][] mul(double[][] a, double[][] b) {
		double[][] r = new double[3][3];
		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 3; j++) r[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
		return r;
	}

	static double[][] rotY(double a) {
		double c = Math.cos(a), s = Math.sin(a);
		return new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
	}

	static double[][] rotX(double a) {
		double c = Math.cos(a), s = Math.sin(a);
		return new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}};
	}

	/** Rotation around a unit axis (Rodrigues). */
	static double[][] rotAxis(double x, double y, double z, double a) {
		double c = Math.cos(a), s = Math.sin(a), t = 1 - c;
		return new double[][]{
				{t * x * x + c, t * x * y - s * z, t * x * z + s * y},
				{t * x * y + s * z, t * y * y + c, t * y * z - s * x},
				{t * x * z - s * y, t * y * z + s * x, t * z * z + c}};
	}

	static double[] apply(double[][] m, double[] v, double scale) {
		return new double[]{(m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2]) * scale,
				(m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2]) * scale,
				(m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]) * scale};
	}

	/**
	 * @param tex  the crystal texture (vanilla layout, any multiple of 64x32)
	 * @param anim optional animation strip of the crystal (frames of tex size), may be null
	 */
	static NativeImage render(NativeImage tex, NativeImage anim, int frames, int size) {
		int tw = tex.getWidth(), th = tex.getHeight();
		double s = tw / 64.0; // texture pixels per model pixel
		int animFrames = anim == null || anim.getWidth() != tw ? 0 : anim.getHeight() / th;
		NativeImage out = new NativeImage(size, size * frames, true);
		double k = 0.7071;
		double[][] axis = rotAxis(k, 0, k, Math.PI / 3);
		double[][] view = rotX(Math.toRadians(-22)); // camera a little above
		double edge = size * 0.42;                  // outer cube edge in pixels
		for (int f = 0; f < frames; f++) {
			double t = f / (double) frames;
			double spin = t * Math.PI * 2;             // one full turn per loop
			double bob = Math.sin(t * Math.PI * 2) * size * 0.06;
			int texFrame = animFrames > 0 ? (int) (t * animFrames * 3) % animFrames : -1;
			List<Face> faces = new ArrayList<>();
			double[][] m = view;
			double[] scales = {1.0, 0.875, 0.875 * 0.875 * 0.98};
			int[][] origins = {{0, 0}, {0, 0}, {32, 0}};
			for (int level = 0; level < 3; level++) {
				m = mul(mul(m, rotY(spin)), axis);
				double sc = scales[level] * edge;
				for (int fi = 0; fi < 6; fi++) {
					double[] p0 = apply(m, FACES[fi][0], sc), pu = apply(m, FACES[fi][1], sc), pv = apply(m, FACES[fi][2], sc);
					double[] n = apply(m, NORMALS[fi], 1);
					double depth = (p0[2] + pu[2] + pv[2] + (pu[2] + pv[2] - p0[2])) / 4;
					double light = 0.72 + 0.28 * Math.max(0, n[1] * 0.6 + n[2] * 0.8);
					int[] r = rect(fi);
					faces.add(new Face(p0, pu, pv, (int) Math.round((origins[level][0] + r[0]) * s), (int) Math.round((origins[level][1] + r[1]) * s),
							(int) Math.round(r[2] * s), (int) Math.round(r[3] * s), depth, light, texFrame));
				}
			}
			faces.sort((a, b) -> Double.compare(a.depth, b.depth)); // far first (camera looks along -z)
			int oy = f * size;
			// soft shadow on the ground
			double cx = size / 2.0, cy = size * 0.52 - bob;
			for (int y = 0; y < size; y++)
				for (int x = 0; x < size; x++) {
					double ex = (x + 0.5 - cx) / (size * 0.23), ey = (y + 0.5 - size * 0.9) / (size * 0.05);
					double d = ex * ex + ey * ey;
					int a = d < 1 ? (int) ((1 - d) * (90 - bob / size * 300)) : 0;
					out.setPixel(x, oy + y, Math.max(0, Math.min(255, a)) << 24);
				}
			for (Face fc : faces) draw(out, oy, size, cx, cy, fc, tex, anim, th);
		}
		return out;
	}

	private static void draw(NativeImage out, int oy, int size, double cx, double cy, Face fc, NativeImage tex, NativeImage anim, int th) {
		// screen: x right, y down; model y up
		double ax = cx + fc.p0[0], ay = cy - fc.p0[1];
		double ux = fc.pu[0] - fc.p0[0], uy = -(fc.pu[1] - fc.p0[1]);
		double vx = fc.pv[0] - fc.p0[0], vy = -(fc.pv[1] - fc.p0[1]);
		double det = ux * vy - uy * vx;
		if (Math.abs(det) < 1e-3) return;
		double minX = Math.min(Math.min(ax, ax + ux), Math.min(ax + vx, ax + ux + vx));
		double maxX = Math.max(Math.max(ax, ax + ux), Math.max(ax + vx, ax + ux + vx));
		double minY = Math.min(Math.min(ay, ay + uy), Math.min(ay + vy, ay + uy + vy));
		double maxY = Math.max(Math.max(ay, ay + uy), Math.max(ay + vy, ay + uy + vy));
		int x0 = Math.max(0, (int) Math.floor(minX)), x1 = Math.min(size - 1, (int) Math.ceil(maxX));
		int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(size - 1, (int) Math.ceil(maxY));
		for (int y = y0; y <= y1; y++)
			for (int x = x0; x <= x1; x++) {
				double px = x + 0.5 - ax, py = y + 0.5 - ay;
				double a = (px * vy - py * vx) / det, b = (ux * py - uy * px) / det;
				if (a < 0 || a >= 1 || b < 0 || b >= 1) continue;
				int tx = fc.u + Math.min(fc.w - 1, (int) (a * fc.w)), ty = fc.v + Math.min(fc.h - 1, (int) (b * fc.h));
				int c = fc.texFrame >= 0 ? anim.getPixel(tx, fc.texFrame * th + ty) : tex.getPixel(tx, ty);
				int al = c >>> 24;
				if (al < 10) continue;
				int r = (int) Math.min(255, ((c >> 16) & 0xFF) * fc.shade), g = (int) Math.min(255, ((c >> 8) & 0xFF) * fc.shade),
						bl = (int) Math.min(255, (c & 0xFF) * fc.shade);
				int dst = out.getPixel(x, oy + y);
				int da = dst >>> 24;
				if (al >= 250 || da == 0) {
					out.setPixel(x, oy + y, (Math.max(al, da) << 24) | (r << 16) | (g << 8) | bl);
				} else {
					double k = al / 255.0;
					int nr = (int) (r * k + ((dst >> 16) & 0xFF) * (1 - k)), ng = (int) (g * k + ((dst >> 8) & 0xFF) * (1 - k)),
							nb = (int) (bl * k + (dst & 0xFF) * (1 - k));
					out.setPixel(x, oy + y, (Math.max(al, da) << 24) | (nr << 16) | (ng << 8) | nb);
				}
			}
	}

	private Crystal3D() {
	}
}
