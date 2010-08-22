package com.dysoft.chatter;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sean Micklethwaite
 *         22-Aug-2010
 */
public class Message {
	public final static byte VERSION = (byte)1;
	
	public interface Codec {
		Message decode(ByteBuffer buf);
	}
	
	public enum Type {
		HEARTBEAT(0),
		PARTY_BROADCAST(1),
		;
		
		final static Map<Byte, Type> typeMap = new HashMap<Byte, Type>();
		static {
			for(Type t : values()) {
				typeMap.put(t.tag, t);
			}
		}

		public final byte tag;
		
		Type(int tag) {
			this.tag = (byte)tag;
		}

		public static Type lookup(byte tag) {
			return typeMap.get(tag);
		}
	}
	
	
	final Type type;

	public Message(Type type) {
		this.type = type;
	}

	void encode(ByteBuffer buf) {
		buf.put(VERSION);
		buf.put(type.tag);
	}
	
	
	//// STATIC
	
	final static Map<Type, Codec> CODECS = new HashMap<Type, Codec>();
	
	public static Message decode(ByteBuffer buf) {
		byte version = buf.get();
		byte tag = buf.get();
		Type type = Type.lookup(tag);
		
		if(type != null) {
			Codec codec = CODECS.get(type);
			return codec.decode(buf);
		} else {
			return null;
		}
	}
}
