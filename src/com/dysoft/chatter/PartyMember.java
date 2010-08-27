package com.dysoft.chatter;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * @author Sean Micklethwaite
 *         24-Aug-2010
 */
public class PartyMember {
	final String name;
	PeerSession session = null;

	public PartyMember(String name) {
		this.name = name;
	}

	public PartyMember(ByteBuffer buf, int len) {
		int nameLen = buf.getInt();
		byte [] nameBytes = new byte [nameLen];
		buf.get(nameBytes);
		name = new String(nameBytes, Message.CHARSET);
	}

	public int getEncodedLength() {
		return name.getBytes(Message.CHARSET).length + 4;
	}

	public void encode(ByteBuffer buf) {
		byte [] nameBytes = name.getBytes(Message.CHARSET);
		buf.putInt(nameBytes.length);
		buf.put(nameBytes);
	}
}
