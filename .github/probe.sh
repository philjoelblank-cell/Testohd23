set +e
f(){ find . -name "$1.java" | head -1; }
echo "### PLAYER"; grep -n "public void .*Message\|public void .*message" $(f Player) $(f LocalPlayer) $(f Gui) $(f Hud) 2>/dev/null | head -20
echo "### GUIDIR"; grep -rn "public void setOverlayMessage\|public void displayClientMessage\|public void sendOverlayMessage" . | head -8
