set +e
f(){ find . -name "$1.java" | head -1; }
echo "### CAPE"; cat $(f CapeLayer) | sed -n '/public void submit/,/^   }/p' | head -40
echo "### AVS"; grep -n "public\|skin\|Cape" $(f AvatarRenderState) | head -40
echo "### SKIN"; grep -n "record PlayerSkin\|public static PlayerSkin\|with" $(f PlayerSkin) | head
echo "### CA"; grep -n "record\|interface" $(f ClientAsset) | head
echo "### EXTRACT"; grep -n "extractRenderState\|public AvatarRenderState createRenderState" $(f AvatarRenderer) | head
echo "### PM"; grep -n "public void setupAnim" -A3 $(f PlayerModel)
echo "### KEY"; grep -n "record Category\|public static final Category\|Category register\|public KeyMapping(" $(f KeyMapping) | head
echo "### ELY"; grep -n "elytra\|cape" $(f WingsLayer) | head -5
