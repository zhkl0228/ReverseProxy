jsvc -Djava.awt.headless=true \
	-home /usr/java/default \
	-stop -cp `find $(dirname $0)/lib -name '*.jar' | tr '\n' ':'` \
	-pidfile $(dirname $0)/logs/jsvc_rp cn.banny.rp.client.ReverseProxyDaemon
