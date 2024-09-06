package cn.banny.rp.server.mina;

import cn.banny.rp.ReverseProxy;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author zhkl0228
 *
 */
public class LengthArrayMessageDecoder extends CumulativeProtocolDecoder {

	private static final Logger log = LoggerFactory.getLogger(LengthArrayMessageDecoder.class);

	@Override
	protected boolean doDecode(IoSession session, IoBuffer in,
			ProtocolDecoderOutput out) {
		if(in.remaining() < 4) {
			return false;
		}
		
		in.mark();
		int length = in.getInt();
		if(in.remaining() < length) {
			in.reset();
			return false;
		}
		
		byte[] data = new byte[length];
		in.get(data);

		if (log.isDebugEnabled()) {
			ReverseProxy.inspect(data, "LengthArrayMessageDecoder.doDecode session=" + session);
		}

		ByteBuffer msg = ByteBuffer.wrap(data);
		msg.order(ByteOrder.BIG_ENDIAN);
		out.write(msg);
		
		return true;
	}

}
