set +e
f(){ find . -name "$1.java" | head -1; }
echo "### CAPELAYER"; cat $(f CapeLayer)
echo "### CAPEMODEL"; sed -n 1,200p $(f PlayerCapeModel)
echo "### ARMORLAYER"; grep -n "class\|HEAD\|submit\|renderArmorPiece\|EquipmentSlot" $(f HumanoidArmorLayer) | head -30
echo "### AVATARRENDERER"; grep -n "addLayer\|new .*Layer" $(f AvatarRenderer) | head -20
echo "### ARS"; grep -n "headEquipment\|chestEquipment\|ItemStack" $(f HumanoidRenderState) $(f AvatarRenderState) | head
