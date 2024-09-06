#!/bin/sh
#
# chkconfig: 345 90 10
# description: Reverse proxy client service script
#

# The directory where you own program resides
DAEMON_HOME=/usr/local/rp

# your arguments for your program
DAEMON_ARGS=""

# Give this daemon a nicer name so you know what is running
NAME=rp

# Make sure you installed jsvc. This is the location where it should reside
DAEMON=/usr/local/bin/jsvc

# Set to whatever your java home is
JAVA_HOME=/usr/java/default

# Some Java Opts - set your own
JAVA_OPTS="$JAVA_OPTS -Djava.awt.headless=true"

# The location of the libraries for your program. Either pur here a list of the files or use this little script to just include all jars from the /lib folder
CLASSPATH=$(echo "$DAEMON_HOME/lib"/*.jar | tr ' ' ':')

# Define other required variables
DAEMON_PID="/var/run/jsvc_$NAME"
JSVC_BOOTSTRAP=cn.banny.rp.client.ReverseProxyDaemon

START_STOP_DAEMON=/usr/local/bin/start-stop-daemon

########### Configuration end, rest is script executing the daemon

if [ `id -u` -ne 0 ]; then
    echo "You need root privileges to run this script"
    exit 1
fi

# Source function library.
. /etc/rc.d/init.d/functions

# Source networking configuration.
. /etc/sysconfig/network

if [ ! -f "$DAEMON" ]; then
    echo "missing $DAEMON"
    exit 1
fi

if [ ! -f "$START_STOP_DAEMON" ]; then
    echo "missing $START_STOP_DAEMON"
    exit 1
fi

start() {
    if [ -z "$JAVA_HOME" ]; then
        echo "no JDK found - please set JAVA_HOME"
        exit 1
    fi

    if $START_STOP_DAEMON --test --start --pidfile "$DAEMON_PID" \
        --startas "$JAVA_HOME/bin/java" \
        >/dev/null; then

    	echo "Starting $NAME $DEBUG_MODE"
    	rm -f $DAEMON_PID

		# Make sure number of open files limit is high enough
		ulimit -n 1048576

        cd "$DAEMON_HOME"
        $DAEMON -cp "$CLASSPATH" \
            -procname $NAME -jvm server -home "$JAVA_HOME" \
            -outfile "$DAEMON_HOME/logs/rp.out" \
            -errfile "$DAEMON_HOME/logs/rp.err" \
            -pidfile "$DAEMON_PID" $JAVA_OPTS \
            "$JSVC_BOOTSTRAP" $DAEMON_ARGS

        sleep 3
        
        if $START_STOP_DAEMON --test --start --pidfile "$DAEMON_PID" \
            --startas "$JAVA_HOME/bin/java" \
            >/dev/null; then
            exit 1
        else
            exit 0
        fi
    else
        echo "$NAME already running with pid `cat $DAEMON_PID`"
        exit 0
    fi
}

stop() {
    if $START_STOP_DAEMON --test --start --pidfile "$DAEMON_PID" \
        --startas "$JAVA_HOME/bin/java" \
        >/dev/null; then
        echo "$NAME is not running."
    else
    	echo "Stopping $NAME"
        $DAEMON -cp "$CLASSPATH" -home "$JAVA_HOME" \
        	-pidfile "$DAEMON_PID" \
        	-stop "$JSVC_BOOTSTRAP"
    fi
    exit 0
}

status() {
	if $START_STOP_DAEMON --test --start --pidfile "$DAEMON_PID" --startas "$JAVA_HOME/bin/java" >/dev/null; then
        if [ -f "$DAEMON_PID" ]; then
            echo "$NAME is not running, but pid file exists."
            exit 1
        else
            echo "$NAME is not running."
            exit 3
        fi
    else
        echo "$NAME is running with pid `cat $DAEMON_PID`"
    fi
}

case "$1" in
  start)
    start
    ;;
  stop)
    stop
    ;;
  status)
    status
    ;;
  restart|force-reload)
    if ! $START_STOP_DAEMON --test --start --pidfile "$DAEMON_PID" --startas "$JAVA_HOME/bin/java" >/dev/null; then
        $0 stop
        sleep 1
    fi
    $0 start
    ;;
  debug)
	JAVA_OPTS="$JAVA_OPTS -Xdebug"
	JAVA_OPTS="$JAVA_OPTS -Xrunjdwp:transport=dt_socket,server=y,address=8000"
    DEBUG_MODE="with debug mode"
    start
    ;;
  install)
	cp $0 /etc/init.d/$NAME
	chkconfig --add $NAME
	chkconfig $NAME on
	;;
  uninstall)
	chkconfig --del $NAME
	rm -f /etc/init.d/$NAME
	;;
  *)
    echo "Usage: $0 {start|stop|restart|status|debug|install|uninstall}"
    exit 1
    ;;
esac

exit 0
