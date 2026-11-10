set +e
f(){ find . -name "$1.java" | head -1; }
echo "### STYLE"; grep -n "public Style withFont\|FontDescription" $(f Style) | head
echo "### FD"; f FontDescription; grep -n "record\|interface\|static" $(f FontDescription) | head
echo "### TAB"; grep -n "getNameForDisplay\|private Component decorateName" $(f PlayerTabOverlay) | head
echo "### NT"; grep -n "nameTag" $(f EntityRenderState) $(f EntityRenderer) | head
echo "### ITEMCTX"; grep -n "FIXED\|HEAD\|NONE" $(f ItemDisplayContext) | head -5
