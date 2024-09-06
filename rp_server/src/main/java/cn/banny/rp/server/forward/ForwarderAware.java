package cn.banny.rp.server.forward;

import cn.banny.rp.forward.RouteForwarder;
import cn.banny.rp.io.NIOSocketSessionDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;

/**
 * @author zhkl0228
 *
 */
public abstract class ForwarderAware {
	
	private static final Logger log = LoggerFactory.getLogger(ForwarderAware.class);
	
	/**
	 * @return 所有Route映射器
	 */
	protected abstract RouteForwarder[] getForwarders();
	
	protected final void checkSelector(Selector selector, NIOSocketSessionDispatcher dispatcher) {
		try { 
			if(selector.select() < 1) {
				for(RouteForwarder forwarder : getForwarders()) {
					forwarder.checkForwarder();
				}
				return;
			}
			
			Set<SelectionKey> selectionKeys = selector.selectedKeys();
			dispatcher.dispatch(selectionKeys);
		} catch(Throwable t) {
			if(log.isDebugEnabled()) {
				log.debug(t.getMessage(), t);
			}
		}
	}

}
