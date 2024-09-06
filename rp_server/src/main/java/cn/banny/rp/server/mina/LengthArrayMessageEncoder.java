package cn.banny.rp.server.mina;

import cn.banny.rp.ReverseProxy;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderAdapter;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * @author zhkl0228
 *
 */
public class LengthArrayMessageEncoder extends ProtocolEncoderAdapter {
	
	private static final Logger log = LoggerFactory.getLogger(LengthArrayMessageEncoder.class);

	/* (non-Javadoc)
	 * @see org.apache.mina.filter.codec.ProtocolEncoder#encode(org.apache.mina.core.session.IoSession, java.lang.Object, org.apache.mina.filter.codec.ProtocolEncoderOutput)
	 */
	@Override
	public void encode(IoSession session, Object message,
			ProtocolEncoderOutput out) {
		ByteBuffer data = (ByteBuffer) message;
		
		data.mark();
		data.putInt(data.remaining() - 4);
		data.reset();
		
		if(log.isDebugEnabled()) {
			data.mark();
			data.position(4);
			byte[] temp = new byte[data.remaining()];
			data.get(temp);
			data.reset();
			ReverseProxy.inspect(temp, "LengthArrayMessageEncoder.encode");
		}
		
		out.write(IoBuffer.wrap(data));
	}

}
