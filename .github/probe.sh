set +e
echo "### ARM"; sed -n 418,432p net/minecraft/client/renderer/ItemInHandRenderer.java
echo "### SKY"; sed -n 319,330p net/minecraft/client/renderer/SkyRenderer.java
echo "### ENC"; grep -n "RenderPass createRenderPass" -A8 $(find . -name CommandEncoder.java) | head -30
echo "### DYN"; grep -n "writeTransform" -A1 $(find . -name DynamicUniforms.java) | head
echo "### RT"; grep -n "getMainRenderTarget\|mainRenderTarget" net/minecraft/client/Minecraft.java net/minecraft/client/renderer/GameRenderer.java | head -4
grep -n "public .* get\(Color\|Depth\)TextureView" $(find . -name RenderTarget.java)
echo "### SLICE"; grep -n "public GpuBufferSlice slice" com/mojang/blaze3d/buffers/GpuBuffer.java
echo "### SKYUSE"; grep -n "createRenderPass\|setVertexBuffer\|drawIndexed\|bindTexture\|setPipeline" net/minecraft/client/renderer/SkyRenderer.java | head
