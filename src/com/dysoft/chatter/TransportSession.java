package com.dysoft.chatter;

import java.nio.ByteBuffer;

/**
 * @author Sean Micklethwaite
 *         22-Aug-2010
 */
public interface TransportSession {
	void onReceive(ByteBuffer data);
	void onClose();
}
