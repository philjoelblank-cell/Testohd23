set +e
f(){ find . -name "$1.java" | head -1; }
echo "### GGE"; grep -n "EntityRenderState\|entity(" $(f GuiGraphicsExtractor) | head -20
echo "### INV"; grep -n "static void .*Entity\|extractEntity\|renderEntity" $(f InventoryScreen) | head -10
sed -n '/public static void extractEntityInInventory(/,/^	}/p' $(f InventoryScreen) | head -40
