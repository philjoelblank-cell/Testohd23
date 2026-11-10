set +e
f(){ find . -name "$1.java" | head -1; }
echo "### CAPELAYER"; sed -n '/public void submit/,$p' $(f CapeLayer)
echo "### SNC"; grep -n "submitModel" $(f SubmitNodeCollector) $(f OrderedSubmitNodeCollector) | head
