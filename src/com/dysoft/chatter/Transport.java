package com.dysoft.chatter;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sean Micklethwaite
 *         Aug 14, 2010 5:49:12 PM
 */
public interface Transport {
	void send(ByteBuffer data) throws IOException;
	void close() throws IOException;
}
