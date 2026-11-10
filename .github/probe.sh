set +e
f(){ find . -name "$1.java" | head -1; }
echo "### CAPELAYER"; cat $(f CapeLayer)
echo "### CAPEMODEL"; sed -n 1,200p $(f PlayerCapeModel)



echo "### REG"; grep -rn "interface\|void .*(" $(find . -name "LivingEntityRenderLayerRegistrationCallback.java" -o -name "LivingEntityFeatureRendererRegistrationCallback.java") 2>/dev/null | head
echo "### GLIDER"; grep -n "GLIDER" $(f DataComponents) | head -2
