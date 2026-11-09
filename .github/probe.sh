set +e
f(){ find . -name "$1.java" | head -1; }
echo "### SKIN"; sed -n 1,80p $(f PlayerSkin)
echo "### AVR"; sed -n 1,40p $(f AvatarRenderer) | grep -n "class\|import"
sed -n 190,215p $(f AvatarRenderer)
echo "### AVATAR"; f Avatar
echo "### HM"; sed -n 95,130p $(f HumanoidModel)
echo "### PMS"; sed -n 105,140p $(f PlayerModel)
