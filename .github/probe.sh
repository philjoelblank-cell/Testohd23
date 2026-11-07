set +e
echo "### FILES"; for n in RenderPass GpuBuffer GpuBufferSlice VertexFormat PrimitiveTopology FirstPersonHandsAndItemsRenderer ItemInHandRenderer PlayerRenderState FirstPersonHandsAndItemsRenderState SkyRenderer KeyMapping; do find . -name "$n.java"; done
echo "### ARM"; grep -rn "ArmWithItem(" net/minecraft/client/renderer/*.java | grep "void"
echo "### SKY"; grep -n "void renderSunMoonAndStars\|void renderEndSky" -A2 net/minecraft/client/renderer/SkyRenderer.java
echo "### INPUT"; grep -n "enum Type" -A4 com/mojang/blaze3d/platform/InputConstants.java; grep -n "KEY_K \|KEY_K=" com/mojang/blaze3d/platform/InputConstants.java
echo "### OPEN"; grep -rn "openPath\|void openFile\|public void openUri" com/mojang/blaze3d/*.java net/minecraft/Util.java net/minecraft/util/Util.java 2>/dev/null | head
echo "### VANILLA"; grep -n "public .*(" net/minecraft/server/packs/VanillaPackResources.java | head -20
echo "### RS"; grep -n "public static .*Pipeline\|public static .*Sequential\|AutoStorageIndexBuffer getSequential\|getDynamicUniforms\|bindDefaultUniforms\|getModelViewStack\|getDevice()" com/mojang/blaze3d/systems/RenderSystem.java
echo "### POSE"; grep -n "public void \(rotateDegrees\|mulPose\)" com/mojang/blaze3d/vertex/PoseStack.java
echo "### PASS"; grep -n "void setPipeline\|void setUniform\|void setVertexBuffer\|void setIndexBuffer\|void drawIndexed\|void bindTexture\|void pushDebugGroup" $(find . -name RenderPass.java)
echo "### BB"; grep -n "public BufferBuilder(" -A1 com/mojang/blaze3d/vertex/BufferBuilder.java; grep -n "createBuffer(" com/mojang/blaze3d/systems/GpuDevice.java $(find . -name GpuDevice.java) | head -4
echo "### TEX"; grep -n "getTextureView\|getSampler" net/minecraft/client/renderer/texture/AbstractTexture.java
echo "### CLOCK"; grep -rn "getOverworldClockTime\|public long getDayTime" net/minecraft/client/multiplayer/ClientLevel.java net/minecraft/world/level/Level.java | head -3
echo "### GUI"; grep -n "public .* screen()\|public void setScreen\|public .* overlay()\|public Screen screen\|public Overlay getOverlay\|public void setScreen(" net/minecraft/client/Minecraft.java net/minecraft/client/gui/Gui.java | head
echo "### MOONPHASE"; find . -name MoonPhase.java
