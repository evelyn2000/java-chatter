package com.dysoft.chatter;

import java.nio.ByteBuffer;

/**
 * @author Sean Micklethwaite
 *         25-Aug-2010
 */
public class PartyCodec {
	
	public PartyMember decodePartyMember(ByteBuffer buf, int memberSize) {
		return new PartyMember(buf, memberSize);
	}
}
