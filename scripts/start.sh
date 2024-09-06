#su - root -s /bin/bash -c "cd /usr/local/rp ; ./start.sh"
#route add -net 192.168.1.0 netmask 255.255.255.0 gw 10.0.2.2
jsvc -Djava.awt.headless=true \
	-home /usr/java/default \
	-cp `find $(dirname $0)/lib -name '*.jar' | tr '\n' ':'` \
	-outfile $(dirname $0)/logs/rp.out \
	-errfile $(dirname $0)/logs/rp.err \
	-pidfile $(dirname $0)/logs/jsvc_rp \
	-procname rp cn.banny.rp.client.ReverseProxyDaemon
