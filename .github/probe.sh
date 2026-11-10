set +e
f(){ find . -name "$1.java" | head -1; }
sed -n 1000,1025p $(f GuiGraphicsExtractor)
